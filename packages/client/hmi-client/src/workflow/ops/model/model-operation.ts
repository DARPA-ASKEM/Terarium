import { Operation, WorkflowOperationTypes } from '@/types/workflow';

export interface ModelOperationState {
	modelId: string | null;
	modelConfigurationIds: string[];
}

export const ModelOperation: Operation = {
	name: WorkflowOperationTypes.MODEL,
	displayName: 'Model',
	description: 'Select a model and configure its initial and parameter values.',
	isRunnable: true,
	inputs: [],
	outputs: [{ type: 'modelId' }],
	action: async () => ({}),
	initState: () => {
		const init: ModelOperationState = {
			modelId: null,
			modelConfigurationIds: []
		};
		return init;
	}
};
