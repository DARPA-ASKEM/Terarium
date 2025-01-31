<template>
	<main>
		<vega-chart
			v-if="(inProgressCalibrationId || inProgressForecastId) && !_.isEmpty(lossValues)"
			:are-embed-actions-visible="false"
			:visualization-spec="lossChartSpec"
		/>
		<div v-if="outputData">
			<vega-chart v-for="(chart, index) of ensembleVariableCharts" :key="index" :visualization-spec="chart" />
		</div>
		<tera-progress-spinner
			v-if="inProgressCalibrationId || inProgressForecastId"
			:font-size="2"
			is-centered
			style="height: 100%"
		>
			{{ node.state.currentProgress }}%
		</tera-progress-spinner>

		<Button v-if="areInputsFilled" label="Open" @click="emit('open-drilldown')" severity="secondary" outlined />
		<tera-operator-placeholder v-else :node="node">
			Connect at least two model configurations and dataset
		</tera-operator-placeholder>
	</main>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch, onMounted, toRef } from 'vue';
import _ from 'lodash';
import Button from 'primevue/button';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import {
	pollAction,
	getCalibrateBlobURL,
	makeEnsembleCiemssSimulation,
	getSimulation,
	DataArray
} from '@/services/models/simulation-service';
import { setupCsvAsset } from '@/services/calibrate-workflow';
import { nodeMetadata, nodeOutputLabel } from '@/components/workflow/util';
import { logger } from '@/utils/logger';
import { Poller, PollerState } from '@/api/api';
import type { WorkflowNode } from '@/types/workflow';
import { WorkflowPortStatus } from '@/types/workflow';
import type { CsvAsset, EnsembleSimulationCiemssRequest, Dataset, Simulation, ModelConfiguration } from '@/types/Types';
import { createDatasetFromSimulationResult, getDataset } from '@/services/dataset';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { useProjects } from '@/composables/project';
import { useChartSettings } from '@/composables/useChartSettings';
import { useCharts } from '@/composables/useCharts';
import { GroupedDataArray } from '@/services/charts';
import { parseCsvAsset } from '@/utils/csv';
import {
	CalibrateEnsembleCiemssOperation,
	CalibrateEnsembleCiemssOperationState
} from './calibrate-ensemble-ciemss-operation';
import {
	updateLossChartSpec,
	getLossValuesFromSimulation,
	formatCalibrateModelConfigurations,
	getChartEnsembleMapping,
	buildChartData,
	fetchModelConfigurations,
	fetchOutputData
} from './calibrate-ensemble-util';

const props = defineProps<{
	node: WorkflowNode<CalibrateEnsembleCiemssOperationState>;
}>();
const emit = defineEmits(['open-drilldown', 'update-state', 'append-output', 'append-input-port']);

const csvAsset = shallowRef<CsvAsset | undefined>(undefined);

const allModelConfigurations = ref<ModelConfiguration[]>([]);

const areInputsFilled = computed(
	() =>
		props.node.inputs[0].value &&
		props.node.inputs.filter((ele) => ele.type === 'modelConfigId' && ele.value).length >= 2
);
const inProgressCalibrationId = computed(() => props.node.state.inProgressCalibrationId);
const inProgressForecastId = computed(() => props.node.state.inProgressForecastId);
const lossValues = ref<{ [key: string]: number }[]>([]);
const lossChartSpec = ref();
const chartSize = { width: 180, height: 120 };

// Charts setup
const outputData = ref<{
	result: DataArray;
	resultSummary: DataArray;
	pyciemssMap: Record<string, string>;
	resultGroupByTimepoint: GroupedDataArray;
} | null>(null);
const groundTruthData = computed<DataArray>(() => parseCsvAsset(csvAsset.value as CsvAsset));
const selectedOutputMapping = computed(() => getChartEnsembleMapping(props.node, {}));
const { selectedEnsembleVariableSettings } = useChartSettings(props, emit);
const { useEnsembleVariableCharts } = useCharts(
	props.node.id,
	null,
	allModelConfigurations,
	computed(() => buildChartData(outputData.value, selectedOutputMapping.value)),
	toRef(chartSize),
	null,
	selectedOutputMapping
);
const ensembleVariableCharts = computed(() => {
	const charts = useEnsembleVariableCharts(selectedEnsembleVariableSettings, groundTruthData);
	const ensembleCharts = selectedEnsembleVariableSettings.value.map((setting) => {
		// Grab the first chart only since the rest of the charts are for model configurations charts
		const spec = charts.value[setting.id][0];
		// Make sure the chart since width of the chart can be too small if charts were small multiple charts.
		spec.width = chartSize.width;
		spec.height = chartSize.height + 100;
		return spec;
	});
	return ensembleCharts;
});
// ----------------------------

