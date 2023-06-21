import { defineStore } from 'pinia';
import { CsvAsset } from '@/types/Types';
import { ProjectAssetTypes } from '@/types/Project';
import { WorkflowNode, WorkflowPortStatus } from '@/types/workflow';
import { TspanUnits, ChartConfig } from '@/types/SimulateConfig';
import { NumericValueMap } from '@/types/common';
import { v4 as uuidv4 } from 'uuid';

// Probably will be an array later
export const useOpenedWorkflowNodeStore = defineStore('opened-workflow-node', {
	state: () => ({
		assetId: null as string | null,
		pageType: null as ProjectAssetTypes | null,
		selectedOutputIndex: 0 as number,
		// model node
		// FIXME: won't need a store for model config values once workflow/model configs are saved
		initialValues: null as NumericValueMap[] | null,
		parameterValues: null as NumericValueMap[] | null,
		node: null as WorkflowNode | null,
		// simulate node
		numCharts: 1,
		chartConfigs: [] as ChartConfig[],
		tspanUnit: TspanUnits[0],
		tspan: [0, 100],
		// calibrate node
		calibrateNumCharts: 1,
		calibrateRunIdList: [] as number[],
		calibrateRunResults: {},
		readOnlyMapping: null as any[] | null
	}),
	actions: {
		// model node
		setDrilldown(
			assetId: string | null,
			pageType: ProjectAssetTypes | null,
			node: WorkflowNode | null
		) {
			this.assetId = assetId;
			this.pageType = pageType;
			this.setNode(node);
		},
		// simulate node
		setNode(node: WorkflowNode | null) {
			this.node = node;
		},
		// FIXME: Not a good idea to update reactive variables through global storage
		appendOutputPort(port: { type: string; label?: string; value: any }) {
			if (this.node) {
				this.node.outputs.push({
					id: uuidv4(),
					type: port.type,
					label: port.label,
					value: [port.value],
					status: WorkflowPortStatus.NOT_CONNECTED
				});
			}
		},
		appendChart() {
			this.numCharts++;
		},
		setChartConfig(chartIdx: number, chartConfig: ChartConfig) {
			this.chartConfigs[chartIdx] = {
				...this.chartConfigs[chartIdx],
				...chartConfig
			};
		},
		// calibrate node
		setCalibrateResults(
			datasetData: CsvAsset,
			simulateData: { [stateVarName: string]: number }[],
			indexOfTimestep: number,
			featureMapping: { [datasetFeature: string]: string },
			readOnlyMapping: any[] | null
		) {
			this.readOnlyMapping = readOnlyMapping;
			const datasetFeatures = Object.keys(featureMapping);
			const modelFeatures = Object.values(featureMapping);

			const datasetPickedIndexes = datasetData.headers.reduce((acc, value, idx) => {
				if (datasetFeatures.includes(value)) acc.push(idx);
				return acc;
			}, [] as number[]);

			const dataset: any = []; // temp typing

			// start from index 1 because index 0 contains csv headers
			for (let i = 1; i < datasetData.csv.length; i++) {
				const datasetRow = datasetData.csv[i];
				const outputRow = { timestep: 0 };
				datasetPickedIndexes.forEach((datasetIndex) => {
					const indexOfFeatureName = datasetFeatures.indexOf(datasetData.headers[datasetIndex]);
					const resultKey = modelFeatures[indexOfFeatureName];
					outputRow[resultKey] = Number(datasetRow[datasetIndex]);
				});
				outputRow.timestep = Number(datasetRow[indexOfTimestep]);
				dataset.push(outputRow);
			}

			const simulate = simulateData.map((simulateRow) => {
				const outputRow = { timestep: 0 };
				modelFeatures.forEach((key) => {
					outputRow[key] = simulateRow[key];
				});
				outputRow.timestep = simulateRow.timestep;
				return outputRow;
			});

			this.calibrateRunIdList = [1, 2];

			this.calibrateRunResults = {
				1: dataset,
				2: simulate
			};
		}
	}
});
