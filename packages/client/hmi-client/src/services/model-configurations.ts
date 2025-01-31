import API from '@/api/api';
import type {
	InitialSemantic,
	Model,
	ModelConfiguration,
	ModelDistribution,
	ObservableSemantic,
	ParameterSemantic
} from '@/types/Types';
import { SemanticType } from '@/types/Types';
import { isEmpty, isNaN, isNumber, keyBy } from 'lodash';
import { pythonInstance } from '@/python/PyodideController';
import { DistributionType } from './distribution';

export interface SemanticOtherValues {
	name: string;
	description?: string;
	target?: string;
	expression?: string;
	expressionMathml?: string;
	referenceId?: string;
	distribution?: ModelDistribution;
	default?: boolean;
}

export const getModelConfigurationById = async (id: string): Promise<ModelConfiguration> => {
	const response = await API.get(`/model-configurations/${id}`);
	return response?.data ?? null;
};

export const createModelConfiguration = async (modelConfiguration: ModelConfiguration): Promise<ModelConfiguration> => {
	delete modelConfiguration.id;
	delete modelConfiguration.createdOn;
	delete modelConfiguration.updatedOn;
	modelConfiguration.temporary = modelConfiguration.temporary ?? false;
	const response = await API.post(`/model-configurations`, modelConfiguration);
	return response?.data ?? null;
};

export const updateModelConfiguration = async (modelConfiguration: ModelConfiguration) => {
	const response = await API.put(`/model-configurations/${modelConfiguration.id}`, modelConfiguration);
	return response?.data ?? null;
};

export const deleteModelConfiguration = async (id: string) => {
	const response = await API.delete(`/model-configurations/${id}`);
	return response?.data ?? null;
};

export const getAsConfiguredModel = async (modelConfigurationId: string): Promise<Model> => {
	const response = await API.get<Model>(`model-configurations/as-configured-model/${modelConfigurationId}`);
	return response?.data ?? null;
};

export const getArchive = async (modelConfiguration: ModelConfiguration): Promise<any> => {
	const response = await API.get(`model-configurations/download/${modelConfiguration.id}`, {
		responseType: 'arraybuffer'
	});
	const blob = new Blob([response?.data], { type: 'application/octet-stream' });
	return blob ?? null;
};

export const postAsConfiguredModel = async (model: Model): Promise<ModelConfiguration> => {
	const response = await API.post<ModelConfiguration>(`model-configurations/as-configured-model/`, model);
	return response?.data ?? null;
};

export const amrToModelConfiguration = async (model: Model): Promise<ModelConfiguration> => {
	const response = await API.post<ModelConfiguration>(`/models/amr-to-model-configuration`, model);
	return response?.data ?? null;
};

export const getModelIdFromModelConfigurationId = async (id: string) => {
	const modelConfiguration = await getModelConfigurationById(id);
	return modelConfiguration?.modelId ?? null;
};

export function getParameters(config: ModelConfiguration): ParameterSemantic[] {
	return config.parameterSemanticList ?? [];
}

export function getInitials(config: ModelConfiguration): InitialSemantic[] {
	return config.initialSemanticList ?? [];
}

export function setParameterDistributions(
	config: ModelConfiguration,
	distributionParameterMappings: { id: string; distribution: ModelDistribution }[]
): void {
	distributionParameterMappings.forEach((mapping) => {
		setParameterDistribution(config, mapping.id, mapping.distribution);
	});
}

export function getParameter(config: ModelConfiguration, parameterId: string): ParameterSemantic | undefined {
	return config.parameterSemanticList?.find((param) => param.referenceId === parameterId);
}

export function getParameterDistribution(
	config: ModelConfiguration,
	parameterId: string,
	useDefaultNan: boolean = false
): ModelDistribution {
	const parameter = getParameter(config, parameterId);
	const defaultValue = useDefaultNan ? NaN : 0;
	if (!parameter) return { type: DistributionType.Constant, parameters: { value: defaultValue } };
	return parameter.distribution;
}

export function setParameterDistribution(
	config: ModelConfiguration,
	parameterId: string,
	distribution: ModelDistribution
): void {
	const parameter = getParameter(config, parameterId);
	if (!parameter) return;

	const type = distribution.type;

	if (type === DistributionType.Constant) {
		parameter.distribution = convertToConstantDistribution(distribution);
	} else if (type === DistributionType.Uniform) {
		parameter.distribution = convertToUniformDistribution(distribution);
	}
}

function convertToConstantDistribution(distribution: ModelDistribution): ModelDistribution {
	// if no parameters, initialize
	if (isEmpty(distribution.parameters)) distribution.parameters = { value: 0 };

	return {
		type: DistributionType.Constant,
		parameters: {
			value: distribution.parameters.value
		}
	};
}

