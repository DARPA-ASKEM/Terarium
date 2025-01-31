import { CiemssMethodOptions } from '@/services/models/simulation-service';
import { ChartSetting } from '@/types/common';
import type { TimeSpan } from '@/types/Types';
import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import simulateProbabilistic from '@assets/svg/operator-images/simulate-probabilistic.svg';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/simulation/simulate-model/';

export interface SimulateCiemssOperationState extends BaseState {
	// state shared across all runs
	chartSettings: ChartSetting[] | null; // null indicates that the chart settings have not been set yet

	// state specific to individual simulate runs
	currentTimespan: TimeSpan;
	numSamples: number;
	solverStepSize: number;
	method: CiemssMethodOptions;
	forecastId: string; // Completed run's Id
	baseForecastId: string; // Simulation without intervention

	// In progress
	inProgressForecastId: string;
	inProgressBaseForecastId: string;

	errorMessage: { name: string; value: string; traceback: string };
}

export const SimulateCiemssOperation: Operation = {
	name: WorkflowOperationTypes.SIMULATE_CIEMSS,
	displayName: 'Simulate',
	description: 'given a model id, and configuration id, run a simulation',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: simulateProbabilistic,
	inputs: [
		{ type: 'modelConfigId', label: 'Model configuration' },
		{
			type: 'policyInterventionId',
			label: 'Interventions',
			isOptional: true
		}
	],
	outputs: [{ type: 'datasetId', label: 'Dataset' }],
	isRunnable: true,

	initState: () => {
		const init: SimulateCiemssOperationState = {
			chartSettings: null,
			currentTimespan: { start: 0, end: 100 },
			numSamples: 100,
			solverStepSize: 0.1,
			method: CiemssMethodOptions.dopri5,
			forecastId: '',
			baseForecastId: '',
			inProgressForecastId: '',
			inProgressBaseForecastId: '',
			errorMessage: { name: '', value: '', traceback: '' }
		};
		return init;
	}
};
