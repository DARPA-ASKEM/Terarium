import { DistributionType } from '@/services/distribution';
import { InterventionPolicy, ParameterSemantic, SimulationRequest } from '@/types/Types';
import { calculateUncertaintyRange } from '@/utils/math';
import { v4 as uuidv4 } from 'uuid';
import { CalibrateMap } from '@/services/calibrate-workflow';
import { makeForecastJobCiemss } from '@/services/models/simulation-service';
import { WorkflowNode } from '@/types/workflow';
import * as workflowService from '@/services/workflow';
import { operation as SimulateCiemssOp } from '@/components/workflow/ops/simulate-ciemss/mod';
import { CiemssPresetTypes } from '@/types/common';
import { nodeMetadata } from '../util';
import { CalibrateEnsembleMappingRow } from '../ops/calibrate-ensemble-ciemss/calibrate-ensemble-ciemss-operation';
import { qualityPreset, speedPreset } from '../ops/simulate-ciemss/simulate-utils';
import { isInterventionPolicyBlank } from '../ops/intervention-policy/intervention-policy-operation';

export const displayParameter = (parameters: ParameterSemantic[], parameterName: string) => {
	let value = '';
	const parameter = parameters.find((p) => p.referenceId === parameterName);
	switch (parameter?.distribution.type) {
		case DistributionType.Constant:
			value = `${parameter.distribution.parameters.value}`;
			break;
		case DistributionType.Uniform:
			value = `${parameter.distribution.parameters.minimum} - ${parameter.distribution.parameters.maximum}`;
			break;
		default:
			return '';
	}
	return `${parameterName}  [${value}]`;
};

/**
 * Generates the Cartesian product of the provided arrays.
 *
 * The Cartesian product of multiple arrays is a set of all possible combinations
 * where each combination contains one element from each array.
 *
 * @param arrays - An array of arrays, where each inner array contains elements to combine.
 * @returns An array of arrays, where each inner array is a combination of elements from the input arrays.
 *
 * Example:
 * const arrays = [
 *   ['low', 'high'],
 *   ['A', 'B'],
 *   [1, 2]
 * ];
 *
 * const result = cartesianProduct(arrays);
 * console.log(result);
 * // Output:
 * // [
 * //   ['low', 'A', 1],
 * //   ['low', 'A', 2],
 * //   ['low', 'B', 1],
 * //   ['low', 'B', 2],
 * //   ['high', 'A', 1],
 * //   ['high', 'A', 2],
 * //   ['high', 'B', 1],
 * //   ['high', 'B', 2]
 * // ]
 */
export function cartesianProduct<T>(arrays: T[][]): T[][] {
	return arrays.reduce((acc, curr) => acc.flatMap((a) => curr.map((b) => [...a, b])), [[]] as T[][]);
}

export const switchToUniformDistribution = (parameter: ParameterSemantic) => {
	if (parameter.distribution.type !== DistributionType.Uniform) {
		let minimum = 0;
		let maximum = 1;
		if (parameter.distribution.type === DistributionType.Constant) {
			// +10% and -10% of the constant value
			const { min, max } = calculateUncertaintyRange(parameter.distribution.parameters.value, 10);
			minimum = min;
			maximum = max;
		}
		parameter.distribution.type = DistributionType.Uniform;
		parameter.distribution.parameters = { minimum, maximum };
	}
};

/**
 * Converts an array of CalibrateEnsembleMappingRow objects to a Map of CalibrateMap arrays.
 *
 * The resulting Map has model configuration IDs as keys and arrays of CalibrateMap objects as values.
 * Each CalibrateMap object contains a modelVariable and a datasetVariable.
 *
 * @param rows - An array of CalibrateEnsembleMappingRow objects.
 * @returns A Map where the key is a model configuration ID and the value is an array of CalibrateMap objects.
 *
 * Example:
 * const input: CalibrateEnsembleMappingRow[] = [
 *   {
 *     newName: "Sus",
 *     datasetMapping: "s",
 *     modelConfigurationMappings: {
 *       "82d2dbf0-587a-4b97-b927-6de5e6b41f09": "Susceptible",
 *       "c6b24192-0fdc-46f5-b4ff-0d01737d1fac": "S",
 *     },
 *   },
 *   {
 *     newName: "Inf",
 *     datasetMapping: "i",
 *     modelConfigurationMappings: {
 *       "82d2dbf0-587a-4b97-b927-6de5e6b41f09": "Infected",
 *       "c6b24192-0fdc-46f5-b4ff-0d01737d1fac": "I",
 *     },
 *   },
 * ];
 *
 * const result = convertToCalibrateMap(input);
 * console.log(result);
 * // Output:
 * // Map {
 * //   "82d2dbf0-587a-4b97-b927-6de5e6b41f09" => [
 * //     { modelVariable: "Susceptible", datasetVariable: "s" },
 * //     { modelVariable: "Infected", datasetVariable: "i" }
 * //   ],
 * //   "c6b24192-0fdc-46f5-b4ff-0d01737d1fac" => [
 * //     { modelVariable: "S", datasetVariable: "s" },
 * //     { modelVariable: "I", datasetVariable: "i" }
 * //   ]
 * // }
 */
