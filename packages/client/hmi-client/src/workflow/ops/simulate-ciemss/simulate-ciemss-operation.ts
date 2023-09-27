import { TimeSpan } from '@/types/Types';
import { Operation, WorkflowOperationTypes } from '@/types/workflow';
import { ChartConfig } from '@/types/SimulateConfig';

export interface SimulateCiemssOperationState {
	chartConfigs: ChartConfig[];
	currentTimespan: TimeSpan;
	numSamples: number;
	method: string;
	simulationsInProgress: string[];
}

export const SimulateCiemssOperation: Operation = {
	name: WorkflowOperationTypes.SIMULATE_CIEMSS,
	displayName: 'Simulate (probabilistic)',
	description: 'given a model id, and configuration id, run a simulation',
	inputs: [{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: false }],
	outputs: [{ type: 'simOutput' }],
	isRunnable: true,

	initState: () => {
		const init: SimulateCiemssOperationState = {
			chartConfigs: [],
			currentTimespan: { start: 1, end: 100 },
			numSamples: 100,
			method: 'dopri5',
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
