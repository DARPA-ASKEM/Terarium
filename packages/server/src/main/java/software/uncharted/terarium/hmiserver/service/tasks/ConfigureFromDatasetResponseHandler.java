package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.configurations.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Initial;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceRelationType;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProvenanceService;
import software.uncharted.terarium.hmiserver.service.gollm.ScenarioExtraction;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigureFromDatasetResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm_task:dataset_configure";

	private final ObjectMapper objectMapper;
	private final ModelService modelService;
	private final ModelConfigurationService modelConfigurationService;
	private final ProvenanceService provenanceService;
	private final DatasetService datasetService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		@JsonProperty("datasets")
		List<String> datasets;

		@JsonProperty("amr")
		Model amr;

		@JsonProperty("matrix_str")
		String matrixStr;
	}

	@Data
	public static class Response {

		JsonNode response;
	}

	@Data
	public static class Properties {

		List<UUID> datasetIds;
		UUID projectId;
		UUID modelId;
		UUID workflowId;
		UUID nodeId;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		try {
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final Model model = modelService
				.getAsset(props.getModelId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER)
				.orElseThrow();
			final Response configurations = objectMapper.readValue(resp.getOutput(), Response.class);

			// Map the parameters values to the model
			final Model modelCopy = (Model) model.clone();
			modelCopy.setId(model.getId());
			ScenarioExtraction.setNullDefaultModelInitials(modelCopy);
			ScenarioExtraction.setNullDefaultModelParameters(modelCopy);

			final JsonNode condition = configurations.getResponse().get("values");

			// Map the parameters values to the model
			if (condition.has("parameters")) {
				ScenarioExtraction.getModelParameters(condition.get("parameters"), modelCopy);
			}

			// Map the initials values to the model
			if (condition.has("initials")) {
				ScenarioExtraction.getModelInitials(condition.get("initials"), modelCopy);
			}

			// Fetch the dataset names
			final List<String> datasetsNames = new ArrayList<>(List.of());
			props.datasetIds.forEach(datasetId -> {
				final Optional<Dataset> dataset = datasetService.getAsset(datasetId, ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
				dataset.ifPresent(value -> datasetsNames.add(value.getName()));
			});
			final String source = String.join(", ", datasetsNames);

			// Create the new configuration
			final ModelConfiguration modelConfiguration = ModelConfigurationService.modelConfigurationFromAMR(
				modelCopy,
				"Configuration from dataset(s)",
				"This configuration was created from the dataset(s): " + source
			);

			// Update the source of the model-configuration with the datasets names
			modelConfiguration.getInitialSemanticList().forEach(initial -> initial.setSource(source));
			modelConfiguration.getParameterSemanticList().forEach(parameter -> parameter.setSource(source));

			try {
				for (final UUID datasetId : props.datasetIds) {
					final ModelConfiguration newConfig = modelConfigurationService.createAsset(
						modelConfiguration,
						props.projectId,
						ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER
					);
					// add provenance
					provenanceService.createProvenance(
						new Provenance()
							.setLeft(newConfig.getId())
							.setLeftType(ProvenanceType.MODEL_CONFIGURATION)
							.setRight(datasetId)
							.setRightType(ProvenanceType.DATASET)
							.setRelationType(ProvenanceRelationType.EXTRACTED_FROM)
					);
				}
			} catch (final IOException e) {
				log.error("Failed to set model configuration", e);
				throw new RuntimeException(e);
			}
		} catch (final Exception e) {
			log.error("Failed to configure model", e);
			throw new RuntimeException(e);
		}
		log.info("Model configured successfully");

		return resp;
	}
}
