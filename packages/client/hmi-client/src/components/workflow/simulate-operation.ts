import { Operation, WorkflowOperationTypes } from '@/types/workflow';

export const SimulateOperation: Operation = {
	name: WorkflowOperationTypes.SIMULATE,
	description: 'given a model id, and configuration id, run a simulation',
	inputs: [{ type: 'modelConfigId', acceptMultiple: true }],
	outputs: [{ type: 'simOutput' }],
	isRunnable: true,

	// TODO: Figure out mapping
	// Calls API, returns results.
	action: async (): Promise<void> => {
		console.log('test');
	}
};
