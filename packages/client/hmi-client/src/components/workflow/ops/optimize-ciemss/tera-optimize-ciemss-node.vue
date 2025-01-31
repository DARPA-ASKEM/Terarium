<template>
	<main>
		<tera-operator-placeholder v-if="!showSpinner && !runResults[node.state.postForecastRunId]" :node="node">
			<template v-if="!node.inputs[0].value"> Attach a model configuration </template>
		</tera-operator-placeholder>
		<template v-if="node.inputs[0].value">
			<div v-if="showSpinner">
				<div v-if="node.state.inProgressOptimizeId !== ''">
					{{ props.node.state.currentProgress }}% of maximum iterations complete
				</div>
				<div v-else>Optimize complete. Running simulations</div>
				<tera-progress-spinner :font-size="2" is-centered style="height: 100%" />
			</div>

			<div v-if="!showSpinner && runResults">
				<vega-chart
					v-for="setting of selectedVariableSettings"
					:key="setting.id"
					:visualization-spec="variableCharts[setting.id]"
					:are-embed-actions-visible="false"
				/>
			</div>
			<div class="flex gap-2">
				<Button @click="emit('open-drilldown')" label="Open" severity="secondary" outlined class="w-full" />
			</div>
		</template>
	</main>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { computed, watch, ref, onUnmounted, toRef } from 'vue';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { WorkflowNode } from '@/types/workflow';
import Button from 'primevue/button';
import { Poller, PollerResult, PollerState } from '@/api/api';
import { pollAction, makeForecastJobCiemss, getRunResult, getRunResultCSV } from '@/services/models/simulation-service';
import { nodeMetadata, nodeOutputLabel } from '@/components/workflow/util';
import {
	SimulationRequest,
	InterventionPolicy,
	Simulation,
	CiemssOptimizeStatusUpdate,
	ModelConfiguration,
	Model
} from '@/types/Types';
import { createLLMSummary } from '@/services/summary-service';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { renameFnGenerator } from '@/components/workflow/ops/calibrate-ciemss/calibrate-utils';
import { getModelByModelConfigurationId, getUnitsFromModelParts } from '@/services/model';
import { getModelConfigurationById } from '@/services/model-configurations';
import { createDatasetFromSimulationResult } from '@/services/dataset';
import { useProjects } from '@/composables/project';
import { useChartSettings } from '@/composables/useChartSettings';
import { useCharts } from '@/composables/useCharts';
import {
	OptimizeCiemssOperationState,
	OptimizeCiemssOperation,
	createInterventionPolicyFromOptimize
} from './optimize-ciemss-operation';
import { usePreparedChartInputs } from './optimize-utils';

const emit = defineEmits(['open-drilldown', 'append-output', 'update-state']);

const props = defineProps<{
	node: WorkflowNode<OptimizeCiemssOperationState>;
}>();

const runResults = ref<any>({});
const runResultsSummary = ref<any>({});
const modelConfigId = computed<string | undefined>(() => props.node.inputs[0]?.value?.[0]);
const modelConfiguration = ref<ModelConfiguration | null>(null);
const model = ref<Model | null>(null);

const modelVarUnits = ref<{ [key: string]: string }>({});

const preparedChartInputs = usePreparedChartInputs(props, runResults, runResultsSummary);
const { selectedVariableSettings } = useChartSettings(props, emit);

const { useVariableCharts } = useCharts(
	props.node.id,
	model,
	modelConfiguration,
	preparedChartInputs,
	toRef({ width: 180, height: 120 }),
	null,
	null
);
const variableCharts = useVariableCharts(selectedVariableSettings, null);

const showSpinner = computed<boolean>(
	() =>
		props.node.state.inProgressOptimizeId !== '' ||
		props.node.state.inProgressPostForecastId !== '' ||
		props.node.state.inProgressPreForecastId !== ''
);

