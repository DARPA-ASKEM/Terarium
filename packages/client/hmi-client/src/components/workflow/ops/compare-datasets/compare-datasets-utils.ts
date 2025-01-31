import { isEmpty, cloneDeep } from 'lodash';
import { mean, stddev } from '@/utils/stats';
import { Dataset, InterventionPolicy, ModelConfiguration } from '@/types/Types';
import { WorkflowNode, WorkflowPortStatus } from '@/types/workflow';
import { renameFnGenerator } from '@/components/workflow/ops/calibrate-ciemss/calibrate-utils';
import { Ref } from 'vue';

import { createRankingInterventionsChart, CATEGORICAL_SCHEME } from '@/services/charts';
import { DATASET_VAR_NAME_PREFIX, getDatasetResultCSV, mergeResults, getDataset } from '@/services/dataset';
import {
	DataArray,
	parsePyCiemssMap,
	processAndSortSamplesByTimepoint,
	getRunResultCSV
} from '@/services/models/simulation-service';
import { getInterventionPolicyById } from '@/services/intervention-policy';
import { getModelConfigurationById } from '@/services/model-configurations';

import { ChartData } from '@/composables/useCharts';

import { PlotValue, TimepointOption, RankOption, CompareDatasetsState } from './compare-datasets-operation';

interface DataResults {
	results: DataArray[];
	summaryResults: DataArray[];
	datasetResults: DataArray[];
}

export function isSimulationData(dataset: Dataset) {
	return Boolean(dataset.metadata.simulationId);
}

/**
 * Similar to `renameFnGenerator` but it generates a rename function for the dataset results. Prefix dataset column names with 'data/' to distinguish them from the simulation results
 * @param label
 * @returns
 */
const datasetRenameFnGenerator = (label: string) => (col: string) => {
	if (col === 'timepoint_id' || col === 'sample_id') return col;
	return `${DATASET_VAR_NAME_PREFIX}${col}:${label}`;
};

export async function fetchDatasetResults(datasetList: Dataset[]) {
	// Fetch simulation results
	const results: DataArray[] = (
		await Promise.all(
			datasetList.map((dataset, datasetIndex) => {
				if (!isSimulationData(dataset)) return Promise.resolve([]);
				return getRunResultCSV(dataset.metadata.simulationId, 'result.csv', renameFnGenerator(`${datasetIndex}`));
			})
		)
	).filter((result) => result.length > 0);
	// Fetch simulation summary results
	const summaryResults: DataArray[] = (
		await Promise.all(
			datasetList.map((dataset, datasetIndex) => {
				if (!isSimulationData(dataset)) return Promise.resolve([]);
				return getRunResultCSV(
					dataset.metadata.simulationId,
					'result_summary.csv',
					renameFnGenerator(`${datasetIndex}`)
				);
			})
		)
	).filter((result) => result.length > 0);
	// Fetch non simulation dataset results
	const datasetResults: DataArray[] = (
		await Promise.all(
			datasetList.map((dataset, datasetIndex) => {
				if (isSimulationData(dataset)) return Promise.resolve([]);
				return getDatasetResultCSV(dataset, dataset.fileNames?.[0] ?? '', datasetRenameFnGenerator(`${datasetIndex}`));
			})
		)
	).filter((result) => result.length > 0);
	return {
		results,
		summaryResults,
		datasetResults
	};
}

export const transformRowValuesRelativeToBaseline = (
	row: Record<string, number>,
	baselineDataIndex: number,
	plotType: PlotValue
) => {
	const transformed: Record<string, number> = {};
	// FIXME: When refreshing the drilldown Chrome auto-pauses here sometimes to avoid a potential memory crash
	Object.entries(row).forEach(([key, value]) => {
		const [dataKey, datasetIndex] = key.split(':');
		if (datasetIndex === undefined) {
			transformed[key] = value;
			return;
		}
		const baselineValue = row[`${dataKey}:${baselineDataIndex}`];
		if (plotType === PlotValue.DIFFERENCE) {
			transformed[key] = value - baselineValue;
		} else if (plotType === PlotValue.PERCENTAGE) {
			transformed[key] = ((value - baselineValue) / baselineValue) * 100;
		} else if (plotType === PlotValue.VALUE) {
			transformed[key] = value;
		}
	});
	return transformed;
};

