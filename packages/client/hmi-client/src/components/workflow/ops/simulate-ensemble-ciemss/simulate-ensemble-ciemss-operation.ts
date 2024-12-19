import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import simulateEnsembleCiemss from '@assets/svg/operator-images/simulate-ensemble-probabilistic.svg';
import { CiemssMethodOptions } from '@/services/models/simulation-service';
import { ChartSetting } from '@/types/common';

const DOCUMENTATION_URL = 'https://github.com/ciemss/pyciemss/blob/main/pyciemss/interfaces.py#L35';

export interface SimulateEnsembleMappingRow {
	id: string; // uuid that can be used as a row key
	newName: string; // This is the new name provided by the user.
	modelConfigurationMappings: { [key: string]: string };
}
export const isSimulateEnsembleMappingRow = (obj: any): obj is SimulateEnsembleMappingRow =>
	obj.id !== undefined && obj.modelConfigurationMappings !== undefined;

export interface SimulateEnsembleWeights {
	[key: string]: number;
}

export const speedValues = Object.freeze({
	numSamples: 1,
	method: CiemssMethodOptions.euler
});

export const normalValues = Object.freeze({
	numSamples: 100,
	method: CiemssMethodOptions.dopri5
});

export interface SimulateEnsembleCiemssOperationState extends BaseState {
	chartSettings: ChartSetting[] | null;
	mapping: SimulateEnsembleMappingRow[];
	weights: SimulateEnsembleWeights;
	endTime: number;
	numSamples: number;
	method: CiemssMethodOptions;
	stepSize: number;
	inProgressForecastId: string;
	forecastId: string; // Completed run's Id
	errorMessage: { name: string; value: string; traceback: string };
}

export const SimulateEnsembleCiemssOperation: Operation = {
	name: WorkflowOperationTypes.SIMULATE_ENSEMBLE_CIEMSS,
	displayName: 'Simulate ensemble',
	description: '',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: simulateEnsembleCiemss,
	inputs: [{ type: 'modelConfigId', label: 'Model configuration' }],
	outputs: [{ type: 'datasetId' }],
	isRunnable: true,
	uniqueInputs: true,

	initState: () => {
		const init: SimulateEnsembleCiemssOperationState = {
			chartSettings: null,
			mapping: [],
			weights: {},
			endTime: 100,
			numSamples: normalValues.numSamples,
			method: normalValues.method,
			stepSize: 1,
			inProgressForecastId: '',
			forecastId: '',
			errorMessage: { name: '', value: '', traceback: '' }
		};
		return init;
	}
};
