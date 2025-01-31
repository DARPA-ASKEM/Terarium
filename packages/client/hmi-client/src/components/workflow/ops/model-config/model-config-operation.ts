import type { BaseState, Operation } from '@/types/workflow';
import { WorkflowOperationTypes } from '@/types/workflow';
import type { ModelConfiguration } from '@/types/Types';
import { NotebookHistory } from '@/services/notebook';
import configureModel from '@assets/svg/operator-images/configure-model.svg';
import { cloneDeep, differenceWith, isEqual, omit } from 'lodash';

export const name = 'ModelConfigOperation';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/config-and-intervention/configure-model/';

export interface ModelConfigOperationState extends BaseState {
	transientModelConfig: ModelConfiguration;
	notebookHistory: NotebookHistory[];
	hasCodeRun: boolean;
	modelConfigTaskIds: string[];
}

export const blankModelConfig: ModelConfiguration = {
	id: '',
	modelId: '',
	name: '',
	description: '',
	simulationId: '',
	observableSemanticList: [],
	parameterSemanticList: [],
	initialSemanticList: [],
	inferredParameterList: []
};

export const ModelConfigOperation: Operation = {
	name: WorkflowOperationTypes.MODEL_CONFIG,
	displayName: 'Configure model',
	description: 'Create model configurations.',
	documentationUrl: DOCUMENTATION_URL,
	imageUrl: configureModel,
	isRunnable: true,
	inputs: [
		{ type: 'modelId', label: 'Model' },
		{ type: 'documentId', label: 'Document', isOptional: true },
		{ type: 'datasetId', label: 'Dataset', isOptional: true }
	],
	outputs: [{ type: 'modelConfigId', label: 'Model configuration' }],
	action: async () => ({}),
	initState: () => {
		const init: ModelConfigOperationState = {
			transientModelConfig: blankModelConfig,
			notebookHistory: [],
			hasCodeRun: false,
			modelConfigTaskIds: []
		};
		return init;
	}
};

// check if model configs are equal
export const isModelConfigsEqual = (
	originalConfig: ModelConfiguration | null,
	newConfig: ModelConfiguration | null
): boolean => {
	if (!originalConfig || !newConfig) return false;
	originalConfig = cloneDeep(originalConfig);
	newConfig = cloneDeep(newConfig);

	// first check if the base keys are equal
	const baseKeysToOmit = [
		'createdOn',
		'updatedOn',
		'initialSemanticList',
		'parameterSemanticList',
		'observableSemanticList',
		'inferredParameterList'
	];
	const baseKeysAreEqual = isEqual(omit(originalConfig, baseKeysToOmit), omit(newConfig, baseKeysToOmit));

	if (!baseKeysAreEqual) return false;

	// secondly check if the lists are equals
	const listKeysToOmit = ['createdOn', 'updatedOn', 'id'];

	const omitKeys = (list, keys) => list.map((item) => omit(item, keys));

	const compareLists = (list1, list2, keysToOmit) => {
		const processedList1 = omitKeys(list1, keysToOmit);
		const processedList2 = omitKeys(list2, keysToOmit);
		// Check if the lengths are equal
		if (processedList1.length !== processedList2.length) {
			return false;
		}

		// Compare the elements of the lists
		return differenceWith(processedList1, processedList2, isEqual).length === 0;
	};

	const listsAreEqual =
		compareLists(originalConfig.initialSemanticList, newConfig.initialSemanticList, listKeysToOmit) &&
		compareLists(originalConfig.parameterSemanticList, newConfig.parameterSemanticList, listKeysToOmit) &&
		compareLists(originalConfig.observableSemanticList, newConfig.observableSemanticList, listKeysToOmit);

	if (!listsAreEqual) return false;

	return true;
};

// check if model config values are equal
export const isModelConfigValuesEqual = (
	originalConfig: ModelConfiguration | null,
	newConfig: ModelConfiguration | null
): boolean => {
	// if either of the configs is empty, return false
	if (!originalConfig || !newConfig) return false;
	// remove proxies
	originalConfig = cloneDeep(originalConfig);
	newConfig = cloneDeep(newConfig);

	const originalInitialList = originalConfig.initialSemanticList;
	const originalParameterList = originalConfig.parameterSemanticList;
	const originalObservableList = originalConfig.observableSemanticList;

	const newInitialList = newConfig.initialSemanticList;
	const newParameterList = newConfig.parameterSemanticList;
	const newObservableList = newConfig.observableSemanticList;

	const initialMap = new Map(newInitialList.map((item) => [item.target, item]));
	const parameterMap = new Map(newParameterList.map((item) => [item.referenceId, item]));
	const observableMap = new Map(newObservableList.map((item) => [item.referenceId, item]));

	// compare initial values are the same
	const initialValuesDifferent = originalInitialList.some((originalItem) => {
		const newItem = initialMap.get(originalItem.target);
		return (
			newItem &&
			(!isEqual(originalItem.expression, newItem.expression) ||
				!isEqual(originalItem.expressionMathml, newItem.expressionMathml))
		);
	});

	if (initialValuesDifferent) return false;

	// compare parameter values are the same
	const parameterValuesDifferent = originalParameterList.some((originalItem) => {
		const newItem = parameterMap.get(originalItem.referenceId);
		return newItem && !isEqual(originalItem.distribution, newItem.distribution);
	});

	if (parameterValuesDifferent) return false;

	// compare observable values are the same
	const observableValuesDifferent = originalObservableList.some((originalItem) => {
		const newItem = observableMap.get(originalItem.referenceId);
		return (
			newItem &&
			(!isEqual(originalItem.expression, newItem.expression) ||
				!isEqual(originalItem.expressionMathml, newItem.expressionMathml))
		);
	});

	if (observableValuesDifferent) return false;

	return true;
};
