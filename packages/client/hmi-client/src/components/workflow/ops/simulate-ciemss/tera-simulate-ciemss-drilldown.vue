<template>
	<tera-drilldown
		v-bind="$attrs"
		:node="node"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
		class="drilldown"
	>
		<!-- Wizard -->
		<tera-drilldown-section :tabName="DrilldownTabs.Wizard" class="wizard">
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Simulation settings"
				content-width="420px"
			>
				<template #content>
					<div class="flex align-items-center justify-content-between px-3">
						<p>{{ blankMessage }}.</p>
						<span class="flex gap-2">
							<tera-pyciemss-cancel-button :simulation-run-id="cancelRunIds" />
							<Button
								label="Run"
								icon="pi pi-play"
								@click="run"
								:loading="inProgressForecastRun"
								:disabled="isRunDisabled"
							/>
						</span>
					</div>
					<div class="form-section" v-if="isSidebarOpen">
						<!-- Presets -->
						<div class="label-and-input">
							<label>Preset (optional)</label>
							<Dropdown
								v-model="presetType"
								placeholder="Select an option"
								:options="[CiemssPresetTypes.Fast, CiemssPresetTypes.Normal]"
								@update:model-value="setPresetValues"
							/>
						</div>

						<!-- Start & End -->
						<div class="input-row">
							<tera-timestep-calendar
								disabled
								v-if="model && modelConfiguration"
								label="Start time"
								:start-date="modelConfiguration.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								v-model="timespan.start"
								@update:model-value="updateState"
								class="common-input-height"
							/>
							<tera-timestep-calendar
								v-if="model && modelConfiguration"
								label="End time"
								:start-date="modelConfiguration.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								v-model="timespan.end"
								@update:model-value="updateState"
								class="common-input-height"
							/>
						</div>

						<!-- Number of Samples & Method -->
						<div class="input-row mt-3">
							<div class="label-and-input">
								<label for="num-samples">Number of samples</label>
								<tera-input-number
									id="num-samples"
									v-model="numSamples"
									inputId="integeronly"
									:min="1"
									@update:model-value="updateState"
								/>
							</div>
							<div class="label-and-input">
								<label for="solver-method">Method</label>
								<Dropdown
									id="solver-method"
									v-model="method"
									:options="[CiemssMethodOptions.dopri5, CiemssMethodOptions.rk4, CiemssMethodOptions.euler]"
									@update:model-value="updateState"
								/>
							</div>
							<div class="label-and-input">
								<label for="num-samples">Solver step size</label>
								<tera-input-number
									v-model="solverStepSize"
									:disabled="![CiemssMethodOptions.rk4, CiemssMethodOptions.euler].includes(method)"
									:min="0"
									@update:model-value="updateState"
								/>
							</div>
						</div>
						<template v-if="interventionPolicy && model">
							<h4>Intervention Policies</h4>
							<tera-intervention-summary-card
								v-for="(intervention, index) in interventionPolicy.interventions"
								:start-date="modelConfiguration?.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								:intervention="intervention"
								:state-units="modelStateUnits"
								:parameter-units="modelParameterUnits"
								:key="index"
							/>
						</template>
					</div>
				</template>
			</tera-slider-panel>
		</tera-drilldown-section>

		<!-- Notebook -->
		<tera-drilldown-section :tabName="DrilldownTabs.Notebook" class="notebook-section">
			<tera-notebook-jupyter-input
				:kernel-manager="kernelManager"
				:context-language="'python3'"
				@llm-output="(data: any) => processLLMOutput(data)"
				@llm-thought-output="(data: any) => llmThoughts.push(data)"
				@question-asked="updateLlmQuery"
			>
				<template #toolbar-right-side>
					<Button label="Run" size="small" icon="pi pi-play" @click="runCode" :disabled="isEmpty(codeText)" />
				</template>
			</tera-notebook-jupyter-input>
			<v-ace-editor
				v-model:value="codeText"
				@init="initializeAceEditor"
				lang="python"
				theme="chrome"
				style="flex-grow: 1; width: 100%"
				class="ace-editor"
			/>
		</tera-drilldown-section>

		<!-- Preview -->
		<template #preview>
			<tera-drilldown-section
				:is-loading="inProgressForecastRun"
				:is-blank="!selectedOutputId"
				:blank-message="blankMessage"
			>
				<template #header-controls-left>
					<h4 class="ml-4">Output {{ selectedOutputLabel }}</h4>
				</template>
				<template #header-controls-right>
					<Button class="mr-3" label="Save for re-use" severity="secondary" outlined @click="showSaveDataset = true" />
				</template>
				<tera-operator-output-summary
					v-if="node.state.summaryId && runResults[selectedRunId]"
					:summary-id="node.state.summaryId"
					class="p-3 pt-0"
				/>
				<div
					v-if="node.state.summaryId && runResults[selectedRunId]"
					class="pl-3 pr-3 pb-2 flex flex-row align-items-center gap-2"
				>
					<SelectButton
						class=""
						:model-value="view"
						@change="if ($event.value) view = $event.value;"
						:options="viewOptions"
						option-value="value"
					>
						<template #option="{ option }">
							<i :class="`${option.icon} p-button-icon-left`" />
							<span class="p-button-label">{{ option.value }}</span>
						</template>
					</SelectButton>
				</div>
				<tera-notebook-error v-bind="node.state.errorMessage" />
				<template v-if="runResults[selectedRunId]">
					<div v-if="view === OutputView.Charts">
						<div class="mx-4" ref="chartWidthDiv"></div>
						<Accordion multiple :active-index="currentActiveIndicies" class="px-2">
							<!-- Section: Interventions over time -->
							<AccordionTab v-if="selectedInterventionSettings.length > 0" header="Interventions over time">
								<template v-for="setting in selectedInterventionSettings" :key="setting.id">
									<vega-chart
										expandable
										are-embed-actions-visible
										:visualization-spec="interventionCharts[setting.id]"
									/>
								</template>
							</AccordionTab>
							<!-- Section: Variables over time -->
							<AccordionTab v-if="selectedVariableSettings.length > 0" header="Variables over time">
								<template v-for="setting of selectedVariableSettings" :key="setting.id">
									<vega-chart
										expandable
										:are-embed-actions-visible="true"
										:visualization-spec="variableCharts[setting.id]"
									/>
								</template>
							</AccordionTab>
							<!-- Section: Comparison charts -->
							<AccordionTab v-if="selectedComparisonChartSettings.length > 0" header="Comparison charts">
								<div
									class="flex justify-content-center"
									v-for="setting of selectedComparisonChartSettings"
									:key="setting.id"
								>
									<div class="flex flex-row flex-wrap" v-if="setting.selectedVariables.length > 0">
										<vega-chart
											v-for="(spec, index) of comparisonCharts[setting.id]"
											:key="index"
											expandable
											:are-embed-actions-visible="true"
											:visualization-spec="spec"
										/>
									</div>
									<div v-else class="empty-state-chart">
										<img
											src="@assets/svg/operator-images/simulate-deterministic.svg"
											alt="Select a variable"
											draggable="false"
											height="80px"
										/>
										<p class="text-center">Select a variable for comparison</p>
									</div>
								</div>
							</AccordionTab>
							<!-- Section: Sensitivity -->
							<AccordionTab v-if="selectedSensitivityChartSettings.length > 0" header="Sensitivity analysis">
								<p class="ml-2 mb-3">
									A selected outcome (a model output at a given timepoint) can be strongly or weakly sensitive on the
									value of the different model parameters. Color is used here to illustrate this mapping: if the color
									varies quickly along a parameter axis, then the outcome is strongly sensitive to this parameter.
								</p>
								<tera-progress-spinner v-if="sensitivityCharts.loading" :font-size="2" is-centered />

								<div v-else>
									<template v-for="setting of selectedSensitivityChartSettings" :key="setting.id">
										<template v-if="sensitivityCharts.data[setting.id]">
											<vega-chart
												expandable
												:are-embed-actions-visible="true"
												:visualization-spec="sensitivityCharts.data[setting.id].lineChart"
											/>
											<vega-chart
												expandable
												:are-embed-actions-visible="true"
												:visualization-spec="sensitivityCharts.data[setting.id].rankingChart"
											/>
											<div class="sensitivity-scatterplot">
												<vega-chart
													expandable
													:are-embed-actions-visible="true"
													:visualization-spec="sensitivityCharts.data[setting.id].scatterChart"
												/>
											</div>
										</template>
									</template>
								</div>
							</AccordionTab>
						</Accordion>

						<!-- Empty state if all sections are empty -->
						<div
							v-if="
								isEmpty(selectedInterventionSettings) &&
								isEmpty(selectedVariableSettings) &&
								isEmpty(selectedComparisonChartSettings) &&
								isEmpty(selectedSensitivityChartSettings)
							"
						>
							<div class="empty-state-chart">
								<img
									src="@assets/svg/operator-images/simulate-deterministic.svg"
									alt=""
									draggable="false"
									height="80px"
								/>
								<p class="text-center">Configure charts in the output settings.</p>
							</div>
						</div>

						<!-- Spacer at bottom of page -->
						<div style="height: 2rem"></div>
					</div>
					<div v-else-if="view === OutputView.Data" class="p-3">
						<tera-dataset-datatable
							v-if="rawContent[selectedRunId]"
							:rows="10"
							:raw-content="rawContent[selectedRunId]"
						/>
					</div>
				</template>
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
							[ChartSettingType.VARIABLE, ChartSettingType.VARIABLE_COMPARISON].includes(
								activeChartSettings?.type as ChartSettingType
							)
								? getChartAnnotationsByChartId(activeChartSettings?.id ?? '')
								: undefined
						"
						:active-settings="activeChartSettings"
						:generate-annotation="generateAnnotation"
						:comparison="activeChartSettings?.type === ChartSettingType.VARIABLE_COMPARISON"
						@update-settings="updateActiveChartSettings"
						@delete-annotation="deleteAnnotation"
						@close="setActiveChartSettings(null)"
					>
						<template #normalize-content>
							<div
								class="p-3 border-2 border-gray-300 border-round-md font-italic bg-gray-100"
								v-for="(equation, i) of normalizeEquations"
								:key="i"
							>
								{{ equation }}
							</div>
						</template>
					</tera-chart-settings-panel>
				</template>
				<template #content>
					<div class="output-settings-panel">
						<!-- Intervention charts -->
						<tera-chart-settings
							:title="'Interventions over time'"
							:settings="chartSettings"
							:type="ChartSettingType.INTERVENTION"
							:select-options="Object.keys(groupedInterventionOutputs)"
							:selected-options="selectedInterventionSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@selection-change="updateChartSettings"
						/>
						<Divider />
						<!-- Variable charts -->
						<tera-chart-settings
							:title="'Variables over time'"
							:settings="chartSettings"
							:type="ChartSettingType.VARIABLE"
							:select-options="
								Object.keys(pyciemssMap).filter((c) => ['state', 'observable'].includes(modelPartTypesMap[c]))
							"
							:selected-options="selectedVariableSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@selection-change="updateChartSettings"
						/>
						<Divider />
						<!-- Comparison charts -->
						<tera-chart-settings
							:title="'Comparison charts'"
							:settings="chartSettings"
							:type="ChartSettingType.VARIABLE_COMPARISON"
							:select-options="Object.keys(pyciemssMap)"
							:comparison-selected-options="comparisonChartsSettingsSelection"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@comparison-selection-change="updateComparisonChartSetting"
						/>
						<div>
							<Button
								size="small"
								text
								@click="addEmptyComparisonChart"
								label="Add comparison chart"
								icon="pi pi-plus"
								class="mt-2"
							/>
						</div>
						<Divider />

						<!--
						FIXME: Need to support this scheme
               inputs = [a, b, c]
               outputs = [x, y]
							 That results in
                - { a, b, c } => X
                - { a, b, c } => Y
						-->
						<tera-chart-settings
							:title="'Sensitivity analysis'"
							:settings="chartSettings"
							:type="ChartSettingType.SENSITIVITY"
							:select-options="
								Object.keys(pyciemssMap).filter((c) => ['state', 'observable'].includes(modelPartTypesMap[c]))
							"
							:selected-options="selectedSensitivityChartSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							:sensitivity-options="{
								inputOptions: Object.keys(pyciemssMap).filter((c) => ['parameter'].includes(modelPartTypesMap[c])),
								selectedInputOptions: selectedSensitivityChartSettings[0]?.selectedInputVariables ?? [],
								timepoint: selectedSensitivityChartSettings[0]?.timepoint ?? lastTimepoint
							}"
							@selection-change="
								(e) =>
									updateSensitivityChartSettings({
										selectedVariables: e,
										selectedInputVariables: selectedSensitivityChartSettings[0]?.selectedInputVariables ?? [],
										timepoint: selectedSensitivityChartSettings[0]?.timepoint ?? lastTimepoint
									})
							"
							@sensitivity-selection-change="
								(e) =>
									updateSensitivityChartSettings({
										selectedVariables: selectedSensitivityChartSettings.map((s) => s.selectedVariables[0]),
										...e
									})
							"
						/>
						<Divider />
						<tera-chart-settings-quantiles :settings="chartSettings" @update-options="updateQauntilesOptions" />
						<Divider />
					</div>
				</template>
			</tera-slider-panel>
		</template>
	</tera-drilldown>
	<tera-save-simulation-modal
		:is-visible="showSaveDataset"
		@close-modal="showSaveDataset = false"
		:simulation-id="node.state.forecastId"
		:assets="[{ id: datasetId, type: AssetType.Dataset }]"
	/>
