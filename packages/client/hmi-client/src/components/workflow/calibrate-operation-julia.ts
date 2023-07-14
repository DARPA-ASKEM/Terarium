import { WorkflowPort, Operation, WorkflowOperationTypes } from '@/types/workflow';
// import { CalibrationRequest } from '@/types/Types';
// import { makeCalibrateJob } from '@/services/models/simulation-service';
import { getModel } from '@/services/model';
import { ChartConfig } from '@/types/SimulateConfig';

export interface CalibrateMap {
	modelVariable: string;
	datasetVariable: string;
}

export interface CalibrationOperationState {
	chartConfigs: ChartConfig[];
	mapping: CalibrateMap[];
}

export const CalibrationOperation: Operation = {
	name: WorkflowOperationTypes.CALIBRATION_JULIA,
	description:
		'given a model id, a dataset id, and optionally a configuration. calibrate the models initial values and rates',
	inputs: [{ type: 'modelConfigId' }, { type: 'datasetId' }],
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
		const init: CalibrationOperationState = {
			chartConfigs: [],
			mapping: [{ modelVariable: '', datasetVariable: '' }]
		};
		return init;
	}
};
