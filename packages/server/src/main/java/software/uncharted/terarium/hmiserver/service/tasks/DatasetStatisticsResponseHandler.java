package software.uncharted.terarium.hmiserver.service.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatasetStatisticsResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:dataset_statistics";

	@Override
	public String getName() {
		return NAME;
	}
}
