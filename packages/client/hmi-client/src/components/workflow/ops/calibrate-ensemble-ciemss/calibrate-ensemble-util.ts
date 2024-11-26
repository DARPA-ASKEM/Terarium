import _ from 'lodash';
import { createForecastChart, AUTOSIZE } from '@/services/charts';
import {
	DataArray,
	getEnsembleResultModelConfigMap,
	getRunResultCSV,
	getSimulation,
	parseEnsemblePyciemssMap
} from '@/services/models/simulation-service';
import { EnsembleModelConfigs } from '@/types/Types';
import { WorkflowNode } from '@/types/workflow';
import { getActiveOutput } from '@/components/workflow/util';
import {
	CalibrateEnsembleCiemssOperationState,
	CalibrateEnsembleMappingRow,
	CalibrateEnsembleWeights
} from './calibrate-ensemble-ciemss-operation';
import { mergeResults, renameFnGenerator } from '../calibrate-ciemss/calibrate-utils';

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

	console.log(runResult);
	console.log(runResultSummary);

	const runResultPre = await getRunResultCSV(preForecastId, 'result.csv', renameFnGenerator('pre'));
	const runResultSummaryPre = await getRunResultCSV(preForecastId, 'result_summary.csv', renameFnGenerator('pre'));

	const pyciemssMap = parseEnsemblePyciemssMap(runResult[0], ensembleVarModelConfigMap);

	// Merge before/after for chart
	const { result, resultSummary } = mergeResults(runResultPre, runResult, runResultSummaryPre, runResultSummary);

	return {
		result,
		resultSummary,
		pyciemssMap
	};
}

// Build chart data by adding variable translation map to the given output data
export function buildChartData(
	outputData: {
		result: DataArray;
		resultSummary: DataArray;
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
		translationMap[`${pyciemssMap[key]}_mean`] = `${varName} after calibration`;
		translationMap[`${pyciemssMap[key]}_mean:pre`] = `${varName} before calibration`;
	});
	// Add translation map for dataset variables
	mappings.forEach((mapObj) => {
		translationMap[mapObj.datasetMapping] = 'Observations';
	});
	return { ...outputData, translationMap };
}
