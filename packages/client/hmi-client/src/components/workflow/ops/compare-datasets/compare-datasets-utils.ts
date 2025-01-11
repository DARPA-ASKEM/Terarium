import { Dataset } from '@/types/Types';
import { renameFnGenerator } from '@/components/workflow/ops/calibrate-ciemss/calibrate-utils';
import { DataArray, parsePyCiemssMap, processAndSortSamplesByTimepoint } from '@/services/models/simulation-service';
import { DATASET_VAR_NAME_PREFIX, getDatasetResultCSV, mergeResults } from '@/services/dataset';
import { ChartData } from '@/composables/useCharts';
import { PlotValue } from './compare-datasets-operation';

interface DataResults {
	results: DataArray[];
	summaryResults: DataArray[];
	datasetResults: DataArray[];
}

export function isSimulationData(dataset: Dataset) {
	// assume if the dataset has these two files, it's a simulation dataset
	return (
		dataset.fileNames?.find((name) => name === 'result.csv') &&
		dataset.fileNames?.find((name) => name === 'result_summary.csv')
	);
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
				return getDatasetResultCSV(dataset, 'result.csv', renameFnGenerator(`${datasetIndex}`));
			})
		)
	).filter((result) => result.length > 0);
	// Fetch simulation summary results
	const summaryResults: DataArray[] = (
		await Promise.all(
			datasetList.map((dataset, datasetIndex) => {
				if (!isSimulationData(dataset)) return Promise.resolve([]);
				return getDatasetResultCSV(dataset, 'result_summary.csv', renameFnGenerator(`${datasetIndex}`));
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