</template>

<script setup lang="ts">
import _, { isEmpty } from 'lodash';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { VAceEditor } from 'vue3-ace-editor';
import { VAceEditorInstance } from 'vue3-ace-editor/types';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import Divider from 'primevue/divider';
import SelectButton from 'primevue/selectbutton';
import type {
	CsvAsset,
	InterventionPolicy,
	Model,
	ModelConfiguration,
	SimulationRequest,
	TimeSpan
} from '@/types/Types';
import { AssetType } from '@/types/Types';
import type { WorkflowNode } from '@/types/workflow';
import { deleteAnnotation } from '@/services/chart-settings';
import { flattenInterventionData, getInterventionPolicyById } from '@/services/intervention-policy';
import {
	getModelByModelConfigurationId,
	getUnitsFromModelParts,
	getCalendarSettingsFromModel,
	getTypesFromModelParts,
	groupVariablesByStrata
} from '@/services/model';
import { getModelConfigurationById } from '@/services/model-configurations';
import {
	getRunResultCSV,
	parsePyCiemssMap,
	makeForecastJobCiemss as makeForecastJob,
	convertToCsvAsset,
	DataArray,
	CiemssMethodOptions
} from '@/services/models/simulation-service';
import { logger } from '@/utils/logger';
import { ChartSettingType, CiemssPresetTypes, DrilldownTabs } from '@/types/common';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { KernelSessionManager } from '@/services/jupyter';
import TeraChartSettings from '@/components/widgets/tera-chart-settings.vue';
import TeraChartSettingsPanel from '@/components/widgets/tera-chart-settings-panel.vue';
import TeraChartSettingsQuantiles from '@/components/widgets/tera-chart-settings-quantiles.vue';
import TeraDatasetDatatable from '@/components/dataset/tera-dataset-datatable.vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import TeraInterventionSummaryCard from '@/components/intervention-policy/tera-intervention-summary-card.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import TeraNotebookJupyterInput from '@/components/llm/tera-notebook-jupyter-input.vue';
import TeraOperatorOutputSummary from '@/components/operator/tera-operator-output-summary.vue';
import TeraPyciemssCancelButton from '@/components/pyciemss/tera-pyciemss-cancel-button.vue';
import TeraSaveSimulationModal from '@/components/project/tera-save-simulation-modal.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraTimestepCalendar from '@/components/widgets/tera-timestep-calendar.vue';
import { nodeMetadata } from '@/components/workflow/util';
import { useCharts } from '@/composables/useCharts';
import { useChartSettings } from '@/composables/useChartSettings';
import { useProjects } from '@/composables/project';
import { useDrilldownChartSize } from '@/composables/useDrilldownChartSize';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { SimulateCiemssOperationState } from './simulate-ciemss-operation';
import { mergeResults, renameFnGenerator } from '../calibrate-ciemss/calibrate-utils';
import { qualityPreset, speedPreset, usePreparedChartInputs } from './simulate-utils';
import { isInterventionPolicyBlank } from '../intervention-policy/intervention-policy-operation';

