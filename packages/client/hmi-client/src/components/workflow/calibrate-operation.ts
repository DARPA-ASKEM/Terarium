import { WorkflowPort, Operation } from '@/types/workflow';
import { CalibrationParams } from '@/types/Types';
import { calibrationParamExample } from '@/temp/calibrationExample';
import { makeCalibrateJob } from '@/services/models/simulation-service';
import { getModel } from '@/services/model';

export const CalibrationOperation: Operation = {
	name: 'CalibrationOperation',
	description:
		'given a model id, a dataset id, and optionally a configuration. calibrate the models initial values and rates',
	inputs: [{ type: 'modelConfig' }, { type: 'dataset' }],
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
			const petriNetString = JSON.stringify(model?.content);
			console.log('Petrinet String: ');
			console.log(petriNetString);

			// Make calibration job.
			const calibrationParam: CalibrationParams = calibrationParamExample;
			const result = makeCalibrateJob(calibrationParam);
			return [{ type: 'number', result }];
		}
		return [{ type: null, value: null }];
	}
};
