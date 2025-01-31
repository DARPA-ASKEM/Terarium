import { CiemssMethodOptions } from '@/services/models/simulation-service';
import { ChartSetting } from '@/types/common';
import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import calibrateEnsembleCiemss from '@assets/svg/operator-images/calibrate-ensemble-probabilistic.svg';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/simulation/calibrate-ensemble/';

export const speedPreset = Object.freeze({
	numSamples: 1,
	method: CiemssMethodOptions.euler,
	numIterations: 10,
	learningRate: 0.1,
	stepSize: 0.1
});

export const qualityPreset = Object.freeze({
	numSamples: 10,
	method: CiemssMethodOptions.dopri5,
	numIterations: 100,
	learningRate: 0.03
});
export interface EnsembleCalibrateExtraCiemss {
	numParticles: number; // The number of particles to use for the inference algorithm. https://github.com/ciemss/pyciemss/blob/1fc62b0d4b0870ca992514ad7a9b7a09a175ce44/pyciemss/interfaces.py#L225
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
export const isCalibrateEnsembleMappingRow = (obj: any): obj is CalibrateEnsembleMappingRow =>
	obj.newName !== undefined && obj.datasetMapping !== undefined;

export interface CalibrateEnsembleWeights {
	[key: string]: number;
}

export interface CalibrateEnsembleCiemssOperationOutputSettingsState {
	showLossChart: boolean;
	chartSettings: ChartSetting[] | null;
	showModelWeightsCharts: boolean;
}

export interface CalibrateEnsembleCiemssOperationState
	extends BaseState,
		CalibrateEnsembleCiemssOperationOutputSettingsState {
	ensembleMapping: CalibrateEnsembleMappingRow[];
	configurationWeights: CalibrateEnsembleWeights;
	timestampColName: string;
	extra: EnsembleCalibrateExtraCiemss;
	inProgressCalibrationId: string;
	inProgressPreForecastId: string;
	inProgressForecastId: string;
	errorMessage: { name: string; value: string; traceback: string };
	calibrationId: string;
	postForecastId: string;
	preForecastId: string;
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
		{ type: 'modelConfigId', label: 'Model configuration' },
		{ type: 'modelConfigId', label: 'Model configuration' }
	],
	outputs: [{ type: 'datasetId' }],
	isRunnable: true,
	uniqueInputs: true,

	initState: () => {
		const init: CalibrateEnsembleCiemssOperationState = {
			chartSettings: null,
			showLossChart: true,
			showModelWeightsCharts: true,
			ensembleMapping: [],
			configurationWeights: {},
			timestampColName: '',
			extra: {
				solverMethod: qualityPreset.method,
				numParticles: qualityPreset.numSamples,
				numIterations: qualityPreset.numIterations,
				endTime: 100,
				stepSize: 1,
				learningRate: qualityPreset.learningRate
			},
			inProgressCalibrationId: '',
			inProgressForecastId: '',
			inProgressPreForecastId: '',
			calibrationId: '',
			postForecastId: '',
			preForecastId: '',
			errorMessage: { name: '', value: '', traceback: '' },
			currentProgress: 0
		};
		return init;
	}
};