export const convertToCalibrateMap = (rows: CalibrateEnsembleMappingRow[]): Map<string, CalibrateMap[]> => {
	const result = new Map<string, CalibrateMap[]>();

	rows.forEach((row) => {
		const { datasetMapping, modelConfigurationMappings } = row;

		Object.entries(modelConfigurationMappings).forEach(([modelConfigId, modelVariable]) => {
			if (!result.has(modelConfigId)) {
				result.set(modelConfigId, []);
			}

			const calibrateMap: CalibrateMap = {
				modelVariable,
				datasetVariable: datasetMapping
			};

			result.get(modelConfigId)?.push(calibrateMap);
		});
	});

	return result;
};

export const createDefaultForecastSettings = (endTime: number, preset: CiemssPresetTypes) => ({
	currentTimespan: {
		start: 0,
		end: endTime
	},
	numSamples: preset === CiemssPresetTypes.Fast ? speedPreset.numSamples : qualityPreset.numSamples,
	solverStepSize: 0.1,
	method: preset === CiemssPresetTypes.Fast ? speedPreset.method : qualityPreset.method
});

export const makeForecastRequest = async (
	node: WorkflowNode<any>,
	modelConfigurationId: string,
	options: ReturnType<typeof createDefaultForecastSettings>,
	interventionPolicyId?: string
) => {
	const { solverStepSize, method, numSamples, currentTimespan } = options;
	const payload: SimulationRequest = {
		modelConfigId: modelConfigurationId,
		timespan: {
			start: currentTimespan.start,
			end: currentTimespan.end
		},
		extra: {
			solver_method: method,
			solver_step_size: solverStepSize,
			num_samples: numSamples
		},
		engine: 'ciemss'
	};

	if (interventionPolicyId) {
		payload.policyInterventionId = interventionPolicyId;
	}

	const response = await makeForecastJobCiemss(payload, {
		...nodeMetadata(node),
		isBaseForecast: !interventionPolicyId
	});
	return response.id;
};

// find all forecast nodes and run simulations + update state
export const runSimulations = async (
	wf: workflowService.WorkflowWrapper,
	options: ReturnType<typeof createDefaultForecastSettings>,
	interventionPolicies: InterventionPolicy[] = []
) => {
	const forecastNodes = wf.getNodes().filter((node) => node.operationType === SimulateCiemssOp.name);

	/* eslint-disable no-restricted-syntax */
	for (const node of forecastNodes) {
		/* eslint-enable no-restricted-syntax */
		const modelConfigId = node.inputs[0].value?.[0];
		const policy = interventionPolicies.find((intervention) => intervention.id === node.inputs[1].value?.[0]);

		if (policy && isInterventionPolicyBlank(policy)) {
			// If policy exists but has no interventions, skip the simulation
			continue;
		}

		const baseSimulationPromise = policy
			? makeForecastRequest(node, modelConfigId, options, policy.id)
			: Promise.resolve('');

		const simulationPromise = makeForecastRequest(node, modelConfigId, options);

		/* eslint-disable no-await-in-loop */
		const [baseSimulationId, simulationId] = await Promise.all([baseSimulationPromise, simulationPromise]);
		/* eslint-enable no-await-in-loop */

		wf.updateNode(node, {
			state: {
				...node.state,
				...options,
				inProgressBaseForecastId: baseSimulationId,
				inProgressForecastId: simulationId
			}
		});
	}
};
export function usePolicyModel(props, interventionDropdowns, policyModalContext, isPolicyModalVisible) {
	const onOpenPolicyModel = (index: number) => {
		interventionDropdowns.value[index].hide();
		policyModalContext.value = index;
		isPolicyModalVisible.value = true;
	};

	const addNewPolicy = (newPolicyName: string) => {
		if (newPolicyName && policyModalContext.value !== null) {
			const id = uuidv4();
			props.scenario.setNewInterventionSpec(id, newPolicyName);
			props.scenario.setInterventionSpec(id, policyModalContext.value);
			isPolicyModalVisible.value = false;
			policyModalContext.value = null;
		}
	};

	return {
		onOpenPolicyModel,
		addNewPolicy
	};
}
