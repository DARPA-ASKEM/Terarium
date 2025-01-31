package software.uncharted.terarium.hmiserver.service.gollm;

import static software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn.mapDataType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumnStats;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumnStats.NonNumericColumnStats;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumnStats.NumericColumnStats;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.utils.Messages;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatasetStatistics {

	private final ObjectMapper objectMapper;
	private final TaskService taskService;
	final Messages messages;

	@Data
	public static class DatasetStatisticsRequest {

		private String datasetUrl;
	}

	// Following is based on the response from the Gollm service
	// packages/gollm/tasks/dataset_statistics.py
	@Data
	public static class DatasetStatisticsResponse {

		@JsonProperty("numeric_columns")
		private Map<String, NumericColumnStats> numericColumns;

		@JsonProperty("non_numeric_columns")
		private Map<String, NonNumericColumnStats> nonNumericColumns;

		@JsonProperty("total_rows")
		private int totalRows;

		@JsonProperty("total_columns")
		private int totalColumns;
	}

	/**
	 * Add statistics to the columns of a Dataset
	 * @param dataset The Dataset
	 * @throws IllegalArgumentException If the Dataset does not exist, the statistics have already been calculated, or the download URL is not found
	 * @throws IOException If there is an error reading the response from the Gollm service
	 * @throws ExecutionException If there is an error running the Task
	 * @throws InterruptedException If the Task is interrupted
	 * @throws TimeoutException If the Task times out
	 */
	public void add(final Dataset dataset, final PresignedURL datasetUrl)
		throws IllegalArgumentException, IOException, ExecutionException, InterruptedException, TimeoutException {
		// Check that the Statistics have already been calculated, as Dataset are immutable
		final boolean isStatisticsExist = dataset.getColumns().stream().anyMatch(column -> column.getStats() != null);
		if (isStatisticsExist) {
			log.info(messages.get("dataset.statistics.exist"));
			return;
		}

		// Create the request to the Gollm service
		final DatasetStatisticsRequest request = new DatasetStatisticsRequest();
		request.setDatasetUrl(datasetUrl.getUrl());

		final TaskRequest taskRequest = new TaskRequest();
		taskRequest.setType(TaskType.GOLLM);
		taskRequest.setInput(request);
		taskRequest.setScript("gollm:dataset_statistics");

		// Get the response from the Gollm service
		final TaskResponse taskResponse = taskService.runTaskSync(taskRequest);
		final byte[] outputBytes = taskResponse.getOutput();
		final JsonNode output = objectMapper.readTree(outputBytes);
		final DatasetStatisticsResponse response = objectMapper.convertValue(output, DatasetStatisticsResponse.class);

		if (response.getNumericColumns() == null && response.getNonNumericColumns() == null) {
			log.info("No statistics found for dataset {}, {}", dataset.getName(), dataset.getId());
			return;
		}

		// Update the numbers of rows and columns
		JsonNode metadata = dataset.getMetadata();
		if (metadata == null) {
			metadata = objectMapper.createObjectNode();
		}
		((ObjectNode) metadata).put("total_columns", response.getTotalColumns());
		((ObjectNode) metadata).put("total_rows", response.getTotalRows());
		dataset.setMetadata(metadata);

		// For each column, update the statistics
		dataset
			.getColumns()
			.stream()
			.filter(Objects::nonNull)
			.forEach(column -> {
				if (column.getStats() == null) {
					column.setStats(new DatasetColumnStats());
				}

				// Get the statistics for the column
				if (response.getNumericColumns() != null && response.getNumericColumns().containsKey(column.getName())) {
					final DatasetColumnStats.NumericColumnStats responseNumericColumn = response
						.getNumericColumns()
						.get(column.getName());
					column.setDataType(mapDataType(responseNumericColumn.getDataType()));
					column.getStats().setNumericStats(responseNumericColumn);
				}
				if (response.getNonNumericColumns() != null && response.getNonNumericColumns().containsKey(column.getName())) {
					final DatasetColumnStats.NonNumericColumnStats responseNonNumericColumn = response
						.getNonNumericColumns()
						.get(column.getName());
					column.setDataType(mapDataType(responseNonNumericColumn.getDataType()));
					column.getStats().setNonNumericStats(responseNonNumericColumn);
				}
			});

		log.info("Updated statistics for dataset \"{}\" — {}", dataset.getName(), dataset.getId());
	}
}
