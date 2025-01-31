import type { Operation, BaseState } from '@/types/workflow';
import { WorkflowOperationTypes } from '@/types/workflow';
import stratifyModel from '@assets/svg/operator-images/stratify-model.svg';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/modeling/stratify-model/';

export interface StratifyGroup {
	borderColour: string;
	name: string;
	selectedVariables: string[];
	groupLabels: string;
	cartesianProduct: boolean;

	directed: boolean;
	structure: null | any[];

	useStructure: boolean;
}

export interface StratifyCode {
	llmQuery: string;
	llmThoughts: any[];
	code: string;
	timestamp: number;
}

export interface StratifyOperationStateMira extends BaseState {
	strataGroup: StratifyGroup;
	strataCodeHistory: StratifyCode[];
	hasCodeBeenRun: boolean;
}

export const blankStratifyGroup: StratifyGroup = {
	borderColour: '#c300a6',
	name: '',
	selectedVariables: [],
	groupLabels: '',

	// Allow existing transitions to involve multiple strata
	cartesianProduct: true,

	// Create new transitions between strata, this act as a proxy to MIRA "structure", which is
	//   null = everything
	//   [] = nothing
	//   [[a, b], [c, d]] = somewhere in between
	//
	// Here we use a simpler proxy useStructure, where
	//   true => structure = null
	//   false => structuer = []
	structure: null,
	useStructure: true,

	// Always true for now - Feb 2024
	directed: true
};

export const StratifyMiraOperation: Operation = {
	name: WorkflowOperationTypes.STRATIFY_MIRA,
	displayName: 'Stratify model',
	description: 'Stratify a model',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: stratifyModel,
	inputs: [{ type: 'modelId|modelConfigId', label: 'Model or Model configuration' }],
	outputs: [{ type: 'modelId', label: 'Model' }],
	isRunnable: false,
	action: () => {},
	initState: () => {
		const init: StratifyOperationStateMira = {
			strataGroup: blankStratifyGroup,
			strataCodeHistory: [],
			hasCodeBeenRun: false
		};
		return init;
	}
};