const poller = new Poller();
const pollResult = async (runId: string) => {
	poller
		.setPollAction(async () => pollAction(runId))
		.setProgressAction((data: Simulation) => {
			if (runId === props.node.state.inProgressOptimizeId && data.updates.length > 0) {
				data.updates.sort((a, b) => a.data.progress - b.data.progress);
				const checkpointData = _.last(data.updates)?.data as CiemssOptimizeStatusUpdate;
				if (checkpointData) {
					const state = _.cloneDeep(props.node.state);
					const newProgress = Math.floor((100 * checkpointData.progress) / checkpointData.totalPossibleIterations);
					if (newProgress !== state.currentProgress) {
						state.currentProgress = newProgress;
						emit('update-state', state);
					}
				}
			}
		});

	const pollerResults = await poller.start();

	if (pollerResults.state === PollerState.Cancelled) {
		return pollerResults;
	}
	if (pollerResults.state !== PollerState.Done || !pollerResults.data) {
		console.error(`Optimize: ${runId} has failed`, pollerResults);
	}
	return pollerResults;
};

const startForecast = async (optimizedInterventions?: InterventionPolicy) => {
	const simulationPayload: SimulationRequest = {
		modelConfigId: modelConfigId.value as string,
		timespan: {
			start: 0,
			end: props.node.state.endTime
		},
		extra: {
			num_samples: props.node.state.numSamples,
			method: props.node.state.solverMethod,
			solver_step_size: props.node.state.solverStepSize
		},
		engine: 'ciemss'
	};

	if (optimizedInterventions) {
		// Use the intervention policy ID provided.
		simulationPayload.policyInterventionId = optimizedInterventions.id;
	} else {
		// Use the input interventions provided
		const inputIntervention = props.node.inputs[1].value?.[0];
		simulationPayload.policyInterventionId = inputIntervention;
	}

	if (modelConfiguration.value?.simulationId) {
		simulationPayload.extra.inferred_parameters = modelConfiguration.value.simulationId;
	}

	return makeForecastJobCiemss(simulationPayload, nodeMetadata(props.node));
};

watch(
	() => props.node.state.inProgressOptimizeId,
	async (optId) => {
		if (!optId || optId === '') return;

		const response = await pollResult(optId);
		if (response.state === PollerState.Done) {
			// Start 2nd simulation to get sample simulation from dill
			const newInterventionResponse = await createInterventionPolicyFromOptimize(modelConfigId.value as string, optId);
			if (newInterventionResponse) {
				const preForecastResponse = startForecast();
				const postForecastResponse = startForecast(newInterventionResponse);
				const forecastResults = await Promise.all([preForecastResponse, postForecastResponse]);
				const [{ id: preForecastId }, { id: postForecastId }] = forecastResults;

				const state = _.cloneDeep(props.node.state);
				state.inProgressOptimizeId = '';
				state.optimizationRunId = optId;
				state.inProgressPreForecastId = preForecastId;
				state.inProgressPostForecastId = postForecastId;
				state.optimizedInterventionPolicyId = newInterventionResponse.id ?? '';
				emit('update-state', state);
			} else {
				// Failed to create intervention policy
				const state = _.cloneDeep(props.node.state);
				state.inProgressOptimizeId = '';
				state.optimizationRunId = '';
				state.inProgressPreForecastId = '';
				state.inProgressPostForecastId = '';
				state.optimizedInterventionPolicyId = '';
				state.optimizeErrorMessage = {
					name: optId,
					value: 'Failed to create intervention',
					traceback: 'Failed to create the intervention provided from optimize.'
				};
				emit('update-state', state);
			}
		} else {
			// Simulation Failed:
			const state = _.cloneDeep(props.node.state);
			if (response?.state && response?.error) {
				state.optimizeErrorMessage = {
					name: optId,
					value: response.state,
					traceback: response.error
				};
			}
			state.inProgressOptimizeId = '';
			state.inProgressPreForecastId = '';
			state.inProgressPostForecastId = '';
			emit('update-state', state);
		}
	},
	{ immediate: true }
);

