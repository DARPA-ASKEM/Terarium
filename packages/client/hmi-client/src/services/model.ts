import API from '@/api/api';
import { useProjects } from '@/composables/project';
import * as EventService from '@/services/event';
import type { Initial, InterventionPolicy, Model, ModelConfiguration, ModelParameter } from '@/types/Types';
import { Artifact, EventType } from '@/types/Types';
import { AMRSchemaNames } from '@/types/common';
import { fileToJson } from '@/utils/file';
import { isEmpty } from 'lodash';
import type { MMT } from '@/model-representation/mira/mira-common';

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

	return (response?.data?.response as MMT) ?? null;
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
	await EventService.create(
		EventType.PersistModel,
		useProjects().activeProject.value?.id,
		JSON.stringify({
			id: model.id
		})
	);
	return response?.data ?? null;
}

export async function getModelConfigurationsForModel(modelId: Model['id']): Promise<ModelConfiguration[]> {
	const response = await API.get(`/models/${modelId}/model-configurations`);
	return response?.data ?? ([] as ModelConfiguration[]);
}

export async function getInterventionPoliciesForModel(modelId: Model['id']): Promise<InterventionPolicy[]> {
	const response = await API.get(`/models/${modelId}/intervention-policies`);
	return response?.data ?? ([] as InterventionPolicy[]);
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
	return Object.values(AMRSchemaNames).some((name) => schema.includes(name));
}

// Helper function to get the model type, will always default to PetriNet if the model is not found
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

// Converts a model into LaTeX equation, either one of PetriNet, StockN'Flow, or RegNet;
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

export const getUnitsFromModelParts = (model: Model) => {
	const modelVariableUnits: { [key: string]: string } = {
		_time: model?.semantics?.ode?.time?.units?.expression || ''
	};
	[...(model?.model.states ?? []), ...(model?.semantics?.ode?.parameters ?? [])].forEach((v) => {
		modelVariableUnits[v.id] = v.units?.expression || '';
	});
	return modelVariableUnits;
};

export function isInitial(obj: Initial | ModelParameter | null): obj is Initial {
	return obj !== null && 'target' in obj && 'expression' in obj && 'expression_mathml' in obj;
}

export function isModelParameter(obj: Initial | ModelParameter | null): obj is ModelParameter {
	return obj !== null && 'id' in obj;
}
