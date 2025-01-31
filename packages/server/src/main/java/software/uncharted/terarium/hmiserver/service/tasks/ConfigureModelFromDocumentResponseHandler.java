package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.configurations.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceRelationType;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationService;
import software.uncharted.terarium.hmiserver.service.data.ProvenanceService;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigureModelFromDocumentResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:configure_model_from_document";

	private final ObjectMapper objectMapper;
	private final ModelConfigurationService modelConfigurationService;
	private final ProvenanceService provenanceService;
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
		String amr;
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
			final Response configurations = objectMapper.readValue(resp.getOutput(), Response.class);

			// For each configuration, create a new model configuration
			for (final JsonNode condition : configurations.response.get("conditions")) {
				final ModelConfiguration configuration = objectMapper.treeToValue(condition, ModelConfiguration.class);

				if (configuration.getModelId() != props.modelId) {
					configuration.setModelId(props.modelId);
				}

				if (configuration.getExtractionDocumentId() != props.documentId) {
					configuration.setExtractionDocumentId(props.documentId);
				}

				// Fetch the document name
				final Optional<DocumentAsset> document = documentAssetService.getAsset(
					props.documentId,
					ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER
				);
				final String source = document.map(TerariumAsset::getName).orElse(null);

				// Set the extraction document id
				configuration.setExtractionDocumentId(props.documentId);

				// Update the source of the model-configuration with the Document name
				if (source != null) {
					configuration.getInitialSemanticList().forEach(initial -> initial.setSource(source));
					configuration.getParameterSemanticList().forEach(parameter -> parameter.setSource(source));
				}

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
