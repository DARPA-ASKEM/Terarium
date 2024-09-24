package software.uncharted.terarium.hmiserver.models;

public enum ClientEventType {
	CLONE_PROJECT,
	HEARTBEAT,
	NOTIFICATION,
	SIMULATION_PYCIEMSS,
	SIMULATION_NOTIFICATION,
	FILE_UPLOAD_PROGRESS,
	EXTRACTION,
	EXTRACTION_PDF,
	// Events for the task runner notifications
	TASK_UNDEFINED_EVENT,
	TASK_GOLLM_MODEL_CARD,
	TASK_GOLLM_CONFIGURE_MODEL_FROM_DOCUMENT,
	TASK_GOLLM_CONFIGURE_MODEL_FROM_DATASET,
	TASK_GOLLM_COMPARE_MODEL,
	TASK_GOLLM_GENERATE_SUMMARY,
	TASK_FUNMAN_VALIDATION,
	TASK_GOLLM_ENRICH_AMR,
	TASK_MIRA_AMR_TO_MMT,
	TASK_MIRA_GENERATE_MODEL_LATEX,
	TASK_ENRICH_AMR, // deprecated use TASK_GOLLM_ENRICH_AMR
	WORKFLOW_UPDATE,
	WORKFLOW_DELETE,
	CHART_ANNOTATION_CREATE,
	CHART_ANNOTATION_DELETE,
	TASK_EXTRACT_TEXT_PDF,
	TASK_EXTRACT_TABLE_PDF,
	TASK_EXTRACT_EQUATION_PDF
}
