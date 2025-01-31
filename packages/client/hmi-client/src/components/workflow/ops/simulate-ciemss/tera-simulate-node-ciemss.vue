<template>
	<main>
		<section>
			<tera-node-preview
				:node="node"
				:is-loading="!!inProgressForecastRun"
				:prepared-charts="Object.assign({}, interventionCharts, variableCharts, comparisonCharts)"
				:chart-settings="[
					...selectedInterventionSettings,
					...selectedVariableSettings,
					...selectedComparisonChartSettings
				]"
				:are-embed-actions-visible="true"
				:placeholder="placeholderText"
			/>
		</section>
		<Button v-if="areInputsFilled" label="Open" @click="emit('open-drilldown')" severity="secondary" outlined />
	</main>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { computed, ref, toRef, watch } from 'vue';
import Button from 'primevue/button';
import { logger } from '@/utils/logger';
import { updateChartSettingsBySelectedVariables, updateSensitivityChartSettingOption } from '@/services/chart-settings';
import { createDatasetFromSimulationResult } from '@/services/dataset';
import { flattenInterventionData, getInterventionPolicyById } from '@/services/intervention-policy';
import { getModelByModelConfigurationId, getTypesFromModelParts, getUnitsFromModelParts } from '@/services/model';
import { getModelConfigurationById } from '@/services/model-configurations';
import {
	getRunResultCSV,
	parsePyCiemssMap,
	pollAction,
	DataArray,
	getSimulation
} from '@/services/models/simulation-service';
import { createLLMSummary } from '@/services/summary-service';

import { nodeOutputLabel } from '@/components/workflow/util';

import { ModelConfiguration, type InterventionPolicy, type Model } from '@/types/Types';
import { ChartSettingSensitivity, ChartSettingType } from '@/types/common';
import type { WorkflowNode } from '@/types/workflow';

import { useChartSettings } from '@/composables/useChartSettings';
import { useCharts } from '@/composables/useCharts';
import { useProjects } from '@/composables/project';

import { Poller, PollerState } from '@/api/api';
import TeraNodePreview from '../tera-node-preview.vue';
import { SimulateCiemssOperationState, SimulateCiemssOperation } from './simulate-ciemss-operation';
import { mergeResults, renameFnGenerator } from '../calibrate-ciemss/calibrate-utils';
import { usePreparedChartInputs } from './simulate-utils';

const props = defineProps<{
	node: WorkflowNode<SimulateCiemssOperationState>;
}>();

const emit = defineEmits(['open-drilldown', 'update-state', 'append-output']);
const model = ref<Model | null>(null);
const modelConfiguration = ref<ModelConfiguration | null>(null);
const modelVarUnits = ref<{ [key: string]: string }>({});
const modelPartTypesMap = computed(() => (!model.value ? {} : getTypesFromModelParts(model.value)));

const runResults = ref<{ [runId: string]: DataArray }>({});
const runResultsSummary = ref<{ [runId: string]: DataArray }>({});

const selectedRunId = ref<string>();
const inProgressForecastId = computed(() => props.node.state.inProgressForecastId);
const inProgressBaseForecastId = computed(() => props.node.state.inProgressBaseForecastId);
const inProgressForecastRun = computed(() => inProgressForecastId.value || inProgressBaseForecastId.value);
const areInputsFilled = computed(() => props.node.inputs[0].value);
const interventionPolicyId = computed(() => props.node.inputs[1].value?.[0]);
const interventionPolicy = ref<InterventionPolicy | null>(null);

const pyciemssMap = ref<Record<string, string>>({});

