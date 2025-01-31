<template>
	<section class="gap-0">
		<ul v-if="node.state.interventionPolicy.id">
			<li v-for="(_interventions, appliedTo) in selectedOutputParameters" :key="appliedTo">
				<vega-chart expandable :are-embed-actions-visible="false" :visualization-spec="preparedCharts[appliedTo]" />
			</li>
		</ul>
		<div v-if="displayedInterventions.length > 0" class="intervention-summary">
			<div v-for="(intervention, index) in displayedInterventions" :key="index">
				<p class="name">{{ intervention.name }}</p>
				<div>
					Starting at time step
					<span class="semi-bold">{{ intervention.staticInterventions[0].timestep }},</span>
				</div>
				<div class="body" v-if="intervention.staticInterventions.length > 0">
					<p
						v-for="staticIntervention in intervention.staticInterventions"
						:key="staticIntervention.type + staticIntervention.appliedTo"
					>
						Set {{ staticIntervention.type }} <span class="semi-bold">{{ staticIntervention.appliedTo }}</span> to
						<span class="semi-bold">{{ staticIntervention.value }}</span>
					</p>
				</div>
				<div class="body" v-if="intervention.dynamicInterventions.length > 0">
					<p
						v-for="dynamicIntervention in intervention.dynamicInterventions"
						:key="dynamicIntervention.type + dynamicIntervention.appliedTo"
					>
						Set {{ dynamicIntervention.type }} <span class="semi-bold">{{ dynamicIntervention.appliedTo }}</span> to
						<span class="semi-bold">{{ dynamicIntervention.value }}</span> when
						<span class="semi-bold">{{ dynamicIntervention.parameter }}</span> crosses threshold
						<span class="semi-bold">{{ dynamicIntervention.threshold }}</span>
					</p>
				</div>
			</div>
			<p v-if="remainingInterventionsCount > 0" class="text-gray-600 mt-2">
				+ {{ remainingInterventionsCount }} more interventions...
			</p>
		</div>
		<tera-operator-placeholder v-else :node="node">
			<template v-if="!node.inputs[0].value"> Attach a model</template>
		</tera-operator-placeholder>
		<tera-progress-spinner is-centered :font-size="2" v-if="isLoading" />
		<Button
			:label="isModelInputConnected ? 'Open' : 'Attach a model'"
			@click="emit('open-drilldown')"
			severity="secondary"
			outlined
			:disabled="!isModelInputConnected"
			class="mt-2"
		/>
	</section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { WorkflowNode, WorkflowPortStatus } from '@/types/workflow';
import Button from 'primevue/button';
import _, { cloneDeep, groupBy } from 'lodash';
import { blankIntervention, flattenInterventionData } from '@/services/intervention-policy';
import { createInterventionChart } from '@/services/charts';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { useClientEvent } from '@/composables/useClientEvent';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import { type ClientEvent, ClientEventType, type TaskResponse, TaskStatus } from '@/types/Types';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { InterventionPolicyState } from './intervention-policy-operation';

const emit = defineEmits(['open-drilldown', 'update-state']);
const props = defineProps<{
	node: WorkflowNode<InterventionPolicyState>;
}>();

const taskIds = ref<string[]>([]);

const interventionEventHandler = async (event: ClientEvent<TaskResponse>) => {
	if (!taskIds.value.includes(event.data?.id)) return;
	if ([TaskStatus.Success, TaskStatus.Cancelled, TaskStatus.Failed].includes(event.data.status)) {
		taskIds.value = taskIds.value.filter((id) => id !== event.data.id);
	}
};

useClientEvent(ClientEventType.TaskGollmInterventionsFromDocument, interventionEventHandler);
useClientEvent(ClientEventType.TaskGollmInterventionsFromDataset, interventionEventHandler);

const isLoading = computed(() => taskIds.value.length > 0);
const isModelInputConnected = ref(false);

const groupedOutputParameters = computed(() =>
	Object.fromEntries(
		Object.entries(groupBy(flattenInterventionData(props.node.state.interventionPolicy.interventions), 'appliedTo'))
	)
);

const interventionSummary = computed(() => {
	const interventions = cloneDeep(props.node.state.interventionPolicy.interventions);
	return interventions;
});

const selectedOutputParameters = computed(() => {
	const charts = {};
	props.node.state.selectedCharts?.forEach((chart) => {
		const paramOutput = groupedOutputParameters.value[chart];
		if (paramOutput) charts[chart] = paramOutput;
	});
	return charts;
});

const preparedCharts = computed(() =>
	_.mapValues(groupedOutputParameters.value, (interventions, key) =>
		createInterventionChart(interventions, {
			title: key,
			width: 180,
			height: 120,
			xAxisTitle: 'Time',
			yAxisTitle: 'Value',
			hideLabels: false
		})
	)
);

watch(
	() => props.node.inputs,
	(inputs) => {
		const modelId = inputs.find((input) => input.type === 'modelId')?.value?.[0];
		const state = cloneDeep(props.node.state);

		const modelInputs = inputs.filter((input) => input.type === 'modelId');
		if (modelInputs[0].status === WorkflowPortStatus.CONNECTED) {
			isModelInputConnected.value = true;
		}

		if (!modelId || modelId === state.interventionPolicy?.modelId) return;

		// Reset previous model cache
		state.interventionPolicy = {
			modelId,
			interventions: [blankIntervention]
		};
		emit('update-state', state);
	},
	{ immediate: true, deep: true }
);

watch(
	() => props.node.state.taskIds,
	() => {
		taskIds.value = props.node.state.taskIds ?? [];
	}
);

watch(
	() => isLoading.value,
	() => {
		if (!isLoading.value) {
			const state = cloneDeep(props.node.state);
			state.taskIds = [];
			emit('update-state', state);
		}
	}
);

/* only show the first 10 interventions */
const MAX_DISPLAYED_INTERVENTIONS = 5;

const displayedInterventions = computed(() => interventionSummary.value.slice(0, MAX_DISPLAYED_INTERVENTIONS));

const remainingInterventionsCount = computed(() =>
	Math.max(0, interventionSummary.value.length - MAX_DISPLAYED_INTERVENTIONS)
);
</script>

<style scoped>
.intervention-summary {
	font-size: var(--font-caption);
	border: 1px solid var(--surface-border-light);
	border-left: 4px solid var(--primary-color);
	border-radius: var(--border-radius);
	padding: var(--gap-3);
	margin-bottom: var(--gap-1);
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
	& .name {
		font-weight: 600;
	}
	& .body {
		padding-bottom: var(--gap-1);
	}
}
ul {
	list-style-type: none;
}
.intervention-title {
	& > :deep(h5) {
		padding-top: 15px;
	}
}
.semi-bold {
	font-weight: 600;
}
</style>
