import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import { Intervention, InterventionSemanticType, InterventionPolicy } from '@/types/Types';
import { getRunResult, getSimulation } from '@/services/models/simulation-service';
import { getModelIdFromModelConfigurationId } from '@/services/model-configurations';
import { createInterventionPolicy } from '@/services/intervention-policy';

const DOCUMENTATION_URL =
	'https://github.com/ciemss/pyciemss/blob/main/pyciemss/interfaces.py#L747';

export enum InterventionTypes {
	paramValue = 'param_value', // provide a parameter value to get a better start time.
	startTime = 'start_time', // provide a start time to get a better parameter value.
	paramValueAndStartTime = 'start_time_param_value'
}

export enum InterventionObjectiveFunctions {
	lowerBound = 'lower_bound',
	upperbound = 'upper_bound',
	initialGuess = 'initial_guess'
}

export enum ContextMethods {
	day_average = 'day_average',
	max = 'max'
}

export interface InterventionPolicyGroupForm {
	startTime: number;
	endTime: number;
	startTimeGuess: number;
	lowerBoundValue: number;
	upperBoundValue: number;
	initialGuessValue: number;
	isActive: boolean;
	optimizationType: InterventionTypes;
	objectiveFunctionOption: InterventionObjectiveFunctions;
	intervention: Intervention;
}

export interface Criterion {
	name: string; // Title of the group
	targetVariable: string;
	qoiMethod: ContextMethods;
	riskTolerance: number;
	threshold: number;
	isMinimized: boolean;
	isActive: boolean; // Denotes whether this should be used when user hits run.
}

export interface OptimizeCiemssOperationState extends BaseState {
	// Settings
	endTime: number;
	numSamples: number;
	solverMethod: string;
	maxiter: number;
	maxfeval: number;
	// Intervention policies
	interventionPolicyId: string; // Used to determine if we need to reset the InterventionPolicyGroupForm.
	interventionPolicyGroups: InterventionPolicyGroupForm[];
	// Constraints:
	constraintGroups: Criterion[];
	selectedInterventionVariables: string[];
	selectedSimulationVariables: string[];
	inProgressOptimizeId: string;
	inProgressPreForecastId: string;
	preForecastRunId: string;
	inProgressPostForecastId: string;
	postForecastRunId: string;
	optimizationRunId: string;
	optimizeErrorMessage: { name: string; value: string; traceback: string };
	simulateErrorMessage: { name: string; value: string; traceback: string };
}

// This is used as a map between dropdown labels and the inner values used by pyciemss-service.
export const OPTIMIZATION_TYPE_MAP = [
	{ label: 'new value', value: InterventionTypes.startTime },
	{ label: 'new start time', value: InterventionTypes.paramValue },
	{ label: 'new value and start time', value: InterventionTypes.paramValueAndStartTime }
];

// This is used as a map between dropdown labels and the inner values used by pyciemss-service.
export const OBJECTIVE_FUNCTION_MAP = [
	{ label: 'initial guess', value: InterventionObjectiveFunctions.initialGuess },
	{ label: 'lower bound', value: InterventionObjectiveFunctions.lowerBound },
	{ label: 'upper bound', value: InterventionObjectiveFunctions.upperbound }
];

export const blankIntervention: Intervention = {
	name: 'New Intervention',
	appliedTo: '',
	type: InterventionSemanticType.Parameter,
	staticInterventions: [{ timestep: Number.NaN, value: Number.NaN }],
	dynamicInterventions: []
};

export const blankInterventionPolicyGroup: InterventionPolicyGroupForm = {
	startTime: 0,
	endTime: 0,
	startTimeGuess: 0,
	lowerBoundValue: 0,
	upperBoundValue: 0,
	initialGuessValue: 0,
	isActive: true,
	optimizationType: InterventionTypes.paramValue,
	objectiveFunctionOption: InterventionObjectiveFunctions.initialGuess,
	intervention: blankIntervention
};

export const defaultCriterion: Criterion = {
	name: 'Criterion',
	qoiMethod: ContextMethods.max,
	targetVariable: '',
	riskTolerance: 5,
	threshold: 1,
	isMinimized: true,
	isActive: true
};

export const OptimizeCiemssOperation: Operation = {
	name: WorkflowOperationTypes.OPTIMIZE_CIEMSS,
	displayName: 'Optimize intervention policy',
	description: 'Optimize intervention policy',
	documentationUrl: DOCUMENTATION_URL,
	inputs: [
		{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: false },
		{
			type: 'calibrateSimulationId',
			label: 'Calibration',
			acceptMultiple: false,
			isOptional: true
		},
		{
			type: 'policyInterventionId',
			label: 'Intervention Policy',
			acceptMultiple: false
		}
	],
	outputs: [{ type: 'simulationId' }],
	isRunnable: true,

	initState: () => {
		const init: OptimizeCiemssOperationState = {
			endTime: 90,
			numSamples: 100,
			solverMethod: 'dopri5',
			maxiter: 5,
			maxfeval: 25,
			interventionPolicyId: '',
			interventionPolicyGroups: [],
			constraintGroups: [defaultCriterion],
			selectedInterventionVariables: [],
			selectedSimulationVariables: [],
			inProgressOptimizeId: '',
			inProgressPostForecastId: '',
			inProgressPreForecastId: '',
			preForecastRunId: '',
			postForecastRunId: '',
			optimizationRunId: '',
			optimizeErrorMessage: { name: '', value: '', traceback: '' },
			simulateErrorMessage: { name: '', value: '', traceback: '' }
		};
		return init;
	}
};

