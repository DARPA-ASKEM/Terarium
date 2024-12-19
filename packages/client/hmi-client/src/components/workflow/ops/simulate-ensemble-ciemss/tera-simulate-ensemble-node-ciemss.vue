<template>
	<Button v-if="node.inputs[0].value" label="Edit" @click="emit('open-drilldown')" severity="secondary" outlined />
	<tera-operator-placeholder v-else :node="node">Connect a model configuration</tera-operator-placeholder>
	<tera-progress-spinner v-if="inProgressForecastId" :font-size="2" is-centered style="height: 100%" />
</template>

<script setup lang="ts">
import _ from 'lodash';
import { ref, computed, watch } from 'vue';
import { getRunResultCiemss, pollAction, getSimulation } from '@/services/models/simulation-service';
import Button from 'primevue/button';
import { WorkflowNode, WorkflowPortStatus } from '@/types/workflow';
import { RunResults } from '@/types/SimulateConfig';
import { Poller, PollerState } from '@/api/api';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { logger } from '@/utils/logger';
import { nodeOutputLabel } from '@/components/workflow/util';
import { useProjects } from '@/composables/project';
import { createDatasetFromSimulationResult } from '@/services/dataset';
import {
	SimulateEnsembleCiemssOperation,
	SimulateEnsembleCiemssOperationState
} from './simulate-ensemble-ciemss-operation';

const props = defineProps<{
	node: WorkflowNode<SimulateEnsembleCiemssOperationState>;
}>();
const emit = defineEmits(['append-output', 'update-state', 'open-drilldown', 'append-input-port']);

// const runResults = ref<RunResults>({});
const runResults = ref<{ [runId: string]: RunResults }>({});
const inProgressForecastId = computed(() => props.node.state.inProgressForecastId);
const selectedRunId = ref<string>('');

const poller = new Poller();

const getStatus = async (simulationId: string) => {
	poller
		.setInterval(3000)
		.setThreshold(300)
		.setPollAction(async () => pollAction(simulationId));
	const pollerResults = await poller.start();
	let state = _.cloneDeep(props.node.state);
	state.errorMessage = { name: '', value: '', traceback: '' };

	if (pollerResults.state === PollerState.Cancelled) {
		state.inProgressForecastId = '';
		poller.stop();
	} else if (pollerResults.state !== PollerState.Done || !pollerResults.data) {
		logger.error(`Simulation: ${simulationId} has failed`, {
			toastTitle: 'Error - Pyciemss'
		});
		const simulation = await getSimulation(simulationId);
		if (simulation?.status && simulation?.statusMessage) {
			state = _.cloneDeep(props.node.state);
			state.inProgressForecastId = '';
			state.errorMessage = {
				name: simulationId,
				value: simulation.status,
				traceback: simulation.statusMessage
			};
			emit('update-state', state);
		}
		throw Error('Failed Runs');
	}

	emit('update-state', state);
	return pollerResults;
};

const processResult = async (simulationId: string) => {
	const portLabel = props.node.inputs[0].label;
	const state = _.cloneDeep(props.node.state);

	const datasetName = `Forecast run ${simulationId}`;
	const projectId = useProjects().activeProjectId.value;
	const datasetResult = await createDatasetFromSimulationResult(projectId, simulationId, datasetName, false);
	if (!datasetResult) {
		return;
	}

	emit('append-output', {
		type: SimulateEnsembleCiemssOperation.outputs[0].type,
		label: nodeOutputLabel(props.node, `${portLabel} Result`),
		value: [datasetResult.id],
		state,
		isSelected: false
	});
};

watch(
	() => props.node.inputs,
	() => {
		if (props.node.inputs.every((input) => input.status === WorkflowPortStatus.CONNECTED)) {
			emit('append-input-port', { type: 'modelConfigId', label: 'Model configuration' });
		}
	},
	{ deep: true }
);

watch(
	() => props.node.state.inProgressForecastId,
	async (id) => {
		if (!id || id === '') return;

		const response = await getStatus(id);
		if (response?.state === PollerState.Done) {
			await processResult(id);
		}
		const state = _.cloneDeep(props.node.state);
		state.inProgressForecastId = '';
		state.forecastId = id;
		emit('update-state', state);
	},
	{ immediate: true }
);

watch(
	() => props.node.active,
	async () => {
		const active = props.node.active;
		if (!active) return;

		selectedRunId.value = props.node.outputs.find((o) => o.id === active)?.value?.[0];
		if (!selectedRunId.value) return;
		const forecastId = props.node.state.forecastId;
		if (!forecastId) return;

		const output = await getRunResultCiemss(forecastId, 'result.csv');
		runResults.value[selectedRunId.value] = output.runResults;
	},
	{ immediate: true }
);
</script>

<style scoped>
section {
	display: flex;
	flex-direction: column;
	width: 100%;
	padding: 10px;
	background: var(--surface-overlay);
}

.simulate-chart {
	margin: 1em 0em;
}
</style>
