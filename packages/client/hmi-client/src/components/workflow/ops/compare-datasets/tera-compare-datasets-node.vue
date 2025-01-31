<template>
	<section>
		<tera-node-preview
			:node="node"
			:prepared-charts="comparisonCharts"
			:is-loading="isFetchingDatasets"
			:chart-settings="selectedVariableSettings"
			:placeholder="'Attach datasets/simulation outputs to compare'"
		/>
		<Button v-if="hasAtLeastTwoInputs" label="Open" @click="emit('open-drilldown')" severity="secondary" outlined />
	</section>
</template>

<script setup lang="ts">
import { computed, ref, toRef, watch, onMounted } from 'vue';
import Button from 'primevue/button';

import { Dataset, InterventionPolicy, ModelConfiguration } from '@/types/Types';
import { DataArray } from '@/services/models/simulation-service';
import { type WorkflowNode, WorkflowPortStatus } from '@/types/workflow';

import { useCharts, type ChartData } from '@/composables/useCharts';
import { useChartSettings } from '@/composables/useChartSettings';
import TeraNodePreview from '../tera-node-preview.vue';

import { CompareDatasetsState } from './compare-datasets-operation';

import { initialize } from './compare-datasets-utils';

const props = defineProps<{
	node: WorkflowNode<CompareDatasetsState>;
}>();

const emit = defineEmits(['append-input-port', 'open-drilldown']);

const hasAtLeastTwoInputs = computed(() => props.node.inputs.filter((input) => input.value).length >= 2);

const { selectedVariableSettings } = useChartSettings(props, emit);
const isFetchingDatasets = ref(false);
const datasets = ref<Dataset[]>([]);
const datasetResults = ref<{
	results: DataArray[];
	summaryResults: DataArray[];
	datasetResults: DataArray[];
} | null>(null);
const modelConfigurations = ref<ModelConfiguration[]>([]);
const interventionPolicies = ref<InterventionPolicy[]>([]);
const modelConfigIdToInterventionPolicyIdMap = ref<Record<string, string[]>>({});
const chartData = ref<ChartData | null>(null);
const rankingResultsChart = ref<any>(null);
const rankingCriteriaCharts = ref<any>([]);

const selectedPlotType = computed(() => props.node.state.selectedPlotType);
const baselineDatasetIndex = computed(() =>
	datasets.value.findIndex((dataset) => dataset.id === props.node.state.selectedBaselineDatasetId)
);

const { useCompareDatasetCharts } = useCharts(
	props.node.id,
	null,
	null,
	chartData,
	toRef({ width: 180, height: 120 }),
	null,
	null
);
onMounted(() => {
	initialize(
		props.node,
		null,
		isFetchingDatasets,
		datasets,
		datasetResults,
		modelConfigIdToInterventionPolicyIdMap,
		chartData,
		null,
		baselineDatasetIndex,
		selectedPlotType,
		modelConfigurations,
		interventionPolicies,
		rankingCriteriaCharts,
		rankingResultsChart
	);
});
const comparisonCharts = useCompareDatasetCharts(selectedVariableSettings, selectedPlotType, baselineDatasetIndex);

watch(
	() => props.node.inputs,
	() => {
		if (props.node.inputs.every((input) => input.status === WorkflowPortStatus.CONNECTED)) {
			emit('append-input-port', { type: 'datasetId', label: 'Dataset or Simulation result' });
			initialize(
				props.node,
				null,
				isFetchingDatasets,
				datasets,
				datasetResults,
				modelConfigIdToInterventionPolicyIdMap,
				chartData,
				null,
				baselineDatasetIndex,
				selectedPlotType,
				modelConfigurations,
				interventionPolicies,
				rankingCriteriaCharts,
				rankingResultsChart
			);
		}
	},
	{ deep: true }
);
</script>