/**
 * Removes the dataset index suffix from the variable names and returns a new object with the unique variable names which can be used as an input to the parsePyCiemssMap function.
 * E.g. { 'variableName:0': 1, 'variableName:1': 2 } -> { variableName: 'variableName' }
 * @param obj
 */
export const uniqueVarNames = (obj: Record<string, any>) => {
	const newObj = {};
	Object.keys(obj)
		.map((key) => key.split(':')[0])
		.forEach((key) => {
			newObj[key] = key;
		});
	return newObj;
};

/**
 * Build variable display name mapping for the non simulation result dataset which can be added to the pyciemss map
 */
export const buildDatasetVarMapping = (dsets: DataArray[]) => {
	const map: Record<string, any> = {};
	dsets.forEach((dataset) => {
		Object.keys(dataset[0]).forEach((key) => {
			const varName = key.split(':')[0];
			map[varName.replace(DATASET_VAR_NAME_PREFIX, '')] = varName;
		});
	});
	return map;
};

export function buildChartData(
	datasets: Dataset[],
	dataResults: DataResults | null,
	baselineDatasetIndex: number,
	plotType: PlotValue
): ChartData | null {
	if (datasets.length <= 1 || !dataResults) return null;
	const { results, summaryResults, datasetResults } = dataResults;

	// Merge all datasets into single dataset
	const result = mergeResults(...results);
	const summaryResult = mergeResults(...summaryResults, ...datasetResults);

	// Transform the values relative to the baseline dataset
	const resultTransformed = result.map((row) =>
		transformRowValuesRelativeToBaseline(row, baselineDatasetIndex, plotType)
	);
	const resultSummaryTransformed = summaryResult.map((row) =>
		transformRowValuesRelativeToBaseline(row, baselineDatasetIndex, plotType)
	);

	// Process data for uncertainty intervals chart mode
	const resultGroupByTimepoint = processAndSortSamplesByTimepoint(resultTransformed);

	// Build pyciemss map for display variable name map for the simulation result variables
	const pyciemssMap = parsePyCiemssMap(uniqueVarNames(result[0] ?? {}));
	// Augment the pyciemss map with the dataset variable mapping.
	Object.assign(pyciemssMap, buildDatasetVarMapping(datasetResults));

	// Build translation map
	const translationMap = {};
	Object.keys(pyciemssMap).forEach((key) => {
		datasets.forEach((dataset, index) => {
			translationMap[`${pyciemssMap[key]}:${index}`] = `${dataset.name}`;
			translationMap[`${pyciemssMap[key]}_mean:${index}`] = `${dataset.name}`;
		});
	});

	return {
		result: resultTransformed,
		resultSummary: resultSummaryTransformed,
		resultGroupByTimepoint,
		pyciemssMap,
		translationMap,
		numComparableDatasets: datasets.length
	};
}