function convertToUniformDistribution(distribution: ModelDistribution): ModelDistribution {
	// if no parameters, initialize
	if (isEmpty(distribution.parameters)) distribution.parameters = { minimum: 0, maximum: 0 };

	return {
		type: DistributionType.Uniform,
		parameters: {
			minimum: distribution.parameters.minimum,
			maximum: distribution.parameters.maximum
		}
	};
}

export function getParameterSource(config: ModelConfiguration, parameterId: string): string {
	return getParameter(config, parameterId)?.source ?? '';
}

export function setParameterSource(config: ModelConfiguration, parameterId: string, source: string): void {
	const parameter = getParameter(config, parameterId);
	if (parameter) {
		parameter.source = source;
	}
}

export function getInitial(config: ModelConfiguration, initialId: string): InitialSemantic | undefined {
	return getInitials(config).find((initial) => initial.target === initialId);
}

export async function setInitialExpression(
	config: ModelConfiguration,
	initialId: string,
	expression: string
): Promise<void> {
	const initial = getInitial(config, initialId);
	if (!initial) return;

	const result = await pythonInstance.parseExpression(expression);
	const mathml = result.mathml;
	initial.expression = expression;
	initial.expressionMathml = mathml;
}

export async function setInitialExpressions(
	config: ModelConfiguration,
	initialExpressions: { id: string; value: string }[]
): Promise<void> {
	await Promise.all(
		initialExpressions.map(async (initial) => {
			await setInitialExpression(config, initial.id, initial.value);
		})
	);
}

export function setInitialSource(config: ModelConfiguration, initialId: string, source: string): void {
	const initial = getInitial(config, initialId);
	if (initial) {
		initial.source = source;
	}
}

export function getInitialSource(config: ModelConfiguration, initialId: string): string {
	return getInitial(config, initialId)?.source ?? '';
}

export function getInitialExpression(config: ModelConfiguration, initialId: string): string {
	return getInitial(config, initialId)?.expression ?? '';
}

export function getObservables(config: ModelConfiguration): ObservableSemantic[] {
	return config.observableSemanticList ?? [];
}

export function getOtherValues(
	configs: ModelConfiguration[],
	id: string,
	key: string,
	otherValueList: string,
	description?: string
) {
	let otherValues: SemanticOtherValues[] = [];

	const modelConfigTableData = configs.map((modelConfig) => ({
		name: modelConfig.name ?? '',
		list: modelConfig[otherValueList]
	}));

	modelConfigTableData.forEach((modelConfig) => {
		const config: ParameterSemantic[] | InitialSemantic[] = modelConfig.list.filter((item) => item[key] === id)[0];
		if (config && modelConfig.name) {
			const data: SemanticOtherValues = { name: modelConfig.name, ...config };
			if (description) {
				data.description = description;
			}
			otherValues = [...otherValues, data];
		}
	});
	return otherValues;
}

export function isNumberInputEmpty(value: string) {
	const number = parseFloat(value);
	return isNaN(number) || !isNumber(number);
}

export function getMissingInputAmount(modelConfiguration: ModelConfiguration) {
	let missingInputs = 0;
	modelConfiguration.initialSemanticList.forEach((initial) => {
		if (isEmpty(initial.expression)) {
			missingInputs++;
		}
	});

	modelConfiguration.parameterSemanticList.forEach((parameter) => {
		if (parameter.distribution.type === DistributionType.Constant) {
			if (isNumberInputEmpty(parameter.distribution.parameters.value)) {
				missingInputs++;
			}
		} else if (
			isNumberInputEmpty(parameter.distribution.parameters.minimum) ||
			isNumberInputEmpty(parameter.distribution.parameters.maximum)
		) {
			missingInputs++;
		}
	});
	return missingInputs;
}

export function getModelParameters(modelConfiguration, source, amrParameters) {
	const configParameters = keyBy(getParameters(modelConfiguration), 'referenceId');
	return amrParameters.map((parameter) => {
		if (configParameters[parameter.id]) return { ...configParameters[parameter.id] };
		return {
			default: false,
			distribution: {
				type: DistributionType.Constant,
				parameters: { value: NaN }
			},
			referenceId: parameter.id,
			type: SemanticType.Parameter,
			source
		};
	});
}

export function getModelInitials(modelConfiguration, source, amrInitials) {
	const configInitials = keyBy(getInitials(modelConfiguration), 'target');
	return amrInitials.map((initial) => {
		if (configInitials[initial.target]) return { ...configInitials[initial.target] };
		return {
			expression: '',
			expressionMathml: '',
			target: initial.target,
			type: SemanticType.Initial,
			source
		};
	});
}

// Get the model configuration name for the given model configuration id
export function getModelConfigName(modelConfigs: ModelConfiguration[], configId: string) {
	const modelConfig = modelConfigs.find((d) => d.id === configId);
	return modelConfig?.name ?? '';
}
