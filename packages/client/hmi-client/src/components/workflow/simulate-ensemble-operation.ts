import { Operation, WorkflowOperationTypes } from '@/types/workflow';
// import { EnsembleRequest } from '@/types/Types';
// import { makeEnsembleJob } from '@/services/models/simulation-service';
import { ChartConfig } from '@/types/SimulateConfig';

export interface EnsembleMap {
	genericModelVariable: string;
	modelVariable: string;
}

export interface EnsembleOperationState {
	modelConfigIds: string[];
	chartConfigs: ChartConfig[];
	mapping: EnsembleMap[];
}

export const EnsembleOperation: Operation = {
	name: WorkflowOperationTypes.SIMULATEENSEMBLE,
	description: '',
	inputs: [{ type: 'modelConfigId', acceptMultiple: true }],
	outputs: [{ type: 'number' }],
	isRunnable: true,

	// TODO: Figure out mapping
	// Calls API, returns results.
	action: async (): Promise<void> => {
		console.log('test');
	},

	initState: () => {
		const init: EnsembleOperationState = {
			modelConfigIds: [],
			chartConfigs: [],
			mapping: [{ genericModelVariable: '', modelVariable: '' }]
		};
		return init;
	}
};
