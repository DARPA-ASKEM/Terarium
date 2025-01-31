import _ from 'lodash';
import API from '@/api/api';
import {
	ChartSetting,
	ChartSettingEnsembleVariable,
	ChartSettingEnsembleVariableOptions,
	ChartSettingComparison,
	ChartSettingSensitivity,
	ChartSettingType
} from '@/types/common';
import { v4 as uuidv4 } from 'uuid';
import { b64DecodeUnicode } from '@/utils/binary';
import { ChartAnnotation } from '@/types/Types';
import { CATEGORICAL_SCHEME, ForecastChartOptions } from './charts';

export interface LLMGeneratedChartAnnotation {
	request: string;
	layerSpec: any;
}

export type EnsembleVariableChartSettingOption =
	| 'showIndividualModels'
	| 'relativeToEnsemble'
	| 'showIndividualModelsWithWeight';

export function isChartSettingEnsembleVariable(setting: ChartSetting): setting is ChartSettingEnsembleVariable {
	return (<ChartSettingEnsembleVariable>setting).type === ChartSettingType.VARIABLE_ENSEMBLE;
}

export function isChartSettingComparisonVariable(setting: ChartSetting): setting is ChartSettingComparison {
	return (<ChartSettingComparison>setting).type === ChartSettingType.VARIABLE_COMPARISON;
}

export const CHART_SETTING_WITH_QUANTILES_OPTIONS = [
	ChartSettingType.VARIABLE_ENSEMBLE,
	ChartSettingType.VARIABLE_COMPARISON,
	ChartSettingType.VARIABLE
];

/**
 * Adds a new multi-variable chart setting to the provided settings array if it doesn't already exist.
 * Note that the order of the variables doesn't matter. e.g. chart settings with selectedVariables, ['a', 'b'] is treated same as the one with ['b', 'a'].
 *
 * @param settings - The array of existing chart settings.
 * @param type - The type of the chart setting to be added.
 * @param selectedVariables - The array of selected variables for the new chart setting.
 * @returns The updated array of chart settings.
 */
export function addMultiVariableChartSetting(
	settings: ChartSetting[],
	type: ChartSettingType,
	selectedVariables: string[]
) {
	const existingSetting = settings.find(
		(setting) => setting.type === type && _.isEqual(new Set(setting.selectedVariables), new Set(selectedVariables))
	);
	if (existingSetting || _.isEmpty(selectedVariables)) return settings;
	const newSetting: ChartSetting = {
		id: uuidv4(),
		name: selectedVariables.join(', '),
		selectedVariables,
		type,
		scale: ''
	} as ChartSetting;
	return [...settings, newSetting];
}

/**
 * Get ensemble chart setting options from the chart settings. Assumes that all ensemble settings have the same options.
 * @param chartSettings
 * @returns The ensemble chart setting options.
 */
export function getEnsembleChartSettingOptions(
	chartSettings: ChartSetting[]
): ChartSettingEnsembleVariableOptions | null {
	const ensembleSettings = chartSettings.find(isChartSettingEnsembleVariable);
	return ensembleSettings
		? {
				showIndividualModels: ensembleSettings.showIndividualModels,
				relativeToEnsemble: ensembleSettings.relativeToEnsemble,
				showIndividualModelsWithWeight: ensembleSettings.showIndividualModelsWithWeight
			}
		: null;
}

/**
 * Get existing qauntile chart setting options from the chart settings with the assumed that all applicable settings have the same options.
 * @param chartSettings - The array of chart settings.
 * @returns - The quantile chart setting options.
 */
export function getQauntileChartSettingOptions(chartSettings: ChartSetting[]) {
	const settingsWithQuantilesOption = chartSettings.find((s) => s.showQuantiles || Boolean(s.quantiles?.length));
	return settingsWithQuantilesOption
		? {
				showQuantiles: settingsWithQuantilesOption.showQuantiles,
				quantiles: settingsWithQuantilesOption.quantiles
			}
		: null;
}

