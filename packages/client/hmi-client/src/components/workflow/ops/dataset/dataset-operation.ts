import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import dataset from '@assets/svg/operator-images/dataset.svg';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/datasets/review-and-enrich-dataset/';

export interface DatasetOperationState extends BaseState {
	datasetId: string | null;
}

export const DatasetOperation: Operation = {
	name: WorkflowOperationTypes.DATASET,
	description: 'Select a dataset',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: dataset,
	displayName: 'Dataset',
	isRunnable: true,
	inputs: [],
	outputs: [{ type: 'datasetId', label: 'Dataset' }],
	action: () => {},

	initState: () => {
		const init: DatasetOperationState = {
			datasetId: null
		};
		return init;
	}
};