const processResult = async (runId: string) => {
	const result = await getRunResultCSV(runId, 'result.csv');
	pyciemssMap.value = parsePyCiemssMap(result[0]);
	const state = _.cloneDeep(props.node.state);
	if (_.isEmpty(state.chartSettings)) {
		state.chartSettings = updateChartSettingsBySelectedVariables(
			state.chartSettings ?? [],
			ChartSettingType.INTERVENTION,
			Object.keys(groupedInterventionOutputs.value)
		);
		state.chartSettings = updateChartSettingsBySelectedVariables(
			state.chartSettings,
			ChartSettingType.VARIABLE,
			Object.keys(pyciemssMap)
				.filter((c) => ['state', 'observable'].includes(modelPartTypesMap.value[c]))
				.slice(0, 5) // Limit the number of initial variables to first 5 to prevent too many charts
		);
	}

	const summaryData = await getRunResultCSV(runId, 'result_summary.csv');

	if (
		(state.chartSettings as ChartSettingSensitivity[]).some((setting) => setting.type === ChartSettingType.SENSITIVITY)
	) {
		// If sensitivity chart settings are present, set the timepoint to the last timepoint
		const selectedSensitivityVariables = state
			.chartSettings!.filter((setting) => setting.type === ChartSettingType.SENSITIVITY)
			.flatMap((setting) => setting.selectedVariables);
		const lastTimepoint = _.last(result)?.timepoint_id;
		const firstSensitiveSetting = (state.chartSettings as ChartSettingSensitivity[]).find(
			(setting) => setting.type === ChartSettingType.SENSITIVITY
		);
		state.chartSettings = updateSensitivityChartSettingOption(state.chartSettings as ChartSettingSensitivity[], {
			selectedVariables: selectedSensitivityVariables,
			selectedInputVariables: firstSensitiveSetting!.selectedInputVariables,
			timepoint: lastTimepoint
		});
	}

	// FIXME: This emit might be getting overridden by the one in the poller
	emit('update-state', state);
	const start = _.first(summaryData);
	const end = _.last(summaryData);

	const prompt = `
The following are the key attributes of a simulation/forecasting process for a ODE epidemilogy model.

The input parameters are as follows:
- samples: ${state.numSamples}
- method: ${state.method}
- timespan: ${JSON.stringify(state.currentTimespan)}
- interventions: ${JSON.stringify(interventionPolicy.value?.interventions)};


The output has these metrics at the start:
- ${JSON.stringify(start)}

The output has these metrics at the end:
- ${JSON.stringify(end)}

Provide a summary in 100 words or less.
	`;

	const summaryResponse = await createLLMSummary(prompt);

	// generate label like "Configuration (intervention)"
	const nodeLabel = (): string => {
		const configName = modelConfiguration.value?.name;
		const interventionName = interventionPolicy.value?.name ?? 'no intervention';
		return configName ? `${configName} (${interventionName})` : '';
	};

	const datasetName = nodeOutputLabel(props.node, nodeLabel());
	const projectId = useProjects().activeProject.value?.id ?? '';
	const datasetResult = await createDatasetFromSimulationResult(
		projectId,
		runId,
		datasetName,
		false,
		modelConfiguration.value?.id,
		interventionPolicy.value?.id
	);
	if (!datasetResult) {
		logger.error('Error creating dataset from simulation result.');
		return;
	}

	emit('append-output', {
		type: SimulateCiemssOperation.outputs[0].type,
		label: datasetName,
		value: [datasetResult.id],
		state: _.omit({ ...props.node.state, summaryId: summaryResponse?.id }, ['chartSettings']),
		isSelected: false
	});
};
const groupedInterventionOutputs = computed(() =>
	_.groupBy(flattenInterventionData(interventionPolicy.value?.interventions ?? []), 'appliedTo')
);

const preparedChartInputs = usePreparedChartInputs(props, runResults, runResultsSummary, pyciemssMap);

const { selectedVariableSettings, selectedInterventionSettings, selectedComparisonChartSettings } = useChartSettings(
	props,
	emit
);

const { useInterventionCharts, useVariableCharts, useComparisonCharts } = useCharts(
	props.node.id,
	model,
	modelConfiguration,
	preparedChartInputs,
	toRef({ width: 180, height: 120 }),
	computed(() => interventionPolicy.value?.interventions ?? []),
	null
);
const interventionCharts = useInterventionCharts(selectedInterventionSettings, true);
const variableCharts = useVariableCharts(selectedVariableSettings, null);
const comparisonCharts = useComparisonCharts(selectedComparisonChartSettings, true);
const isChartsEmpty = computed(
	() => _.isEmpty(interventionCharts.value) && _.isEmpty(variableCharts.value) && _.isEmpty(comparisonCharts.value)
);
const placeholderText = computed(() => {
	if (!areInputsFilled.value) {
		return 'Connect a model configuration';
	}
	if (isChartsEmpty.value) {
		return 'No variables selected';
	}
	return undefined;
});

