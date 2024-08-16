package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.configurations.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceRelationType;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProvenanceService;
import software.uncharted.terarium.hmiserver.service.gollm.ScenarioExtraction;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigureModelResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm_task:configure_model";

	private final ObjectMapper objectMapper;
	private final ModelService modelService;
	private final ModelConfigurationService modelConfigurationService;
	private final ProvenanceService provenanceService;
	private final Config config;
	private final DocumentAssetService documentAssetService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		@JsonProperty("research_paper")
		String researchPaper;

		@JsonProperty("amr")
		Model amr;
	}

	@Data
	public static class Response {

		JsonNode response;
	}

	@Data
	public static class Properties {

		UUID projectId;
		UUID documentId;
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

			// For each configuration, create a new model configuration with parameters set
			for (final JsonNode condition : configurations.response.get("conditions")) {
				final Model modelCopy = model.clone();
				modelCopy.setId(model.getId());
				ScenarioExtraction.setNullDefaultModelInitials(modelCopy);
				ScenarioExtraction.setNullDefaultModelParameters(modelCopy);
				// Map the parameters values to the model
				if (condition.has("parameters")) {
					ScenarioExtraction.getModelParameters(condition.get("parameters"), modelCopy);
				}

				// Map the initials values to the model
				if (condition.has("initials")) {
					ScenarioExtraction.getModelInitials(condition.get("initials"), modelCopy);
				}

				// Create the new configuration
				final ModelConfiguration configuration = ModelConfigurationService.modelConfigurationFromAMR(
					modelCopy,
					condition.get("name").asText(),
					condition.get("description").asText()
				);

				// Fetch the document name
				final Optional<DocumentAsset> document = documentAssetService.getAsset(
					props.documentId,
					ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER
				);
				final String source = document.map(TerariumAsset::getName).orElse(null);

				// Update the source of the model-configuration with the Document name
				configuration.getInitialSemanticList().forEach(initial -> initial.setSource(source));
				configuration.getParameterSemanticList().forEach(parameter -> parameter.setSource(source));

				final ModelConfiguration newConfig = modelConfigurationService.createAsset(
					configuration,
					props.projectId,
					ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER
				);
				// add provenance
				provenanceService.createProvenance(
					new Provenance()
						.setLeft(newConfig.getId())
						.setLeftType(ProvenanceType.MODEL_CONFIGURATION)
						.setRight(props.documentId)
						.setRightType(ProvenanceType.DOCUMENT)
						.setRelationType(ProvenanceRelationType.EXTRACTED_FROM)
				);
			}
		} catch (final Exception e) {
			log.error("Failed to configure model", e);
			throw new RuntimeException(e);
		}
		log.info("Model configured successfully");
		return resp;
	}
}
