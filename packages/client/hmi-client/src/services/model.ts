import _ from 'lodash';
import API from '@/api/api';
import { useProjects } from '@/composables/project';
import type { MMT } from '@/model-representation/mira/mira-common';
import * as EventService from '@/services/event';
import type { Initial, InterventionPolicy, Model, ModelConfiguration, ModelParameter } from '@/types/Types';
import { Artifact, EventType } from '@/types/Types';
import { AMRSchemaNames, CalendarDateType } from '@/types/common';
import { fileToJson } from '@/utils/file';
import { Ref } from 'vue';
import { DateOptions } from './charts';

export async function createModel(model: Model): Promise<Model | null> {
	delete model.id;
	const response = await API.post(`/models`, model);
	return response?.data ?? null;
}

export async function createModelFromOld(oldModel: Model, newModel: Model): Promise<Model | null> {
	delete newModel.id;
	const response = await API.post(`/models/new-from-old`, {
		newModel,
		oldModel
	});
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
export async function getMMT(model: Model): Promise<MMT | null> {
	const response = await API.post('/mira/amr-to-mmt', model);
	const mmt = response?.data?.response;
	if (!mmt) {
		console.error(`Failed to convert model ${model.id}`);
		return null;
	}
	return mmt as MMT;
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
		return _.isEmpty(model.model?.states) && _.isEmpty(model.model?.transitions);
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

/**
 * For a given state variable, return the state modifiers
 */
export const getModelStateModifiers = (varId: string, model: Model) => {
	const states = model.model.states;
	const modifiers: Record<string, string> = (states as any[]).find((s) => s.id === varId)?.grounding?.modifiers || {};
	return modifiers;
};

/**
 * For a given state variable, return the state modifiers object entries in a array of string formatted in the form of key:value, e.g. ['key:value']. The array is sorted.
 */
export const getStateVariableStrataEntries = (varId: string, model: Model) => {
	const modifiers = getModelStateModifiers(varId, model);
	const entries = Object.entries(modifiers)
		.map(([key, value]) => `${key}:${value}`)
		.sort();
	// Filter out entries with value that are not included in the name.
	// For example, we see something like "modifiers": { "diagnosis": "ncit:C15220", "Age": "old" }, for id, `I_old`. We only care about strata, 'old' in this case.
	// TODO: This is not ideal and have potential issues in some edge cases. We need to find a better way to identify the strata for each state variable.
	const filtered = entries.filter((entry) => varId.includes(entry.split(':')[1]));
	return filtered;
};

/**
 * Group selected state variables for the model by strata.
 *
 * @param {string[]} selectedVariables - Array of selected variable IDs.
 * @param {Record<string, string>} pyciemssMap - Mapping of variable IDs to their corresponding pyciemss representation.
 * @param {Model} model - The model containing the state variables.
 * @returns {Object} An object containing two properties:
 *  - selectedVariablesGroupByStrata: A grouping of selected variables by their strata.
 *  - allVariablesGroupByStrata: A grouping of all variables by their strata.
 */
export const groupVariablesByStrata = (
	selectedVariables: string[],
	pyciemssMap: Record<string, string>,
	model: Model
) => {
	const selectedVariablesGroupByStrata = _.groupBy(selectedVariables, (v) =>
		getStateVariableStrataEntries(v, model).join('-')
	);
	const allVariablesGroupByStrata = {};
	Object.keys(selectedVariablesGroupByStrata).forEach((group) => {
		allVariablesGroupByStrata[group] = Object.keys(pyciemssMap).filter(
			(k) => group === getStateVariableStrataEntries(k, model).join('-')
		);
	});
	return { selectedVariablesGroupByStrata, allVariablesGroupByStrata };
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

export function getTimeUnits(model: Model): CalendarDateType {
	return model?.semantics?.ode?.time?.units?.expression || '';
}

export function getCalendarSettingsFromModel(model: Model): { view: CalendarDateType; format: string } {
	const units = model?.semantics?.ode?.time?.units?.expression;
	const view = units;
	let format;

	switch (units) {
		case CalendarDateType.MONTH:
			format = 'MM, yy';
			break;
		case CalendarDateType.YEAR:
			format = 'yy';
			break;
		case CalendarDateType.DATE:
		default:
			format = 'MM dd, yy';
			break;
	}

	return { view, format };
}

export function getVegaDateOptions(
	model: Model | null,
	modelConfiguration: ModelConfiguration | null
): DateOptions | undefined {
	let dateOptions;
	if (model && modelConfiguration?.temporalContext) {
		dateOptions = {
			dateFormat: getTimeUnits(model),
			startDate: new Date(modelConfiguration.temporalContext)
		};
	}
	return dateOptions;
}
