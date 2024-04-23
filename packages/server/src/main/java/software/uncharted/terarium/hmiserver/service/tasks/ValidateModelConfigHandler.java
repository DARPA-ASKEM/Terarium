package software.uncharted.terarium.hmiserver.service.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidateModelConfigHandler extends TaskResponseHandler {
	public static final String NAME = "funman_task:validate_modelconfig";

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Properties {
		UUID simulationId;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		try {
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final UUID simulationId = props.getSimulationId();

			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! " + simulationId.toString());
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
		return resp;
	}

}
