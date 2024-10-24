import { Operation, WorkflowOperationTypes, BaseState } from '@/types/workflow';
import type { FunmanInterval, TimeSpan } from '@/types/Types';

const DOCUMENTATION_URL = 'https://github.com/siftech/funman';

export enum Constraint {
	State = 'state variable(s)',
	Parameter = 'parameter(s)',
	Observable = 'observable(s)'
}

export enum ConstraintType {
	LessThan = 'less than',
	LessThanOrEqualTo = 'less than or equal to',
	GreaterThan = 'greater than',
	GreaterThanOrEqualTo = 'greater than or equal to',
	Increasing = 'increasing',
	Decreasing = 'decreasing',
	LinearlyConstrained = 'linearly constrained',
	Following = 'following'
}

export interface ConstraintGroup {
	name: string;
	isActive: boolean;
	constraint: Constraint;
	constraintType: ConstraintType;
	variables: string[];
	weights: number[]; // 1 to 1 mapping with variables
	timepoints: FunmanInterval;
	interval: FunmanInterval;
}

export interface CompartmentalConstraint {
	name: string;
	isActive: boolean;
}

export interface RequestParameter {
	name: string;
	interval?: FunmanInterval;
	label: string;
}

export interface FunmanOperationState extends BaseState {
	currentTimespan: TimeSpan;
	numSteps: number;
	tolerance: number;
	inProgressId: string;
	runId: string;
	compartmentalConstraint: CompartmentalConstraint;
	constraintGroups: ConstraintGroup[];
	requestParameters: RequestParameter[];
	currentProgress: number;
	// selected state in ouptut
	trajectoryState?: string;
}

export const FunmanOperation: Operation = {
	name: WorkflowOperationTypes.FUNMAN,
	displayName: 'Validate configuration',
	description: 'Validate configuration',
	documentationUrl: DOCUMENTATION_URL,
	inputs: [
		{ type: 'modelConfigId', label: 'Model configuration' },
		{ type: 'datasetId', label: 'Dataset', isOptional: true }
	],
	outputs: [{ type: 'modelConfigId', label: 'Model configuration' }],
	isRunnable: true,
	action: () => {},
	initState: () => {
		const init: FunmanOperationState = {
			currentTimespan: { start: 0, end: 100 },
			numSteps: 10,
			tolerance: 0.2,
			compartmentalConstraint: { name: 'Compartmental constraint', isActive: true },
			constraintGroups: [],
			requestParameters: [],
			inProgressId: '',
			runId: '',
			currentProgress: 0
		};
		return init;
	}
};
