package software.uncharted.terarium.esingest.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElasticIngestService {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private BlockingQueue<List<String>> workQueue = new LinkedBlockingQueue<>();

	private List<String> errors = Collections.synchronizedList(new ArrayList<>());

	private final int ERROR_THRESHOLD = 10;

	private final int BULK_SIZE = 1000;

	private final int POOL_SIZE = 8;

	private final ElasticsearchService esService;

	private ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
	private List<Future<Void>> futures = new ArrayList<>();

	private ElasticIngestParams params;

	private List<Path> getFilesInDir(Path dir) {
		List<Path> files = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path file : stream) {
				// Process the file here
				// For example, you can print the filename
				System.out.println(file.getFileName());
				files.add(file);
			}
		} catch (IOException e) {
			log.error("Error reading directory", e);
		}
		return files;
	}

	private <InputType, OutputType> void startIngestDocumentWorkers(Function<InputType, OutputType> processor) {
		for (int i = 0; i < POOL_SIZE; i++) {
			futures.add(executor.submit(() -> {
				while (true) {
					try {
						List<String> items = workQueue.take();
						if (items.size() == 0) {
							break;
						}

						List<Object> output = new ArrayList<>();
						for (String item : items) {
							InputType input = objectMapper.readValue(item, new TypeReference<InputType>() {
							});
							OutputType out = processor.apply(input);
							if (out != null) {
								output.add(out);
							}
						}

						List<String> errs = esService.bulkIndex(params.getOutputIndex(), output);
						if (errs.size() > 0) {
							errors.addAll(errs);
							if (errors.size() > ERROR_THRESHOLD) {
								log.error("Too many errors, stopping ingest");
								break;
							}
						}

					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				return null;
			}));
		}
	}

	private <InputType, OutputType> void startIngestEmbeddingsWorkers(Function<InputType, OutputType> processor) {
		for (int i = 0; i < POOL_SIZE; i++) {
			futures.add(executor.submit(() -> {
				while (true) {
					try {
						List<String> items = workQueue.take();
						if (items.size() == 0) {
							break;
						}

						List<Object> output = new ArrayList<>();
						for (String item : items) {
							InputType input = objectMapper.readValue(item, new TypeReference<InputType>() {
							});
							OutputType out = processor.apply(input);
							if (out != null) {
								output.add(out);
							}
						}

						// TODO: implement bulk update
						// List<String> errs = esService.bulkUpdate(output);
						// if (errs.size() > 0) {
						// errors.addAll(errs);
						// if (errors.size() > ERROR_THRESHOLD) {
						// log.error("Too many errors, stopping ingest");
						// break;
						// }
						// }

					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				return null;
			}));
		}
	}

	private void waitUntilWorkersAreDone() throws InterruptedException {

		// now lets dispatch the worker kill signals (empty lists)
		for (int i = 0; i < POOL_SIZE; i++) {
			workQueue.put(new ArrayList<>());
		}

		// now we wait for them to finish
		for (Future<Void> future : futures) {
			try {
				future.get();
			} catch (Exception e) {
				log.error("Error waiting on workers to finish", e);
			}
		}

		futures.clear();
	}

	private void readLinesIntoWorkQueue(Path p) throws InterruptedException {
		List<Path> paths = getFilesInDir(p);
		for (Path path : paths) {
			// read the file and put the lines into the work queue
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				List<String> lines = new ArrayList<>();
				for (String line; (line = reader.readLine()) != null;) {
					lines.add(line);
					if (lines.size() == BULK_SIZE) {
						workQueue.put(lines);
						lines = new ArrayList<>();
					}
				}
				// process the remaining lines if there are any
				if (!lines.isEmpty()) {
					workQueue.put(lines);
				}
			} catch (IOException e) {
				log.error("Error reading file", e);
			}
		}
	}

	public <DocInputType, DocOutputType, EmbeddingInputType, EmbeddingOutputType> void ingestData(
			ElasticIngestParams params,
			Function<DocInputType, DocOutputType> docProcessor,
			Function<EmbeddingInputType, EmbeddingOutputType> embeddingProcessor)
			throws InterruptedException {

		this.params = params;

		// first we insert the documents

		startIngestDocumentWorkers(docProcessor);

		readLinesIntoWorkQueue(Paths.get(params.getInputDir()).resolve("documents"));

		waitUntilWorkersAreDone();

		// then we insert the embeddings

		startIngestEmbeddingsWorkers(embeddingProcessor);

		readLinesIntoWorkQueue(Paths.get(params.getInputDir()).resolve("embeddings"));

		waitUntilWorkersAreDone();

	}

}
