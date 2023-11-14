import { WorkflowPort, Operation, WorkflowOperationTypes } from '@/types/workflow';
// import { CalibrationRequest } from '@/types/Types';
// import { makeCalibrateJob } from '@/services/models/simulation-service';
import { getModel } from '@/services/model';
import { ChartConfig } from '@/types/SimulateConfig';
import { CalibrateConfig } from '@/types/CalibrateConfig';

export interface CalibrateMap {
	modelVariable: string;
	datasetVariable: string;
}

export interface CalibrateExtraJulia {
	numChains: number;
	numIterations: number;
	odeMethod: string;
	calibrateMethod: string;
}

export enum CalibrateMethodOptions {
	BAYESIAN = 'bayesian',
	LOCAL = 'local',
	GLOBAL = 'global'
}

export interface CalibrationOperationStateJulia {
	chartConfigs: ChartConfig[];
	calibrateConfigs: CalibrateConfig;
	mapping: CalibrateMap[];
	extra: CalibrateExtraJulia;
	simulationsInProgress: string[];
}

export const CalibrationOperationJulia: Operation = {
	name: WorkflowOperationTypes.CALIBRATION_JULIA,
	displayName: 'Calibrate (deterministic)',
	description:
		'given a model id, a dataset id, and optionally a configuration. calibrate the models initial values and rates',
	inputs: [
		{ type: 'modelConfigId', label: 'Model configuration' },
		{ type: 'datasetId', label: 'Dataset' }
	],
	outputs: [{ type: 'number' }],
	isRunnable: true,

	// TODO: Figure out mapping
	// Calls API, returns results.
	action: async (v: WorkflowPort[]) => {
		// TODO Add more safety checks.
		if (v.length) {
			// TODO: The mapping is not 0 -> modelId as i am assuming here for testing
			const modelId = v[0].value?.[0];
			// let datasetId = v[1].value;
			// let configuration = v[2].value; //TODO Not sure if this is a required input

			// Get the model:
			const model = await getModel(modelId);
			if (model) {
				// Make calibration job.
				// const calibrationParam: CalibrationRequest = calibrationParamExample;
				// const result = makeCalibrateJob(calibrationParam);
				// return [{ type: 'number', result }];
				return [{ type: null, value: null }];
			}
		}
		return [{ type: null, value: null }];
	},

	initState: () => {
		const init: CalibrationOperationStateJulia = {
			chartConfigs: [],
			calibrateConfigs: { runConfigs: {}, chartConfigs: [] },
			mapping: [{ modelVariable: '', datasetVariable: '' }],
			extra: {
				numChains: 4,
				numIterations: 50,
				odeMethod: 'default',
				calibrateMethod: CalibrateMethodOptions.GLOBAL
			},
			simulationsInProgress: []
		};
		return init;
	}
};