export function generateRankingCharts(
	rankingCriteriaCharts,
	rankingResultsChart,
	node: WorkflowNode<CompareDatasetsState>,
	chartData,
	datasets: Ref<Dataset[]>,
	modelConfigurations,
	interventionPolicies
) {
	// Reset charts
	rankingCriteriaCharts.value = [];
	rankingResultsChart.value = null;

	const allRankedCriteriaValues: { score: number; policyName: string; configName: string }[][] = [];
	const interventionNameColorMap: Record<string, string> = {};
	const interventionNameScoresMap: Record<string, number[]> = {};

	node.state.criteriaOfInterestCards.forEach((card) => {
		if (!chartData.value || !card.selectedVariable) return;

		const variableKey = `${chartData.value.pyciemssMap[card.selectedVariable]}_mean`;
		let pointOfComparison: Record<string, number> = {};

		if (card.timepoint === TimepointOption.OVERALL) {
			const resultSummary = cloneDeep(chartData.value.resultSummary); // Must clone to avoid modifying the original data

			// Note that the reduce function here only compares the variable of interest
			// so only those key/value pairs will be relevant in the pointOfComparison object.
			// Other keys like timepoint_id (that we aren't using) will be in pointOfComparison
			// but they won't coincide with the value of the variable of interest.
			pointOfComparison = resultSummary.reduce((acc, val) =>
				Object.keys(val).reduce((acc2, key) => {
					if (key.includes(variableKey)) {
						acc2[key] = Math.max(acc[key], val[key]);
					}
					return acc2;
				}, acc)
			);
		} else if (card.timepoint === TimepointOption.FIRST) {
			pointOfComparison = chartData.value.resultSummary[0];
		} else if (card.timepoint === TimepointOption.LAST) {
			pointOfComparison = chartData.value.resultSummary[chartData.value.resultSummary.length - 1];
		}

		const rankingCriteriaValues: { score: number; policyName: string; configName: string }[] = [];

		let colorIndex = 0;
		datasets.value.forEach((dataset, index: number) => {
			const { metadata } = dataset;
			const modelConfiguration: ModelConfiguration = modelConfigurations.value.find(
				({ id }) => id === metadata.simulationAttributes?.modelConfigurationId
			);
			const policy: InterventionPolicy = interventionPolicies.value.find(
				({ id }) => id === metadata.simulationAttributes?.interventionPolicyId
			);

			const policyName = policy?.name ?? 'no policy';

			if (!modelConfiguration?.name) {
				return;
			}

			if (!interventionNameColorMap[policyName]) {
				interventionNameScoresMap[policyName] = [];
				if (!policy?.name) {
					interventionNameColorMap[policyName] = 'black';
				} else {
					interventionNameColorMap[policyName] = CATEGORICAL_SCHEME[colorIndex];
					colorIndex++;
				}
			}

			rankingCriteriaValues.push({
				score: pointOfComparison[`${variableKey}:${index}`] ?? 0,
				policyName,
				configName: modelConfiguration.name
			});
		});

		const sortedRankingCriteriaValues =
			card.rank === RankOption.MAXIMUM
				? rankingCriteriaValues.sort((a, b) => b.score - a.score)
				: rankingCriteriaValues.sort((a, b) => a.score - b.score);

		rankingCriteriaCharts.value.push(
			createRankingInterventionsChart(
				sortedRankingCriteriaValues,
				interventionNameColorMap,
				card.name,
				card.selectedVariable
			)
		);
		allRankedCriteriaValues.push(sortedRankingCriteriaValues);
	});

	// For each criteria
	allRankedCriteriaValues.forEach((criteriaValues, index) => {
		const rankMutliplier = node.state.criteriaOfInterestCards[index].rank === RankOption.MINIMUM ? -1 : 1;

		// Calculate mean and stdev for this criteria
		const values = criteriaValues.map((val) => val.score);
		const meanValue = mean(values);
		let stdevValue = stddev(values);
		if (stdevValue === 0) stdevValue = 1;

		// For each policy
		Object.keys(interventionNameScoresMap).forEach((policyName) => {
			// For each value of the criteria
			criteriaValues.forEach((criteriaValue) => {
				if (criteriaValue.policyName !== policyName) return; // Skip criteria values that don't belong to this policy
				const scoredPolicyCriteria = rankMutliplier * ((criteriaValue.score - meanValue) / stdevValue);
				interventionNameScoresMap[policyName].push(scoredPolicyCriteria);
			});
		});
	});

	const scoredPolicies = Object.keys(interventionNameScoresMap)
		.map((policyName) => ({
			score: mean(interventionNameScoresMap[policyName]),
			policyName,
			configName: ''
		})) // Sort from highest to lowest value
		.sort((a, b) => b.score - a.score);

	rankingResultsChart.value = createRankingInterventionsChart(scoredPolicies, interventionNameColorMap);
}

export async function generateImpactCharts(
	chartData,
	datasets: Ref<Dataset[]>,
	datasetResults,
	baselineDatasetIndex,
	selectedPlotType
) {
	chartData.value = buildChartData(
		datasets.value,
		datasetResults.value,
		baselineDatasetIndex.value,
		selectedPlotType.value
	);
}

