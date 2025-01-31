import { WorkflowOperationTypes } from '@/types/workflow';
import type { Operation, BaseState } from '@/types/workflow';
import { ChartSetting } from '@/types/common';

const DOCUMENTATION_URL = 'https://documentation.terarium.ai/datasets/compare-datasets/';

export enum TimepointOption {
	LAST = 'at the last timepoint',
	FIRST = 'at the first timepoint',
	OVERALL = 'at its peak'
}

export enum RankOption {
	MINIMUM = 'minimize',
	MAXIMUM = 'maximize'
}

export enum PlotValue {
	VALUE = 'value',
	PERCENTAGE = 'percent change',
	DIFFERENCE = 'difference'
}

export enum CompareValue {
	SCENARIO = 'scenario',
	RANK = 'rank',
	ERROR = 'error'
}

export const blankCriteriaOfInterest = {
	name: 'Criteria of interest',
	selectedConfigurationId: null,
	selectedVariable: null,
	rank: RankOption.MINIMUM,
	timepoint: TimepointOption.OVERALL
};

// Map groundTruth dataset variable to the other dataset variables
export interface CompareDatasetsMap {
	[id: string]: string;
}

export interface CriteriaOfInterestCard {
	name: string;
	selectedVariable: string | null;
	rank: RankOption;
	timepoint: TimepointOption;
}
export interface CompareDatasetsState extends BaseState {
	criteriaOfInterestCards: CriteriaOfInterestCard[];
	selectedPlotType: PlotValue;
	selectedCompareOption: CompareValue;
	selectedBaselineDatasetId: string | null;
	selectedGroundTruthDatasetId: string | null;
	chartSettings: ChartSetting[] | null;
	mapping: CompareDatasetsMap[];
}

export const CompareDatasetsOperation: Operation = {
	name: WorkflowOperationTypes.COMPARE_DATASETS,
	displayName: 'Compare datasets',
	description: 'Compare datasets, or simulation results',
	documentationUrl: DOCUMENTATION_URL,
	inputs: [
		{ type: 'datasetId', label: 'Dataset or Simulation result' },
		{ type: 'datasetId', label: 'Dataset or Simulation result' }
	],
	outputs: [{ type: 'datasetId', label: 'Dataset' }],
	isRunnable: true,
	action: () => {},
	initState: () => {
		const init: CompareDatasetsState = {
			criteriaOfInterestCards: [blankCriteriaOfInterest],
			selectedPlotType: PlotValue.DIFFERENCE,
			selectedCompareOption: CompareValue.SCENARIO,
			selectedBaselineDatasetId: null,
			selectedGroundTruthDatasetId: null,
			chartSettings: null,
			mapping: []
		};
		return init;
	}
};
