package software.uncharted.terarium.hmiserver.service.s3;

import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;
import software.uncharted.terarium.hmiserver.utils.JSONLInputStreamReader;

/**
 * Service to download static Elasticsearch index from S3 and initialize it.
 */
@Slf4j
@Service
@Configuration
@RequiredArgsConstructor
public class DownloadStaticESIndexService {

	private final S3ClientService s3ClientService;
	private final ElasticsearchService elasticsearchService;

	@Value("${terarium.static-index-path}")
	private String staticIndexPath;

	private static final String EPI_ROOT = "epi_dkg_20250128";
	private static final String AWS_ID = "static-index";
	private final Environment env;

	private final List<IndexAndMapping> INDICES = List.of(new IndexAndMapping(EPI_ROOT));

	@Data
	private static class IndexAndMapping {

		private final String fileName;
		private final String indexName;
		private final String mappingFileName;

		public IndexAndMapping(String root) {
			this.fileName = root + ".jsonl";
			this.mappingFileName = root + "_mapping.json";
			this.indexName = root;
		}
	}

	private boolean isRunningTestProfile() {
		final String[] activeProfiles = env.getActiveProfiles();

		for (final String profile : activeProfiles) {
			if ("test".equals(profile)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Initialize the service by downloading and setting up the Elasticsearch indices.
	 */
	@PostConstruct
	@Async
	void init() {
		if (isRunningTestProfile()) return;

		final S3Service s3Service = s3ClientService.getS3Service(AWS_ID);
		INDICES.forEach(index -> {
			if (createIndexIfNotExists(s3Service, index)) insertDocumentsIntoIndex(s3Service, index);
		});
	}

	private boolean createIndexIfNotExists(S3Service s3Service, IndexAndMapping index) {
		if (!elasticsearchService.hasIndex(index.getIndexName())) {
			try {
				ResponseInputStream<GetObjectResponse> esMappingStream = s3Service.getObject(
					staticIndexPath,
					index.getMappingFileName()
				);
				String esMappingContent = new BufferedReader(new InputStreamReader(esMappingStream))
					.lines()
					.collect(Collectors.joining("\n"));
				elasticsearchService.createIndex(index.getIndexName(), esMappingContent);
				return true;
			} catch (Exception e) {
				log.error("Failed to read mapping file: {}", index.getMappingFileName(), e);
			}
		}
		return false;
	}

	private void insertDocumentsIntoIndex(S3Service s3Service, IndexAndMapping index) {
		try {
			ResponseInputStream<GetObjectResponse> esIndexStream = s3Service.getObject(staticIndexPath, index.getFileName());
			final JSONLInputStreamReader jsonlInputStreamReader = new JSONLInputStreamReader(10 * 1024 * 1024);
			jsonlInputStreamReader.read(esIndexStream, jsonNodes -> {
				final List<JsonNode> sources = jsonNodes.stream().map(node -> node.at("/_source")).toList();
				final List<String> ids = sources.stream().map(node -> node.at("/" + DKG.ID).asText()).toList();
				try {
					final BulkResponse response = elasticsearchService.bulkInsert(index.getIndexName(), sources, ids);
					if (response.errors()) {
						log.error(
							"Failed to insert documents into index {}: {}",
							staticIndexPath,
							response
								.items()
								.stream()
								.filter(item -> item.error() != null)
								.map(item -> item.error().toString())
								.collect(Collectors.joining(", "))
						);
					}
				} catch (IOException e) {
					log.error("Failed to insert documents into index {}", staticIndexPath, e);
				}
			});
		} catch (Exception e) {
			log.error("Failed to read index file: {}", index.getFileName(), e);
		}
	}
}
