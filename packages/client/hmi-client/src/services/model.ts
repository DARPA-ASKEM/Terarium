import API from '@/api/api';
import { useProjects } from '@/composables/project';
import * as EventService from '@/services/event';
import type { Initial, Model, ModelConfigurationLegacy, ModelParameter } from '@/types/Types';
import { Artifact, AssetType, EventType } from '@/types/Types';
import { AMRSchemaNames, ModelServiceType } from '@/types/common';
import { fileToJson } from '@/utils/file';
import { logger } from '@/utils/logger';
import { isEmpty } from 'lodash';
import { modelCard } from './goLLM';
import { profileModel } from './knowledge';

export async function createModel(model: Model): Promise<Model | null> {
	delete model.id;
	const response = await API.post(`/models`, model);
	return response?.data ?? null;
}

/**
 * Get Model from the data service
 * @return Model|null - the model, or null if none returned by API
 */
export async function getModel(modelId: string): Promise<Model | null> {
	const response = await API.get(`/models/${modelId}`);
	return response?.data ?? null;
}

//
// Retrieve multiple datasets by their IDs
// FIXME: the backend does not support bulk fetch
//        so for now we are fetching by issuing multiple API calls
export async function getBulkModels(modelIDs: string[]) {
	const result: Model[] = [];
	const promiseList = [] as Promise<Model | null>[];
	modelIDs.forEach((modelId) => {
		promiseList.push(getModel(modelId));
	});
	const responsesRaw = await Promise.all(promiseList);
	responsesRaw.forEach((r) => {
		if (r) {
			result.push(r);
		}
	});
	return result;
}

// Note: will not work with decapodes
export async function getMMT(model: Model) {
	const response = await API.post('/mira/amr-to-mmt', model);

	const miraModel = response?.data?.response;
	if (!miraModel) throw new Error(`Failed to convert model ${model.id}`);

	return response?.data?.response ?? null;
}

/**
 * Get all models
 * @return Array<Model>|null - the list of all models, or null if none returned by API
 */
export async function getAllModelDescriptions(): Promise<Model[] | null> {
	const response = await API.get('/models/descriptions?page-size=500');
	return response?.data ?? null;
}

export async function updateModel(model: Model) {
	const response = await API.put(`/models/${model.id}`, model);
	EventService.create(
		EventType.PersistModel,
		useProjects().activeProject.value?.id,
		JSON.stringify({
			id: model.id
		})
	);
	return response?.data ?? null;
}

export async function getModelConfigurations(
	modelId: Model['id']
): Promise<ModelConfigurationLegacy[]> {
	const response = await API.get(`/models/${modelId}/model-configurations`);
	return response?.data ?? ([] as ModelConfigurationLegacy[]);
}

export async function processAndAddModelToProject(artifact: Artifact): Promise<string | null> {
	const response = await API.post(`/mira/convert-and-create-model`, {
		artifactId: artifact.id
	});
	const modelId = response.data.id;
	return modelId ?? null;
}

// A helper function to check if a model is empty.
export function isModelEmpty(model: Model) {
	if (getModelType(model) === AMRSchemaNames.PETRINET) {
		return isEmpty(model.model?.states) && isEmpty(model.model?.transitions);
	}
	// TODO: support different frameworks' version of empty
	return false;
}

// A helper function to check if a model name already exists
export function validateModelName(name: string): boolean {
	const existingModelNames: string[] = useProjects()
		.getActiveProjectAssets(AssetType.Model)
		.map((item) => item.assetName ?? '');

	if (name.trim().length === 0) {
		logger.info('Model name cannot be empty - please enter a different name');
		return false;
	}
	if (existingModelNames.includes(name.trim())) {
		logger.info('Duplicate model name - please enter a different name');
		return false;
	}

	return true;
}

/**
 * Validates the provided file and returns the json object if it is a valid AMR
 * @param file file to validate
 * @returns json object if valid, null otherwise
 */
export async function validateAMRFile(file: File): Promise<Model | null> {
	if (!file.name.endsWith('.json')) return null;
	const jsonObject = await fileToJson(file);
	if (!jsonObject) return null;
	if (!isValidAMR(jsonObject)) return null;
	return jsonObject as unknown as Model;
}

/**
 * Checks if the provided json object is a valid AMR
 * @param json json object to validate
 * @returns boolean
 */
export function isValidAMR(json: Record<string, unknown>) {
	const schema: string | undefined = (json?.header as any)?.schema?.toLowerCase();
	let schemaName: string | undefined = (json?.header as any)?.schema_name?.toLowerCase();

	if (!schemaName && schema && json?.header) {
		schemaName = Object.values(AMRSchemaNames).find((v) => schema.toLowerCase().includes(v));
		(json.header as any).schema_name = schemaName;
	}

	if (!schema || !schemaName) return false;
	if (!Object.values(AMRSchemaNames).includes(schemaName as AMRSchemaNames)) return false;
	if (!Object.values(AMRSchemaNames).some((name) => schema.includes(name))) return false;
	return true;
}

export async function profile(modelId: string, documentId: string): Promise<Model | null> {
	return profileModel(modelId, documentId);
}

/**
 * Generates a model card based on the provided document ID, model ID, and model service type.
 *
 * @param {string} documentId - The ID of the document.
 * @param {string} modelId - The ID of the model.
 * @param {ModelServiceType} modelServiceType - The type of the model service.
 */
export async function generateModelCard(
	documentId: string,
	modelId: string,
	modelServiceType: ModelServiceType
): Promise<void> {
	if (modelServiceType === ModelServiceType.TA1) {
		await profile(modelId, documentId);
	}

	if (modelServiceType === ModelServiceType.TA4) {
		await modelCard(documentId);
	}
}

// helper function to get the model type, will always default to petrinet if the model is not found
export function getModelType(model: Model | null | undefined): AMRSchemaNames {
	const schemaName = model?.header?.schema_name?.toLowerCase();
	if (schemaName === 'regnet') {
		return AMRSchemaNames.REGNET;
	}
	if (schemaName === 'stockflow') {
		return AMRSchemaNames.STOCKFLOW;
	}
	if (schemaName === 'decapodes' || schemaName === 'decapode') {
		return AMRSchemaNames.DECAPODES;
	}
	return AMRSchemaNames.PETRINET;
}

// Converts a model into latex equation, either one of petrinet, stocknflow, or regnet;
export async function getModelEquation(model: Model): Promise<string> {
	const unSupportedFormats = ['decapodes'];
	if (unSupportedFormats.includes(model.header.schema_name as string)) {
		console.warn(`getModelEquation: ${model.header.schema_name} not supported `);
		return '';
	}

	/* TODO - Replace the GET with the POST when the backend is ready,
	 *        see PR https://github.com/DARPA-ASKEM/sciml-service/pull/167
	 */
	const response = await API.get(`/transforms/model-to-latex/${model.id}`);
	// const response = await API.post(`/transforms/model-to-latex/`, model);
	const latex = response?.data?.latex;
	if (!latex) return '';
	return latex ?? '';
}

export function isInitial(obj: Initial | ModelParameter | null): obj is Initial {
	return obj !== null && 'target' in obj && 'expression' in obj && 'expression_mathml' in obj;
}

export function isModelParameter(obj: Initial | ModelParameter | null): obj is ModelParameter {
	return obj !== null && 'id' in obj;
}