// Get the simulation object that ran the optimize.
// Get the fixed static interventions from this, as well as some details about the optimization interevntions
// Get the optimization result file
// Concat the optimization result file with the optimization interventions from simulation object
export async function getOptimizedInterventions(optimizeRunId: string) {
	const allInterventions: Intervention[] = [];
	// Get the interventionPolicyGroups from the simulation object.
	// This will prevent any inconsistencies being passed via knobs or state when matching with result file.
	const simulation = await getSimulation(optimizeRunId);

	const simulationStaticInterventions: any[] =
		simulation?.executionPayload.fixed_static_parameter_interventions ?? [];
	const optimizeInterventions = simulation?.executionPayload?.optimize_interventions;

	// From snake case -> camel case.
	simulationStaticInterventions.forEach((inter) => {
		const newIntervetion: Intervention = {
			appliedTo: inter.applied_to,
			dynamicInterventions: inter.dynamic_interventions,
			name: inter.name,
			staticInterventions: inter.static_interventions,
			type: inter.type
		};
		allInterventions.push(newIntervetion);
	});

	// At the moment we only accept one intervention type. Pyciemss, pyciemss-service and this will all need to be updated.
	// https://github.com/DARPA-ASKEM/terarium/issues/3909
	const interventionType = optimizeInterventions.intervention_type ?? '';
	const paramNames: string[] = optimizeInterventions.param_names ?? [];
	const paramValues: number[] = optimizeInterventions.param_values ?? [];
	const startTimes: number[] = optimizeInterventions.start_time ?? [];

	const policyResult = await getRunResult(optimizeRunId, 'policy.json');
	// TODO: https://github.com/DARPA-ASKEM/terarium/issues/3909
	// This will need to be updated to allow multiple intervention types. This is not allowed at the moment.
	if (interventionType === InterventionTypes.paramValue && startTimes.length !== 0) {
		// If we our intervention type is param value our policyResult will provide a timestep.
		for (let i = 0; i < paramNames.length; i++) {
			allInterventions.push({
				name: `Optimized ${paramNames[i]}`,
				appliedTo: paramNames[i],
				type: InterventionSemanticType.Parameter,
				staticInterventions: [
					{
						timestep: policyResult[i],
						value: paramValues[i]
					}
				],
				dynamicInterventions: []
			});
		}
	} else if (interventionType === InterventionTypes.startTime && paramValues.length !== 0) {
		// If we our intervention type is start time our policyResult will provide a parameter value.
		for (let i = 0; i < paramNames.length; i++) {
			allInterventions.push({
				name: `Optimized ${paramNames[i]}`,
				appliedTo: paramNames[i],
				type: InterventionSemanticType.Parameter,
				staticInterventions: [
					{
						timestep: startTimes[i],
						value: policyResult[i]
					}
				],
				dynamicInterventions: []
			});
		}
	} else if (interventionType === InterventionTypes.paramValueAndStartTime) {
		// If we our intervention type is start time our policyResult will provide a parameter value.
		for (let i = 0; i < paramNames.length; i++) {
			allInterventions.push({
				name: `Optimized ${paramNames[i]}`,
				appliedTo: paramNames[i],
				type: InterventionSemanticType.Parameter,
				staticInterventions: [
					{
						timestep: policyResult[i * 2],
						value: policyResult[i * 2 + 1]
					}
				],
				dynamicInterventions: []
			});
		}
	} else {
		// Should realistically not be hit unless we change the interface and do not update
		console.error(`Unable to find the intevention type for optimization run: ${optimizeRunId}`);
	}
	return allInterventions;
}

/**
 *
 * 1) Get optimize interventions
 *
 *
 */
export async function createInterventionPolicyFromOptimize(
	modelConfigId: string,
	optimizeRunId: string
) {
	const modelId = await getModelIdFromModelConfigurationId(modelConfigId);
	const optimizedInterventions = await getOptimizedInterventions(optimizeRunId);

	const newIntervention: InterventionPolicy = {
		name: `Optimize run: ${optimizeRunId}`,
		modelId,
		interventions: optimizedInterventions
	};
	const newInterventionPolicy: InterventionPolicy = await createInterventionPolicy(newIntervention);
	return newInterventionPolicy;
}
