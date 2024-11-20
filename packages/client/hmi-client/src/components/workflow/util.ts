import _ from 'lodash';
import { DataseriesConfig, ChartConfig } from '@/types/SimulateConfig';
import type { CsvAsset, TimeSpan } from '@/types/Types';
import type { WorkflowNode } from '@/types/workflow';
import { isCalibrateMap, type CalibrateMap } from '@/services/calibrate-workflow';
import { useProjects } from '@/composables/project';
import { CalibrateEnsembleMappingRow } from './ops/calibrate-ensemble-ciemss/calibrate-ensemble-ciemss-operation';
import {
	isSimulateEnsembleMappingRow,
	SimulateEnsembleMappingRow
} from './ops/simulate-ensemble-ciemss/simulate-ensemble-ciemss-operation';

export const drilldownChartSize = (element: HTMLElement | null) => {
	if (!element) return { width: 100, height: 270 };

	const parentContainerWidth = (element as HTMLElement).clientWidth - 24;
	return { width: parentContainerWidth, height: 270 };
};

/**
 * Function generator for common TA3-operator operations, such add, update, and delete charts
 * The idea is to have a single place to do data manipulations but let the caller retain control
 * via the callback function
 * */
export const chartActionsProxy = (node: WorkflowNode<any>, updateStateCallback: Function) => {
	if (!node.state.chartConfigs) throw new Error('Cannot find chartConfigs in state object');

	const addChart = (selectedVariables: string[] = []) => {
		const copy = _.cloneDeep(node.state);
		copy.chartConfigs.push(selectedVariables);
		updateStateCallback(copy);
	};

	const removeChart = (index: number) => {
		const copy = _.cloneDeep(node.state);
		copy.chartConfigs.splice(index, 1);
		updateStateCallback(copy);
	};

	const configurationChange = (index: number, config: ChartConfig) => {
		const copy = _.cloneDeep(node.state);
		copy.chartConfigs[index] = config.selectedVariable;
		updateStateCallback(copy);
	};

	return {
		addChart,
		removeChart,
		configurationChange
	};
};

export const nodeOutputLabel = (node: WorkflowNode<any>, prefix: string) => {
	const outputNumber = node.outputs.filter((d) => d.value).length;
	return `${prefix} - ${outputNumber + 1}`;
};

export const nodeMetadata = (node: WorkflowNode<any>) => ({
	workflowId: node.workflowId,
	workflowName: useProjects().getAssetName(node.workflowId),
	nodeId: node.id,
	nodeName: node.displayName
});

export interface GetTimespanParams {
	dataset?: CsvAsset;
	mapping?: CalibrateMap[];
	timestampColName?: string;
}

export function getTimespan(params: GetTimespanParams): TimeSpan {
	let start = 0;
	let end = 90;
	// If we have the min/max timestamp available from the csv asset use it
	if (params.dataset) {
		let tVar = params.timestampColName ?? 'timestamp';
		if (params.mapping && !params.timestampColName) {
			// if there's a mapping for timestamp, then the model variable is guaranteed to be 'timestamp'
			const tMap = params.mapping.find((m) => m.modelVariable === 'timestamp');
			if (tMap) {
				tVar = tMap.datasetVariable;
			}
		}
		let tIndex = params.dataset.headers.indexOf(tVar);
		// if the timestamp column is not found, default to 0 as this is what is assumed to be the default
		// timestamp column in the pyciemss backend
		tIndex = tIndex === -1 ? 0 : tIndex;

		start = params.dataset.stats?.[tIndex].minValue!;
		end = params.dataset.stats?.[tIndex].maxValue!;
	}
	return { start, end };
}

export const getGraphDataFromDatasetCSV = (
	dataset: CsvAsset,
	columnVar: string,
	mapping?: { [key: string]: string }[]
): DataseriesConfig | null => {
	// Default
	let selectedVariable = columnVar;
	let timeVariable = 'timestamp';

	// Map variable names to dataset column names
	if (mapping) {
		let result = mapping.find((m) => m.modelVariable === selectedVariable);
		if (result) {
			selectedVariable = result.datasetVariable;
		}

		result = mapping.find((m) => m.modelVariable === timeVariable);
		if (result) {
			timeVariable = result.datasetVariable;
		}
	}

	// Graph dataset index columns for x: timeVariable and y: selectedVariable
	let tIndex = -1;
	let sIndex = -1;
	for (let i = 0; i < dataset.headers.length; i++) {
		if (timeVariable.trim() === dataset.headers[i].trim()) tIndex = i;
		if (selectedVariable.trim() === dataset.headers[i].trim()) sIndex = i;
	}
	if (tIndex === -1 || sIndex === -1) {
		return null;
	}

	const graphData: DataseriesConfig = {
		// ignore the first row, it's the header
		data: dataset.csv.slice(1).map((datum: string[]) => ({
			x: +datum[tIndex],
			y: +datum[sIndex]
		})),
		label: `${columnVar} - dataset`,
		fill: false,
		borderColor: '#000000',
		borderDash: [3, 3]
	};

	return graphData;
};

export function getSelectedOutput<S>(node: WorkflowNode<S>) {
	const selectedOutputId = node.active;
	const wfOutput = node.outputs.find((output) => output.id === selectedOutputId);
	return wfOutput;
}

export type VariableMappings = CalibrateMap[] | CalibrateEnsembleMappingRow[] | SimulateEnsembleMappingRow[];

export const modelVarToDatasetVar = (mapping: VariableMappings, modelVariable: string) => {
	if (_.isEmpty(mapping) || isSimulateEnsembleMappingRow(mapping[0])) return '';
	const found = (mapping as CalibrateMap[] | CalibrateEnsembleMappingRow[]).find((d) =>
		isCalibrateMap(d) ? d.modelVariable === modelVariable : d.newName === modelVariable
	);
	if (!found) return '';
	return isCalibrateMap(found) ? found.datasetVariable : found.datasetMapping;
};
