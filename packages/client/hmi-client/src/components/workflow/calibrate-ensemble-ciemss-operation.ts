import { Operation, WorkflowOperationTypes } from '@/types/workflow';
// import { EnsembleRequest } from '@/types/Types';
// import { makeEnsembleJob } from '@/services/models/simulation-service';
import { ChartConfig } from '@/types/SimulateConfig';
import { EnsembleModelConfigs, TimeSpan } from '@/types/Types';

export interface CalibrateEnsembleCiemssOperationState {
	modelConfigIds: string[];
	chartConfigs: ChartConfig[];
	mapping: EnsembleModelConfigs[];
	timeSpan: TimeSpan;
	numSamples: number;
}

export const CalibrateEnsembleCiemssOperation: Operation = {
	name: WorkflowOperationTypes.CALIBRATE_ENSEMBLE_CIEMSS,
	displayName: 'Simulate ensemble (probabilistic)',
	description: '',
	inputs: [{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: true }],
	outputs: [{ type: 'number' }],
	isRunnable: true,

	// TODO: Figure out mapping
	// Calls API, returns results.
	action: async (): Promise<void> => {
		console.log('test');
	},

	initState: () => {
		const init: CalibrateEnsembleCiemssOperationState = {
			modelConfigIds: [],
			chartConfigs: [],
			mapping: [],
			timeSpan: { start: 0, end: 40 },
			numSamples: 40
		};
		return init;
	}
};
