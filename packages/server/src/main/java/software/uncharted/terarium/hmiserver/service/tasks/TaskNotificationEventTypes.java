package software.uncharted.terarium.hmiserver.service.tasks;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.ClientEventType;

@Slf4j
public class TaskNotificationEventTypes {

	private static final Map<String, ClientEventType> clientEventTypes = Map.ofEntries(
		Map.entry(AMRToMMTResponseHandler.NAME, ClientEventType.TASK_MIRA_AMR_TO_MMT),
		Map.entry(DatasetStatisticsResponseHandler.NAME, ClientEventType.TASK_GOLLM_DATASET_STATISTICS),
		Map.entry(CompareModelsConceptsResponseHandler.NAME, ClientEventType.TASK_MIRA_COMPARE_MODELS_CONCEPTS),
		Map.entry(CompareModelsResponseHandler.NAME, ClientEventType.TASK_GOLLM_COMPARE_MODEL),
		Map.entry(ConfigureModelFromDatasetResponseHandler.NAME, ClientEventType.TASK_GOLLM_CONFIGURE_MODEL_FROM_DATASET),
		Map.entry(ConfigureModelFromDocumentResponseHandler.NAME, ClientEventType.TASK_GOLLM_CONFIGURE_MODEL_FROM_DOCUMENT),
		Map.entry(EnrichAmrResponseHandler.NAME, ClientEventType.TASK_GOLLM_ENRICH_AMR),
		Map.entry(EnrichDatasetResponseHandler.NAME, ClientEventType.TASK_GOLLM_ENRICH_DATASET),
		Map.entry(EquationsFromImageResponseHandler.NAME, ClientEventType.TASK_GOLLM_EQUATIONS_FROM_IMAGE),
		Map.entry(InterventionsFromDocumentResponseHandler.NAME, ClientEventType.TASK_GOLLM_INTERVENTIONS_FROM_DOCUMENT),
		Map.entry(InterventionsFromDatasetResponseHandler.NAME, ClientEventType.TASK_GOLLM_INTERVENTIONS_FROM_DATASET),
		Map.entry(ExtractEquationsResponseHandler.NAME, ClientEventType.TASK_EXTRACT_EQUATION_PDF),
		Map.entry(ExtractTablesResponseHandler.NAME, ClientEventType.TASK_EXTRACT_TABLE_PDF),
		Map.entry(ExtractTextResponseHandler.NAME, ClientEventType.TASK_EXTRACT_TEXT_PDF),
		Map.entry(GenerateSummaryHandler.NAME, ClientEventType.TASK_GOLLM_GENERATE_SUMMARY),
		Map.entry(ModelCardResponseHandler.NAME, ClientEventType.TASK_GOLLM_MODEL_CARD),
		Map.entry(ValidateModelConfigHandler.NAME, ClientEventType.TASK_FUNMAN_VALIDATION)
	);

	public static ClientEventType getTypeFor(final String taskName) {
		final ClientEventType eventType = clientEventTypes.get(taskName);
		if (eventType == null) {
			log.warn("Event type not found for task: " + taskName);
			return ClientEventType.TASK_UNDEFINED_EVENT;
		}
		return eventType;
	}
}
