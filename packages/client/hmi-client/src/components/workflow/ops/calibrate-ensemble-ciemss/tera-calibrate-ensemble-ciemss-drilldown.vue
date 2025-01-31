<template>
	<tera-drilldown
		:node="node"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<section :tabName="DrilldownTabs.Wizard" class="wizard">
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Calibrate ensemble settings"
				content-width="600px"
			>
				<template #header>
					<div class="flex gap-2 ml-auto">
						<tera-pyciemss-cancel-button :simulation-run-id="cancelRunId" />
						<div v-tooltip="runButtonMessage">
							<Button
								:disabled="isRunDisabled"
								label="Run"
								icon="pi pi-play"
								@click="runEnsemble"
								:loading="!!inProgressCalibrationId || !!inProgressForecastId"
							/>
						</div>
					</div>
				</template>
				<template #content>
					<Accordion multiple :active-index="currentActiveIndicies">
						<AccordionTab header="Mapping">
							<div class="overflow-x-scroll">
								<table>
									<thead>
										<tr>
											<th v-for="(header, i) in tableHeaders" :key="i">
												{{ header }}
											</th>
										</tr>
									</thead>
									<tbody>
										<!-- Timestamp selection-->
										<tr>
											<td>Timestamp</td>
											<td>
												<Dropdown v-model="knobs.timestampColName" placeholder="Select" :options="datasetColumnNames" />
											</td>
										</tr>
										<tr v-for="(config, i) in knobs.ensembleMapping" :key="i">
											<td>
												<tera-input-text v-model="config.newName" placeholder="Variable name" />
											</td>
											<td>
												<Dropdown v-model="config.datasetMapping" placeholder="Select" :options="datasetColumnNames" />
											</td>
											<td v-for="(configuration, index) in allModelConfigurations" :key="configuration.id">
												<Dropdown
													v-if="configuration?.id"
													v-model="config.modelConfigurationMappings[configuration.id]"
													placeholder="Select"
													:options="allModelOptions[index].map((option) => option.referenceId ?? option.id)"
												/>
											</td>
											<td>
												<Button
													v-if="knobs.ensembleMapping.length > 1"
													icon="pi pi-trash"
													text
													@click="removeMapping(i)"
												/>
											</td>
										</tr>
									</tbody>
								</table>
							</div>

							<Button size="small" text icon="pi pi-plus" label="Add mapping" @click="addMapping" />
							<!--
						TODO: Add auto mapping here
						<Button
							text
							size="small"
							icon="pi pi-sparkles"
							label="Auto map"
							@click="getAutoMapping"
						/> -->
						</AccordionTab>
						<AccordionTab header="Model weights">
							<div
								v-for="modelConfiguration in allModelConfigurations"
								class="flex align-items-center"
								:key="modelConfiguration.id"
							>
								<h6>{{ modelConfiguration.name }}</h6>
								<tera-signal-bars
									v-if="modelConfiguration?.id"
									class="ml-auto"
									:min-option="1"
									:model-value="knobs.configurationWeights[modelConfiguration.id] ?? 0"
									@update:model-value="knobs.configurationWeights[modelConfiguration.id] = $event"
									label="Relative certainty"
								/>
							</div>
						</AccordionTab>

						<AccordionTab header="Other settings">
							<div class="input-row">
								<tera-timestep-calendar
									class="flex-1"
									disabled
									:model-value="0"
									label="Start time"
									:start-date="undefined"
								/>
								<tera-timestep-calendar
									class="flex-1"
									v-model="knobs.extra.endTime"
									label="End time"
									:start-date="undefined"
								/>
							</div>

							<div class="label-and-input">
								<label> Preset </label>
								<Dropdown
									class="flex-1"
									v-model="presetType"
									placeholder="Select an option"
									:options="[CiemssPresetTypes.Fast, CiemssPresetTypes.Normal]"
									@update:model-value="setPresetValues"
								/>
								<label class="mb-1 p-text-secondary text-sm">
									<i class="pi pi-info-circle" />
									This impacts solver method, iterations and learning rate.
								</label>
							</div>

							<fieldset class="mt-1 additional-settings">
								<div class="label-and-input">
									<label>Number of Samples</label>
									<tera-input-number v-model="knobs.extra.numParticles" />
								</div>
								<div class="spacer m-3" />

								<h6 class="mb-2">ODE solver options</h6>

								<div class="input-row">
									<div class="label-and-input">
										<label>Solver method</label>
										<Dropdown
											v-model="knobs.extra.solverMethod"
											:options="[CiemssMethodOptions.dopri5, CiemssMethodOptions.rk4, CiemssMethodOptions.euler]"
										/>
									</div>
									<div class="label-and-input">
										<label for="num-steps">Solver step size</label>
										<tera-input-number
											:disabled="
												![CiemssMethodOptions.rk4, CiemssMethodOptions.euler].includes(knobs.extra.solverMethod)
											"
											:min="0"
											v-model="knobs.extra.stepSize"
										/>
									</div>
								</div>
								<div class="spacer m-3" />
								<h6 class="mb-2">Inference Options</h6>
								<div class="input-row">
									<div class="label-and-input">
										<label for="num-iterations">Number of solver iterations</label>
										<tera-input-number v-model="knobs.extra.numIterations" />
									</div>
									<div class="label-and-input">
										<label for="learning-rate">Learning rate</label>
										<tera-input-number v-model="knobs.extra.learningRate" />
									</div>
									<div class="label-and-input">
										<label>Inference algorithm</label>
										<tera-input-text disabled model-value="SVI" />
									</div>
									<div class="label-and-input">
										<label>Loss function</label>
										<tera-input-text disabled model-value="ELBO" />
									</div>
									<div class="label-and-input">
										<label>Optimizer method</label>
										<tera-input-text disabled model-value="ADAM" />
									</div>
								</div>
							</fieldset>
						</AccordionTab>
					</Accordion>
				</template>
			</tera-slider-panel>
		</section>
		<section :tabName="DrilldownTabs.Notebook">
			<h4>Notebook</h4>
		</section>
		<template #preview>
			<tera-drilldown-section
				:is-loading="isRunInProgress"
				:show-slot-while-loading="true"
				:loading-progress="node.state.currentProgress"
			>
				<section class="pb-3 px-2">
					<div class="mx-2" ref="chartWidthDiv"></div>
					<Accordion multiple :active-index="[0, 1, 2, 3]">
						<!-- <AccordionTab header="Summary">
						</AccordionTab> -->
						<AccordionTab v-if="node.state.showLossChart" header="Loss">
							<vega-chart
								v-if="!_.isEmpty(lossValues)"
								expandable
								ref="lossChartRef"
								:are-embed-actions-visible="true"
								:visualization-spec="lossChartSpec"
							/>
						</AccordionTab>
						<template v-if="!isRunInProgress">
							<AccordionTab v-if="selectedEnsembleVariableSettings.length > 0" header="Ensemble variables over time">
								<div class="flex flex-row" v-for="setting of selectedEnsembleVariableSettings" :key="setting.id">
									<vega-chart
										v-for="(spec, index) of ensembleVariableCharts[setting.id]"
										:key="index"
										expandable
										:are-embed-actions-visible="true"
										:visualization-spec="spec"
									/>
								</div>
							</AccordionTab>
							<AccordionTab v-if="selectedErrorVariableSettings.length > 0" header="Error">
								<div class="flex flex-row">
									<vega-chart
										v-for="(spec, index) of errorCharts"
										:key="index"
										:expandable="() => onExpandErrorChart(index)"
										:are-embed-actions-visible="true"
										:visualization-spec="spec"
									/>
								</div>
							</AccordionTab>
							<AccordionTab v-if="node.state.showModelWeightsCharts" header="Model weights">
								<div class="flex flex-row">
									<vega-chart
										v-for="(spec, index) of weightsDistributionCharts"
										:key="index"
										expandable
										:are-embed-actions-visible="true"
										:visualization-spec="spec"
									/>
								</div>
							</AccordionTab>
						</template>
					</Accordion>
					<tera-notebook-error v-bind="node.state.errorMessage" />
				</section>
			</tera-drilldown-section>
		</template>
		<template #sidebar-right>
			<tera-slider-panel
				v-model:is-open="isOutputSettingsPanelOpen"
				direction="right"
				class="input-config"
				header="Output settings"
				content-width="360px"
			>
				<template #overlay>
					<tera-chart-settings-panel
						:annotations="
							[ChartSettingType.VARIABLE_ENSEMBLE].includes(activeChartSettings?.type as ChartSettingType)
								? getChartAnnotationsByChartId(activeChartSettings?.id ?? '')
								: undefined
						"
						:active-settings="activeChartSettings"
						:generate-annotation="generateAnnotation"
						@update-settings="updateActiveChartSettings"
						@delete-annotation="deleteAnnotation"
						@close="setActiveChartSettings(null)"
					/>
				</template>
				<template #content>
					<div class="output-settings-panel">
						<h5>Loss</h5>
						<tera-checkbox
							label="Show loss chart"
							:model-value="Boolean(node.state.showLossChart)"
							@update:model-value="emit('update-state', { ...node.state, showLossChart: $event })"
						/>
						<Divider />
						<tera-chart-settings
							:title="'Ensemble variables over time'"
							:settings="chartSettings"
							:type="ChartSettingType.VARIABLE_ENSEMBLE"
							:select-options="ensembleVariables"
							:selected-options="selectedEnsembleVariableSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@selection-change="updateChartSettings"
							@toggle-ensemble-variable-setting-option="updateEnsembleVariableSettingOption"
						/>
						<Divider />
						<tera-chart-settings
							:title="'Error'"
							:settings="chartSettings"
							:type="ChartSettingType.ERROR_DISTRIBUTION"
							:select-options="ensembleVariables"
							:selected-options="selectedErrorVariableSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@selection-change="updateChartSettings"
						/>
						<Divider />
						<h5>Model Weights</h5>
						<tera-checkbox
							label="Show distributions in charts"
							:model-value="Boolean(node.state.showModelWeightsCharts)"
							@update:model-value="emit('update-state', { ...node.state, showModelWeightsCharts: $event })"
						/>
						<Divider />
						<tera-chart-settings-quantiles :settings="chartSettings" @update-options="updateQauntilesOptions" />
						<Divider />
					</div>
				</template>
			</tera-slider-panel>
		</template>
	</tera-drilldown>
	<tera-save-dataset-from-simulation
		:simulation-run-id="props.node.state.postForecastId"
		:showDialog="showSaveDataDialog"
		@dialog-hide="showSaveDataDialog = false"
	/>