export function createNewChartSetting(
	name: string,
	type: ChartSettingType,
	selectedVariables: string[],
	options: Partial<ChartSetting> = {}
): ChartSetting {
	// Default setting
	const setting: ChartSetting = {
		id: uuidv4(),
		name,
		selectedVariables,
		type,
		scale: '',
		primaryColor: CATEGORICAL_SCHEME[0]
	};
	if (isChartSettingEnsembleVariable(setting)) {
		// Default options for ensemble variable chart
		setting.showIndividualModels = true;
		setting.relativeToEnsemble = false;
	}
	if (CHART_SETTING_WITH_QUANTILES_OPTIONS.includes(type)) {
		// Default options for quantile chart
		setting.showQuantiles = false;
		setting.quantiles = [0.95, 0.5];
	}
	// Apply and override defaults with the provided options.
	Object.assign(setting, options);

	return setting;
}

/**
 * Updates the given chart settings based on the selected variables and return it as new settings.
 * This function assumes that the given chart settings are for single variable charts.
 *
 * @param {ChartSetting[]} settings - The current array of chart settings.
 * @param {string[]} variableSelection - An array of selected variables to update the chart settings with.
 * @param {ChartSettingType} type - The type of chart setting to update.
 * @returns {ChartSetting[]} The updated array of chart settings.
 */
export function updateChartSettingsBySelectedVariables(
	settings: ChartSetting[],
	type: ChartSettingType,
	variableSelection: string[]
) {
	// Extract existing chart setting options that were applied to multiple charts for the new settings to inherit.
	// So that the new settings will have the same options as the existing settings.
	const existingOptions: Partial<ChartSetting> = {};
	if (CHART_SETTING_WITH_QUANTILES_OPTIONS.includes(type))
		Object.assign(existingOptions, getQauntileChartSettingOptions(settings));
	if (type === ChartSettingType.VARIABLE_ENSEMBLE)
		Object.assign(existingOptions, getEnsembleChartSettingOptions(settings));

	// previous settings without the settings of the given type
	const previousSettings = settings.filter((setting) => setting.type !== type);
	// selected settings for the given type
	const selectedSettings = variableSelection.map((variable) => {
		const found = settings.find((setting) => setting.selectedVariables[0] === variable && setting.type === type);
		return found ?? createNewChartSetting(variable, type, [variable], existingOptions);
	});
	const newSettings: ChartSetting[] = [...previousSettings, ...selectedSettings];
	return newSettings;
}

export function updateSensitivityChartSettingOption(
	settings: ChartSettingSensitivity[],
	options: { selectedVariables: string[]; selectedInputVariables: string[]; timepoint: number }
) {
	// previous settings without the settings of the given type
	const previousSettings = settings.filter((setting) => setting.type !== ChartSettingType.SENSITIVITY);

	const selectedSettings = options.selectedVariables?.map((variable) => {
		const found = settings.find(
			(setting) => setting.selectedVariables[0] === variable && setting.type === ChartSettingType.SENSITIVITY
		);
		if (found) {
			found.selectedInputVariables = options.selectedInputVariables;
			found.timepoint = options.timepoint;
			return found;
		}
		return createNewChartSetting(variable, ChartSettingType.SENSITIVITY, [variable], {
			selectedInputVariables: options.selectedInputVariables,
			timepoint: options.timepoint
		});
	});

	const newSettings: ChartSetting[] = [...previousSettings, ...(selectedSettings ?? [])];
	return newSettings;
}

/**
 * Update all chart settings with the given update object. If includeTypes is provided, only the settings with the specified types will be updated.
 * @param settings - The current array of chart settings.
 * @param update - The partial update to apply to the chart settings.
 * @param includeTypes - The types of chart settings to update.
 * @returns The updated array of chart settings.
 */
export function updateAllChartSettings(
	settings: ChartSetting[],
	update: Partial<ChartSetting>,
	includeTypes?: ChartSettingType[]
) {
	const newSettings = settings.map((setting) => {
		if (includeTypes === undefined || includeTypes.includes(setting.type)) {
			Object.assign(setting, update);
		}
		return setting;
	});
	return newSettings;
}