const props = defineProps<{
	node: WorkflowNode<SimulateCiemssOperationState>;
}>();
const emit = defineEmits(['update-state', 'select-output', 'close']);

const isSidebarOpen = ref(true);
const isOutputSettingsPanelOpen = ref(false);
const blankMessage = "Click 'Run' to start the simulation";

const currentActiveIndicies = ref([0, 1, 2, 3]);

const modelVarUnits = ref<{ [key: string]: string }>({});
let editor: VAceEditorInstance['_editor'] | null;
const codeText = ref('');

const modelConfiguration = ref<ModelConfiguration | null>(null);
const model = ref<Model | null>(null);

const lastTimepoint = computed<number>(() => {
	if (isEmpty(runResults.value) || !selectedRunId.value) return 0;
	const lastResult = _.last(runResults.value[selectedRunId.value]);
	return lastResult?.timepoint_id ?? 0;
});
const modelStateUnits = computed(() => {
	const states = model.value?.model.states;
	let units = {};
	if (states.length) {
		units = _.keyBy(
			states.map((state) => ({ id: state.id, units: state?.units?.expression ?? '' })),
			'id'
		);
	}
	return units;
});

const modelPartTypesMap = computed(() => (!model.value ? {} : getTypesFromModelParts(model.value)));

const modelParameterUnits = computed(() => {
	const parametes = model.value?.semantics?.ode?.parameters;
	let units = {};
	if (parametes?.length) {
		units = _.keyBy(
			parametes.map((parameter) => ({ id: parameter.id, units: parameter?.units?.expression ?? '' })),
			'id'
		);
	}
	return units;
});

