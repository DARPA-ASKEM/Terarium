import type { DocumentExtraction } from '@/types/Types';
import { AssetBlock, Operation, WorkflowOperationTypes } from '@/types/workflow';
import createModelFromEquations from '@assets/svg/operator-images/create-model-from-equation.svg';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/modeling/create-model-from-equations/';

export interface EquationBlock {
	text: string;
	isEditedByAI?: boolean;
	pageNumber?: number;
	extractionError?: boolean;
}
export interface EquationFromImageBlock extends DocumentExtraction {
	text: string;
	extractionError?: boolean;
}

export function instanceOfEquationFromImageBlock(
	object: EquationBlock | EquationFromImageBlock
): object is EquationFromImageBlock {
	return 'fileName' in object;
}

export interface ModelFromEquationsState {
	equations: AssetBlock<EquationBlock>[];
	text: string;
	modelFramework: string;
	modelId: string | null;
}

export const ModelFromEquationsOperation: Operation = {
	name: WorkflowOperationTypes.MODEL_FROM_EQUATIONS,
	description: 'Create model from equations',
	displayName: 'Create model from equations',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: createModelFromEquations,
	isRunnable: true,
	inputs: [{ type: 'documentId', label: 'Document' }],
	outputs: [{ type: 'modelId', label: 'Model' }],
	action: () => {},

	initState: () => {
		const init: ModelFromEquationsState = {
			equations: [],
			text: '',
			modelFramework: 'petrinet',
			modelId: null
		};
		return init;
	}
};