/**
 * Filters chart settings based on the specified type and variable selection.
 * Assume that this is used for single variable charts only.
 *
 * @param {ChartSetting[]} settings - The array of chart settings to filter.
 * @param {ChartSettingType} type - The type of chart setting to filter by.
 * @param {string[]} variableSelection - The array of variable selections to filter by.
 * @returns {ChartSetting[]} The filtered array of chart settings.
 */
export function filterChartSettingsByVariables(
	settings: ChartSetting[],
	type: ChartSettingType,
	variableSelection: string[]
) {
	// previous settings without the settings of the given type
	const previousSettings = settings.filter((setting) => setting.type !== type);
	const selected = settings.filter(
		(setting) => setting.type === type && variableSelection.includes(setting.selectedVariables[0])
	);
	const newSettings: ChartSetting[] = [...previousSettings, ...selected];
	return newSettings;
}

/**
 * Deletes the chart settings with the given id and returns the new settings.
 *
 * @param {ChartSetting[]} settings - The current array of chart settings.
 * @param {string} id - The id of the chart setting to delete.
 * @returns {ChartSetting[]} The updated array of chart settings.
 */
export function removeChartSettingById(settings: ChartSetting[], id: string) {
	return settings.filter((setting) => setting.id !== id);
}

export async function saveAnnotation(
	annotationLayerSpec: LLMGeneratedChartAnnotation,
	nodeId: string,
	chartId: string
) {
	const annotation: ChartAnnotation = {
		id: uuidv4(),
		description: annotationLayerSpec.request,
		nodeId,
		outputId: '',
		chartId,
		layerSpec: annotationLayerSpec.layerSpec,
		llmGenerated: true,
		metadata: {}
	};
	const { data } = await API.post('/chart-annotations', annotation);
	return data as ChartAnnotation;
}

export async function deleteAnnotation(annotationId: string) {
	const { data } = await API.delete(`/chart-annotations/${annotationId}`);
	return data;
}

export async function fetchAnnotations(nodeId: string) {
	const { data } = await API.post(`/chart-annotations/search`, { nodeId });
	return data as ChartAnnotation[];
}

export async function generateForecastChartAnnotation(
	request: string,
	timeField: string,
	variables: string[],
	options: Partial<ForecastChartOptions>
): Promise<LLMGeneratedChartAnnotation> {
	const axisTitle = {
		x: options.xAxisTitle ?? '',
		y: options.yAxisTitle ?? ''
	};
	const translateMap = _.pick(options.translationMap ?? {}, variables);
	const preamble = `
		Here is the information of the existing target chart spec where you need to add the annotations:
    - The existing chart follows a similar pattern as the above Example Chart Spec like:
        {{
          ...
          "transform": [
            {{
              "fold": ${JSON.stringify(variables)},
              "as": ["variableField', "valueField"]
            }}
          ],
          "layer": [
            {{
              ...
              "encoding": {{
                "x": {{"field": "${timeField}", "type": "quantitative", "axis": {{"title": "${axisTitle.x}"}}}},
                "y": {{"field": "valueField", "type": "quantitative", "axis": {{"title": "${axisTitle.y}"}}}}
              }}
            }}
            ...
          ]
        }}
    - Assume all unknown variables except the time field are for the y-axis and are renamed to the valueField.
    - Make sure possible values for 'valueField' are ${JSON.stringify(variables)} and try best to translate the variables mentioned from the request to the variables for the 'valueField'.
    - Leverage this variable to human readable name mapping: ${JSON.stringify(translateMap)} if needed.
	`;
	const instruction = `
    Give me the layer object to be added to the existing chart spec based on the following user request.

    ${request}
	`;
	const { data } = await API.post(
		`/gollm/chart-annotation?mode=SYNC`,
		{ preamble, instruction },
		{
			headers: {
				'Content-Type': 'application/json'
			}
		}
	);
	const str = b64DecodeUnicode(data.output);
	const result = JSON.parse(str);
	const layerSpec = result.response ?? null;
	return {
		request,
		layerSpec
	};
}
