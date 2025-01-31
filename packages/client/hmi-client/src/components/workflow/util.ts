import _ from 'lodash';
import { DataseriesConfig, ChartConfig } from '@/types/SimulateConfig';
import type { CsvAsset, Dataset, TimeSpan } from '@/types/Types';
import type { WorkflowNode } from '@/types/workflow';
import { useProjects } from '@/composables/project';

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

// Get the min and max value for a column within a dataset.
// Allow for user to default to a provided end time should our stats fail
export function getTimespan(dataset: Dataset, timeColName: string, defaultEndTime: number = 90): TimeSpan {
	let start = 0;
	let end = defaultEndTime;
	const stats = dataset.columns?.find((col) => col.name === timeColName)?.stats?.numericStats;
	if (stats) {
		start = stats.min;
		end = stats.max;
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

export function getActiveOutput<S>(node: WorkflowNode<S>) {
	return node.outputs.find((output) => output.id === node.active);
}
