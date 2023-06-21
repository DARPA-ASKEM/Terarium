import { Operation, WorkflowOperationTypes } from '@/types/workflow';

export const StratifyOperation: Operation = {
	name: WorkflowOperationTypes.STRATIFY,
	description: 'Stratify a model',
	inputs: [{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: false }],
	outputs: [{ type: 'model' }],
	isRunnable: false,
	action: () => {}
};
