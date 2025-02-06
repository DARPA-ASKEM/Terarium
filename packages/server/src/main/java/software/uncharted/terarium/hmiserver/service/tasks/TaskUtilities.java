package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	public static TaskRequest getEnrichModelTaskRequest(
		String userId,
		DocumentAsset document,
		Model model,
		UUID projectId,
		Boolean overwrite
	) throws IOException {
		final ObjectMapper objectMapper = new ObjectMapper();

		final EnrichModelResponseHandler.Input input = new EnrichModelResponseHandler.Input();
		if (document != null) {
			try {
				input.setDocument(objectMapper.writeValueAsString(document.getExtractions()));
			} catch (JsonProcessingException e) {
				throw new IOException("Unable to serialize document text");
			}
		}

		input.setAmr(model.serializeWithoutTerariumFields(null, null));

		// Create the task
		final TaskRequest req = new TaskRequest();
		req.setType(TaskRequest.TaskType.GOLLM);
		req.setScript(EnrichModelResponseHandler.NAME);
		req.setUserId(userId);

		try {
			req.setInput(objectMapper.writeValueAsBytes(input));
		} catch (final Exception e) {
			throw new IOException("Unable to serialize input");
		}

		req.setProjectId(projectId);

		final EnrichModelResponseHandler.Properties props = new EnrichModelResponseHandler.Properties();
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

	@Deprecated
	@Observed(name = "function_profile")
	public static void performDKGSearchAndSetGrounding(DKGService dkgService, List<? extends GroundedSemantic> parts) {
		// First check if we have a curated grounding match for the parts
		getCuratedGrounding(parts);

		// Create a set to store unique search terms
		Set<String> uniqueSearchTerms = new HashSet<>();

		// Create a map to store the search terms and their corresponding parts
		Map<String, GroundedSemantic> searchTermToPartMap = parts
			.stream()
			.filter(part -> (part != null && getSearchTerm(part) != null))
			.filter(part -> uniqueSearchTerms.add(getSearchTerm(part))) // Filter out duplicates
			.collect(Collectors.toMap(TaskUtilities::getSearchTerm, part -> part));

		// Perform the DKG search for all search terms at once
		final List<String> searchTerms = new ArrayList<>(searchTermToPartMap.keySet());
		List<DKG> listDKG = new ArrayList<>();
		try {
			if (!searchTerms.isEmpty()) listDKG = dkgService.knnSearchEpiDKG(0, 100, 1, searchTerms, null);
		} catch (Exception e) {
			log.warn("Unable to find DKG for semantics: {}", searchTerms, e);
		}

		// Map the DKG results back to the corresponding parts using an index
		for (int i = 0; i < listDKG.size(); i++) {
			DKG dkg = listDKG.get(i);
			String searchTerm = searchTerms.get(i);
			GroundedSemantic part = searchTermToPartMap.get(searchTerm);
			if (part != null) {
				part.setGrounding(new Grounding(dkg));
			}
		}
	}

	/** Perform a search for curated groundings for all parts. */
	@Observed(name = "function_profile")
	public static void getCuratedGrounding(List<? extends GroundedSemantic> parts) {
		for (GroundedSemantic part : parts) {
			if (part == null) continue;
			final Grounding curatedGrounding = ContextMatcher.searchBest(getNameSearchTerm(part));
			if (curatedGrounding != null) {
				final Grounding newGrounding = part.getGrounding();
				if (newGrounding == null) {
					part.setGrounding(curatedGrounding);
				} else {
					newGrounding.setIdentifiers(curatedGrounding.getIdentifiers());
					newGrounding.setContext(curatedGrounding.getContext());
					part.setGrounding(newGrounding);
				}
			}
		}
	}

	/** Get the search term for a grounded semantic part. This is the description if it exists, otherwise the name. */
	private static String getSearchTerm(GroundedSemantic part) {
		return (part.getDescription() == null || part.getDescription().isBlank()) ? part.getName() : part.getDescription();
	}

	/** Get the search term for a grounded semantic part. This is the name if it exists, otherwise the id. */
	private static String getNameSearchTerm(GroundedSemantic part) {
		return (part.getName() == null || part.getName().isBlank()) ? part.getConceptReference() : part.getName();
	}

	/** Check if a grounding is non-existent. */
	private static Boolean isGroundingNonExistent(Grounding grounding) {
		return grounding == null || grounding.isEmpty();
	}
}
