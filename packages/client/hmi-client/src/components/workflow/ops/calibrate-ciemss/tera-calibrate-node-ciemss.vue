<template>
	<main>
		<section>
			<tera-node-preview
				:node="node"
				:is-loading="isLoading"
				:prepared-charts="Object.assign({}, interventionCharts, variableCharts)"
				:chart-settings="[...selectedInterventionSettings, ...selectedVariableSettings]"
				:are-embed-actions-visible="true"
				:placeholder="placeholderText"
				:processing="processingMessage"
			/>
			<vega-chart v-if="lossChartSpec" :are-embed-actions-visible="false" :visualization-spec="lossChartSpec" />
		</section>
		<Button v-if="areInputsFilled" label="Open" @click="emit('open-drilldown')" severity="secondary" outlined />
	</main>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { computed, watch, ref, shallowRef, onMounted, toRef } from 'vue';
import Button from 'primevue/button';
import {
	getRunResultCSV,
	pollAction,
	makeForecastJobCiemss,
	getSimulation,
	parsePyCiemssMap,
	DataArray
} from '@/services/models/simulation-service';
import { getModelConfigurationById, createModelConfiguration } from '@/services/model-configurations';
import { setupCsvAsset } from '@/services/calibrate-workflow';
import { getModelByModelConfigurationId, getUnitsFromModelParts } from '@/services/model';
import { nodeMetadata, nodeOutputLabel } from '@/components/workflow/util';
import { logger } from '@/utils/logger';
import { Poller, PollerState } from '@/api/api';
import type { WorkflowNode } from '@/types/workflow';
import VegaChart from '@/components/widgets/VegaChart.vue';
import {
	CsvAsset,
	Simulation,
	SimulationRequest,
	Model,
	ModelConfiguration,
	SemanticType,
	InferredParameterSemantic,
	InterventionPolicy,
	Dataset
} from '@/types/Types';
import { createLLMSummary } from '@/services/summary-service';
import { createForecastChart, AUTOSIZE } from '@/services/charts';
import * as stats from '@/utils/stats';
import { createDatasetFromSimulationResult, getDataset } from '@/services/dataset';
import { useProjects } from '@/composables/project';
import { getInterventionPolicyById } from '@/services/intervention-policy';
import { useChartSettings } from '@/composables/useChartSettings';
import { useCharts } from '@/composables/useCharts';
import { filterChartSettingsByVariables } from '@/services/chart-settings';
import { ChartSettingType } from '@/types/common';
import { parseCsvAsset } from '@/utils/csv';
import TeraNodePreview from '../tera-node-preview.vue';
import type { CalibrationOperationStateCiemss } from './calibrate-operation';
import { CalibrationOperationCiemss } from './calibrate-operation';
import { renameFnGenerator, usePreparedChartInputs, getSelectedOutputMapping } from './calibrate-utils';

const props = defineProps<{
	node: WorkflowNode<CalibrationOperationStateCiemss>;
}>();
const emit = defineEmits(['open-drilldown', 'update-state', 'append-output']);

const modelConfigId = computed<string | undefined>(() => props.node.inputs[0].value?.[0]);

const model = ref<Model | null>(null);
const modelConfiguration = ref<ModelConfiguration | null>(null);
const modelVarUnits = ref<{ [key: string]: string }>({});

const runResult = ref<DataArray>([]);
const runResultPre = ref<DataArray>([]);
const runResultSummary = ref<DataArray>([]);
const runResultSummaryPre = ref<DataArray>([]);
const policyInterventionId = computed(() => props.node.inputs[2].value?.[0]);
const interventionPolicy = ref<InterventionPolicy | null>(null);

const csvAsset = shallowRef<CsvAsset | undefined>(undefined);

const groundTruth = computed<DataArray>(() => parseCsvAsset(csvAsset.value as CsvAsset));

const areInputsFilled = computed(() => props.node.inputs[0].value && props.node.inputs[1].value);
const isLoading = computed<boolean>(
	() =>
		props.node.state.inProgressCalibrationId !== '' ||
		props.node.state.inProgressPreForecastId !== '' ||
		props.node.state.inProgressForecastId !== ''
);
const placeholderText = computed(() => {
	if (!areInputsFilled.value) {
		return 'Connect a model configuration and dataset';
	}
	return undefined;
});
const processingMessage = computed(() => {
	if (props.node.state.inProgressCalibrationId) {
		return `Processing calibration... ${props.node.state.currentProgress}%`;
	}
	if (props.node.state.inProgressPreForecastId || props.node.state.inProgressForecastId) {
		return 'Calibration complete. Running simulations';
	}

	return undefined;
});

const chartSize = { width: 180, height: 120 };

let lossValues: { [key: string]: number }[] = [];

