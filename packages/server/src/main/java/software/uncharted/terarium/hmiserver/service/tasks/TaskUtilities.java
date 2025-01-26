package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.GroundedSemantic;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.service.ContextMatcher;
import software.uncharted.terarium.hmiserver.service.data.DKGService;

@Slf4j
public class TaskUtilities {

	public static TaskRequest getEnrichAMRTaskRequest(String userId, DocumentAsset document, Model model, UUID projectId)
		throws IOException {
		final ObjectMapper objectMapper = new ObjectMapper();

		final EnrichAmrResponseHandler.Input input = new EnrichAmrResponseHandler.Input();
		if (document != null) {
			try {
				input.setResearchPaper(objectMapper.writeValueAsString(document.getExtractions()));
			} catch (JsonProcessingException e) {
				throw new IOException("Unable to serialize document text");
			}
		}

		input.setAmr(model.serializeWithoutTerariumFields(null, null));

		// Create the task
		final TaskRequest req = new TaskRequest();
		req.setType(TaskRequest.TaskType.GOLLM);
		req.setScript(EnrichAmrResponseHandler.NAME);
		req.setUserId(userId);

		try {
			req.setInput(objectMapper.writeValueAsBytes(input));
		} catch (final Exception e) {
			throw new IOException("Unable to serialize input");
		}

		req.setProjectId(projectId);

		final EnrichAmrResponseHandler.Properties props = new EnrichAmrResponseHandler.Properties();
		props.setProjectId(projectId);
		if (document != null) props.setDocumentId(document.getId());
		props.setModelId(model.getId());
		req.setAdditionalProperties(props);

		return req;
	}

	public static TaskRequest getEnrichDatasetTaskRequest(
		String userId,
		DocumentAsset document,
		Dataset dataset,
		UUID projectId,
		Boolean overwrite
	) throws IOException {
		final ObjectMapper objectMapper = new ObjectMapper();

		final EnrichDatasetResponseHandler.Input input = new EnrichDatasetResponseHandler.Input();
		if (document != null) {
			try {
				input.setDocument(objectMapper.writeValueAsString(document.getExtractions()));
			} catch (JsonProcessingException e) {
				throw new IOException("Unable to serialize document text");
			}
		}

		// create a json object of the dataset that includes the name, description, and the columns. The columns should include the name and the DatasetColumnStats
		final ObjectNode datasetNode = objectMapper.createObjectNode();
		datasetNode.put("name", dataset.getName());
		datasetNode.put("description", dataset.getDescription());
		final ArrayNode columnsNode = objectMapper.createArrayNode();
		for (DatasetColumn column : dataset.getColumns()) {
			final ObjectNode columnNode = objectMapper.createObjectNode();
			columnNode.put("name", column.getName());
			columnNode.put("description", column.getDescription());
			columnNode.set("stats", objectMapper.valueToTree(column.getStats()));
			columnsNode.add(columnNode);
		}
		datasetNode.set("columns", columnsNode);
		final String serializedColumns = objectMapper.writeValueAsString(columnsNode);

		input.setDataset(serializedColumns);

		// Create the task
		final TaskRequest taskRequest = new TaskRequest();
		taskRequest.setType(TaskRequest.TaskType.GOLLM);
		taskRequest.setScript(EnrichDatasetResponseHandler.NAME);
		taskRequest.setUserId(userId);

		try {
			taskRequest.setInput(objectMapper.writeValueAsBytes(input));
		} catch (final Exception e) {
			throw new IOException("Unable to serialize input");
		}

		taskRequest.setProjectId(projectId);

		final EnrichDatasetResponseHandler.Properties properties = new EnrichDatasetResponseHandler.Properties();
		properties.setProjectId(projectId);
		if (document != null) properties.setDocumentId(document.getId());
		properties.setDatasetId(dataset.getId());
		properties.setOverwrite(overwrite);
		taskRequest.setAdditionalProperties(properties);

		return taskRequest;
	}

	public static TaskRequest getModelCardTask(String userId, DocumentAsset document, Model model, UUID projectId)
		throws IOException {
		final ObjectMapper objectMapper = new ObjectMapper();

		final ModelCardResponseHandler.Input input = new ModelCardResponseHandler.Input();
		input.setAmr(model.serializeWithoutTerariumFields(null, new String[] { "gollmCard" }));

		if (document != null) {
			try {
				input.setResearchPaper(objectMapper.writeValueAsString(document.getExtractions()));
			} catch (JsonProcessingException e) {
				throw new IOException("Unable to serialize document text");
			}
		} else {
			input.setResearchPaper("");
		}

		// Create the task
		final TaskRequest req = new TaskRequest();
		req.setType(TaskRequest.TaskType.GOLLM);
		req.setScript(ModelCardResponseHandler.NAME);
		req.setUserId(userId);

		try {
			req.setInput(objectMapper.writeValueAsBytes(input));
		} catch (final Exception e) {
			throw new IOException("Unable to serialize input");
		}

		req.setProjectId(projectId);

		final ModelCardResponseHandler.Properties props = new ModelCardResponseHandler.Properties();
		props.setProjectId(projectId);
		if (document != null) props.setDocumentId(document.getId());
		props.setModelId(model.getId());
		req.setAdditionalProperties(props);

		return req;
	}

	@Observed(name = "function_profile")
	public static void performDKGSearchAndSetGrounding(DKGService dkgService, List<? extends GroundedSemantic> parts) {
		// First check if we have a curated grounding match for the parts
		for (GroundedSemantic part : parts) {
			if (part == null || part.getGrounding() != null) continue;
			final Grounding curatedGrounding = ContextMatcher.searchBest(getSearchTerm(part));
			if (curatedGrounding != null) {
				part.setGrounding(curatedGrounding);
			}
		}

		// Then do a DKD search for the parts that don't have a grounding
		final List<String> searchTerms = parts
			.stream()
			.filter(part -> part != null && part.getId() != null && !part.getId().isBlank() && part.getGrounding() == null)
			.map(TaskUtilities::getSearchTerm)
			.collect(Collectors.toList());

		List<DKG> listDKG = new ArrayList<>();
		try {
			listDKG = dkgService.knnSearchEpiDKG(0, 100, 1, searchTerms, null);
		} catch (Exception e) {
			log.warn("Unable to find DKG for semantics: {}", searchTerms, e);
		}

		// I'm not sure that parts.get(i) fetch the appropriate part?
		for (int i = 0; i < listDKG.size(); i++) {
			DKG dkg = listDKG.get(i);
			parts.get(i).setGrounding(new Grounding(dkg));
		}
	}

	/**
	 * Get the search term for a grounded semantic part. This is the description if it exists, otherwise the name.
	 */
	private static String getSearchTerm(GroundedSemantic part) {
		return (part.getDescription() == null || part.getDescription().isBlank()) ? part.getName() : part.getDescription();
	}
}
