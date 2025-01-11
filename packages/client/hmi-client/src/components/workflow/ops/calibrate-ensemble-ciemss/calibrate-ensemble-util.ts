import _ from 'lodash';
import { createForecastChart, AUTOSIZE, GroupedDataArray } from '@/services/charts';
import {
	DataArray,
	extractModelConfigIdsInOrder,
	getEnsembleResultModelConfigMap,
	getRunResultCSV,
	getSimulation,
	parseEnsemblePyciemssMap,
	processAndSortSamplesByTimepoint
} from '@/services/models/simulation-service';
import { EnsembleModelConfigs, ModelConfiguration } from '@/types/Types';
import { WorkflowNode } from '@/types/workflow';
import { getActiveOutput } from '@/components/workflow/util';
import { CalibrateMap, setupModelInput } from '@/services/calibrate-workflow';
import {
	CalibrateEnsembleCiemssOperationState,
	CalibrateEnsembleMappingRow,
	CalibrateEnsembleWeights
} from './calibrate-ensemble-ciemss-operation';
import { getErrorData, mergeResults, renameFnGenerator } from '../calibrate-ciemss/calibrate-utils';

export async function getLossValuesFromSimulation(calibrationId: string) {
	if (!calibrationId) return [];
	const simulationObj = await getSimulation(calibrationId);
	if (simulationObj?.updates) {
		const lossValues = simulationObj?.updates
			.sort((a, b) => a.data.progress - b.data.progress)
			.map((d, i) => ({
				iter: i,
				loss: d.data.loss
			}));
		return lossValues;
	}
	return [];
}

export const updateLossChartSpec = (data: string | Record<string, any>[], size: { width: number; height: number }) =>
	createForecastChart(
		null,
		{
			data: Array.isArray(data) ? data : { name: data },
			variables: ['loss'],
			timeField: 'iter'
		},
		null,
		{
			title: '',
			width: size.width,
			height: 100,
			xAxisTitle: 'Solver iterations',
			yAxisTitle: 'Loss',
			autosize: AUTOSIZE.FIT,
			fitYDomain: true
		}
	);

export function formatCalibrateModelConfigurations(
	rows: CalibrateEnsembleMappingRow[],
	weights: CalibrateEnsembleWeights
): EnsembleModelConfigs[] {
	const ensembleModelConfigMap: { [key: string]: EnsembleModelConfigs } = {};
	// 1. map the weights to the ensemble model configs
	Object.entries(weights).forEach(([key, value]) => {
		// return if there is no weight
		if (!value) return;

		const ensembleModelConfig: EnsembleModelConfigs = {
			id: key,
			solutionMappings: {},
			weight: value
		};

		ensembleModelConfigMap[key] = ensembleModelConfig;
	});

	// 2. format the solution mappings
	rows.forEach((row) => {
		Object.entries(row.modelConfigurationMappings).forEach(([key, value]) => {
			if (!ensembleModelConfigMap[key]) return;
			ensembleModelConfigMap[key].solutionMappings[row.datasetMapping] = value;
		});
	});

	return [...Object.values(ensembleModelConfigMap)];
}

export function getSelectedOutputEnsembleMapping(
	node: WorkflowNode<CalibrateEnsembleCiemssOperationState>,
	hasTimestampCol = true
) {
	const wfOutputState = getActiveOutput(node)?.state;
	const mapping = _.clone(wfOutputState?.ensembleMapping ?? []);
	if (hasTimestampCol)
		mapping.push({
			newName: 'timepoint_id',
			datasetMapping: wfOutputState?.timestampColName ?? '',
			modelConfigurationMappings: {}
		});
	return mapping;
}

export async function fetchOutputData(preForecastId: string, postForecastId: string) {
	if (!postForecastId || !preForecastId) return null;
	const runResult = await getRunResultCSV(postForecastId, 'result.csv');
	const runResultSummary = await getRunResultCSV(postForecastId, 'result_summary.csv');
	const ensembleVarModelConfigMap = (await getEnsembleResultModelConfigMap(preForecastId)) ?? {};

	const runResultPre = await getRunResultCSV(preForecastId, 'result.csv', renameFnGenerator('pre'));
	const runResultSummaryPre = await getRunResultCSV(preForecastId, 'result_summary.csv', renameFnGenerator('pre'));

	const pyciemssMap = parseEnsemblePyciemssMap(runResult[0], ensembleVarModelConfigMap);

	// Merge before/after for chart
	const { result, resultSummary } = mergeResults(runResultPre, runResult, runResultSummaryPre, runResultSummary);

	const resultGroupByTimepoint = processAndSortSamplesByTimepoint(result);
	return {
		result,
		resultSummary,
		resultGroupByTimepoint,
		pyciemssMap
	};
}

