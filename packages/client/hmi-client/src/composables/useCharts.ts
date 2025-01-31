import _, { capitalize, cloneDeep } from 'lodash';
import { mean, variance } from 'd3';
import { computed, ComputedRef, ref, Ref, watchEffect } from 'vue';
import { VisualizationSpec } from 'vega-embed';
import {
	applyForecastChartAnnotations,
	AUTOSIZE,
	SENSITIVITY_COLOUR_SCHEME,
	CATEGORICAL_SCHEME,
	createErrorChart,
	createForecastChart,
	createForecastChartAnnotation,
	createHistogramChart,
	createInterventionChartMarkers,
	createQuantilesForecastChart,
	createSimulateSensitivityScatter,
	ForecastChartOptions,
	expressionFunctions,
	GroupedDataArray,
	createSensitivityRankingChart
} from '@/services/charts';
import { flattenInterventionData } from '@/services/intervention-policy';
import { DataArray, extractModelConfigIdsInOrder, extractModelConfigIds } from '@/services/models/simulation-service';
import {
	ChartSetting,
	ChartSettingComparison,
	ChartSettingEnsembleVariable,
	ChartSettingSensitivity,
	ChartSettingType
} from '@/types/common';
import { ChartAnnotation, Intervention, Model, ModelConfiguration } from '@/types/Types';
import { displayNumber } from '@/utils/number';
import {
	getStateVariableStrataEntries,
	getUnitsFromModelParts,
	getVegaDateOptions,
	groupVariablesByStrata
} from '@/services/model';
import { CalibrateMap, isCalibrateMap } from '@/services/calibrate-workflow';
import { isChartSettingComparisonVariable, isChartSettingEnsembleVariable } from '@/services/chart-settings';
import {
	CalibrateEnsembleMappingRow,
	isCalibrateEnsembleMappingRow
} from '@/components/workflow/ops/calibrate-ensemble-ciemss/calibrate-ensemble-ciemss-operation';
import { SimulateEnsembleMappingRow } from '@/components/workflow/ops/simulate-ensemble-ciemss/simulate-ensemble-ciemss-operation';
import { getModelConfigName } from '@/services/model-configurations';
import { EnsembleErrorData } from '@/components/workflow/ops/calibrate-ensemble-ciemss/calibrate-ensemble-util';
import { PlotValue } from '@/components/workflow/ops/compare-datasets/compare-datasets-operation';
import { DATASET_VAR_NAME_PREFIX } from '@/services/dataset';
import { calculatePercentage, calculatePercentages, sumArrays } from '@/utils/math';
import { pythonInstance } from '@/python/PyodideController';
import { useChartAnnotations } from './useChartAnnotations';

export interface ChartData {
	result: DataArray;
	resultSummary: DataArray;
	pyciemssMap: Record<string, string>;
	translationMap: Record<string, string>;
	resultGroupByTimepoint?: GroupedDataArray;
	numComparableDatasets?: number;
}

type EnsembleVariableMappings = CalibrateEnsembleMappingRow[] | SimulateEnsembleMappingRow[];
type VariableMappings = CalibrateMap[] | EnsembleVariableMappings;

const BASE_GREY = '#AAB3C6';
const PRIMARY_COLOR = CATEGORICAL_SCHEME[0];

// ======================================= Helper functions ==========================================================

// Get the model variable name for the corresponding model configuration and the ensemble variable name from the mapping
const getModelConfigVariable = (
	mapping: EnsembleVariableMappings,
	ensembleVariableName: string,
	modelConfigId: string
) => mapping.find((d) => d.newName === ensembleVariableName)?.modelConfigurationMappings[modelConfigId] ?? '';

const getModelConfigIdPrefix = (configId: string) => (configId ? `${configId}/` : '');

/**
 * Converts a model variable name to a dataset variable name based on the provided mapping.
 *
 * @param {VariableMappings} mapping - The mapping object that contains the variable mappings.
 * @param {string} modelVariable - The name of the model variable to be converted.
 * @returns {string} - The corresponding dataset variable name, or an empty string if the mapping is empty or not found.
 */
const modelVarToDatasetVar = (mapping: VariableMappings, modelVariable: string) => {
	if (_.isEmpty(mapping)) return '';
	if (isCalibrateMap(mapping[0])) {
		return (mapping as CalibrateMap[]).find((d) => d.modelVariable === modelVariable)?.datasetVariable ?? '';
	}
	if (isCalibrateEnsembleMappingRow(mapping[0])) {
		return (mapping as CalibrateEnsembleMappingRow[]).find((d) => d.newName === modelVariable)?.datasetMapping ?? '';
	}
	return '';
};

// For ensemble charts, add model names to translation map to give more context for the usage in llm annotation generation
// For example, if a variable name is 'model_0/infected', we want to give llm {model_0/infected: 'Model Antwerp Infected after calibration'}
// instead of just { model_0/infected: 'Infected after calibration' } so that it is clear to LLM which model the variable belongs to.
const addModelConfigNameToTranslationMap = (
	translationMap: Record<string, string>,
	pyciemssMap: Record<string, string>,
	modelConfigs: ModelConfiguration[]
) => {
	const newMap = {};
	const modelPrefixToModelconfigIdMap = extractModelConfigIds(pyciemssMap);
	// Build model index (model_#) to model config name mapping with pyciemssMap
	Object.entries(translationMap).forEach(([key, value]) => {
		const modelPrefix = key.split('/')[0];
		const modelConfigName = getModelConfigName(modelConfigs, modelPrefixToModelconfigIdMap[modelPrefix] ?? '');
		const newValue = modelConfigName ? `${modelConfigName} ${value}` : value;
		newMap[key] = newValue;
	});
	return newMap;
};

/**
 * Calculate the extent of the y-axis based on the provided result summary and variables.
 * @param resultSummary The result summary data array.
 * @param variables The list of variables to calculate the extent for.
 * @returns The extent of the y-axis as a tuple of [min, max].
 */
