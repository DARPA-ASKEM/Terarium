import { CiemssMethodOptions } from '@/services/models/simulation-service';
import { CiemssPresetTypes } from '@/types/common';
import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import calibrateEnsembleCiemss from '@assets/svg/operator-images/calibrate-ensemble-probabilistic.svg';

const DOCUMENTATION_URL = 'https://github.com/ciemss/pyciemss/blob/main/pyciemss/interfaces.py#L156';

export const speedPreset = Object.freeze({
	numSamples: 1,
	method: CiemssMethodOptions.euler,
	numIterations: 10,
	learningRate: 0.1
});

export const qualityPreset = Object.freeze({
	numSamples: 10,
	method: CiemssMethodOptions.dopri5,
	numIterations: 100,
	learningRate: 0.03
});
export interface EnsembleCalibrateExtraCiemss {
	numParticles: number; // The number of particles to use for the inference algorithm. https://github.com/ciemss/pyciemss/blob/1fc62b0d4b0870ca992514ad7a9b7a09a175ce44/pyciemss/interfaces.py#L225
	presetType: CiemssPresetTypes;
	solverMethod: CiemssMethodOptions;
	numIterations: number;
	endTime: number;
	stepSize: number;
	learningRate: number;
}

export interface CalibrateEnsembleMappingRow {
	newName: string;
	datasetMapping: string;
	modelConfigurationMappings: { [key: string]: string };
}

export interface CalibrateEnsembleWeights {
	[key: string]: number;
}

export interface CalibrateEnsembleCiemssOperationState extends BaseState {
	chartConfigs: string[][];
	ensembleMapping: CalibrateEnsembleMappingRow[];
	configurationWeights: CalibrateEnsembleWeights;
	timestampColName: string;
	extra: EnsembleCalibrateExtraCiemss;
	inProgressCalibrationId: string;
	inProgressForecastId: string;
	calibrationId: string;
	forecastRunId: string;
	currentProgress: number;
}

export const CalibrateEnsembleCiemssOperation: Operation = {
	name: WorkflowOperationTypes.CALIBRATE_ENSEMBLE_CIEMSS,
	displayName: 'Calibrate ensemble',
	description: '',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: calibrateEnsembleCiemss,
	inputs: [
		{ type: 'datasetId', label: 'Dataset' },
		{ type: 'modelConfigId', label: 'Model configuration' }
	],
	outputs: [{ type: 'simulationId' }],
	isRunnable: true,

	initState: () => {
		const init: CalibrateEnsembleCiemssOperationState = {
			chartConfigs: [],
			ensembleMapping: [],
			configurationWeights: {},
			timestampColName: '',
			extra: {
				solverMethod: speedPreset.method,
				numParticles: speedPreset.numSamples,
				numIterations: speedPreset.numIterations,
				presetType: CiemssPresetTypes.Fast,
				endTime: 100,
				stepSize: 1,
				learningRate: speedPreset.learningRate
			},
			inProgressCalibrationId: '',
			inProgressForecastId: '',
			calibrationId: '',
			forecastRunId: '',
			currentProgress: 0
		};
		return init;
	}
};