const poller = new Poller();
const pollResult = async (runId: string) => {
	poller.setPollAction(async () => pollAction(runId));
	const pollerResults = await poller.start();
	const state = _.cloneDeep(props.node.state);
	state.errorMessage = { name: '', value: '', traceback: '' };

	if (pollerResults.state === PollerState.Cancelled) {
		state.inProgressForecastId = '';
		state.inProgressBaseForecastId = '';
		poller.stop();
	} else if (pollerResults.state !== PollerState.Done || !pollerResults.data) {
		state.inProgressForecastId = '';
		state.inProgressBaseForecastId = '';
		const simulation = await getSimulation(runId);
		if (simulation?.status && simulation?.statusMessage) {
			state.errorMessage = {
				name: runId,
				value: simulation.status,
				traceback: simulation.statusMessage
			};
			emit('update-state', state);
		}
		// throw if there are any failed runs for now
		logger.error(`Simulate: ${runId} has failed`, {
			toastTitle: 'Error - Pyciemss'
		});
	}
	emit('update-state', state);
	return pollerResults;
};

watch(
	() => props.node.state.inProgressForecastId + props.node.state.inProgressBaseForecastId,
	async (id) => {
		if (!id || id === '') return;

		let doneProcess = true;
		let response;
		if (props.node.state.inProgressBaseForecastId) {
			const baseForecastId = props.node.state.inProgressBaseForecastId;
			response = await pollResult(baseForecastId);
		}
		if (response?.state && response.state !== PollerState.Done) {
			doneProcess = false;
		}

		if (props.node.state.inProgressForecastId) {
			const forecastId = props.node.state.inProgressForecastId;
			response = await pollResult(forecastId);
		}
		if (response?.state && response.state !== PollerState.Done) {
			doneProcess = false;
		}
		if (doneProcess) {
			const state = _.cloneDeep(props.node.state);
			state.forecastId = state.inProgressForecastId;
			state.baseForecastId = state.inProgressBaseForecastId;
			state.inProgressForecastId = '';
			state.inProgressBaseForecastId = '';
			emit('update-state', state);
			await processResult(state.forecastId); // Only process and output result for main forecast
		}
	},
	{ immediate: true }
);

watch(
	() => props.node.inputs[0].value,
	async () => {
		const input = props.node.inputs[0];
		if (!input.value) return;

		const id = input.value[0];
		modelConfiguration.value = await getModelConfigurationById(id);
		model.value = await getModelByModelConfigurationId(id);
		modelVarUnits.value = getUnitsFromModelParts(model.value as Model);
	},
	{ immediate: true }
);

watch(
	() => props.node.active,
	async () => {
		const active = props.node.active;
		if (!active) return;

		selectedRunId.value = props.node.outputs.find((o) => o.id === active)?.value?.[0];
		const forecastId = props.node.state.forecastId;
		if (!forecastId || !selectedRunId.value || inProgressForecastRun.value) return;

		let [result, resultSummary] = await Promise.all([
			getRunResultCSV(forecastId, 'result.csv'),
			getRunResultCSV(forecastId, 'result_summary.csv')
		]);
		pyciemssMap.value = parsePyCiemssMap(result[0]);

		const baseForecastId = props.node.state.baseForecastId;
		if (baseForecastId) {
			// If forecast run before intervention (base run) is available, merge the results
			const [baseResult, baseResultSummary] = await Promise.all([
				getRunResultCSV(baseForecastId, 'result.csv', renameFnGenerator('pre')),
				getRunResultCSV(baseForecastId, 'result_summary.csv', renameFnGenerator('pre'))
			]);
			const merged = mergeResults(baseResult, result, baseResultSummary, resultSummary);
			result = merged.result;
			resultSummary = merged.resultSummary;
		}
		runResults.value[selectedRunId.value] = result;
		runResultsSummary.value[selectedRunId.value] = resultSummary;
	},
	{ immediate: true }
);

watch(
	() => interventionPolicyId.value,
	() => {
		if (interventionPolicyId.value) {
			getInterventionPolicyById(interventionPolicyId.value).then((policy) => {
				interventionPolicy.value = policy;
			});
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
:deep(.vega-chart-container) {
	margin-bottom: 0;
}

.empty-chart {
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	height: 9rem;
	gap: var(--gap-4);
	background: var(--surface-50);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	margin-bottom: var(--gap-4);
	color: var(--text-color-secondary);
	font-size: var(--font-caption);
}
.empty-image {
	width: 5rem;
	height: 5rem;
}
</style>
