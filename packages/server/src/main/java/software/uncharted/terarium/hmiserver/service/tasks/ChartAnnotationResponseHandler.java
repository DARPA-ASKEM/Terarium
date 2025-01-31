package software.uncharted.terarium.hmiserver.service.tasks;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChartAnnotationResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:chart_annotation";

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		String preamble;
		String instruction;
	}

	@Data
	private static class ResponseOutput {

		private String response;
	}
}
