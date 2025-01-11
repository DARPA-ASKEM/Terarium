package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class LatexToSympyResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:latex_to_sympy";

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		List<String> equations;
	}

	@Data
	public static class Response {

		JsonNode response;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		return resp;
	}
}
