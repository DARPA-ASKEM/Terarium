package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.controller.mira.MiraController.ConversionAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.ModelService;

@Component
@RequiredArgsConstructor
@Slf4j
public class StellaToStockflowResponseHandler extends TaskResponseHandler {

	public static final String NAME = "mira_task:stella_to_stockflow";

	private final ObjectMapper objectMapper;
	private final ModelService modelService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Response {

		Model response;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		try {
			final Response modelResp = objectMapper.readValue(resp.getOutput(), Response.class);
			Model model = modelResp.getResponse();
			final ConversionAdditionalProperties props = resp.getAdditionalProperties(ConversionAdditionalProperties.class);
			// override the default stockflow name / description
			model.setName(props.getFileName());
			model.getHeader().setName(props.getFileName());

			model
				.getParameters()
				.forEach(param -> {
					if (param.getName() == null || param.getName().isEmpty()) {
						param.setName(param.getConceptReference());
					}
				});

			model = modelService.createAsset(model, props.getProjectId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
			resp.setOutput(objectMapper.writeValueAsString(model).getBytes());
		} catch (final Exception e) {
			log.error("Failed to create model", e);
			throw new RuntimeException(e);
		}
		return resp;
	}
}