watch(
	() => `${props.node.state.inProgressPreForecastId},${props.node.state.inProgressPostForecastId}`,
	async () => {
		const preSimId = props.node.state.inProgressPreForecastId;
		const postSimId = props.node.state.inProgressPostForecastId;
		if (!preSimId || preSimId === '' || !postSimId || postSimId === '') return;
		const responseList: Promise<PollerResult<any>>[] = [];
		responseList.push(pollResult(preSimId));
		responseList.push(pollResult(postSimId));
		const [preResponse, postResponse] = await Promise.all(responseList);
		if (preResponse.state === PollerState.Done && postResponse.state === PollerState.Done) {
			const state = _.cloneDeep(props.node.state);

			// Generate output summary, collect key facts and get agent to summarize
			const optimizationResult = await getRunResult(state.optimizationRunId, 'optimize_results.json');
			const prompt = `
The following are the key attributes and findings of an optimization process for a ODE epidemilogy model, the goal is to find the best values or time points that satisfy a set of constraints.

- The succss constraints are, in JSON: ${JSON.stringify(state.constraintGroups)}
- The best guesses are: ${optimizationResult.x}
- The result is ${optimizationResult.success}

Provide a consis summary in 100 words or less.
			`;
			const summaryResponse = await createLLMSummary(prompt);
			state.summaryId = summaryResponse?.id;

			state.inProgressPreForecastId = '';
			state.preForecastRunId = preSimId;
			state.inProgressPostForecastId = '';
			state.postForecastRunId = postSimId;
			state.currentProgress = 0;
			emit('update-state', state);

			const datasetName = `Forecast run ${state.postForecastRunId}`;
			const projectId = useProjects().activeProjectId.value;
			const datasetResult = await createDatasetFromSimulationResult(
				projectId,
				state.postForecastRunId,
				datasetName,
				false
			);
			if (!datasetResult) {
				return;
			}

			emit('append-output', {
				type: OptimizeCiemssOperation.outputs[0].type,
				label: nodeOutputLabel(props.node, `Optimize output`),
				value: [
					{
						policyInterventionId: state.optimizedInterventionPolicyId,
						datasetId: datasetResult.id
					}
				],
				isSelected: false,
				state: _.omit(state, ['chartSettings'])
			});
		} else {
			// Simulation Failed:
			const state = _.cloneDeep(props.node.state);
			if (preResponse.error) {
				state.simulateErrorMessage = {
					name: preSimId,
					value: preResponse.state,
					traceback: preResponse.error
				};
			}
			// Probably no need to capture both simulation error messages as theyre very similar simulation calls.
			else if (postResponse.error) {
				state.simulateErrorMessage = {
					name: postSimId,
					value: postResponse.state,
					traceback: postResponse.error
				};
			}
			state.inProgressOptimizeId = '';
			state.inProgressPreForecastId = '';
			state.inProgressPostForecastId = '';
			state.currentProgress = 0;
			emit('update-state', state);
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
		if (!state.postForecastRunId || !state.preForecastRunId) return;

		modelConfiguration.value = await getModelConfigurationById(modelConfigId.value as string);
		model.value = await getModelByModelConfigurationId(modelConfigId.value as string);
		if (model.value) {
			modelVarUnits.value = getUnitsFromModelParts(model.value);
		}

		const preForecastRunId = state.preForecastRunId;
		const postForecastRunId = state.postForecastRunId;

		const preResult = await getRunResultCSV(preForecastRunId, 'result.csv', renameFnGenerator('pre'));
		const postResult = await getRunResultCSV(postForecastRunId, 'result.csv');

		runResults.value[preForecastRunId] = preResult;
		runResults.value[postForecastRunId] = postResult;

		const preResultSummary = await getRunResultCSV(preForecastRunId, 'result_summary.csv', renameFnGenerator('pre'));
		const postResultSummary = await getRunResultCSV(postForecastRunId, 'result_summary.csv');

		runResultsSummary.value[preForecastRunId] = preResultSummary;
		runResultsSummary.value[postForecastRunId] = postResultSummary;
	},
	{ immediate: true }
);

onUnmounted(() => {
	poller.stop();
});
</script>

<style scoped></style>
