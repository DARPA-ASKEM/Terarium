import _ from 'lodash';
import API from '@/api/api';
import type {
	ModelConfiguration,
	Model,
	Intervention,
	ModelParameter,
	ModelDistribution,
	Initial
} from '@/types/Types';

export const getAllModelConfigurations = async () => {
	const response = await API.get(`/model-configurations`);
	return (response?.data as ModelConfiguration[]) ?? null;
};

export const getModelConfigurationById = async (id: string) => {
	const response = await API.get(`/model-configurations/${id}`);
	return (response?.data as ModelConfiguration) ?? null;
};

export const getModelIdFromModelConfigurationId = async (id: string) => {
	const modelConfiguration = await getModelConfigurationById(id);
	return modelConfiguration?.model_id ?? null;
};

export const createModelConfiguration = async (
	model_id: string | undefined,
	name: string,
	description: string,
	configuration: Model,
	isTemporary?: boolean,
	givenInterventions?: Intervention[]
) => {
	if (!model_id) {
		return null;
	}
	const temporary = isTemporary ?? false;
	const interventions = givenInterventions ?? [];
	const response = await API.post(`/model-configurations`, {
		model_id,
		temporary,
		name,
		description,
		configuration,
		interventions
	});
	return response?.data ?? null;
};

export const addDefaultConfiguration = async (model: Model): Promise<void> => {
	await createModelConfiguration(model.id, 'Default config', 'Default config', model);
};

export const updateModelConfiguration = async (config: ModelConfiguration) => {
	// Do a sanity pass to ensure type-safety
	const model: Model = config.configuration as Model;
	const parameters = model.semantics?.ode.parameters;
	if (parameters) {
		parameters.forEach((param) => {
			if (param.value && typeof param.value === 'string' && _.isNumber(+param.value)) {
				param.value = +param.value;
				console.debug(`corerce ${param.id} ${param.value} to number`);
			}
		});
	}

	// If the "default" config is updated we want to update the model as well
	// because the model as a copy of the data
	if (config.name === 'Default config') {
		API.put(`/models/${config.configuration.id}`, config.configuration);
	}

	const response = await API.put(`/model-configurations/${config.id}`, config);
	return response?.data ?? null;
};

export function getInitial(config: ModelConfiguration, initialId: string): Initial | undefined {
	return config.configuration.semantics?.ode.initials?.find(
		(initial) => initial.target === initialId
	);
}

export function getInitialSource(config: ModelConfiguration, initialId: string): string {
	return config.configuration.metadata?.initials?.[initialId].source ?? '';
}

export function setInitialSource(
	config: ModelConfiguration,
	initialId: string,
	source: string
): void {
	const initial = config.configuration.metadata?.initials?.[initialId];
	if (initial) {
		initial.source = source;
	}
}

export function getParameter(
	config: ModelConfiguration,
	parameterId: string
): ModelParameter | undefined {
	return config.configuration.semantics?.ode.parameters?.find((param) => param.id === parameterId);
}

export function setDistribution(
	config: ModelConfiguration,
	parameterId: string,
	distribution: ModelDistribution
): void {
	const parameter = getParameter(config, parameterId);
	if (parameter) {
		parameter.distribution = distribution;
	}
}

export function removeDistribution(config: ModelConfiguration, parameterId: string): void {
	const parameter = getParameter(config, parameterId);
	if (parameter?.distribution) {
		delete parameter.distribution;
	}
}

export function getInterventions(config: ModelConfiguration): Intervention[] {
	return config.interventions ?? [];
}

// FIXME: for set and remove interventions, we should not be using the index.  This should be addressed when we move to the new model config data structure.
export function setIntervention(
	config: ModelConfiguration,
	index: number,
	intervention: Intervention
): void {
	const interventions = getInterventions(config);
	interventions[index] = intervention;
}

export function removeIntervention(config: ModelConfiguration, index: number): void {
	const interventions = getInterventions(config);
	interventions.splice(index, 1);
}

export function getParameterSource(config: ModelConfiguration, parameterId: string): string {
	return config.configuration.metadata?.parameters?.[parameterId]?.source ?? '';
}

export function setParameterSource(
	config: ModelConfiguration,
	parameterId: string,
	source: string
): void {
	const parameter = config.configuration.metadata?.parameters?.[parameterId];
	if (parameter) {
		parameter.source = source;
	}
}
