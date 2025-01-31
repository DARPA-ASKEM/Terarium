import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import { NotebookHistory } from '@/services/notebook';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/modeling/compare-models/';

export interface ModelComparisonOperationState extends BaseState {
	notebookHistory: NotebookHistory[];
	hasCodeRun: boolean;
	comparisonImageIds: string[];
	comparisonPairs: string[][];
	goal: string;
	hasRun: boolean;
	previousRunId?: string;
}

export const ModelComparisonOperation: Operation = {
	name: WorkflowOperationTypes.MODEL_COMPARISON,
	displayName: 'Compare models',
	description: '',
	documentationUrl: DOCUMENTATION_URL,
	inputs: [
		{ type: 'modelId', label: 'Model' },
		{ type: 'modelId', label: 'Model' }
	],
	outputs: [],
	isRunnable: true,
	action: () => {},
	initState: () => {
		const init: ModelComparisonOperationState = {
			notebookHistory: [],
			hasCodeRun: false,
			comparisonImageIds: [],
			comparisonPairs: [],
			goal: '',
			hasRun: false
		};
		return init;
	}
};
