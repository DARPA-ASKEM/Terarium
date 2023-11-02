import { Operation, WorkflowOperationTypes } from '@/types/workflow';

export interface StratifyGroup {
	borderColour: string;
	name: string;
	selectedVariables: string[];
	groupLabels: string;
	cartesianProduct: boolean;
}

export interface StratifyOperationStateMira {
	strataGroups: StratifyGroup[];
}

export const StratifyMiraOperation: Operation = {
	name: WorkflowOperationTypes.STRATIFY_MIRA,
	displayName: 'Stratify MIRA',
	description: 'Stratify a model',
	inputs: [{ type: 'modelConfigId', label: 'Model configuration', acceptMultiple: false }],
	outputs: [{ type: 'model' }],
	isRunnable: false,
	action: () => {},
	initState: () => {
		const init: StratifyOperationStateMira = {
			strataGroups: [
				{
					borderColour: '#c300a6',
					name: '',
					selectedVariables: [],
					groupLabels: '',
					cartesianProduct: true
				}
			]
		};
		return init;
	}
};
