package software.uncharted.terarium.hmiserver.service.gollm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddingType;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddings;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddings.Embeddings;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

	private final ObjectMapper objectMapper;
	private final TaskService taskService;
	private final CurrentUserService currentUserService;

	private static final int REQUEST_TIMEOUT_MINUTES = 1;
	public static final String EMBEDDING_MODEL = "text-embedding-ada-002";

	@Data
	public static class GoLLMSearchRequest {

		private List<String> text;

		@JsonProperty("embedding_model")
		private String embeddingModel;
	}

	@Data
	private static class EmbeddingsResponse {

		List<double[]> response;
	}

	public TerariumAssetEmbeddings generateEmbeddings(final String input)
		throws TimeoutException, InterruptedException, ExecutionException, IOException {
		// create the embedding search request
		final GoLLMSearchRequest embeddingRequest = new GoLLMSearchRequest();
		embeddingRequest.setText(List.of(input));
		embeddingRequest.setEmbeddingModel(EMBEDDING_MODEL);

		final TaskRequest req = new TaskRequest();
		req.setTimeoutMinutes(REQUEST_TIMEOUT_MINUTES);
		req.setType(TaskType.GOLLM);
		req.setInput(embeddingRequest);
		req.setScript("gollm:embedding");
		try {
			req.setUserId(currentUserService.get().getId());
		} catch (final Exception e) {
			log.warn("No user id to associate with embedding request");
		}

		final TaskResponse resp = taskService.runTaskSync(req);

		final byte[] outputBytes = resp.getOutput();
		final JsonNode output = objectMapper.readTree(outputBytes);

		final EmbeddingsResponse embeddingResp = objectMapper.convertValue(output, EmbeddingsResponse.class);

		final Embeddings embeddingChunk = new Embeddings();
		embeddingChunk.setVector(embeddingResp.response.get(0));
		embeddingChunk.setEmbeddingId(UUID.randomUUID().toString());
		embeddingChunk.setSpan(new long[] { 0, input.length() });

		final TerariumAssetEmbeddings embeddings = new TerariumAssetEmbeddings();
		embeddings.getEmbeddings().add(embeddingChunk);
		return embeddings;
	}

	public Map<TerariumAssetEmbeddingType, TerariumAssetEmbeddings> generateEmbeddingsBySource(
		final Map<TerariumAssetEmbeddingType, String> input
	) throws TimeoutException, InterruptedException, ExecutionException, IOException {
		final List<TerariumAssetEmbeddingType> indices = new ArrayList<>();
		final List<String> inputs = new ArrayList<>();
		for (final TerariumAssetEmbeddingType key : input.keySet()) {
			indices.add(key);
			inputs.add(input.get(key));
		}

		// create the embedding search request
		final GoLLMSearchRequest embeddingRequest = new GoLLMSearchRequest();
		embeddingRequest.setText(inputs);
		embeddingRequest.setEmbeddingModel(EMBEDDING_MODEL);

		final TaskRequest req = new TaskRequest();
		req.setTimeoutMinutes(REQUEST_TIMEOUT_MINUTES);
		req.setType(TaskType.GOLLM);
		req.setInput(embeddingRequest);
		req.setScript("gollm:embedding");
		try {
			req.setUserId(currentUserService.get().getId());
		} catch (final Exception e) {
			log.warn("No user id to associate with embedding request");
		}

		final TaskResponse resp = taskService.runTaskSync(req);

		final byte[] outputBytes = resp.getOutput();
		final JsonNode output = objectMapper.readTree(outputBytes);

		final EmbeddingsResponse embeddingResp = objectMapper.convertValue(output, EmbeddingsResponse.class);

		final Map<TerariumAssetEmbeddingType, TerariumAssetEmbeddings> result = new HashMap<>();

		int index = 0;
		for (final double[] entry : embeddingResp.response) {
			final TerariumAssetEmbeddingType embeddingType = indices.get(index);
			final String source = input.get(embeddingType);

			final Embeddings embeddingChunk = new Embeddings();
			embeddingChunk.setVector(entry);
			embeddingChunk.setEmbeddingId(UUID.randomUUID().toString());
			embeddingChunk.setSpan(new long[] { 0, source.length() });

			final TerariumAssetEmbeddings embeddings = new TerariumAssetEmbeddings();
			embeddings.getEmbeddings().add(embeddingChunk);

			result.put(embeddingType, embeddings);

			index++;
		}

		return result;
	}
}
