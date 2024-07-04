import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import { CalibrateMap } from '@/services/calibrate-workflow';

const DOCUMENTATION_URL =
	'https://github.com/ciemss/pyciemss/blob/main/pyciemss/interfaces.py#L529';

export interface CalibrationOperationStateCiemss extends BaseState {
	chartConfigs: string[][];
	mapping: CalibrateMap[];
	simulationsInProgress: string[];

	inProgressCalibrationId: string;
	inProgressForecastId: string;
	errorMessage: { name: string; value: string; traceback: string };

	calibrationId: string;
	forecastId: string;
	numIterations: number;
	numSamples: number;
	endTime: number;
}

export const CalibrationOperationCiemss: Operation = {
	name: WorkflowOperationTypes.CALIBRATION_CIEMSS,
	displayName: 'Calibrate with PyCIEMSS',
	description:
		'given a model id, a dataset id, and optionally a configuration. calibrate the models initial values and rates',
	documentationUrl: DOCUMENTATION_URL,
	inputs: [
		{ type: 'modelConfigId', label: 'Model configuration' },
		{ type: 'datasetId', label: 'Dataset' },
		{ type: 'policyId', label: 'Interventions', acceptMultiple: false, isOptional: true }
	],
	outputs: [{ type: 'simulationId' }],
	isRunnable: true,

	action: async () => {},

	initState: () => {
		const init: CalibrationOperationStateCiemss = {
			chartConfigs: [],
			mapping: [{ modelVariable: '', datasetVariable: '' }],
			simulationsInProgress: [],
			inProgressCalibrationId: '',
			inProgressForecastId: '',
			calibrationId: '',
			forecastId: '',
			errorMessage: { name: '', value: '', traceback: '' },
			numIterations: 100,
			numSamples: 100,
			endTime: 100
		};
		return init;
	}
};