const calculateYExtent = (resultSummary: DataArray, variables: string[], includeBeforeValues = false) => {
	const extent: [number, number] = [Infinity, -Infinity];
	resultSummary.forEach((row) => {
		variables.forEach((variable) => {
			const minVarName = `${variable}_min`;
			const maxVarName = `${variable}_max`;
			extent[0] = Math.min(extent[0], row[minVarName]);
			extent[1] = Math.max(extent[1], row[maxVarName]);
			if (includeBeforeValues) {
				extent[0] = Math.min(extent[0], row[`${minVarName}:pre`]);
				extent[1] = Math.max(extent[1], row[`${maxVarName}:pre`]);
			}
		});
	});
	return extent;
};

// Consider provided reference object is ready if it is set to null explicitly or if it's value is available
const isRefReady = (reference: Ref | null) => reference === null || Boolean(reference.value);

/**
 * Normalize the stratified model chart data by the total strata population.
 * Only supported for stratified models.
 * @param setting ChartSettingComparison
 * @param data ChartData
 * @param model Model
 * @returns ChartData where the values for the state variables are normalized by the total strata population
 */
const normalizeStratifiedModelChartData = (setting: ChartSettingComparison, data: ChartData, model: Model) => {
	if (!model) return data;
	if (!setting.normalize) return data;

	const { selectedVariablesGroupByStrata, allVariablesGroupByStrata } = groupVariablesByStrata(
		setting.selectedVariables,
		data.pyciemssMap,
		model
	);

	const includeBeforeData = setting.showBeforeAfter && setting.smallMultiples;

	// If show quantiles is on,  normalize group by timepoint data
	if (setting.showQuantiles) {
		const resultGroupByTimepoint: GroupedDataArray = [];
		(data.resultGroupByTimepoint ?? []).forEach((row) => {
			const newEntry = { timepoint_id: row.timepoint_id, sample_id: row.sample_id };
			Object.entries(selectedVariablesGroupByStrata).forEach(([group, variables]) => {
				// Sum all values for the variables in the same strata group
				const denominatorValues = sumArrays(...allVariablesGroupByStrata[group].map((v) => row[data.pyciemssMap[v]]));
				variables
					.map((v) => data.pyciemssMap[v])
					.forEach((variable) => {
						newEntry[variable] = !group ? row[variable] : calculatePercentages(row[variable], denominatorValues);
						if (includeBeforeData) {
							newEntry[`${variable}:pre`] = !group
								? row[variable]
								: calculatePercentages(row[variable], denominatorValues);
						}
					});
			});
			resultGroupByTimepoint.push(newEntry);
		});
		return { ...data, resultGroupByTimepoint };
	}
	// Else, normalize result and resultSummary data

	// Normalize stat data
	const resultSummary: DataArray = [];
	data.resultSummary.forEach((row) => {
		const newEntry = { timepoint_id: row.timepoint_id };
		Object.entries(selectedVariablesGroupByStrata).forEach(([group, variables]) => {
			const denominator = allVariablesGroupByStrata[group].reduce(
				(acc, v) => acc + row[`${data.pyciemssMap[v]}_mean`],
				0
			);
			variables
				.map((v) => data.pyciemssMap[v])
				.forEach((variable) => {
					const key = `${variable}_mean`;
					newEntry[key] = !group ? row[variable] : calculatePercentage(row[key], denominator);
					if (includeBeforeData) {
						newEntry[`${key}:pre`] = !group ? row[variable] : calculatePercentage(row[`${key}:pre`], denominator);
					}
				});
		});
		resultSummary.push(newEntry);
	});

	// Normalize sample data
	const result: DataArray = [];
	data.result.forEach((row) => {
		const newEntry = { timepoint_id: row.timepoint_id, sample_id: row.sample_id };
		Object.entries(selectedVariablesGroupByStrata).forEach(([group, variables]) => {
			const denominator = allVariablesGroupByStrata[group].reduce((acc, v) => acc + row[data.pyciemssMap[v]], 0);
			variables
				.map((v) => data.pyciemssMap[v])
				.forEach((variable) => {
					newEntry[variable] = !group ? row[variable] : calculatePercentage(row[variable], denominator);
					if (includeBeforeData) {
						newEntry[`${variable}:pre`] = !group
							? row[variable]
							: calculatePercentage(row[`${variable}:pre`], denominator);
					}
				});
		});
		result.push(newEntry);
	});
	return { ...data, result, resultSummary };
};

/** A helper function to create a comparison chart */
function createComparisonChart(
	setting: ChartSettingComparison,
	result: DataArray,
	resultSummary: DataArray,
	statLayerVariables: string[],
	sampleLayerVariables: string[],
	options: ForecastChartOptions,
	annotations: ChartAnnotation[],
	resultGroupByTimepoint?: GroupedDataArray
) {
	const chart = !setting.showQuantiles
		? applyForecastChartAnnotations(
				createForecastChart(
					{
						data: result,
						variables: sampleLayerVariables,
						timeField: 'timepoint_id',
						groupField: 'sample_id'
					},
					{
						data: resultSummary,
						variables: statLayerVariables,
						timeField: 'timepoint_id'
					},
					null,
					options
				),
				annotations
			)
		: createQuantilesForecastChart(
				resultGroupByTimepoint ?? [],
				sampleLayerVariables,
				setting.quantiles ?? [],
				options
			);
	return chart as VisualizationSpec;
}

const buildYAxisTitle = (variables: string[], getUnitFn: (id: string) => string) =>
	_.uniq(variables.map(getUnitFn).filter((v) => !!v)).join(', ') || '';

// ====================================================================================================================

/**
 * Composable to manage the creation and configuration of various types of charts used in operator nodes and drilldown.
 *
 * @param nodeId - The ID of the node.
 * @param model - The model reference.
 * @param modelConfig - The model configurations reference. For ensemble operators, this can be an array of model configurations.
 * @param chartData - The chart data reference.
 * @param chartSize - The chart size reference.
 * @param interventions - Optional reference to interventions.
 *
 * @returns An object containing methods to create and manage different types of charts.
 */
