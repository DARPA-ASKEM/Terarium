package software.uncharted.terarium.hmiserver.service.tasks;

import static software.uncharted.terarium.hmiserver.utils.JsonToHTML.renderJsonToHTML;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.DKGService;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichDatasetResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:enrich_dataset";

	private final ObjectMapper objectMapper;
	private final DatasetService datasetService;
	private final DKGService dkgService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		@JsonProperty("document")
		String document;

		@JsonProperty("dataset")
		String dataset;
	}

	@Data
	private static class Enrichment {

		JsonNode card;
		JsonNode columns;
	}

	@Data
	public static class Response {

		Enrichment response;
	}

	@Data
	public static class Properties {

		UUID projectId;
		UUID documentId;
		UUID datasetId;
		Boolean overwrite;
	}

	private void removeNullNodes(ObjectNode objectNode) {
		Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
		List<String> keysToRemove = new ArrayList<>();

		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			JsonNode value = entry.getValue();
			if (value.isNull()) {
				keysToRemove.add(entry.getKey());
			} else if (value.isObject()) {
				removeNullNodes((ObjectNode) value);
			} else if (value.isArray()) {
				for (JsonNode arrayItem : value) {
					if (arrayItem.isObject()) {
						removeNullNodes((ObjectNode) arrayItem);
					}
				}
			}
		}

		for (String key : keysToRemove) {
			objectNode.remove(key);
		}
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		try {
			final Properties properties = resp.getAdditionalProperties(Properties.class);
			final Response response = objectMapper.readValue(resp.getOutput(), Response.class);

			final Dataset dataset = datasetService
				.getAsset(properties.getDatasetId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER)
				.orElseThrow();

			// Create the metadata for the dataset if it doesn't exist
			if (dataset.getMetadata() == null) {
				dataset.setMetadata(objectMapper.createObjectNode());
			}

			// Update the dataset with the new card
			((ObjectNode) dataset.getMetadata()).set("dataCard", response.response.card);

			// Remove fields from the datacard that are null
			final ObjectNode dataCard = (ObjectNode) dataset.getMetadata().get("dataCard");
			removeNullNodes(dataCard);

			((ObjectNode) dataset.getMetadata()).put(
					"description",
					renderJsonToHTML(dataCard).getBytes(StandardCharsets.UTF_8)
				);

			// Update the dataset columns with the new descriptions
			for (final JsonNode enrichedColumn : response.response.columns) {
				final String name = enrichedColumn.get("name").asText();
				final String description = enrichedColumn.get("description").asText();
				final String unit = enrichedColumn.get("unit").asText();
				final String dataType = enrichedColumn.get("dataType").asText();

				// Create a JsonNode for the metadata (name, unit) if it doesn't exist
				final ObjectNode metadata = objectMapper.createObjectNode();
				metadata.put("name", name);
				metadata.put("description", description);
				metadata.put("unit", unit);

				dataset
					.getColumns()
					.stream()
					.filter(column -> column.getName().equalsIgnoreCase(name))
					.findFirst()
					.ifPresent(column -> {
						column.setDescription(description);
						column.setMetadata(metadata);
						column.setDataType(DatasetColumn.ColumnType.valueOf(dataType));
					});
			}

			// Update the dataset
			datasetService.updateAsset(dataset, dataset.getId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
		} catch (final Exception e) {
			log.error("Failed to enrich dataset", e);
			throw new RuntimeException(e);
		}

		log.info("Dataset enriched successfully");
		return resp;
	}
}