const poller = new Poller();
const pollResult = async (runId: string) => {
	poller
		.setPollAction(async () => pollAction(runId))
		.setProgressAction((data: Simulation) => {
			if (data?.updates?.length) {
				lossValues.value = data?.updates
					.sort((a, b) => a.data.progress - b.data.progress)
					.map((d, i) => ({
						iter: i,
						loss: d.data.loss
					}));
				lossChartSpec.value = updateLossChartSpec(lossValues.value, chartSize);
			}
			if (runId === props.node.state.inProgressCalibrationId && data.updates.length > 0) {
				const checkpoint = _.last(data.updates);
				if (checkpoint) {
					const state = _.cloneDeep(props.node.state);
					const newProgress = +((100 * checkpoint.data.progress) / state.extra.numIterations).toFixed(2);
					if (newProgress !== state.currentProgress) {
						state.currentProgress = newProgress;
						emit('update-state', state);
					}
				}
			}
		});
	const pollerResults = await poller.start();
	const state = _.cloneDeep(props.node.state);
	state.errorMessage = { name: '', value: '', traceback: '' };

	if (pollerResults.state === PollerState.Cancelled) {
		state.currentProgress = 0;
		state.inProgressForecastId = '';
		state.inProgressCalibrationId = '';
		poller.stop();
	} else if (pollerResults.state !== PollerState.Done || !pollerResults.data) {
		// throw if there are any failed runs for now
		logger.error(`Calibration: ${runId} has failed`, {
			toastTitle: 'Error - Pyciemss'
		});
		const simulation = await getSimulation(runId);
		if (simulation?.status && simulation?.statusMessage) {
			state.inProgressCalibrationId = '';
			state.errorMessage = {
				name: runId,
				value: simulation.status,
				traceback: simulation.statusMessage
			};
		}
		state.currentProgress = 0;
		state.inProgressForecastId = '';
		state.inProgressCalibrationId = '';
		emit('update-state', state);
		throw Error('Failed Runs');
	}
	emit('update-state', state);
	return pollerResults;
};

// Init loss chart
onMounted(async () => {
	lossValues.value = await getLossValuesFromSimulation(props.node.state.calibrationId);
	lossChartSpec.value = await updateLossChartSpec(lossValues.value, chartSize);
});

// Fetch model configurations
onMounted(async () => {
	const configs = await fetchModelConfigurations(props.node.inputs);
	if (!configs) return;
	allModelConfigurations.value = configs.allModelConfigurations;
});

watch(
	() => props.node.state.inProgressCalibrationId,
	async (id) => {
		if (!id || id === '') return;
		const response = await pollResult(id);
		if (response.state === PollerState.Done) {
			// Start 2nd simulation to get sample simulation from dill
			const dillURL = await getCalibrateBlobURL(id);
			console.log('dill URL is', dillURL);

			const state = _.cloneDeep(props.node.state);
			const baseRequestPayload: EnsembleSimulationCiemssRequest = {
				modelConfigs: formatCalibrateModelConfigurations(
					props.node.state.ensembleMapping,
					props.node.state.configurationWeights
				),
				timespan: {
					// Should probably grab this from csvasset
					start: 0,
					end: state.extra.endTime
				},
				engine: 'ciemss',
				extra: {
					num_samples: props.node.state.extra.numIterations
				}
			};

			// Default (Pre)
			let forecastResponse = await makeEnsembleCiemssSimulation(baseRequestPayload, nodeMetadata(props.node));
			state.inProgressPreForecastId = forecastResponse.simulationId;

			// With calibrated result
			baseRequestPayload.extra.inferred_parameters = id;
			forecastResponse = await makeEnsembleCiemssSimulation(baseRequestPayload, nodeMetadata(props.node));
			state.inProgressForecastId = forecastResponse.simulationId;

			state.inProgressCalibrationId = '';
			state.calibrationId = id;
			emit('update-state', state);
		}
	},
	{ immediate: true }
);

watch(
	() => props.node.state.inProgressForecastId + props.node.state.inProgressPreForecastId,
	async (id) => {
		if (!id || id === '') return;

		let doneProcess = true;
		let response = await pollResult(props.node.state.inProgressPreForecastId);
		if (response.state !== PollerState.Done) {
			doneProcess = false;
		}

		response = await pollResult(props.node.state.inProgressForecastId);
		if (response.state !== PollerState.Done) {
			doneProcess = false;
		}

		if (doneProcess) {
			const state = _.cloneDeep(props.node.state);
			state.postForecastId = state.inProgressForecastId;
			state.preForecastId = state.inProgressPreForecastId;

			state.inProgressForecastId = '';
			state.inProgressPreForecastId = '';
			emit('update-state', state);

			const datasetName = `Forecast run ${state.postForecastId}`;
			const projectId = useProjects().activeProjectId.value;
			const datasetResult = await createDatasetFromSimulationResult(
				projectId,
				state.postForecastId,
				datasetName,
				false
			);
			if (!datasetResult) {
				return;
			}

			emit('append-output', {
				type: CalibrateEnsembleCiemssOperation.outputs[0].type,
				label: nodeOutputLabel(props.node, `Calibration Result`),
				value: datasetResult.id,
				state: _.omit(state, ['chartSettings'])
			});
		}
	},
	{ immediate: true }
);

watch(
	() => props.node.active,
	async () => {
		const active = props.node.active;
		const state = props.node.state;
		if (!active) return;
		if (!state.postForecastId) return;
		// Fetch output data and prepare chart data
		outputData.value = await fetchOutputData(state.preForecastId, state.postForecastId);

		// Dataset used to calibrate
		const datasetId = props.node.inputs[0]?.value?.[0];
		// Get dataset:
		const dataset: Dataset | null = await getDataset(datasetId);
		if (dataset) {
			setupCsvAsset(dataset).then((csv) => {
				csvAsset.value = csv;
			});
		}
	},
	{ immediate: true }
);

watch(
	() => props.node.inputs,
	() => {
		if (
			props.node.inputs.every(
				(input) => input.status === WorkflowPortStatus.CONNECTED || input.type !== 'modelConfigId'
			)
		) {
			emit('append-input-port', { type: 'modelConfigId', label: 'Model configuration' });
		}
	},
	{ deep: true }
);
</script>

<style scoped></style>
