import { Operation, WorkflowOperationTypes } from '@/types/operator';

export interface DatasetTransformerState {
	datasetId: string | null;
	notebookSessionId?: string;
}

export const DatasetTransformerOperation: Operation = {
	name: WorkflowOperationTypes.DATASET_TRANSFORMER,
	description: 'Select a dataset',
	displayName: 'Dataset Transformer',
	isRunnable: true,
	inputs: [{ type: 'datasetId', label: 'Dataset' }],
	outputs: [],
	action: () => {},

	initState: () => {
		const init: DatasetTransformerState = {
			datasetId: null
		};
		return init;
	}
};
