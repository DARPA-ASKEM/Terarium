<template>
	<section>
		<tera-operator-placeholder v-if="!inProgressId" :node="node">
			<template v-if="!node.inputs[0].value"> Attach a model configuration </template>
		</tera-operator-placeholder>

		<tera-progress-spinner v-if="inProgressId" is-centered :font-size="2" style="height: 100%">
			{{ node.state.currentProgress }}%
		</tera-progress-spinner>

		<section v-if="!_.isEmpty(message)">
			<p>{{ message }}</p>
		</section>

		<template v-if="node.inputs[0].value">
			<Button @click="emit('open-drilldown')" label="Open" severity="secondary" outlined />
		</template>
	</section>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { watch, computed, onUnmounted, ref } from 'vue';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { WorkflowNode } from '@/types/workflow';
import { FunmanOperationState, FunmanOperation } from '@/components/workflow/ops/funman/funman-operation';
import Button from 'primevue/button';
import { Poller, PollerState } from '@/api/api';
import { pollAction, getRunResult } from '@/services/models/simulation-service';
import { logger } from '@/utils/logger';
import { useToastService } from '@/services/toast';
import { Simulation } from '@/types/Types';
import { getModelConfigurationById } from '@/services/model-configurations';

const emit = defineEmits(['open-drilldown', 'append-output', 'update-state']);

const props = defineProps<{
	node: WorkflowNode<FunmanOperationState>;
}>();
const inProgressId = computed(() => props.node.state.inProgressId);
const message = ref('');
const timer = ref();
const poller = new Poller();

const TIME_LIMIT = 5 * 60 * 1000;

const addOutputPorts = async (runId: string) => {
	// The validated configuration id is set as the output value
	const rawFunmanResult = await getRunResult(runId, 'validation.json');
	if (!rawFunmanResult) {
		logger.error('Failed to fetch funman result');
		return;
	}
	const validatedConfiguration = await getModelConfigurationById(JSON.parse(rawFunmanResult).modelConfigurationId);

	const outState = _.cloneDeep(props.node.state);
	outState.inProgressId = '';
	outState.runId = runId;

	emit('append-output', {
		label: validatedConfiguration.name,
		type: FunmanOperation.outputs[0].type,
		value: [validatedConfiguration.id],
		state: outState
	});
};

function StartRequestTimer(state) {
	clearTimeout(timer.value);
	timer.value = setTimeout(() => {
		state.isRequestUnresponsive = true;
		message.value = "Process is stuck in Funman, open node and click 'Stop' to cancel.";
		emit('update-state', state);
		clearTimeout(timer.value);
	}, TIME_LIMIT);
}

const getStatus = async (runId: string) => {
	poller
		.setInterval(6000)
		.setThreshold(200)
		.setPollAction(async () => pollAction(runId))
		.setProgressAction((data: Simulation) => {
			if (data.progress) {
				const state = _.cloneDeep(props.node.state);
				const newProgress = +(100 * data.progress).toFixed(2);
				if (newProgress !== state.currentProgress) {
					state.isRequestUnresponsive = false;
					StartRequestTimer(state);

					state.currentProgress = newProgress;
					emit('update-state', state);
				}
			}
		});

	const pollerResults = await poller.start();

	if (pollerResults.state === PollerState.Cancelled) {
		return pollerResults;
	}
	if (pollerResults.state !== PollerState.Done || !pollerResults.data) {
		console.error(`Funman: ${runId} has failed with state=${pollerResults.state}`, pollerResults);

		if (pollerResults.state === PollerState.ExceedThreshold) {
			useToastService().error('Funman polling exceeded', 'Refresh page if you want to resume polling', 8000);
		}
	}
	return pollerResults;
};

onUnmounted(() => {
	poller.stop();
	clearTimeout(timer.value);
});

watch(
	() => props.node.state.inProgressId,
	async (id) => {
		if (!id || id === '') return;
		const state = _.cloneDeep(props.node.state);
		StartRequestTimer(state);
		const response = await getStatus(id);
		if (response.state === PollerState.Done) {
			addOutputPorts(id);
			state.inProgressId = '';
			message.value = '';
			state.isRequestUnresponsive = false;
			state.currentProgress = 0;
			emit('update-state', state);
			clearTimeout(timer.value);
		}
	},
	{ immediate: true }
);
</script>
