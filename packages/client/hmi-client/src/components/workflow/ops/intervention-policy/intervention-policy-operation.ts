import { InterventionPolicy, InterventionSemanticType } from '@/types/Types';
import type { BaseState, Operation } from '@/types/workflow';
import { WorkflowOperationTypes } from '@/types/workflow';
import { isEqual, omit } from 'lodash';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/config-and-intervention/create-intervention-policy/';

export interface InterventionPolicyState extends BaseState {
	interventionPolicy: InterventionPolicy;
	taskIds: string[];
	selectedCharts?: string[];
}

export const InterventionPolicyOperation: Operation = {
	name: WorkflowOperationTypes.INTERVENTION_POLICY,
	description: 'Create intervention policy',
	documentationUrl: DOCUMENTATION_URL,
	displayName: 'Create intervention policy',
	isRunnable: true,
	inputs: [
		{ type: 'modelId', label: 'Model' },
		{ type: 'documentId', label: 'Document', isOptional: true },
		{ type: 'datasetId', label: 'Dataset', isOptional: true }
	],
	outputs: [{ type: 'policyInterventionId', label: 'Intervention policy' }],
	action: () => {},

	initState: () => {
		const init: InterventionPolicyState = {
			interventionPolicy: {
				modelId: '',
				interventions: []
			},
			taskIds: []
		};
		return init;
	}
};

export const isInterventionPoliciesEqual = (
	originalPolicy: InterventionPolicy | null,
	newPolicy: InterventionPolicy | null
): boolean => {
	if (!originalPolicy || !newPolicy) return false;
	const keysToOmit = ['id', 'createdOn', 'updatedOn'];
	return isEqual(omit(originalPolicy, keysToOmit), omit(newPolicy, keysToOmit));
};

// check that the intervention policy values are equal
export const isInterventionPoliciesValuesEqual = (
	originalPolicy: InterventionPolicy | null,
	newPolicy: InterventionPolicy | null
): boolean => {
	if (!originalPolicy || !newPolicy) return false;
	if (originalPolicy.interventions.length !== newPolicy.interventions.length) return false;

	const notEqual = originalPolicy.interventions.some((intervention, index) => {
		if (!isEqual(intervention.staticInterventions, newPolicy.interventions[index].staticInterventions)) return true;
		if (!isEqual(intervention.dynamicInterventions, newPolicy.interventions[index].dynamicInterventions)) return true;
		return false;
	});
	return !notEqual;
};

export const isInterventionPolicyBlank = (policy: InterventionPolicy | null): boolean =>
	policy?.interventions.length === 1 &&
	isEqual(policy.interventions[0], {
		name: 'New intervention',
		staticInterventions: [
			{
				type: InterventionSemanticType.Parameter,
				appliedTo: ''
			}
		],
		dynamicInterventions: []
	});
