import { TimeSpan } from '@/types/Types';
import { Operation, WorkflowOperationTypes } from '@/types/workflow';
import { ChartConfig } from '@/types/SimulateConfig';

export interface SimulateJuliaOperationState {
	chartConfigs: ChartConfig[];
	currentTimespan: TimeSpan;
	simulationsInProgress: string[];
}

export const SimulateJuliaOperation: Operation = {
	name: WorkflowOperationTypes.SIMULATE_JULIA,
	displayName: 'Simulate (deterministic)',
	description: 'given a model id, and configuration id, run a simulation',
	inputs: [{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: false }],
	outputs: [{ type: 'simOutput' }],
	isRunnable: true,

	initState: () => {
		const init: SimulateJuliaOperationState = {
			chartConfigs: [],
			currentTimespan: { start: 1, end: 100 },
			simulationsInProgress: []
		};
		return init;
	},

	// TODO: Figure out mapping
	// Calls API, returns results.
	action: async (): Promise<void> => {
		console.log('test');
	}
};
