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
public class SbmlToPetrinetResponseHandler extends TaskResponseHandler {

	public static final String NAME = "mira_task:sbml_to_petrinet";

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

			model
				.getParameters()
				.forEach(param -> {
					if (param.getName() == null || param.getName().isEmpty()) {
						param.setName(param.getConceptReference());
					}
				});

			final ConversionAdditionalProperties props = resp.getAdditionalProperties(ConversionAdditionalProperties.class);
			model = modelService.createAsset(
				modelResp.getResponse(),
				props.getProjectId(),
				ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER
			);
			resp.setOutput(objectMapper.writeValueAsString(model).getBytes());
		} catch (final Exception e) {
			log.error("Failed to create model", e);
			throw new RuntimeException(e);
		}
		return resp;
	}
}
