package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.Summary;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.SummaryService;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateSummaryHandler extends TaskResponseHandler {
	public static final String NAME = "gollm_task:generate_summary";

	private final SummaryService summaryService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Properties {
		private UUID previousSummaryId;
		private UUID summaryId;
	}

	@Data
	private static class ResponseOutput {
		private String response;
	}

	@Override
	public TaskResponse onQueued(final TaskResponse resp) {
		try {
			// create a new summary
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final Summary newSummary = new Summary();
			newSummary.setId(props.getSummaryId());
			newSummary.setPreviousSummary(props.getPreviousSummaryId());
			summaryService.createAsset(newSummary, ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
		} catch (final Exception e) {
			log.error("Failed to create a summary: {}", e.getMessage());
		}
		return resp;
	}

	@Override
	public TaskResponse onFailure(final TaskResponse resp) {
		try {
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final Summary summary = summaryService
					.getAsset(props.getSummaryId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER)
					.orElseThrow();
			summary.setGeneratedSummary("Generating AI summary has failed.");
			summaryService.updateAsset(summary, ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
		} catch (final Exception e) {
			log.error("Failed to update the summary: {}", e.getMessage());
			throw new RuntimeException(e);
		}
		return resp;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		try {
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final String output = new String(resp.getOutput());
			ObjectMapper mapper = new ObjectMapper();
			GenerateSummaryHandler.ResponseOutput resOutput =
					mapper.readValue(output, GenerateSummaryHandler.ResponseOutput.class);
			final Summary summary = summaryService
					.getAsset(props.getSummaryId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER)
					.orElseThrow();
			summary.setGeneratedSummary(resOutput.response);
			summaryService.updateAsset(summary, ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
		} catch (final Exception e) {
			log.error("Failed to update the summary: ", e.getMessage());
			throw new RuntimeException(e);
		}
		return resp;
	}
}
