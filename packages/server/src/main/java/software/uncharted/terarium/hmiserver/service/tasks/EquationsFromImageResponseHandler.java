package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class EquationsFromImageResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:equations_from_image";

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		// base64 encoded image
		String image;
	}

	@Data
	public static class Properties {

		UUID projectId;
		UUID documentId;
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