export function useCharts(
	nodeId: string,
	model: Ref<Model | null> | null,
	modelConfig: Ref<ModelConfiguration | ModelConfiguration[] | null> | null,
	chartData: Ref<ChartData | null>,
	chartSize: Ref<{ width: number; height: number }>,
	interventions: Ref<Intervention[]> | null,
	mapping: Ref<VariableMappings> | null
) {
	// Check if references of the core dependencies are ready to build the chart to prevent multiple re-renders especially
	// on initial page load where data are fetched asynchronously and assigned to the references in different times.
	const isChartReadyToBuild = computed(() => [model, modelConfig, chartData].every(isRefReady));

	// Setup annotations
	const { getChartAnnotationsByChartId, generateAndSaveForecastChartAnnotation } = useChartAnnotations(nodeId);

	const getUnit = (paramId: string) => {
		if (!model?.value) return '';
		return getUnitsFromModelParts(model.value)[paramId] || '';
	};

	const getUnitForNormalizedStratifiedModelData = (id: string) => {
		if (!model?.value) return '';
		const strata = getStateVariableStrataEntries(id, model.value);
		if (!strata.length) return getUnit(id);
		// sort strata labels in a same order it appears in the id
		const label = strata
			.map((v) => v.split(':')[1])
			.sort((a, b) => id.indexOf(a) - id.indexOf(b))
			.join('_');
		return `% ${label}`;
	};

	/**
	 * Create a base forecast chart options that's common for different types of forecast charts.
	 * @param setting ChartSetting
	 * @returns ForecastChartOptions
	 */
	const createBaseForecastChartOptions = (setting: ChartSetting) => {
		const variables = setting.selectedVariables;
		const dateOptions = getVegaDateOptions(model?.value ?? null, <ModelConfiguration>modelConfig?.value || null);
		const options: ForecastChartOptions = {
			title: '',
			legend: true,
			width: chartSize.value.width,
			height: chartSize.value.height,
			translationMap: chartData.value?.translationMap || {},
			xAxisTitle: getUnit('_time') || 'Time',
			yAxisTitle: buildYAxisTitle(variables, getUnit),
			dateOptions,
			colorscheme: [BASE_GREY, setting.primaryColor ?? PRIMARY_COLOR],
			scale: setting.scale
		};
		return options;
	};

	const createEnsembleVariableChartOptions = (
		setting: ChartSettingEnsembleVariable,
		modelConfigId = '',
		multiVariable = false,
		showBaseLines = false
	) => {
		setting.hideInNode = true;
		const ensembleVarName = setting.selectedVariables[0];
		const options: ForecastChartOptions = {
			title: getModelConfigName(<ModelConfiguration[]>modelConfig?.value ?? [], modelConfigId) || ensembleVarName,
			legend: true,
			width: chartSize.value.width,
			height: chartSize.value.height,
			translationMap: chartData.value?.translationMap || {},
			xAxisTitle: '',
			yAxisTitle: '',
			autosize: AUTOSIZE.FIT,
			colorscheme: multiVariable ? CATEGORICAL_SCHEME : [BASE_GREY, setting.primaryColor ?? PRIMARY_COLOR],
			scale: setting.scale
		};

		const variables: string[] = [];
		if (multiVariable) {
			// If multiVariable flag is true, include the ensemble variable and all model variables all together in one chart. modelConfigId is not used in this case.
			variables.push(modelVarToDatasetVar(mapping?.value ?? [], ensembleVarName) as string);
			(<ModelConfiguration[]>modelConfig?.value ?? []).forEach((config) => {
				const modelVar = getModelConfigVariable(
					<EnsembleVariableMappings>mapping?.value ?? [],
					ensembleVarName,
					config.id ?? ''
				);
				variables.push(getModelConfigIdPrefix(config.id ?? '') + modelVar);
			});
		} else {
			// If model modelConfigId is provided, include a model variable, otherwise include an ensemble variable
			variables.push(
				modelConfigId
					? // model variable
						getModelConfigIdPrefix(modelConfigId) +
							getModelConfigVariable(<EnsembleVariableMappings>mapping?.value ?? [], ensembleVarName, modelConfigId)
					: // ensemble variable
						(modelVarToDatasetVar(mapping?.value ?? [], ensembleVarName) as string)
			);
		}

		const sampleLayerVariables: string[] = [];
		const statLayerVariables: string[] = [];
		variables.forEach((varNameKeys) => {
			const varName = chartData.value?.pyciemssMap[varNameKeys];
			// Add base lines data
			if (showBaseLines) {
				sampleLayerVariables.push(`${varName}:pre`);
				statLayerVariables.push(`${varName}_mean:pre`);
			}
			// Add primary lines data
			sampleLayerVariables.push(`${varName}`);
			statLayerVariables.push(`${varName}_mean`);
		});
		return { statLayerVariables, sampleLayerVariables, options };
	};

	const createComparisonChartOptions = (
		setting: ChartSettingComparison,
		variableIndex = -1,
		includeAllVars = false
	) => {
		const variables = setting.selectedVariables;
		const options = createBaseForecastChartOptions(setting);

		const sampleLayerVariables: string[] = [];
		const statLayerVariables: string[] = [];

		const colorScheme: string[] = [];

		const varName = variables[variableIndex];
		// If varName is provided (single chart from small multiples), include that specific variable in the chart
		if (varName) {
			if (setting.showBeforeAfter) {
				sampleLayerVariables.push(`${chartData.value?.pyciemssMap[varName]}:pre`);
				statLayerVariables.push(`${chartData.value?.pyciemssMap[varName]}_mean:pre`);
				colorScheme.push(BASE_GREY);
			}
			sampleLayerVariables.push(`${chartData.value?.pyciemssMap[varName]}`);
			statLayerVariables.push(`${chartData.value?.pyciemssMap[varName]}_mean`);
			colorScheme.push(CATEGORICAL_SCHEME[variableIndex % CATEGORICAL_SCHEME.length]);
		}
		// Otherwise include all selected variables
		else {
			variables.forEach((v) => {
				sampleLayerVariables.push(`${chartData.value?.pyciemssMap[v]}`);
				statLayerVariables.push(`${chartData.value?.pyciemssMap[v]}_mean`);
				if (includeAllVars) {
					// For the options that's provided to the annotation generation, we need to include pre values as well
					sampleLayerVariables.push(`${chartData.value?.pyciemssMap[v]}:pre`);
					statLayerVariables.push(`${chartData.value?.pyciemssMap[v]}_mean:pre`);
				}
			});
			colorScheme.push(...CATEGORICAL_SCHEME);
		}
		options.colorscheme = colorScheme;
		return { statLayerVariables, sampleLayerVariables, options };
	};

	/**
	 *
	 * Create options for forecast charts based on chart settings and model configuration.
	 *
	 * **Note:** default `generateAnnotation` function in `useCharts` uses this function to generate chart variables and translations that needs to generate AI annotation.
	 * If you need a custom way to create a chart option instead of using this, you need to provide a
	 * custom `generateAnnotation` function to `tera-chart-settings-panel` component for the annotation to work.
	 * @param setting ChartSetting
	 * @returns ForecastChartOptions
	 */
	const createForecastChartOptions = (setting: ChartSetting) => {
		if (isChartSettingEnsembleVariable(setting)) return createEnsembleVariableChartOptions(setting, '', true, true);
		if (isChartSettingComparisonVariable(setting)) return createComparisonChartOptions(setting, -1, true);

		const options = createBaseForecastChartOptions(setting);
		const variables = setting.selectedVariables;

		let sampleLayerVariables: string[] = [];
		let statLayerVariables: string[] = [];
		// Variable names for compare dataset charts
		if (!_.isNil(chartData.value?.numComparableDatasets)) {
			const varName = variables[0];
			for (let i = 0; i < chartData.value.numComparableDatasets; i++) {
				const rawVarName = chartData.value?.pyciemssMap[varName];
				const aggSuffix = rawVarName.startsWith(DATASET_VAR_NAME_PREFIX) ? '' : '_mean';
				sampleLayerVariables.push(`${chartData.value?.pyciemssMap[varName]}:${i}`);
				statLayerVariables.push(`${chartData.value?.pyciemssMap[varName]}${aggSuffix}:${i}`);
			}
		}
		// Default variable names for simulate or calibrate operation
		else {
			sampleLayerVariables = [
				`${chartData.value?.pyciemssMap[variables[0]]}:pre`,
				`${chartData.value?.pyciemssMap[variables[0]]}`
			];
			statLayerVariables = [
				`${chartData.value?.pyciemssMap[variables[0]]}_mean:pre`,
				`${chartData.value?.pyciemssMap[variables[0]]}_mean`
			];
		}
		return { statLayerVariables, sampleLayerVariables, options };
	};

	// Generate annotations for a chart
	const generateAnnotation = async (setting: ChartSetting, query: string) => {
		if (!chartData.value) return null;
		const { statLayerVariables, options } = createForecastChartOptions(setting);
		if (setting.type === ChartSettingType.VARIABLE_ENSEMBLE) {
			options.translationMap = addModelConfigNameToTranslationMap(
				options.translationMap ?? {},
				chartData.value.pyciemssMap,
				<ModelConfiguration[]>modelConfig?.value ?? []
			);
		}
		return generateAndSaveForecastChartAnnotation(setting, query, 'timepoint_id', statLayerVariables, options);
	};

	const groupedInterventionOutputs = computed(() =>
		_.groupBy(flattenInterventionData(interventions?.value ?? []), 'appliedTo')
	);

	// Create intervention charts based on chart settings
	const useInterventionCharts = (chartSettings: ComputedRef<ChartSetting[]>, showSamples = false) => {
		const interventionCharts = computed(() => {
			const charts: Record<string, VisualizationSpec> = {};
			if (!isChartReadyToBuild.value) return charts;
			const { resultSummary, result } = chartData.value as ChartData;
			// intervention chart spec
			chartSettings.value.forEach((setting) => {
				const variable = setting.selectedVariables[0];
				const { sampleLayerVariables, statLayerVariables, options } = createForecastChartOptions(setting);
				const forecastChart = createForecastChart(
					{
						data: showSamples ? result : [],
						variables: sampleLayerVariables,
						timeField: 'timepoint_id',
						groupField: 'sample_id'
					},
					{
						data: resultSummary,
						variables: statLayerVariables,
						timeField: 'timepoint_id'
					},
					null,
					options
				);
				if (groupedInterventionOutputs.value[variable]) {
					// add intervention annotations (rules and text)
					forecastChart.layer.push(...createInterventionChartMarkers(groupedInterventionOutputs.value[variable]));
				}

				charts[setting.id] = forecastChart;
			});
			return charts;
		});
		return interventionCharts;
	};

	// Create compare dataset charts based on chart settings
	const useCompareDatasetCharts = (
		chartSettings: ComputedRef<ChartSetting[]>,
		selectedPlotType: ComputedRef<PlotValue>,
		baselineIndex: ComputedRef<number>
	) => {
		const compareDatasetCharts = computed(() => {
			const charts: Record<string, VisualizationSpec> = {};
			if (!isChartReadyToBuild.value) return charts;

			// Make baseline black
			const colorScheme = cloneDeep(CATEGORICAL_SCHEME);
			colorScheme.splice(baselineIndex.value, 0, 'black');

			chartSettings.value.forEach((settings) => {
				const varName = settings.selectedVariables[0];
				const { statLayerVariables, sampleLayerVariables, options } = createForecastChartOptions(settings);
				options.title = varName;
				options.yAxisTitle = capitalize(selectedPlotType.value);
				options.colorscheme = colorScheme;

				const annotations = getChartAnnotationsByChartId(settings.id);
				const chart = !settings.showQuantiles
					? applyForecastChartAnnotations(
							createForecastChart(
								{
									data: chartData.value?.result ?? [],
									variables: sampleLayerVariables,
									timeField: 'timepoint_id',
									groupField: 'sample_id'
								},
								{
									data: chartData.value?.resultSummary ?? [],
									variables: statLayerVariables,
									timeField: 'timepoint_id'
								},
								null,
								options
							),
							annotations
						)
					: createQuantilesForecastChart(
							chartData.value?.resultGroupByTimepoint ?? [],
							sampleLayerVariables,
							settings.quantiles ?? [],
							options
						);
				charts[settings.id] = chart;
			});
			return charts;
		});
		return compareDatasetCharts;
	};

	// Create variable charts based on chart settings
	const useVariableCharts = (
		chartSettings: ComputedRef<ChartSetting[]>,
		groundTruthData: ComputedRef<DataArray> | null
	) => {
		const variableCharts = computed(() => {
			const charts: Record<string, VisualizationSpec> = {};
			if (!isChartReadyToBuild.value || !isRefReady(groundTruthData)) return charts;
			const { result, resultSummary } = chartData.value as ChartData;
			// eslint-disable-next-line
			chartSettings.value.forEach((settings) => {
				const variable = settings.selectedVariables[0];
				const annotations = getChartAnnotationsByChartId(settings.id);
				const datasetVar = modelVarToDatasetVar(mapping?.value || [], variable);
				const { sampleLayerVariables, statLayerVariables, options } = createForecastChartOptions(settings);

				const chart = !settings.showQuantiles
					? applyForecastChartAnnotations(
							createForecastChart(
								{
									data: result,
									variables: sampleLayerVariables,
									timeField: 'timepoint_id',
									groupField: 'sample_id'
								},
								{
									data: resultSummary,
									variables: statLayerVariables,
									timeField: 'timepoint_id'
								},
								groundTruthData && {
									data: groundTruthData.value,
									variables: datasetVar ? [datasetVar] : [],
									timeField: modelVarToDatasetVar(mapping?.value || [], 'timepoint_id')
								},
								options
							),
							annotations
						)
					: createQuantilesForecastChart(
							chartData.value?.resultGroupByTimepoint ?? [],
							sampleLayerVariables,
							settings.quantiles ?? [],
							options
						);
				_.keys(groupedInterventionOutputs.value).forEach((key) => {
					if (settings.selectedVariables.includes(key)) {
						chart.layer.push(...createInterventionChartMarkers(groupedInterventionOutputs.value[key]));
					}
				});
				charts[settings.id] = chart;
			});
			return charts;
		});
		return variableCharts;
	};

	// Create comparison charts based on chart settings
	const useComparisonCharts = (chartSettings: ComputedRef<ChartSettingComparison[]>, isNodeChart = false) => {
		const comparisonCharts = computed(() => {
			const charts: Record<string, VisualizationSpec[]> = {};
			if (!isChartReadyToBuild.value) return charts;

			chartSettings.value.forEach((setting) => {
				const { result, resultSummary, resultGroupByTimepoint } = normalizeStratifiedModelChartData(
					setting,
					chartData.value as ChartData,
					model?.value as Model
				);

				const selectedVars = setting.selectedVariables;
				const annotations = getChartAnnotationsByChartId(setting.id);
				if (setting.smallMultiples && setting.selectedVariables.length > 1 && !isNodeChart) {
					const sharedYExtent = setting.shareYAxis
						? calculateYExtent(
								resultSummary,
								selectedVars.map((v) => chartData.value?.pyciemssMap[v] ?? ''),
								Boolean(setting.showBeforeAfter)
							)
						: undefined;

					// create multiples
					let width = chartSize.value.width;
					if (selectedVars.length > 1) width = chartSize.value.width / 2;
					if (selectedVars.length > 4) width = chartSize.value.width / 3;
					const height = selectedVars.length <= 1 ? chartSize.value.height : chartSize.value.height / 2;
					charts[setting.id] = selectedVars.map((selectedVar, index) => {
						const { options, sampleLayerVariables, statLayerVariables } = createComparisonChartOptions(setting, index);
						options.width = width;
						options.height = height;
						options.yExtent = sharedYExtent;
						if (setting.normalize) {
							options.yAxisTitle = buildYAxisTitle([selectedVar], getUnitForNormalizedStratifiedModelData);
						}
						return createComparisonChart(
							setting,
							result,
							resultSummary,
							statLayerVariables,
							sampleLayerVariables,
							options,
							annotations,
							resultGroupByTimepoint
						);
					});
				} else {
					const { options, sampleLayerVariables, statLayerVariables } = createComparisonChartOptions(setting);
					if (setting.normalize) {
						options.yAxisTitle = buildYAxisTitle(setting.selectedVariables, getUnitForNormalizedStratifiedModelData);
					}
					const chart = createComparisonChart(
						setting,
						result,
						resultSummary,
						statLayerVariables,
						sampleLayerVariables,
						options,
						annotations,
						resultGroupByTimepoint
					);
					charts[setting.id] = [chart];
				}
			});
			return charts;
		});
		return comparisonCharts;
	};

	const useEnsembleVariableCharts = (
		chartSettings: ComputedRef<ChartSettingEnsembleVariable[]>,
		groundTruthData: ComputedRef<DataArray> | null
	) => {
		const ensembleVariableCharts = computed(() => {
			const charts: Record<string, VisualizationSpec[]> = {};
			if (!isChartReadyToBuild.value || !isRefReady(groundTruthData)) return chartData;
			const { result, resultSummary } = chartData.value as ChartData;
			const modelConfigIds = extractModelConfigIdsInOrder(chartData.value?.pyciemssMap ?? {});
			chartSettings.value.forEach((setting) => {
				const annotations = getChartAnnotationsByChartId(setting.id);
				const datasetVar = modelVarToDatasetVar(mapping?.value || [], setting.selectedVariables[0]);
				if (setting.showIndividualModels) {
					const smallMultiplesCharts = ['', ...modelConfigIds].map((modelConfigId, index) => {
						const { sampleLayerVariables, statLayerVariables, options } = createEnsembleVariableChartOptions(
							setting,
							modelConfigId,
							false,
							true
						);
						options.width = chartSize.value.width / (modelConfigIds.length + 1);
						options.legendProperties = { direction: 'vertical', columns: 1, labelLimit: options.width };
						options.colorscheme = [BASE_GREY, CATEGORICAL_SCHEME[index % CATEGORICAL_SCHEME.length]];
						const smallChart = !setting.showQuantiles
							? applyForecastChartAnnotations(
									createForecastChart(
										{
											data: result,
											variables: sampleLayerVariables,
											timeField: 'timepoint_id',
											groupField: 'sample_id'
										},
										{
											data: resultSummary,
											variables: statLayerVariables,
											timeField: 'timepoint_id'
										},
										groundTruthData && {
											data: groundTruthData.value,
											variables: datasetVar ? [datasetVar] : [],
											timeField: modelVarToDatasetVar(mapping?.value || [], 'timepoint_id')
										},
										options
									),
									annotations
								)
							: createQuantilesForecastChart(
									chartData.value?.resultGroupByTimepoint ?? [],
									sampleLayerVariables,
									setting.quantiles ?? [],
									options
								);
						return smallChart;
					});
					charts[setting.id] = smallMultiplesCharts;
				} else {
					// Build a single ensemble chart
					const { sampleLayerVariables, statLayerVariables, options } = createEnsembleVariableChartOptions(
						setting,
						'',
						false,
						true
					);
					const chart = !setting.showQuantiles
						? applyForecastChartAnnotations(
								createForecastChart(
									{
										data: result,
										variables: sampleLayerVariables,
										timeField: 'timepoint_id',
										groupField: 'sample_id'
									},
									{
										data: resultSummary,
										variables: statLayerVariables,
										timeField: 'timepoint_id'
									},
									groundTruthData && {
										data: groundTruthData.value,
										variables: datasetVar ? [datasetVar] : [],
										timeField: modelVarToDatasetVar(mapping?.value || [], 'timepoint_id')
									},
									options
								),
								annotations
							)
						: createQuantilesForecastChart(
								chartData.value?.resultGroupByTimepoint ?? [],
								sampleLayerVariables,
								setting.quantiles ?? [],
								options
							);
					charts[setting.id] = [chart];
				}
			});
			return charts;
		});
		return ensembleVariableCharts;
	};

	// Create error charts based on chart settings
	const useErrorChart = (chartSettings: ComputedRef<ChartSetting[]>, errorData: ComputedRef<DataArray>) => {
		const errorChartVariables = computed(() => {
			if (!chartSettings.value.length) return [];
			const variables = chartSettings.value
				.map((s) => s.selectedVariables[0])
				.map((variable) => {
					const field = modelVarToDatasetVar(mapping?.value ?? [], variable) as string;
					return { field, label: `${variable}, ${field}` };
				})
				.filter((v) => !!v.field);
			return variables;
		});

		const errorChart = computed(() => {
			if (errorData.value.length === 0) return {};
			const spec = createErrorChart(errorData.value, {
				title: '',
				width: chartSize.value.width,
				variables: errorChartVariables.value,

				xAxisTitle: 'Mean absolute (MAE)'
			});
			return spec;
		});

		const onExpandErrorChart = () => {
			if (errorData.value.length === 0) return {};
			// Customize the chart size by modifying the spec before expanding the chart
			const spec = createErrorChart(errorData.value, {
				title: '',
				width: window.innerWidth / 1.5,
				height: 230,
				boxPlotHeight: 50,
				areaChartHeight: 150,
				variables: errorChartVariables.value,
				xAxisTitle: 'Mean absolute (MAE)'
			});
			return spec as any;
		};

		return {
			errorChart,
			onExpandErrorChart
		};
	};

	// Create ensemble calibrate error charts based on chart settings
	const useEnsembleErrorCharts = (
		chartSettings: ComputedRef<ChartSetting[]>,
		errorData: ComputedRef<EnsembleErrorData>
	) => {
		const getErrorChartVariables = (configId: string) => {
			const variables = chartSettings.value
				.map((s) => s.selectedVariables[0])
				.map((variable) => {
					const modelVarName = configId
						? getModelConfigVariable(<EnsembleVariableMappings>mapping?.value ?? [], variable, configId)
						: variable;
					const field = modelVarToDatasetVar(mapping?.value ?? [], variable) as string;
					return { field, label: `${modelVarName}, ${field}` };
				})
				.filter((v) => !!v.field);
			return variables;
		};

		const errorCharts = computed(() => {
			if (!isChartReadyToBuild.value) return [];
			const data = [errorData.value.ensemble]; // First item is always ensemble error data, and the rest are model error data
			const modelConfigIds = extractModelConfigIdsInOrder(chartData.value?.pyciemssMap ?? {});
			modelConfigIds.forEach((configId) => {
				if (errorData.value[configId]) data.push(errorData.value[configId]);
			});
			const errorChartSpecs = data.map((ed, index) => {
				const spec = createErrorChart(ed, {
					title: '',
					width: chartSize.value.width / data.length - 30, // Note: error chart adds extra 30px padding on top of the provided width so we subtract 30px to make the chart fit the container
					variables: getErrorChartVariables(modelConfigIds[index - 1] ?? ''),
					xAxisTitle: 'Mean absolute (MAE)',
					color: CATEGORICAL_SCHEME[index % CATEGORICAL_SCHEME.length]
				});
				return spec;
			});
			return errorChartSpecs;
		});

		const onExpandErrorChart = (chartSpecIndex: number) => {
			if (!isChartReadyToBuild.value) return {};
			const modelConfigIds = extractModelConfigIdsInOrder(chartData.value?.pyciemssMap ?? {});
			const errorDataKeys = ['ensemble', ...modelConfigIds];
			// Customize the chart size by modifying the spec before expanding the chart
			const spec = createErrorChart(errorData.value[errorDataKeys[chartSpecIndex]], {
				title: '',
				width: window.innerWidth / 1.5,
				height: 230,
				boxPlotHeight: 50,
				areaChartHeight: 150,
				variables: getErrorChartVariables(modelConfigIds[chartSpecIndex - 1] ?? ''),
				xAxisTitle: 'Mean absolute (MAE)',
				color: CATEGORICAL_SCHEME[chartSpecIndex % CATEGORICAL_SCHEME.length]
			});
			return spec as VisualizationSpec;
		};

		return {
			errorCharts,
			onExpandErrorChart
		};
	};

	// Create parameter distribution charts based on chart settings
	const useParameterDistributionCharts = (chartSettings: ComputedRef<ChartSetting[]>) => {
		const parameterDistributionCharts = computed(() => {
			const charts = {};
			if (!isChartReadyToBuild.value) return charts;
			const { result, pyciemssMap } = chartData.value as ChartData;
			// Note that we want to show the parameter distribution at the first timepoint only
			const data = result.filter((d) => d.timepoint_id === 0);
			const labelBefore = 'Before calibration';
			const labelAfter = 'After calibration';
			chartSettings.value.forEach((setting) => {
				const param = setting.selectedVariables[0];
				const fieldName = pyciemssMap[param];
				const beforeFieldName = `${fieldName}:pre`;
				const histogram = createHistogramChart(data, {
					title: `${param}`,
					width: chartSize.value.width,
					height: chartSize.value.height,
					xAxisTitle: `${param}`,
					yAxisTitle: 'Count',
					maxBins: 10,
					variables: [
						{ field: beforeFieldName, label: labelBefore, width: 54, color: BASE_GREY },
						{ field: fieldName, label: labelAfter, width: 24, color: setting.primaryColor ?? PRIMARY_COLOR }
					]
				});
				const toDisplayNumber = (num?: number) => (num ? displayNumber(num.toString()) : '');
				const stat = {
					header: [labelBefore, labelAfter],
					mean: [mean(data, (d) => d[beforeFieldName]), mean(data, (d) => d[fieldName])].map(toDisplayNumber),
					variance: [variance(data, (d) => d[beforeFieldName]), variance(data, (d) => d[fieldName])].map(
						toDisplayNumber
					)
				};
				charts[setting.id] = { histogram, ...stat };
			});
			return charts;
		});
		return parameterDistributionCharts;
	};

	const useWeightsDistributionCharts = () => {
		const WEIGHT_PARAM_NAME = 'weight_param';
		const weightsCharts = computed(() => {
			const charts: VisualizationSpec[] = [];
			if (!isChartReadyToBuild.value) return charts;

			// Model configs are used to get the model config metadata. This order of model configs in arrays are not guaranteed to be the same as the order of model configs in the pyciemss results
			const modelConfigs = <ModelConfiguration[]>modelConfig?.value ?? [];
			// extractModelConfigIdsInOrder ensures that the order of model config IDs are matched with the order of corresponding model index in the pyciemss results
			const modelConfigIds = extractModelConfigIdsInOrder(chartData.value?.pyciemssMap ?? {});

			const data = chartData.value?.result.filter((d) => d.timepoint_id === 0) ?? [];
			const labelBefore = 'Before calibration';
			const labelAfter = 'After calibration';

			const colors = CATEGORICAL_SCHEME.slice(1); // exclude the first color which is for ensemble variable

			modelConfigIds.forEach((configId, index) => {
				const modelConfigName = getModelConfigName(modelConfigs, configId);
				const chartWidth = chartSize.value.width / modelConfigs.length;

				const fieldName = chartData.value?.pyciemssMap[`${getModelConfigIdPrefix(configId)}${WEIGHT_PARAM_NAME}`] ?? '';
				const beforeFieldName = `${fieldName}:pre`;

				const maxBins = 10;
				const barWidth = Math.min((chartWidth - 40) / maxBins, 54);
				const spec = createHistogramChart(data, {
					title: modelConfigName,
					width: chartWidth,
					height: chartSize.value.height,
					xAxisTitle: `Weights`,
					yAxisTitle: 'Count',
					maxBins,
					extent: [0, 1], // Weights are between 0 and 1
					variables: [
						{ field: beforeFieldName, label: labelBefore, width: barWidth, color: BASE_GREY },
						{ field: fieldName, label: labelAfter, width: barWidth / 2, color: colors[index % colors.length] }
					],
					legendProperties: { direction: 'vertical', columns: 1, labelLimit: chartWidth }
				});
				charts.push(spec);
			});
			return charts;
		});
		return weightsCharts;
	};

	function createSensitivityBins(
		records: Record<string, any>[],
		selectedVariable: string,
		numBins: number = 7,
		unit?: string
	): Map<string, number[]> {
		if (numBins < 1) {
			throw new Error('Number of bins must be at least 1.');
		}

		// Extract the selected variable values and ensure they are numeric
		const samples = records.map((record) => {
			const value = record[selectedVariable];
			const id = record.sample_id;
			if (typeof value !== 'number') {
				throw new Error(`The selected variable "${selectedVariable}" must map to numeric values.`);
			}
			return { id, value };
		});

		// Sort the samples by value in ascending order
		const sortedSamples = [...samples].sort((a, b) => a.value - b.value);

		// Extract values for threshold calculation
		const sortedValues = sortedSamples.map((sample) => sample.value);
		const minValue = sortedValues[0];
		const maxValue = sortedValues[sortedValues.length - 1];

		// Calculate the quantile thresholds
		const thresholds: number[] = [];
		for (let i = 1; i < numBins; i++) {
			const quantileIndex = (i / numBins) * (sortedValues.length - 1);
			const lowerIndex = Math.floor(quantileIndex);
			const upperIndex = Math.ceil(quantileIndex);

			// Interpolate if necessary
			const quantileValue =
				lowerIndex === upperIndex
					? sortedValues[lowerIndex]
					: sortedValues[lowerIndex] +
						(sortedValues[upperIndex] - sortedValues[lowerIndex]) * (quantileIndex - lowerIndex);
			thresholds.push(quantileValue);
		}

		// Generate labels for each quantile
		const labels: string[] = [];
		let previousThreshold = minValue;
		thresholds.forEach((threshold) => {
			labels.push(
				`[${expressionFunctions.chartNumberFormatter(previousThreshold)}, ${expressionFunctions.chartNumberFormatter(threshold)}] ${unit ? `${unit}` : ''}`
			);
			previousThreshold = threshold;
		});

		labels.push(
			`[${expressionFunctions.chartNumberFormatter(previousThreshold)}, ${expressionFunctions.chartNumberFormatter(maxValue)}] ${unit ? `${unit}` : ''}`
		);

		// Assign bins to records and create the result map
		const result = new Map<string, number[]>();
		labels.forEach((label) => result.set(label, []));

		samples.forEach((sample) => {
			let bin = 0; // Default bin if the value is below the first threshold
			for (let i = 0; i < thresholds.length; i++) {
				if (sample.value <= thresholds[i]) {
					bin = i + 1;
					break;
				}
			}
			if (bin === 0) {
				bin = numBins; // Assign the last bin if the value exceeds all thresholds
			}
			const label = labels[bin - 1];
			result.get(label)!.push(sample.id);
		});

		return result;
	}

	const useSimulateSensitivityCharts = (chartSettings: ComputedRef<ChartSettingSensitivity[]>) => {
		const sensitivityData = ref<
			Record<string, { lineChart: VisualizationSpec; scatterChart: VisualizationSpec; rankingChart: VisualizationSpec }>
		>({});
		const sensitivityDataLoading = ref(false);
		let rankingScores: Map<string, Map<string, number>> = new Map();

		const fetchSensitivityData = async () => {
			// pick the first setting's timepoint for now
			const timepoint = chartSettings.value[0].timepoint;
			const { result } = chartData.value as ChartData;
			const sliceData = result.filter((d: any) => d.timepoint_id === timepoint) as any[];
			// Translate names ahead of time, because we can't seem to customize titles
			// in vegalite with repeat
			const translationMap = chartData.value?.translationMap;
			const sliceDataTranslated = sliceData.map((obj: any) => {
				const r: any = {};
				Object.keys(obj).forEach((key) => {
					if (translationMap && translationMap[key]) {
						const newKey = translationMap[key];
						r[newKey] = obj[key];
					} else {
						r[key] = obj[key];
					}
				});
				return r;
			});

			const inputVariables: string[] = chartSettings.value[0].selectedInputVariables ?? [];

			const charts: Record<
				string,
				{ lineChart: VisualizationSpec; scatterChart: VisualizationSpec; rankingChart: VisualizationSpec }
			> = {};
			// eslint-disable-next-line
			for (const settings of chartSettings.value) {
				const selectedVariable =
					chartData.value?.pyciemssMap[settings.selectedVariables[0]] || settings.selectedVariables[0];
				const unit = getUnit(settings.selectedVariables[0]);
				const { options } = createForecastChartOptions(settings);
				const bins = createSensitivityBins(sliceData, selectedVariable, 7, unit);

				options.bins = bins;
				options.colorscheme = SENSITIVITY_COLOUR_SCHEME;
				options.legend = true;
				options.title = `${settings.selectedVariables[0]} sensitivity`;
				options.legendProperties = { direction: 'vertical', columns: 1, labelLimit: 500 };

				const rankingSpec = createSensitivityRankingChart(rankingScores.get(selectedVariable)!, options);

				const lineSpec = createForecastChart(
					{
						data: result,
						variables: [selectedVariable],
						timeField: 'timepoint_id',
						groupField: 'sample_id'
					},
					{
						data: result,
						variables: [selectedVariable],
						timeField: 'timepoint_id'
					},
					null,
					options
				);

				// Get the statistical layer
				const statsLayer = lineSpec.layer[1];
				if (statsLayer?.layer) {
					// Remove any unwanted legends
					statsLayer.layer.forEach((sublayer) => {
						if (sublayer.encoding?.color?.legend) {
							sublayer.encoding.color.legend = null;
						}
					});

					// Find the text layers
					const textLayers = statsLayer.layer.filter((l) => l.mark?.type === 'text');
					const lastTwoLayers = textLayers.slice(-2);

					// The white outline layer
					const outlineLayer = lastTwoLayers[0];
					// The colored text layer
					const textLayer = lastTwoLayers[1];

					// Keep outline layer stroke white but remove color encoding
					if (outlineLayer) {
						delete outlineLayer.encoding.color;
					}

					// Copy the exact color encoding from the line layer
					if (textLayer) {
						textLayer.encoding.color = {
							field: 'group',
							type: 'nominal',
							scale: {
								domain: Array.from(bins.keys()),
								range: SENSITIVITY_COLOUR_SCHEME
							}
						};
					}
				}

				// Add sensitivity annotation
				const annotation = createForecastChartAnnotation('x', timepoint, 'Sensitivity analysis', true);
				lineSpec.layer[0].layer.push(annotation.layerSpec);

				const spec = createSimulateSensitivityScatter(
					{
						data: sliceDataTranslated,
						inputVariables,
						outputVariable: settings.selectedVariables[0]
					},
					{
						width: 150,
						height: 150,
						xAxisTitle: '',
						yAxisTitle: '',
						bins,
						colorscheme: SENSITIVITY_COLOUR_SCHEME
					}
				);

				charts[settings.id] = { lineChart: lineSpec, scatterChart: spec, rankingChart: rankingSpec };
			}

			sensitivityData.value = charts;
		};

		watchEffect(async () => {
			if (!chartData.value || !model?.value) return;
			sensitivityDataLoading.value = true;

			const allSelectedVariables = chartSettings.value.map(
				(s) => chartData.value?.pyciemssMap[s.selectedVariables[0]] || s.selectedVariables[0]
			);
			// only run if the ranking scores keys are not equal to the selectedVariables
			const hasAllScores =
				allSelectedVariables.every((v) => rankingScores.has(v)) &&
				Array.from(rankingScores.keys()).every((k) => allSelectedVariables.includes(k));
			if (!hasAllScores) {
				const timepoint = chartSettings.value[0].timepoint;
				const allParameters = model?.value?.semantics?.ode.parameters?.map((p) => p.id) ?? [];
				const sliceData = chartData.value.result.filter((d) => d.timepoint_id === timepoint);
				rankingScores = await pythonInstance.getRankingScores(sliceData, allSelectedVariables, allParameters);
			}
			fetchSensitivityData();
			sensitivityDataLoading.value = false;
		});

		return computed(() => ({ data: sensitivityData.value, loading: sensitivityDataLoading.value }));
	};

	return {
		generateAnnotation,
		getChartAnnotationsByChartId,
		useInterventionCharts,
		useVariableCharts,
		useComparisonCharts,
		useCompareDatasetCharts,
		useEnsembleVariableCharts,
		useErrorChart,
		useParameterDistributionCharts,
		useWeightsDistributionCharts,
		useSimulateSensitivityCharts,
		useEnsembleErrorCharts
	};
}