const lossChartSpec = ref();
const updateLossChartSpec = (data: Record<string, any>[]) => {
	lossChartSpec.value = createForecastChart(
		null,
		{
			data,
			variables: ['loss'],
			timeField: 'iter'
		},
		null,
		{
			title: '',
			xAxisTitle: 'Solver iterations',
			yAxisTitle: 'Loss',
			autosize: AUTOSIZE.FIT,
			...chartSize,
			fitYDomain: true
		}
	);
};

async function updateLossChartWithSimulation() {
	if (props.node.active) {
		const simulationObj = await getSimulation(props.node.state.calibrationId);
		if (simulationObj?.updates) {
			lossValues = simulationObj?.updates
				.sort((a, b) => a.data.progress - b.data.progress)
				.map((d, i) => ({
					iter: i,
					loss: d.data.loss
				}));
			updateLossChartSpec(lossValues);
		}
	}
}

onMounted(async () => updateLossChartWithSimulation());

const selectedOutputMapping = computed(() => getSelectedOutputMapping(props.node));
const preparedChartInputs = usePreparedChartInputs(
	props,
	runResult,
	runResultSummary,
	runResultPre,
	runResultSummaryPre
);

const { selectedVariableSettings, selectedInterventionSettings } = useChartSettings(props, emit);
const { useInterventionCharts, useVariableCharts } = useCharts(
	props.node.id,
	model,
	modelConfiguration,
	preparedChartInputs,
	toRef(chartSize),
	computed(() => interventionPolicy.value?.interventions ?? []),
	selectedOutputMapping
);
const interventionCharts = useInterventionCharts(selectedInterventionSettings);
const variableCharts = useVariableCharts(selectedVariableSettings, groundTruth);

