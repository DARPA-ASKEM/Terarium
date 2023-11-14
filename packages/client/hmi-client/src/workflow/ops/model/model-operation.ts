import { Operation, WorkflowOperationTypes } from '@/types/operator';

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
	outputs: [{ type: 'modelConfigId' }],
	action: async (modelConfigId: string) => [{ type: 'modelConfigId', value: modelConfigId }],

	initState: () => {
		const init: ModelOperationState = {
			modelId: null,
			modelConfigurationIds: []
		};
		return init;
	}
};
