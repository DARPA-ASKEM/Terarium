package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;

import java.util.UUID;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModelCardResponseHandler extends TaskResponseHandler {
	final static public String NAME = "gollm:model_card";
	final private ObjectMapper objectMapper;
	final private DocumentAssetService documentAssetService;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void onSuccess(final Consumer<TaskResponse> resp) {
		try {
			final String serializedString = objectMapper.writeValueAsString(((TaskResponse) resp).getAdditionalProperties());
			final Properties props = objectMapper.readValue(serializedString, Properties.class);
			log.info("Writing model card to database for document {}", props.getDocumentId());
			final DocumentAsset document = documentAssetService.getAsset(props.getDocumentId())
				.orElseThrow();
			final Response card = objectMapper.readValue(((TaskResponse) resp).getOutput(), Response.class);
			if (document.getMetadata() == null) {
				document.setMetadata(new java.util.HashMap<>());
			}
			document.getMetadata().put("gollmCard", card.response);

			documentAssetService.updateAsset(document);
		} catch (final Exception e) {
			log.error("Failed to write model card to database", e);
			throw new RuntimeException(e);
		}
	}

	@Data
	public static class Input {
		@JsonProperty("research_paper")
		String researchPaper;
	}

	@Data
	public static class Response {
		JsonNode response;
	}

	@Data
	public static class Properties {
		UUID documentId;
	}
}
