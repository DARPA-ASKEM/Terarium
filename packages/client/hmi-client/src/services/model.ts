import API from '@/api/api';
import { useProjects } from '@/composables/project';
import type { MMT } from '@/model-representation/mira/mira-common';
import * as EventService from '@/services/event';
import type { Initial, InterventionPolicy, Model, ModelConfiguration, ModelParameter } from '@/types/Types';
import { Artifact, EventType } from '@/types/Types';
import { AMRSchemaNames } from '@/types/common';
import { fileToJson } from '@/utils/file';
import { isEmpty } from 'lodash';
import { Ref } from 'vue';

export async function createModel(model: Model): Promise<Model | null> {
	delete model.id;
	const response = await API.post(`/models`, model);
	return response?.data ?? null;
}

export async function createModelAndModelConfig(file: File, progress?: Ref<number>): Promise<Model | null> {
	const formData = new FormData();
	formData.append('file', file);

	const response = await API.post(`/model-configurations/import`, formData, {
		headers: {
			'Content-Type': 'multipart/form-data'
		},
		onUploadProgress(progressEvent) {
			if (progress) {
				progress.value = Math.min(90, Math.round((progressEvent.loaded * 100) / (progressEvent?.total ?? 100)));
			}
		},
		timeout: 3600000
	});

	return response?.data ?? null;
}

/**
 * Get Model from the data service
 * @return Model|null - the model, or null if none returned by API
 */
export async function getModel(modelId: string, projectId?: string): Promise<Model | null> {
	const response = await API.get(`/models/${modelId}`, {
		params: { 'project-id': projectId }
	});
	return response?.data ?? null;
}

/**
 * Get the model-configuration's underlying model
 */
export async function getModelByModelConfigurationId(configId: string): Promise<Model | null> {
	const response = await API.get(`/models/from-model-configuration/${configId}`);
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

	const response = await API.post(`/mira/model-to-latex`, model);
	return response?.data?.response ?? '';
}

export const getUnitsFromModelParts = (model: Model) => {
	const unitMapping: { [key: string]: string } = {
		_time: model?.semantics?.ode?.time?.units?.expression || ''
	};
	[...(model?.model.states ?? []), ...(model?.semantics?.ode?.parameters ?? [])].forEach((v) => {
		unitMapping[v.id] = v.units?.expression || '';
	});
	// Add units for observables
	(model?.semantics?.ode?.observables || []).forEach((o) => {
		(o.states ?? []).forEach((s) => {
			if (!unitMapping[o.id]) unitMapping[o.id] = unitMapping[s] || '';
		});
	});
	return unitMapping;
};

export const getTypesFromModelParts = (model: Model) => {
	const typeMapping: { [key: string]: string } = {};
	[...(model.model.states ?? [])].forEach((v) => {
		typeMapping[v.id] = 'state';
	});
	[...(model.semantics?.ode?.parameters ?? [])].forEach((v) => {
		typeMapping[v.id] = 'parameter';
	});
	(model.semantics?.ode?.observables || []).forEach((o) => {
		typeMapping[o.id] = 'observable';
	});
	return typeMapping;
};

export function isInitial(obj: Initial | ModelParameter | null): obj is Initial {
	return obj !== null && 'target' in obj && 'expression' in obj && 'expression_mathml' in obj;
}

export function isModelParameter(obj: Initial | ModelParameter | null): obj is ModelParameter {
	return obj !== null && 'id' in obj;
}

export function stringToLatexExpression(expression: string): string {
	// Wrap everything after the first underscore in {} for each variable
	// and add a \ before subsequent underscores
	let latexExpression = expression.replace(/(_)([a-zA-Z0-9_]+)/g, (_match, p1, p2) => {
		// Replace subsequent underscores in p2 with \_
		const modifiedP2 = p2.replace(/_/g, '\\_');
		return `${p1}{${modifiedP2}}`;
	});

	// (Unsure about this) Convert * to space (implicit multiplication) for LaTeX
	// latexExpression = latexExpression.replace(/\*/g, ' ');

	// Convert ^ to LaTeX superscript notation
	latexExpression = latexExpression.replace(/\^([a-zA-Z0-9]+)/g, '^{$1}');

	// Detect and convert fractions a/b to \frac{a}{b}
	latexExpression = latexExpression.replace(/([a-zA-Z0-9]+)\/([a-zA-Z0-9]+)/g, '\\frac{$1}{$2}');
	return latexExpression;
}