const policyInterventionId = computed(() => props.node.inputs[1].value?.[0]);
const interventionPolicy = ref<InterventionPolicy | null>(null);
const datasetId = computed(() => {
	if (!selectedOutputId.value) return '';
	const output = props.node.outputs.find((o) => o.id === selectedOutputId.value);
	return output?.value?.[0] ?? '';
});

const selectedOutputLabel = computed(() => {
	const selectedOutput = props.node.outputs.find((output) => output.isSelected);
	return selectedOutput ? selectedOutput.label : '';
});

const llmThoughts = ref<any[]>([]);
const llmQuery = ref('');

// input params
const timespan = ref<TimeSpan>(props.node.state.currentTimespan);
const numSamples = ref<number>(props.node.state.numSamples);
const solverStepSize = ref<number>(props.node.state.solverStepSize);
const method = ref(props.node.state.method);

enum OutputView {
	Charts = 'Charts',
	Data = 'Data'
}

const updateLlmQuery = (query: string) => {
	llmThoughts.value = [];
	llmQuery.value = query;
};

const processLLMOutput = (data: any) => {
	codeText.value = data.content.code;
};

const view = ref(OutputView.Charts);
const viewOptions = ref([
	{ value: OutputView.Charts, icon: 'pi pi-image' },
	{ value: OutputView.Data, icon: 'pi pi-list' }
]);