</template>

<script setup lang="ts">
import _, { isEmpty } from 'lodash';
import * as vega from 'vega';
import { ref, shallowRef, computed, watch, onMounted } from 'vue';
import {
	makeEnsembleCiemssCalibration,
	unsubscribeToUpdateMessages,
	subscribeToUpdateMessages,
	CiemssMethodOptions
} from '@/services/models/simulation-service';
import Button from 'primevue/button';
import Divider from 'primevue/divider';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import AccordionTab from 'primevue/accordiontab';
import Accordion from 'primevue/accordion';
import Dropdown from 'primevue/dropdown';
import { getFileName, setupCsvAsset } from '@/services/calibrate-workflow';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraSaveDatasetFromSimulation from '@/components/dataset/tera-save-dataset-from-simulation.vue';
import TeraPyciemssCancelButton from '@/components/pyciemss/tera-pyciemss-cancel-button.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraChartSettings from '@/components/widgets/tera-chart-settings.vue';
import TeraChartSettingsPanel from '@/components/widgets/tera-chart-settings-panel.vue';
import TeraChartSettingsQuantiles from '@/components/widgets/tera-chart-settings-quantiles.vue';
import TeraCheckbox from '@/components/widgets/tera-checkbox.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraSignalBars from '@/components/widgets/tera-signal-bars.vue';
import TeraTimestepCalendar from '@/components/widgets/tera-timestep-calendar.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import { getTimespan, nodeMetadata } from '@/components/workflow/util';
import type {
	CsvAsset,
	EnsembleCalibrationCiemssRequest,
	ModelConfiguration,
	Dataset,
	ClientEvent
} from '@/types/Types';
import { ClientEventType } from '@/types/Types';
import { WorkflowNode } from '@/types/workflow';
import { getDataset } from '@/services/dataset';
import { useDrilldownChartSize } from '@/composables/useDrilldownChartSize';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { ChartSettingType, CiemssPresetTypes, DrilldownTabs } from '@/types/common';
import { useCharts } from '@/composables/useCharts';
import { useChartSettings } from '@/composables/useChartSettings';
import { deleteAnnotation, updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import { DataArray } from '@/utils/stats';
import { GroupedDataArray } from '@/services/charts';
import { parseCsvAsset } from '@/utils/csv';
import {
	CalibrateEnsembleCiemssOperationState,
	CalibrateEnsembleMappingRow,
	EnsembleCalibrateExtraCiemss,
	qualityPreset,
	speedPreset
} from './calibrate-ensemble-ciemss-operation';
import {
	updateLossChartSpec,
	getLossValuesFromSimulation,
	formatCalibrateModelConfigurations,
	getChartEnsembleMapping,
	fetchOutputData,
	buildChartData,
	getEnsembleErrorData,
	EnsembleErrorData,
	fetchModelConfigurations,
	setStateToModelConfigMap
} from './calibrate-ensemble-util';

const props = defineProps<{
	node: WorkflowNode<CalibrateEnsembleCiemssOperationState>;
}>();
const showSaveDataDialog = ref<boolean>(false);
const emit = defineEmits(['update-state', 'close', 'select-output']);

interface BasicKnobs {
	ensembleMapping: CalibrateEnsembleMappingRow[];
	configurationWeights: { [key: string]: number }; // Note these are Dirichlet distributions not EXACTLY weights
	extra: EnsembleCalibrateExtraCiemss;
	timestampColName: string;
}

const knobs = ref<BasicKnobs>({
	ensembleMapping: props.node.state.ensembleMapping ?? [],
	configurationWeights: props.node.state.configurationWeights ?? {},
	extra: props.node.state.extra ?? {},
	timestampColName: props.node.state.timestampColName ?? ''
});

const currentActiveIndicies = ref([0, 1, 2]);

const isSidebarOpen = ref(true);
const selectedOutputId = ref<string>();

// Checks for disabling run button:
const isMappingfilled = computed(
	() =>
		knobs.value.ensembleMapping.length !== 0 && knobs.value.ensembleMapping[0].newName && knobs.value.timestampColName
);

const areNodeInputsFilled = computed(() => datasetId.value && allModelConfigurations.value.length >= 2);

const isRunDisabled = computed(() => !isMappingfilled.value || !areNodeInputsFilled.value);

const mappingFilledTooltip = computed(() =>
	!isMappingfilled.value ? 'Must contain a Timestamp column and at least one mapping. \n' : ''
);
const nodeInputsFilledTooltip = computed(() =>
	!areNodeInputsFilled.value ? 'Must contain one dataset and at least two model configurations.\n' : ''
);

const runButtonMessage = computed(() =>
	isRunDisabled.value ? `${mappingFilledTooltip.value} ${nodeInputsFilledTooltip.value}` : ''
);

const cancelRunId = computed(
	() =>
		props.node.state.inProgressForecastId ||
		props.node.state.inProgressCalibrationId ||
		props.node.state.inProgressPreForecastId
);
const inProgressCalibrationId = computed(() => props.node.state.inProgressCalibrationId);
const inProgressForecastId = computed(() => props.node.state.inProgressForecastId);
const isRunInProgress = computed(() => Boolean(inProgressCalibrationId.value || inProgressForecastId.value));

const datasetId = computed(() => props.node.inputs[0].value?.[0] as string | undefined);
const currentDatasetFileName = ref<string>();
const datasetColumnNames = computed(
	() =>
		dataset.value?.columns?.filter((col) => col.fileName === currentDatasetFileName.value).map((col) => col.name) ??
		([] as string[])
);
// Loss Chart:
const lossChartRef = ref<InstanceType<typeof VegaChart>>();
const lossChartSpec = ref();
const lossValues = ref<{ [key: string]: number }[]>([]);
const LOSS_CHART_DATA_SOURCE = 'lossData';
// Model:
const modelConfigIds = computed(() =>
	props.node.inputs.filter((input) => input.type === 'modelConfigId' && input.value).map((input) => input.value?.[0])
);
const listModelLabels = ref<string[]>([]);
const allModelConfigurations = ref<ModelConfiguration[]>([]);
const stateToModelConfigMap = ref<{ [key: string]: string[] }>({});

const tableHeaders = computed(() => {
	const headers = ['Ensemble model'];
	if (currentDatasetFileName.value) headers.push(currentDatasetFileName.value ?? '');
	allModelConfigurations.value.forEach((config) => {
		headers.push(config.name ?? '');
	});
	return headers;
});
// List of each observible + state for each model.
const allModelOptions = ref<any[][]>([]);

const dataset = shallowRef<Dataset | null>(null);
const csvAsset = shallowRef<CsvAsset | undefined>(undefined);

const onSelection = (id: string) => {
	emit('select-output', id);
};

function addMapping() {
	// create empty configuration mappings
	const configMappings = {};
	allModelConfigurations.value.forEach((config) => {
		configMappings[config.id as string] = '';
	});

	knobs.value.ensembleMapping.push({
		newName: '',
		datasetMapping: '',
		modelConfigurationMappings: configMappings
	});

	const state = _.cloneDeep(props.node.state);
	state.ensembleMapping = knobs.value.ensembleMapping;
	emit('update-state', state);
}

function removeMapping(index: number) {
	knobs.value.ensembleMapping.splice(index, 1);
	const state = _.cloneDeep(props.node.state);
	state.ensembleMapping = knobs.value.ensembleMapping;
	emit('update-state', state);
}

const messageHandler = (event: ClientEvent<any>) => {
	const data = { iter: lossValues.value.length, loss: event.data.loss };
	lossValues.value.push(data);
	if (!lossChartRef.value?.view) return;
	lossChartRef.value.view.change(LOSS_CHART_DATA_SOURCE, vega.changeset().insert(data)).resize().run();
};

const presetType = computed(() => {
	if (
		knobs.value.extra.numParticles === speedPreset.numSamples &&
		knobs.value.extra.solverMethod === speedPreset.method &&
		knobs.value.extra.numIterations === speedPreset.numIterations &&
		knobs.value.extra.learningRate === speedPreset.learningRate &&
		knobs.value.extra.stepSize === speedPreset.stepSize
	) {
		return CiemssPresetTypes.Fast;
	}
	if (
		knobs.value.extra.numParticles === qualityPreset.numSamples &&
		knobs.value.extra.solverMethod === qualityPreset.method &&
		knobs.value.extra.numIterations === qualityPreset.numIterations &&
		knobs.value.extra.learningRate === qualityPreset.learningRate
	) {
		return CiemssPresetTypes.Normal;
	}
	return '';
});

const setPresetValues = (data: CiemssPresetTypes) => {
	if (data === CiemssPresetTypes.Normal) {
		knobs.value.extra.numParticles = qualityPreset.numSamples;
		knobs.value.extra.solverMethod = qualityPreset.method;
		knobs.value.extra.numIterations = qualityPreset.numIterations;
		knobs.value.extra.learningRate = qualityPreset.learningRate;
	}
	if (data === CiemssPresetTypes.Fast) {
		knobs.value.extra.numParticles = speedPreset.numSamples;
		knobs.value.extra.solverMethod = speedPreset.method;
		knobs.value.extra.numIterations = speedPreset.numIterations;
		knobs.value.extra.learningRate = speedPreset.learningRate;
		knobs.value.extra.stepSize = speedPreset.stepSize;
	}
};

const initDefaultChartSettings = (state: CalibrateEnsembleCiemssOperationState) => {
	const mappedEnsembleVariables = knobs.value.ensembleMapping.map((c) => c.newName);
	if (_.isEmpty(state.chartSettings)) {
		state.chartSettings = updateChartSettingsBySelectedVariables(
			state.chartSettings ?? [],
			ChartSettingType.VARIABLE_ENSEMBLE,
			mappedEnsembleVariables
		);
		state.chartSettings = updateChartSettingsBySelectedVariables(
			state.chartSettings,
			ChartSettingType.ERROR_DISTRIBUTION,
			mappedEnsembleVariables
		);
	}
};

const runEnsemble = async () => {
	if (!datasetId.value || !currentDatasetFileName.value) return;

	// Reset loss buffer
	lossValues.value = [];

	const datasetMapping: { [index: string]: string } = {};
	datasetMapping[knobs.value.timestampColName] = 'timestamp';
	// Each key used in the ensemble configs is a dataset column.
	// add these columns used to the datasetMapping
	knobs.value.ensembleMapping.forEach((config) => {
		datasetMapping[config.datasetMapping] = config.datasetMapping;
	});

	const calibratePayload: EnsembleCalibrationCiemssRequest = {
		modelConfigs: formatCalibrateModelConfigurations(knobs.value.ensembleMapping, knobs.value.configurationWeights),
		timespan: getTimespan(
			dataset.value as Dataset,
			knobs.value.timestampColName,
			knobs.value.extra.endTime // Default is simulation End Time
		),
		dataset: {
			id: datasetId.value,
			filename: currentDatasetFileName.value,
			mappings: datasetMapping
		},
		engine: 'ciemss',
		extra: {
			num_particles: knobs.value.extra.numParticles,
			num_iterations: knobs.value.extra.numIterations,
			solver_method: knobs.value.extra.solverMethod,
			solver_step_size: knobs.value.extra.stepSize,
			lr: knobs.value.extra.learningRate
		}
	};
	const response = await makeEnsembleCiemssCalibration(calibratePayload, nodeMetadata(props.node));
	if (response?.simulationId) {
		const state = _.cloneDeep(props.node.state);
		state.currentProgress = 0;
		state.inProgressCalibrationId = response?.simulationId;
		state.inProgressForecastId = '';
		// Add default chart settings based on the ensemble mapping on the first run
		initDefaultChartSettings(state);
		emit('update-state', state);
	}
};

onMounted(async () => {
	stateToModelConfigMap.value = await setStateToModelConfigMap(modelConfigIds.value as string[]);
	const configs = await fetchModelConfigurations(props.node.inputs);
	if (!configs) return;
	allModelConfigurations.value = configs.allModelConfigurations;
	allModelOptions.value = configs.allModelOptions;

	// dataset input
	if (datasetId.value) {
		// Get dataset
		dataset.value = await getDataset(datasetId.value);
		if (dataset.value) {
			currentDatasetFileName.value = getFileName(dataset.value);

			setupCsvAsset(dataset.value).then((csv) => {
				csvAsset.value = csv;
			});
		}
	}

	listModelLabels.value = allModelConfigurations.value.map((ele) => ele.name ?? '');

	// add a mapping row if none exist
	if (isEmpty(knobs.value.ensembleMapping)) {
		addMapping();
	}

	// initialze weights
	if (isEmpty(knobs.value.configurationWeights)) {
		allModelConfigurations.value.forEach((config) => {
			knobs.value.configurationWeights[config.id as string] = 1;
		});
	}
});

// -------------- Charts && chart settings ----------------
const chartWidthDiv = ref(null);
const isOutputSettingsPanelOpen = ref(false);
const outputData = ref<{
	result: DataArray;
	resultSummary: DataArray;
	pyciemssMap: Record<string, string>;
	resultGroupByTimepoint: GroupedDataArray;
} | null>(null);
const groundTruthData = computed<DataArray>(() => parseCsvAsset(csvAsset.value as CsvAsset));
const chartSize = useDrilldownChartSize(chartWidthDiv);
const selectedOutputMapping = computed(() => getChartEnsembleMapping(props.node, stateToModelConfigMap.value));
const {
	activeChartSettings,
	chartSettings,
	removeChartSettings,
	updateChartSettings,
	selectedEnsembleVariableSettings,
	selectedErrorVariableSettings,
	updateEnsembleVariableSettingOption,
	updateQauntilesOptions,
	updateActiveChartSettings,
	setActiveChartSettings
} = useChartSettings(props, emit);

const {
	generateAnnotation,
	getChartAnnotationsByChartId,
	useEnsembleVariableCharts,
	useWeightsDistributionCharts,
	useEnsembleErrorCharts
} = useCharts(
	props.node.id,
	null,
	allModelConfigurations,
	computed(() => buildChartData(outputData.value, selectedOutputMapping.value)),
	chartSize,
	null,
	selectedOutputMapping
);
const errorData = computed<EnsembleErrorData>(() =>
	getEnsembleErrorData(
		groundTruthData.value,
		outputData.value?.result ?? [],
		selectedOutputMapping.value,
		outputData.value?.pyciemssMap ?? {}
	)
);

const ensembleVariables = computed(() =>
	getChartEnsembleMapping(props.node, stateToModelConfigMap.value, false).map((d) => d.newName)
);
const ensembleVariableCharts = useEnsembleVariableCharts(selectedEnsembleVariableSettings, groundTruthData);
const weightsDistributionCharts = useWeightsDistributionCharts();
const { errorCharts, onExpandErrorChart } = useEnsembleErrorCharts(selectedErrorVariableSettings, errorData);
// --------------------------------------------------------

watch(
	() => props.node.active,
	async () => {
		// Update selected output
		if (props.node.active) {
			selectedOutputId.value = props.node.active;

			const state = _.cloneDeep(props.node.state);

			// only copy the keys from state that exist in knobs.value
			Object.keys(knobs.value).forEach((key) => {
				if (state[key] !== undefined) {
					knobs.value[key] = state[key];
				}
			});

			lossValues.value = await getLossValuesFromSimulation(props.node.state.calibrationId);
			lossChartSpec.value = updateLossChartSpec(lossValues.value, chartSize.value);

			// Fetch output data and prepare chart data
			outputData.value = await fetchOutputData(state.preForecastId, state.postForecastId);
		}
	},
	{ immediate: true }
);

watch(
	() => knobs.value,
	async () => {
		const state = _.cloneDeep(props.node.state);
		state.timestampColName = knobs.value.timestampColName;
		state.extra = knobs.value.extra;
		state.ensembleMapping = knobs.value.ensembleMapping;
		state.configurationWeights = knobs.value.configurationWeights;
		emit('update-state', state);
	},
	{ deep: true }
);

watch(
	[() => props.node.state.inProgressCalibrationId, chartSize],
	([id, size]) => {
		if (id === '') {
			lossChartSpec.value = updateLossChartSpec(lossValues.value, size);
			unsubscribeToUpdateMessages([id], ClientEventType.SimulationPyciemss, messageHandler);
		} else {
			lossChartSpec.value = updateLossChartSpec(LOSS_CHART_DATA_SOURCE, size);
			subscribeToUpdateMessages([id], ClientEventType.SimulationPyciemss, messageHandler);
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
.tera-ensemble {
	background: white;
	z-index: 1;
}

.model-weights {
	display: flex;
}

.ensemble-header {
	display: flex;
	margin: 1em;
}

th {
	text-align: left;
}

th,
td {
	padding-left: 0;
}

.ensemble-header-label {
	display: flex;
	align-items: center;
	margin: 0 1em;
	font-weight: 700;
	font-size: 1.75em;
}

:deep(.p-inputnumber-input, .p-inputwrapper) {
	width: 100%;
}

.wizard .toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--gap-1) var(--gap-4);
	gap: var(--gap-2);
}

.additional-settings {
	background: var(--surface-200);
	padding: var(--gap-3);
	border-radius: var(--border-radius-medium);
}

.label-and-input {
	display: flex;
	flex-direction: column;
	gap: var(--gap-1);

	:deep(input) {
		text-align: left;
	}
}

.input-row {
	align-items: center;
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	gap: var(--gap-2);
	width: 100%;

	& > * {
		flex: 1;
	}
}

.overlay-container:deep(section.scale main) {
	grid-template-columns: auto 1fr;
}

.output-settings-panel {
	padding: var(--gap-4);
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}
</style>