// TODO: this should probably be split up into smaller functions but for now it's at least not duplicated in the node and drilldown
// TODO: Please type the function params in this file for a later pass
export async function initialize(
	node: WorkflowNode<CompareDatasetsState>,
	knobs: Ref<any> | null,
	isFetchingDatasets: Ref<boolean>,
	datasets: Ref<Dataset[]>,
	datasetResults,
	modelConfigIdToInterventionPolicyIdMap,
	impactChartData,
	rankingChartData,
	baselineDatasetIndex,
	selectedPlotType,
	modelConfigurations,
	interventionPolicies,
	rankingCriteriaCharts,
	rankingResultsChart
) {
	const { inputs } = node;
	const datasetInputs = inputs.filter(
		(input) => input.type === 'datasetId' && input.status === WorkflowPortStatus.CONNECTED
	);

	const datasetPromises = datasetInputs.map((input) => {
		const datasetId = input.value?.[0];
		if (!datasetId) return Promise.resolve(null);
		return getDataset(datasetId);
	});

	isFetchingDatasets.value = true;
	await Promise.all(datasetPromises).then((ds) => {
		ds.forEach((dataset) => {
			// Add dataset
			if (!dataset) return;
			datasets.value.push(dataset);

			// Collect model configuration id and intervention policy id
			const modelConfigurationId: string | undefined = dataset.metadata?.simulationAttributes?.modelConfigurationId;
			const interventionPolicyId: string | undefined = dataset.metadata?.simulationAttributes?.interventionPolicyId;

			if (!modelConfigurationId) return;
			if (!modelConfigIdToInterventionPolicyIdMap.value[modelConfigurationId]) {
				modelConfigIdToInterventionPolicyIdMap.value[modelConfigurationId] = [];
			}
			if (!interventionPolicyId) {
				// Select a default baseline by choosing the first dataset that lacks an intervention policy
				if (knobs && !knobs.value.selectedBaselineDatasetId) knobs.value.selectedBaselineDatasetId = dataset.id;
				return;
			}
			modelConfigIdToInterventionPolicyIdMap.value[modelConfigurationId].push(interventionPolicyId);
		});
	});
	// Fallback to the first dataset if no dataset ends up being selected
	if (knobs) {
		if (!knobs.value.selectedBaselineDatasetId) knobs.value.selectedBaselineDatasetId = datasets.value[0].id;
		if (!knobs.value.selectedGroundTruthDatasetId) knobs.value.selectedGroundTruthDatasetId = datasets.value[0].id;
	}

	// Fetch the results
	datasetResults.value = await fetchDatasetResults(datasets.value);
	isFetchingDatasets.value = false;

	await generateImpactCharts(impactChartData, datasets, datasetResults, baselineDatasetIndex, selectedPlotType);

	const modelConfigurationIds = Object.keys(modelConfigIdToInterventionPolicyIdMap.value);
	if (isEmpty(modelConfigurationIds)) return;
	const modelConfigurationPromises = modelConfigurationIds.map((id) => getModelConfigurationById(id));
	await Promise.all(modelConfigurationPromises).then((configs) => {
		modelConfigurations.value = configs.filter((config) => config !== null);
	});

	const interventionPolicyIds = Object.values(modelConfigIdToInterventionPolicyIdMap.value).flat();
	if (isEmpty(interventionPolicyIds)) return;
	const interventionPolicyPromises = interventionPolicyIds.map((id) => getInterventionPolicyById(`${id}`));
	await Promise.all(interventionPolicyPromises).then((policies) => {
		interventionPolicies.value = policies.filter((policy) => policy !== null);
	});

	if (!rankingChartData) return;

	rankingChartData.value = buildChartData(
		datasets.value,
		datasetResults.value,
		baselineDatasetIndex.value,
		PlotValue.VALUE
	);

	generateRankingCharts(
		rankingCriteriaCharts,
		rankingResultsChart,
		node,
		rankingChartData,
		datasets,
		modelConfigurations,
		interventionPolicies
	);
}