const inProgressForecastRun = computed(() =>
	Boolean(props.node.state.inProgressForecastId || props.node.state.inProgressBaseForecastId)
);
const runResults = ref<{ [runId: string]: DataArray }>({});
const runResultsSummary = ref<{ [runId: string]: DataArray }>({});
const rawContent = ref<{ [runId: string]: CsvAsset }>({});

const pyciemssMap = ref<Record<string, string>>({});

const kernelManager = new KernelSessionManager();

const presetType = computed(() => {
	if (
		numSamples.value === speedPreset.numSamples &&
		method.value === speedPreset.method &&
		solverStepSize.value === speedPreset.stepSize
	) {
		return CiemssPresetTypes.Fast;
	}
	if (numSamples.value === qualityPreset.numSamples && method.value === qualityPreset.method) {
		return CiemssPresetTypes.Normal;
	}

	return '';
});

const selectedOutputId = ref<string>();
const selectedRunId = computed(() => props.node.outputs.find((o) => o.id === selectedOutputId.value)?.value?.[0]);

const cancelRunIds = computed(() =>
	[props.node.state.inProgressForecastId, props.node.state.inProgressBaseForecastId].filter((id) => Boolean(id))
);
const chartWidthDiv = ref(null);
const chartSize = useDrilldownChartSize(chartWidthDiv);

