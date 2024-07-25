import { WorkflowOperationTypes } from '@/types/workflow';
import type { Operation, BaseState } from '@/types/workflow';
import type { ModelConfiguration } from '@/types/Types';

export const name = 'ModelConfigOperation';

export interface ModelEditCode {
	code: string;
	timestamp: number;
}

export interface ModelConfigOperationState extends BaseState {
	transientModelConfig: ModelConfiguration;
	modelEditCodeHistory: ModelEditCode[];
	hasCodeBeenRun: boolean;
}

export const blankModelConfig: ModelConfiguration = {
	id: '',
	modelId: '',
	name: '',
	description: '',
	calibrationRunId: '',
	observableSemanticList: [],
	parameterSemanticList: [],
	initialSemanticList: []
};

export const ModelConfigOperation: Operation = {
	name: WorkflowOperationTypes.MODEL_CONFIG,
	displayName: 'Configure model',
	description: 'Create model configurations.',
	isRunnable: true,
	inputs: [
		{ type: 'modelId', label: 'Model' },
		{ type: 'documentId', label: 'Document', isOptional: true },
		{ type: 'datasetId', label: 'Dataset', isOptional: true }
	],
	outputs: [{ type: 'modelConfigId', label: 'Model configuration' }],
	action: async () => ({}),
	initState: () => {
		const init: ModelConfigOperationState = {
			modelEditCodeHistory: [],
			hasCodeBeenRun: false,
			transientModelConfig: blankModelConfig
		};
		return init;
	}
};