const poller = new Poller();
const pollResult = async (runId: string) => {
	poller
		.setPollAction(async () => pollAction(runId))
		.setProgressAction((data: Simulation) => {
			if (data?.updates?.length) {
				lossValues = data?.updates
					.sort((a, b) => a.data.progress - b.data.progress)
					.map((d, i) => ({
						iter: i,
						loss: d.data.loss
					}));
				updateLossChartSpec(lossValues);
			}
			if (runId === props.node.state.inProgressCalibrationId && data.updates.length > 0) {
				const checkpoint = _.last(data.updates);
				if (checkpoint) {
					const state = _.cloneDeep(props.node.state);
					const newProgress = +((100 * checkpoint.data.progress) / state.numIterations).toFixed(2);
					if (newProgress !== state.currentProgress) {
						state.currentProgress = newProgress;
						emit('update-state', state);
					}
				}
			}
		});

	const pollerResults = await poller.start();
	let state = _.cloneDeep(props.node.state);
	state.errorMessage = { name: '', value: '', traceback: '' };

	if (pollerResults.state === PollerState.Cancelled) {
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
			state = _.cloneDeep(props.node.state);
			state.inProgressCalibrationId = '';
			state.errorMessage = {
				name: runId,
				value: simulation.status,
				traceback: simulation.statusMessage
			};
		}
	}
	emit('update-state', state);
	return pollerResults;
};

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
	() => props.node.state.inProgressCalibrationId,
	async (id) => {
		if (!id || id === '') return;

		const response = await pollResult(id);
		const state = _.cloneDeep(props.node.state);

		const baseRequestPayload: SimulationRequest = {
			modelConfigId: modelConfigId.value as string,
			timespan: {
				start: 0,
				end: state.endTime
			},
			extra: {
				num_samples: state.numSamples,
				method: 'dopri5'
			},
			engine: 'ciemss'
		};

		// Calibration has finished, now kick of two forecast jobs to do comparison
		// - using the default configuration ()
		// - using the calibfrated configuration
		if (response.state === PollerState.Done) {
			// Default (Pre)
			let forecastResponse = await makeForecastJobCiemss(baseRequestPayload, nodeMetadata(props.node));
			state.inProgressPreForecastId = forecastResponse.id;

			// With calibrated result
			baseRequestPayload.extra.inferred_parameters = id;
			forecastResponse = await makeForecastJobCiemss(baseRequestPayload, nodeMetadata(props.node));
			state.inProgressForecastId = forecastResponse.id;

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
			state.forecastId = state.inProgressForecastId;
			state.preForecastId = state.inProgressPreForecastId;

			state.inProgressForecastId = '';
			state.inProgressPreForecastId = '';

			// Get the calibrate losses to generate a run summary
			const calibrateResponse = await pollAction(state.calibrationId);
			const calibreateUpdates = calibrateResponse.data?.updates;
			const errorStart = _.first(calibreateUpdates)?.data;
			const errorEnd = _.last(calibreateUpdates)?.data;

			const prompt = `
		The following are the key attributes of a calibration/fitting process for a ODE epidemilogy model.

		- Fitting: ${JSON.stringify(state.mapping)}
		- Loss at start is: ${JSON.stringify(errorStart)}
		- Loss at end is: ${JSON.stringify(errorEnd)}

		Provide a summary in 100 words or less.
			`;
			const summaryResponse = await createLLMSummary(prompt);
			const baseConfig = await getModelConfigurationById(modelConfigId.value as string);

			// Computed sampled model parameters from the forecast
			// FIXME: this is highly dependent on the number of samples, we may want to compute
			// the mean/stddev metrics independently of the forecsts directly querying the dill file
			const sampledData = await getRunResultCSV(state.forecastId, 'result.csv');
			const translationMap = parsePyCiemssMap(sampledData[0]);

			const timeId = sampledData[0].timepoint_id;
			const ode = model.value?.semantics?.ode;
			const parameterIds = ode?.parameters?.map((d) => d.id) as string[];
			const parameterTable: { [key: string]: number[] } = {};
			parameterIds.forEach((parameterId) => {
				parameterTable[parameterId] = [];
			});

			sampledData.forEach((point) => {
				if (point.timepoint_id === timeId) {
					parameterIds.forEach((parameterId) => {
						let dataKey = translationMap[parameterId];

						// propbably initials, need a proxy-translation map
						if (!dataKey) {
							const surrogateKey = ode?.initials?.find((d) => d.expression === parameterId)?.target;
							if (!surrogateKey) return;

							dataKey = translationMap[surrogateKey];
							if (!dataKey) return;
						}
						parameterTable[parameterId].push(point[dataKey]);
					});
				}
			});

			const inferredParameters: InferredParameterSemantic[] = [];
			Object.keys(parameterTable).forEach((parameterId) => {
				const mean = stats.mean(parameterTable[parameterId]);
				const stddev = stats.stddev(parameterTable[parameterId]);

				inferredParameters.push({
					source: 'calibration',
					type: SemanticType.Inferred,
					referenceId: parameterId,
					default: false,
					distribution: {
						type: 'inferred',
						parameters: {
							mean,
							stddev
						}
					}
				});
			});

			const calibratedModelConfig: ModelConfiguration = {
				name: `Calibrated: ${baseConfig.name}`,
				description: `Calibrated: ${baseConfig.description}`,
				simulationId: state.calibrationId,
				modelId: baseConfig.modelId,
				observableSemanticList: _.cloneDeep(baseConfig.observableSemanticList),
				parameterSemanticList: _.cloneDeep(baseConfig.parameterSemanticList),
				initialSemanticList: _.cloneDeep(baseConfig.initialSemanticList),
				inferredParameterList: inferredParameters,
				temporary: true
			};

			const modelConfigResponse = await createModelConfiguration(calibratedModelConfig);
			const datasetName = `Forecast run ${state.forecastId}`;
			const projectId = useProjects().activeProjectId.value;
			const datasetResult = await createDatasetFromSimulationResult(projectId, state.forecastId, datasetName, false);
			if (!datasetResult) {
				state.errorMessage = {
					name: 'Failed to create dataset',
					value: '',
					traceback: `Failed to create dataset from simulation result: ${state.forecastId}`
				};
				emit('update-state', state);
				return;
			}

			state.summaryId = summaryResponse?.id;

			// For error charts, make sure that only chart settings for mapped model variables are used
			const mappedModelVariables = state.mapping.map((d) => d.modelVariable);
			state.chartSettings = filterChartSettingsByVariables(
				state.chartSettings || [],
				ChartSettingType.ERROR_DISTRIBUTION,
				mappedModelVariables
			);

			emit(
				'append-output',
				{
					type: CalibrationOperationCiemss.outputs[0].type,
					label: nodeOutputLabel(props.node, `Calibration Result`),
					value: [
						{
							modelConfigId: modelConfigResponse.id,
							datasetId: datasetResult.id
						}
					],
					state: _.omit(state, ['chartSettings'])
				},
				state
			);
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
		if (!state.forecastId) return;

		// Simulates
		runResult.value = await getRunResultCSV(state.forecastId, 'result.csv');
		runResultSummary.value = await getRunResultCSV(state.forecastId, 'result_summary.csv');

		runResultPre.value = await getRunResultCSV(state.preForecastId, 'result.csv', renameFnGenerator('pre'));
		runResultSummaryPre.value = await getRunResultCSV(
			state.preForecastId,
			'result_summary.csv',
			renameFnGenerator('pre')
		);

		// Dataset used to calibrate
		const datasetId = props.node.inputs[1]?.value?.[0];
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
	() => policyInterventionId.value,
	() => {
		if (policyInterventionId.value) {
			getInterventionPolicyById(policyInterventionId.value).then((policy) => {
				interventionPolicy.value = policy;
			});
		}
	},
	{ immediate: true }
);
</script>

<style scoped></style>