const showSaveDataset = ref(false);

const setPresetValues = (data: CiemssPresetTypes) => {
	if (data === CiemssPresetTypes.Normal) {
		numSamples.value = qualityPreset.numSamples;
		method.value = qualityPreset.method;
	}
	if (data === CiemssPresetTypes.Fast) {
		numSamples.value = speedPreset.numSamples;
		method.value = speedPreset.method;
		solverStepSize.value = speedPreset.stepSize;
	}
	updateState();
};

const groupedInterventionOutputs = computed(() =>
	_.groupBy(flattenInterventionData(interventionPolicy.value?.interventions ?? []), 'appliedTo')
);

const isRunDisabled = computed(
	() =>
		// run is disable if the attached intervention policy is blank
		!!interventionPolicy.value && isInterventionPolicyBlank(interventionPolicy.value)
);

const preparedChartInputs = usePreparedChartInputs(props, runResults, runResultsSummary, pyciemssMap);
const {
	activeChartSettings,
	chartSettings,
	selectedVariableSettings,
	selectedInterventionSettings,
	selectedComparisonChartSettings,
	selectedSensitivityChartSettings,
	comparisonChartsSettingsSelection,
	removeChartSettings,
	updateChartSettings,
	updateSensitivityChartSettings,
	updateActiveChartSettings,
	setActiveChartSettings,
	addEmptyComparisonChart,
	updateComparisonChartSetting,
	updateQauntilesOptions
} = useChartSettings(props, emit);

const {
	generateAnnotation,
	getChartAnnotationsByChartId,
	useInterventionCharts,
	useVariableCharts,
	useComparisonCharts,
	useSimulateSensitivityCharts
} = useCharts(
	props.node.id,
	model,
	modelConfiguration,
	preparedChartInputs,
	chartSize,
	computed(() => interventionPolicy.value?.interventions ?? []),
	null
);
const interventionCharts = useInterventionCharts(selectedInterventionSettings, true);
const variableCharts = useVariableCharts(selectedVariableSettings, null);
const comparisonCharts = useComparisonCharts(selectedComparisonChartSettings);
const sensitivityCharts = useSimulateSensitivityCharts(selectedSensitivityChartSettings);

const normalizeEquations = computed(() => {
	const equations: string[] = [];
	if (!activeChartSettings.value || !preparedChartInputs.value?.pyciemssMap || !model.value) return equations;
	const { selectedVariablesGroupByStrata, allVariablesGroupByStrata } = groupVariablesByStrata(
		activeChartSettings.value.selectedVariables,
		preparedChartInputs.value.pyciemssMap,
		model.value
	);
	Object.entries(selectedVariablesGroupByStrata).forEach(([group, variables]) => {
		if (group === '') return;
		const denominator = allVariablesGroupByStrata[group].map((v) => `${v}(t)`).join(' + ');
		variables.forEach((variable) => {
			const equation = `${variable}(t) / (${denominator})`;
			equations.push(equation);
		});
	});
	return equations;
});

