import { Operation, WorkflowOperationTypes } from '@/types/workflow';

export interface InterventionPolicyGroup {
	borderColour: string;
	name: string;
	parameter: string;
	startTime: number;
	lowerBound: number;
	upperBound: number;
	initialGuess: number;
	isActive: boolean;
}

export interface OptimizeCiemssOperationState {
	// Settings
	startTime: number;
	endTime: number;
	numStochasticSamples: number;
	solverMethod: string;
	// Intervention policies
	interventionPolicyGroups: InterventionPolicyGroup[];
	// Constraints
	targetVariables: string[];
	riskTolerance: number;
	threshold: number;
	isMinimized: boolean;
	chartConfigs: string[][];
	simulationsInProgress: string[];
	forecastRunId: string;
	optimzationRunId: string;
	modelConfigName: string;
	modelConfigDesc: string;
}

export const blankInterventionPolicyGroup: InterventionPolicyGroup = {
	borderColour: '#cee2a4',
	name: 'Policy bounds',
	parameter: '',
	startTime: 0,
	lowerBound: 0,
	upperBound: 0,
	initialGuess: 0,
	isActive: true
};

export const OptimizeCiemssOperation: Operation = {
	name: WorkflowOperationTypes.OPTIMIZE_CIEMSS,
	displayName: 'Optimize with PyCIEMSS',
	description: 'Optimize with PyCIEMSS',
	inputs: [{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: false }],
	outputs: [{ type: 'simulationId' }],
	isRunnable: true,

	initState: () => {
		const init: OptimizeCiemssOperationState = {
			startTime: 0,
			endTime: 90,
			numStochasticSamples: 5,
			solverMethod: 'dopri5',
			interventionPolicyGroups: [blankInterventionPolicyGroup],
			targetVariables: [],
			riskTolerance: 5,
			threshold: 1,
			isMinimized: true,
			chartConfigs: [],
			simulationsInProgress: [],
			forecastRunId: '',
			optimzationRunId: '',
			modelConfigName: '',
			modelConfigDesc: ''
		};
		return init;
	}
};
