import _ from 'lodash';
import { DataArray, parsePyCiemssMap } from '@/services/models/simulation-service';
import { getSelectedOutput } from '@/components/workflow/util';
import { CalibrateMap } from '@/services/calibrate-workflow';
import { mae } from '@/utils/stats';
import { WorkflowNode } from '@/types/workflow';
import { computed, Ref } from 'vue';
import { CalibrationOperationStateCiemss } from './calibrate-operation';
/**
 * A rename function generator for getRunResultCSV. Here the idea
 * to differentiate before and after columns in the run results
 * */
export const renameFnGenerator = (label: string) => (col: string) => {
	if (col === 'timepoint_id' || col === 'sample_id') return col;
	return `${col}:${label}`;
};

/**
 * Merge before and after run and summary results, assume to be equal length and aligned
 * */
export const mergeResults = (
	resultPre: DataArray,
	resultAfter: DataArray,
	resultSummaryPre: DataArray,
	resultSummaryAfter: DataArray
) => {
	const result: DataArray = [];
	const resultSummary: DataArray = [];
	for (let i = 0; i < resultAfter.length; i++) {
		result.push(_.assign(resultAfter[i], resultPre[i]));
	}
	for (let i = 0; i < resultSummaryAfter.length; i++) {
		resultSummary.push(_.assign(resultSummaryAfter[i], resultSummaryPre[i]));
	}
	return { result, resultSummary };
};

/**
	* Get the mean absolute error from a provided source truth and a simulation run.
	* Utilied in calibration for charts
	* Assume that simulationData is in the form of pyciemss
			states end with _State
			The timestamp column is titled: timepoint_id
	* Assume that the mapping is in the calibration form:
			Ground truth will map to datasetVariable
			Simulation data will map to modelVariable AND not include _State
 * transform data, utilize mae, return mean aboslute error for charts.
 * Note: This will only compare rows with the same timestep value.
 */
export function getErrorData(
	groundTruth: DataArray,
	simulationData: DataArray,
	mapping: CalibrateMap[],
	timestampColName: string
) {
	const errors: DataArray = [];
	if (simulationData.length === 0 || groundTruth.length === 0 || !timestampColName) return errors;
	const pyciemssMap = parsePyCiemssMap(simulationData[0]);

	const datasetVariables = mapping.map((ele) => ele.datasetVariable);
	const relevantGroundTruthColumns = Object.keys(groundTruth[0]).filter(
		(variable) => datasetVariables.includes(variable) && variable !== timestampColName
	);
	if (relevantGroundTruthColumns.length === 0) return errors;

	const simulationDataGrouped = _.groupBy(simulationData, 'sample_id');

	Object.entries(simulationDataGrouped).forEach(([sampleId, entries]) => {
		const resultRow = { sample_id: Number(sampleId) };
		relevantGroundTruthColumns.forEach((columnName) => {
			const newEntries = entries.map((entry) => {
				// Ensure the simulation data maps to the same as the ground truth:
				const varName = mapping.find((m) => m.datasetVariable === columnName)?.modelVariable;
				return { [timestampColName]: entry.timepoint_id, [columnName]: entry[pyciemssMap[varName as string]] };
			});
			const meanAbsoluteError = mae(groundTruth, newEntries, timestampColName, columnName);
			resultRow[columnName] = meanAbsoluteError;
		});
		errors.push(resultRow);
	});
	return errors;
}

export const modelVarToDatasetVar = (mapping: CalibrateMap[], modelVariable: string) =>
	mapping.find((d) => d.modelVariable === modelVariable)?.datasetVariable || '';

// Get the selected output mapping for the node
export function getSelectedOutputMapping(node: WorkflowNode<CalibrationOperationStateCiemss>) {
	const wfOutputState = getSelectedOutput(node)?.state;
	return [
		...(wfOutputState?.mapping || []),
		// special case for timestamp column name mapping
		{ modelVariable: 'timepoint_id', datasetVariable: wfOutputState?.timestampColName ?? '' }
	];
}

export function usePreparedChartInputs(
	props: {
		node: WorkflowNode<CalibrationOperationStateCiemss>;
	},
	runResult: Ref<DataArray>,
	runResultSummary: Ref<DataArray>,
	runResultPre: Ref<DataArray>,
	runResultSummaryPre: Ref<DataArray>
) {
	const pyciemssMap = computed(() => (!runResult.value.length ? {} : parsePyCiemssMap(runResult.value[0])));

	return computed(() => {
		const state = props.node.state;
		if (!state.calibrationId || _.isEmpty(pyciemssMap.value)) return null;

		// Merge before/after for chart
		const { result, resultSummary } = mergeResults(
			runResultPre.value,
			runResult.value,
			runResultSummaryPre.value,
			runResultSummary.value
		);

		// Build lookup map for calibration, include before/after and dataset (observations)
		const translationMap = {};
		Object.keys(pyciemssMap.value).forEach((key) => {
			translationMap[`${pyciemssMap.value[key]}_mean`] = `${key} after calibration`;
			translationMap[`${pyciemssMap.value[key]}_mean:pre`] = `${key} before calibration`;
		});
		getSelectedOutputMapping(props.node).forEach((mapObj) => {
			translationMap[mapObj.datasetVariable] = 'Observations';
		});
		return {
			result,
			resultSummary,
			pyciemssMap: pyciemssMap.value,
			translationMap
		};
	});
}