const updateState = () => {
	const state = _.cloneDeep(props.node.state);
	state.currentTimespan = timespan.value;
	state.numSamples = numSamples.value;
	state.method = method.value;
	state.solverStepSize = solverStepSize.value;
	emit('update-state', state);
};

const run = async () => {
	const [baseSimulationId, simulationId] = await Promise.all([
		// If intervention id is available, request the base forecast run, otherwise resolve with empty string
		policyInterventionId.value ? makeForecastRequest(false) : Promise.resolve(''),
		makeForecastRequest()
	]);

	const state = _.cloneDeep(props.node.state);
	state.inProgressBaseForecastId = baseSimulationId;
	state.inProgressForecastId = simulationId;
	emit('update-state', state);
};

const makeForecastRequest = async (applyInterventions = true) => {
	const modelConfigId = props.node.inputs[0].value?.[0];
	const payload: SimulationRequest = {
		modelConfigId,
		timespan: {
			start: timespan.value.start,
			end: timespan.value.end
		},
		extra: {
			solver_method: method.value,
			solver_step_size: solverStepSize.value,
			num_samples: numSamples.value
		},
		engine: 'ciemss'
	};

	const modelConfig = await getModelConfigurationById(modelConfigId);
	if (modelConfig.simulationId) {
		payload.extra.inferred_parameters = modelConfig.simulationId;
	}

	if (applyInterventions && policyInterventionId.value) {
		payload.policyInterventionId = policyInterventionId.value;
	}
	const response = await makeForecastJob(payload, {
		...nodeMetadata(props.node),
		isBaseForecast: !applyInterventions
	});
	return response.id;
};

const lazyLoadSimulationData = async (outputRunId: string) => {
	if (runResults.value[outputRunId] && rawContent.value[outputRunId]) return;

	const forecastId = props.node.state.forecastId;
	if (!forecastId || inProgressForecastRun.value) return;

	let [result, resultSummary] = await Promise.all([
		getRunResultCSV(forecastId, 'result.csv'),
		getRunResultCSV(forecastId, 'result_summary.csv')
	]);
	pyciemssMap.value = parsePyCiemssMap(result[0]);
	rawContent.value[outputRunId] = convertToCsvAsset(result, Object.values(pyciemssMap.value));

	// Forecast results without the interventions
	const baseForecastId = props.node.state.baseForecastId;
	if (baseForecastId) {
		const [baseResult, baseResultSummary] = await Promise.all([
			getRunResultCSV(baseForecastId, 'result.csv', renameFnGenerator('pre')),
			getRunResultCSV(baseForecastId, 'result_summary.csv', renameFnGenerator('pre'))
		]);
		const merged = mergeResults(baseResult, result, baseResultSummary, resultSummary);
		result = merged.result;
		resultSummary = merged.resultSummary;
	}
	runResults.value[outputRunId] = result;
	runResultsSummary.value[outputRunId] = resultSummary;
	if (_.isNil(props.node.state.chartSettings)) isOutputSettingsPanelOpen.value = true;
};

const onSelection = (id: string) => {
	emit('select-output', id);
};

const buildJupyterContext = async () => {
	const modelConfigId = props.node.inputs[0].value?.[0];
	if (!modelConfigId) return;
	try {
		const jupyterContext = {
			context: 'pyciemss',
			language: 'python3',
			context_info: {
				model_config_id: modelConfigId
			}
		};
		if (jupyterContext) {
			if (kernelManager.jupyterSession !== null) {
				kernelManager.shutdown();
			}
			await kernelManager.init('beaker_kernel', 'Beaker Kernel', jupyterContext);
			kernelManager.sendMessage('get_simulate_request', {}).register('any_get_simulate_reply', (data) => {
				codeText.value = data.msg.content.return;
			});
		}
	} catch (error) {
		logger.error(`Error initializing Jupyter session: ${error}`);
	}
};