export async function fetchModelConfigurations(
	nodeInputs: WorkflowNode<CalibrateEnsembleCiemssOperationState>['inputs']
) {
	const allModelOptions: any[][] = [];
	const allModelConfigurations: ModelConfiguration[] = [];
	const modelConfigurationIds: string[] = [];
	nodeInputs.forEach((ele) => {
		if (ele.value && ele.type === 'modelConfigId') modelConfigurationIds.push(ele.value[0]);
	});
	if (!modelConfigurationIds) return null;

	// Model configuration input
	await Promise.all(
		modelConfigurationIds.map(async (id) => {
			const { modelConfiguration, modelOptions } = await setupModelInput(id);
			if (modelConfiguration) allModelConfigurations.push(modelConfiguration);
			if (modelOptions) allModelOptions.push(modelOptions);
		})
	);
	return { allModelConfigurations, allModelOptions };
}

// Build chart data by adding variable translation map to the given output data
export function buildChartData(
	outputData: {
		result: DataArray;
		resultSummary: DataArray;
		resultGroupByTimepoint: GroupedDataArray;
		pyciemssMap: Record<string, string>;
	} | null,
	mappings: CalibrateEnsembleMappingRow[]
) {
	if (!outputData) return null;
	const pyciemssMap = outputData.pyciemssMap;
	const translationMap = {};
	Object.keys(outputData.pyciemssMap).forEach((key) => {
		// pyciemssMap keys are formatted as either '{modelConfigId}/{displayVariableName}' for model variables or '{displayVariableName}' for ensemble variables
		const tokens = key.split('/');
		const varName = tokens.length > 1 ? tokens[1] : 'Ensemble';
		translationMap[`${pyciemssMap[key]}`] = `${varName} after calibration`;
		translationMap[`${pyciemssMap[key]}:pre`] = `${varName} before calibration`;
		translationMap[`${pyciemssMap[key]}_mean`] = `${varName} after calibration`;
		translationMap[`${pyciemssMap[key]}_mean:pre`] = `${varName} before calibration`;
	});
	// Add translation map for dataset variables
	mappings.forEach((mapObj) => {
		translationMap[mapObj.datasetMapping] = 'Observations';
	});
	return { ...outputData, translationMap };
}

export interface EnsembleErrorData {
	ensemble: DataArray;
	[modelConfigId: string]: DataArray;
}

// Get the error data for the ensemble calibration
export function getEnsembleErrorData(
	groundTruth: DataArray,
	simulationData: DataArray,
	mapping: CalibrateEnsembleMappingRow[],
	pyciemssMap: Record<string, string>
): EnsembleErrorData {
	const errorData: EnsembleErrorData = { ensemble: [] };
	const timestampColName = mapping.find((m) => m.newName === 'timepoint_id')?.datasetMapping ?? '';
	const mappingWithoutTimeCol = mapping.filter((m) => m.newName !== 'timepoint_id');
	// Error data for the ensemble
	const calibrateMappings = mappingWithoutTimeCol.map(
		(m) =>
			({
				datasetVariable: m.datasetMapping,
				modelVariable: m.datasetMapping
			}) as CalibrateMap
	);
	errorData.ensemble = getErrorData(groundTruth, simulationData, calibrateMappings, timestampColName, pyciemssMap);

	// Error data for each model
	const modelConfigIds = extractModelConfigIdsInOrder(pyciemssMap);
	modelConfigIds.forEach((configId) => {
		const cMapping = mappingWithoutTimeCol.map(
			(m) =>
				({
					datasetVariable: m.datasetMapping,
					modelVariable: `${configId}/${m.modelConfigurationMappings[configId]}`
				}) as CalibrateMap
		);
		errorData[configId] = getErrorData(groundTruth, simulationData, cMapping, timestampColName, pyciemssMap);
	});
	return errorData;
}