const runCode = () => {
	const code = editor?.getValue();
	if (!code) return;

	const messageContent = {
		silent: false,
		store_history: false,
		user_expressions: {},
		allow_stdin: true,
		stop_on_error: false,
		code
	};

	// TODO: Utilize the output of this request.
	kernelManager
		.sendMessage('execute_request', { messageContent })
		.register('execute_input', (data) => {
			console.log(data.content.code);
		})
		.register('stream', (data) => {
			console.log('stream', data);
		})
		.register('any_execute_reply', (data) => {
			console.log(data);
			// FIXME: save isnt working...but the idea is to save the simulation results to the HMI with this action
			kernelManager
				.sendMessage('save_results_to_hmi_request', { project_id: useProjects().activeProjectId })
				.register('code_cell', (d) => {
					console.log(d);
				});
		});
};
const initializeAceEditor = (editorInstance: any) => {
	editor = editorInstance;
};

watch(
	() => props.node.inputs[0].value,
	async () => {
		const input = props.node.inputs[0];
		if (!input.value) return;

		const id = input.value[0];
		modelConfiguration.value = await getModelConfigurationById(id);
		model.value = await getModelByModelConfigurationId(id);
		if (model.value) modelVarUnits.value = getUnitsFromModelParts(model.value);
	},
	{ immediate: true }
);

watch(
	() => props.node.active,
	async (newValue, oldValue) => {
		if (!props.node.active || newValue === oldValue) return;
		selectedOutputId.value = props.node.active;

		// Update Wizard form fields with current selected output state
		timespan.value = props.node.state.currentTimespan;
		numSamples.value = props.node.state.numSamples;
		solverStepSize.value = props.node.state.solverStepSize;
		method.value = props.node.state.method;

		lazyLoadSimulationData(selectedRunId.value);
	},
	{ immediate: true }
);

// fetch intervention policy
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

onMounted(() => {
	buildJupyterContext();
});

onUnmounted(() => kernelManager.shutdown());
</script>

<style scoped>
/* Make tera-inputs the same height as the dropdowns */
.tera-input:deep(main) {
	padding: 6px;
}

/* Override grid template so output expands when sidebar is closed */
.overlay-container:deep(section.drilldown main) {
	grid-template-columns: auto 1fr;
}

/* Override top and bottom padding of content-container */
.overlay-container:deep(section.drilldown main .content-container) {
	padding: 0 var(--gap-4);
}

.empty-chart {
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	height: 10rem;
	gap: var(--gap-4);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	margin-bottom: var(--gap-4);
	color: var(--text-color-secondary);
	background: var(--surface-50);
}

.empty-image {
	width: 5rem;
	height: 6rem;
}

/* Notebook */
.notebook-section {
	width: calc(50vw - 4rem);
}

.notebook-section:deep(main) {
	gap: var(--gap-2);
	position: relative;
}

.form-section {
	display: flex;
	flex-direction: column;
	flex-grow: 1;
	gap: var(--gap-4);
	padding: var(--gap-4);
}

.label-and-input {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	margin-bottom: var(--gap-4);
}

.input-row {
	width: 100%;
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	align-items: center;
	gap: 0.5rem;

	& > * {
		flex: 1;
	}
}

.p-button-icon-left {
	color: var(--text-color-primary);
}

.output-settings-panel {
	padding: var(--gap-4);
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	hr {
		border: 0;
		border-top: 1px solid var(--surface-border-alt);
		width: 100%;
	}
}
.empty-state-chart {
	display: flex;
	flex-direction: column;
	flex-grow: 1;
	gap: var(--gap-4);
	justify-content: center;
	align-items: center;
	height: 12rem;
	margin: var(--gap-6);
	padding: var(--gap-4);
	background: var(--surface-100);
	color: var(--text-color-secondary);
	border-radius: var(--border-radius);
}

.comparison-chart-container {
	display: flex;
	flex-direction: column;
	display: inline-block;

	.comparison-chart {
		float: left;
		box-sizing: border-box;
		width: 40%;
	}
}
.common-input-height:deep(main) {
	height: 2.35rem;
}
.sensitivity-scatterplot {
	width: 100%;
	display: flex;
	overflow: scroll;
}
</style>
