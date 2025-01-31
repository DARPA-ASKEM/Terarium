<template>
	<tera-drilldown
		v-bind="$attrs"
		:node="node"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<!-- Wizard tab -->
		<section :tabName="DrilldownTabs.Wizard" class="wizard">
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Optimize intervention settings"
				content-width="600px"
			>
				<template #content>
					<div class="toolbar">
						<p>Click Run to start optimization.</p>
						<span class="flex gap-2">
							<Button
								label="Reset"
								outlined
								@click="resetState"
								severity="secondary"
								:disabled="_.isEmpty(node.outputs[0].value)"
							/>
							<tera-pyciemss-cancel-button class="mr-auto" :simulation-run-id="cancelRunId" />
							<div v-tooltip="runButtonMessage">
								<Button :disabled="isRunDisabled" label="Run" icon="pi pi-play" @click="runOptimize" />
							</div>
						</span>
					</div>

					<section class="form-section">
						<h5>Success criteria</h5>
						<tera-optimize-criterion-group-form
							v-for="(cfg, index) in knobs.constraintGroups"
							:key="selectedOutputId + ':' + index"
							:index="index"
							:criterion="cfg"
							:model-state-and-obs-options="modelStateAndObsOptions"
							@update-self="(config) => updateCriterionGroupForm(index, config)"
							@delete-self="() => deleteCriterionGroupForm(index)"
						/>
						<Button
							icon="pi pi-plus"
							class="p-button-sm p-button-text w-max"
							label="Add new criterion"
							@click="addCriterionGroupForm"
						/>
					</section>
					<section class="form-section">
						<h5>
							Intervention policy
							<i v-if="!isInterventionReady" v-tooltip="interventionReadyTooltip" class="pi pi-exclamation-circle" />
						</h5>
						<template v-for="(cfg, idx) in knobs.interventionPolicyGroups">
							<tera-static-intervention-policy-group
								v-if="
									cfg.intervention?.staticInterventions &&
									cfg.intervention?.staticInterventions.length > 0 &&
									modelConfiguration &&
									model
								"
								:model="model"
								:model-configuration="modelConfiguration"
								:key="cfg.id || '' + idx"
								:config="cfg"
								@update-self="(config) => updateInterventionPolicyGroupForm(idx, config)"
							/>
						</template>
						<section class="empty-state" v-if="knobs.interventionPolicyGroups.length === 0">
							<p class="mt-1">No intervention policies have been added.</p>
						</section>
						<template v-for="(cfg, idx) in knobs.interventionPolicyGroups">
							<tera-dynamic-intervention-policy-group
								v-if="cfg.intervention?.dynamicInterventions && cfg.intervention?.dynamicInterventions.length > 0"
								:key="idx"
								:config="cfg"
								@update-self="(config) => updateInterventionPolicyGroupForm(idx, config)"
							/>
						</template>
					</section>
					<section class="form-section">
						<h5>Optimization settings</h5>
						<div class="input-row pt-1">
							<tera-timestep-calendar
								disabled
								v-if="model && modelConfiguration"
								label="Start time"
								:start-date="modelConfiguration.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								:model-value="0"
							/>
							<tera-timestep-calendar
								v-if="model && modelConfiguration"
								label="End time"
								:start-date="modelConfiguration.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								v-model="knobs.endTime"
							/>
							<i
								class="pi pi-exclamation-circle"
								style="max-width: fit-content"
								v-if="!isEndTimeValid"
								v-tooltip="endTimeTooltip"
							/>
						</div>
						<div class="input-row">
							<div class="label-and-input">
								<label>Preset (optional)</label>
								<Dropdown
									v-model="presetType"
									placeholder="Select an option"
									:options="[CiemssPresetTypes.Fast, CiemssPresetTypes.Normal]"
									@update:model-value="setPresetValues"
								/>
							</div>
						</div>
						<div>
							<Button
								v-if="!showAdditionalOptions"
								icon="pi pi-plus-circle"
								class="p-button-sm p-button-text"
								label="Show additional options"
								@click="toggleAdditionalOptions"
							/>
							<Button
								v-if="showAdditionalOptions"
								icon="pi pi-minus-circle"
								class="p-button-sm p-button-text"
								label="Hide additional options"
								@click="toggleAdditionalOptions"
							/>
						</div>
						<!-- Additional options -->
						<div v-if="showAdditionalOptions" class="additional-options">
							<div class="input-row">
								<div class="label-and-input">
									<label>Number of samples to simulate model</label>
									<div>
										<tera-input-number v-model="knobs.numSamples" />
									</div>
								</div>
								<div class="label-and-input">
									<label><br />Solver method</label>
									<Dropdown
										class="p-inputtext-sm"
										:options="[CiemssMethodOptions.dopri5, CiemssMethodOptions.rk4, CiemssMethodOptions.euler]"
										v-model="knobs.solverMethod"
										placeholder="Select"
									/>
								</div>
								<div class="label-and-input">
									<label><br />Solver step size</label>
									<div>
										<tera-input-number
											v-model="knobs.solverStepSize"
											:disabled="![CiemssMethodOptions.rk4, CiemssMethodOptions.euler].includes(knobs.solverMethod)"
											:min="0"
										/>
									</div>
								</div>
							</div>
							<div class="input-row">
								<h3>Optimizer options</h3>
							</div>
							<div class="input-row">
								<div class="label-and-input">
									<label>Algorithm</label>
									<tera-input-text disabled model-value="basinhopping" />
								</div>
								<div class="label-and-input">
									<label>Minimizer method</label>
									<tera-input-text disabled model-value="COBYLA" />
								</div>
								<div class="label-and-input">
									<label>Maxiter</label>
									<tera-input-number v-model="knobs.maxiter" />
								</div>
								<div class="label-and-input">
									<label>Maxfeval</label>
									<tera-input-number v-model="knobs.maxfeval" />
								</div>
							</div>
						</div>
					</section>
					<!-- This used to be in the footer -->
					<tera-save-dataset-from-simulation
						:simulation-run-id="knobs.postForecastRunId"
						:showDialog="showSaveDataDialog"
						@dialog-hide="showSaveDataDialog = false"
					/>
				</template>
			</tera-slider-panel>
		</section>

		<!-- Notebook tab -->
		<section :tabName="DrilldownTabs.Notebook" class="notebook-section">
			<p>Under construction. Use the wizard for now.</p>
			<div class="result-message-grid">
				<p class="mb-2">For debugging:</p>
				<div v-for="(value, key) in optimizeRequestPayload" :key="key" class="result-message-row">
					<div class="label">{{ key }}:</div>
					<div class="value">{{ formatJsonValue(value) }}</div>
				</div>
			</div>
		</section>

		<!-- Preview tab -->
		<template #preview>
			<tera-drilldown-section
				:is-loading="showSpinner"
				:loading-progress="props.node.state.currentProgress"
				:loading-message="'of maximum iterations complete'"
				:is-blank="showSpinner && node.state.inProgressOptimizeId === ''"
				:blank-message="'Optimize complete. Running simulations'"
				class="ml-3 mr-3"
				:class="{
					'failed-run': optimizationResult.success === 'False',
					'successful-run': optimizationResult.success !== 'False'
				}"
			>
				<template #header-controls-left v-if="optimizedInterventionPolicy?.name">
					<h5>{{ optimizedInterventionPolicy?.name }}</h5>
				</template>
				<template #header-controls-right>
					<Button
						label="Save for re-use"
						severity="secondary"
						outlined
						:disabled="!optimizedInterventionPolicy"
						@click="showSaveInterventionPolicy = true"
					/>
				</template>
				<tera-operator-output-summary v-if="node.state.summaryId && !showSpinner" :summary-id="node.state.summaryId" />
				<!-- Optimize result.json display: -->
				<div
					v-if="optimizationResult && displayOptimizationResultMessage && !showSpinner"
					class="result-message-grid mt-2 mb-2"
				>
					<span class="flex flex-row">
						<p class="mt-2">For debugging</p>
						<Button
							icon="pi pi-times"
							text
							rounded
							size="small"
							class="ml-auto p-button-text"
							@click="displayOptimizationResultMessage = !displayOptimizationResultMessage"
						/>
					</span>
					<div v-for="(value, key) in optimizationResult" :key="key" class="result-message-row">
						<div class="label">{{ key }}:</div>
						<div class="value">{{ formatJsonValue(value) }}</div>
					</div>
				</div>
				<SelectButton
					v-if="!showSpinner"
					:model-value="outputViewSelection"
					@change="if ($event.value) outputViewSelection = $event.value;"
					:options="outputViewOptions"
					option-value="value"
					class="select-button my-2"
				>
					<template #option="{ option }">
						<i :class="`${option.icon} p-button-icon-left`" />
						<span class="p-button-label">{{ option.value }}</span>
					</template>
				</SelectButton>
				<tera-notebook-error v-bind="node.state.optimizeErrorMessage" />
				<tera-notebook-error v-bind="node.state.simulateErrorMessage" />
				<template v-if="runResults[knobs.postForecastRunId] && runResults[knobs.preForecastRunId] && !showSpinner">
					<section v-if="outputViewSelection === OutputView.Charts">
						<div class="mx-4" ref="chartWidthDiv"></div>
						<Accordion multiple :active-index="currentActiveIndicies">
							<AccordionTab header="Success criteria">
								<ul>
									<li v-for="(_constraint, key) in knobs.constraintGroups" :key="key">
										<vega-chart
											expandable
											are-embed-actions-visible
											:visualization-spec="preparedSuccessCriteriaCharts[key]"
										/>
									</li>
								</ul>
							</AccordionTab>
							<AccordionTab header="Interventions over time">
								<ul>
									<li v-for="setting of selectedInterventionSettings" :key="setting.id">
										<vega-chart
											expandable
											are-embed-actions-visible
											:visualization-spec="interventionCharts[setting.id]"
										/>
									</li>
								</ul>
							</AccordionTab>
							<AccordionTab header="Variables over time">
								<ul>
									<li v-for="setting of selectedVariableSettings" :key="setting.id">
										<vega-chart expandable are-embed-actions-visible :visualization-spec="variableCharts[setting.id]" />
									</li>
								</ul>
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
						</Accordion>
					</section>
					<div v-else-if="outputViewSelection === OutputView.Data">
						<tera-dataset-datatable
							v-if="simulationRawContent[knobs.postForecastRunId]"
							:rows="10"
							:raw-content="simulationRawContent[knobs.postForecastRunId] as CsvAsset"
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
						@update-settings="updateActiveChartSettings"
						@delete-annotation="deleteAnnotation"
						@close="setActiveChartSettings(null)"
					/>
				</template>
				<template #content>
					<div class="output-settings-panel">
						<!--Summary-->
						<tera-checkbox
							v-model="summaryCheckbox"
							inputId="generate-summary"
							label="Auto-generate operation summary"
							subtext="Generates a brief summary of the inputs and outputs."
							disabled
						/>
						<Divider />

						<!--Success Criteria-->
						<h5 class="mb-1">Success Criteria</h5>
						<tera-checkbox
							v-model="successDisplayChartsCheckbox"
							inputId="success-criteria-display-charts"
							label="Display chart(s)"
							subtext="Turn this on to generate an interactive chart of the success criteria conditions."
							disabled
						/>
						<Divider />
						<!-- Interventions charts -->
						<tera-chart-settings
							:title="'Interventions over time'"
							:settings="chartSettings"
							:type="ChartSettingType.INTERVENTION"
							:select-options="_.keys(preProcessedInterventionsData)"
							:selected-options="selectedInterventionSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@selection-change="updateChartSettings"
						/>
						<Divider />
						<!-- Variables charts -->
						<tera-chart-settings
							:title="'Variables over time'"
							:settings="chartSettings"
							:type="ChartSettingType.VARIABLE"
							:select-options="modelStateAndObsOptions.map((ele) => ele.label)"
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
							:select-options="simulationChartOptions"
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
					</div>
				</template>
			</tera-slider-panel>
		</template>
	</tera-drilldown>
	<tera-save-simulation-modal
		:initial-name="optimizedInterventionPolicy?.name"
		:is-visible="showSaveInterventionPolicy"
		:assets="[
			{ id: optimizedInterventionPolicy?.id ?? '', type: AssetType.InterventionPolicy },
			{ id: datasetId, type: AssetType.Dataset }
		]"
		@close-modal="showSaveInterventionPolicy = false"
		@on-save="onSaveForReuse"
		:simulation-id="node.state.postForecastRunId"
	/>
</template>

<script setup lang="ts">
import _, { cloneDeep, Dictionary } from 'lodash';
import { computed, onMounted, ref, watch } from 'vue';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import SelectButton from 'primevue/selectbutton';
import { useDrilldownChartSize } from '@/composables/useDrilldownChartSize';
import TeraSaveSimulationModal from '@/components/project/tera-save-simulation-modal.vue';
import TeraDatasetDatatable from '@/components/dataset/tera-dataset-datatable.vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraSaveDatasetFromSimulation from '@/components/dataset/tera-save-dataset-from-simulation.vue';
import TeraPyciemssCancelButton from '@/components/pyciemss/tera-pyciemss-cancel-button.vue';
import TeraOperatorOutputSummary from '@/components/operator/tera-operator-output-summary.vue';
import { getModelByModelConfigurationId, getCalendarSettingsFromModel } from '@/services/model';
import { getModelConfigurationById } from '@/services/model-configurations';
import {
	convertToCsvAsset,
	getRunResult,
	getRunResultCSV,
	makeOptimizeJobCiemss,
	getSimulation,
	CiemssMethodOptions
} from '@/services/models/simulation-service';
import {
	CsvAsset,
	Intervention,
	InterventionPolicy,
	ModelConfiguration,
	Model,
	OptimizeInterventions,
	OptimizeQoi,
	OptimizeRequestCiemss,
	AssetType
} from '@/types/Types';
import { logger } from '@/utils/logger';
import { getActiveOutput, nodeMetadata } from '@/components/workflow/util';
import { WorkflowNode } from '@/types/workflow';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import { flattenInterventionData, getInterventionPolicyById } from '@/services/intervention-policy';
import TeraCheckbox from '@/components/widgets/tera-checkbox.vue';
import Divider from 'primevue/divider';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import { createSuccessCriteriaChart } from '@/services/charts';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { renameFnGenerator } from '@/components/workflow/ops/calibrate-ciemss/calibrate-utils';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import { ChartSettingType, CiemssPresetTypes, DrilldownTabs } from '@/types/common';
import { useConfirm } from 'primevue/useconfirm';
import TeraChartSettings from '@/components/widgets/tera-chart-settings.vue';
import TeraChartSettingsPanel from '@/components/widgets/tera-chart-settings-panel.vue';
import TeraTimestepCalendar from '@/components/widgets/tera-timestep-calendar.vue';
import { deleteAnnotation, updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import { useCharts } from '@/composables/useCharts';
import { useChartSettings } from '@/composables/useChartSettings';
import teraOptimizeCriterionGroupForm from './tera-optimize-criterion-group-form.vue';
import TeraStaticInterventionPolicyGroup from './tera-static-intervention-policy-group.vue';
import TeraDynamicInterventionPolicyGroup from './tera-dynamic-intervention-policy-group.vue';
import {
	blankInterventionPolicyGroup,
	Criterion,
	defaultCriterion,
	InterventionPolicyGroupForm,
	OptimizeCiemssOperationState,
	OptimizationInterventionObjective
} from './optimize-ciemss-operation';
import { setQoIData, usePreparedChartInputs } from './optimize-utils';
import { isInterventionPolicyBlank } from '../intervention-policy/intervention-policy-operation';

const confirm = useConfirm();

const isSidebarOpen = ref(true);
const isOutputSettingsPanelOpen = ref(false);

const props = defineProps<{
	node: WorkflowNode<OptimizeCiemssOperationState>;
}>();

const emit = defineEmits(['update-state', 'close', 'select-output']);

enum OutputView {
	Charts = 'Charts',
	Data = 'Data'
}

interface BasicKnobs {
	endTime: number;
	numSamples: number;
	solverStepSize: number;
	solverMethod: CiemssMethodOptions;
	maxiter: number;
	maxfeval: number;
	preForecastRunId: string;
	postForecastRunId: string;
	optimizationRunId: string;
	constraintGroups: Criterion[];
	interventionPolicyGroups: InterventionPolicyGroupForm[];
}

const knobs = ref<BasicKnobs>({
	endTime: props.node.state.endTime ?? 1,
	numSamples: props.node.state.numSamples ?? 0,
	solverStepSize: props.node.state.solverStepSize ?? 0.1,
	solverMethod: props.node.state.solverMethod ?? CiemssMethodOptions.dopri5,
	maxiter: props.node.state.maxiter ?? 5,
	maxfeval: props.node.state.maxfeval ?? 25,
	preForecastRunId: props.node.state.preForecastRunId ?? '',
	postForecastRunId: props.node.state.postForecastRunId ?? '',
	optimizationRunId: props.node.state.optimizationRunId ?? '',
	constraintGroups: props.node.state.constraintGroups ?? [],
	interventionPolicyGroups: props.node.state.interventionPolicyGroups ?? []
});

const currentActiveIndicies = ref([0, 1, 2, 3]);

const summaryCheckbox = ref(true);

const successDisplayChartsCheckbox = ref(true);

const showSaveDataDialog = ref<boolean>(false);
const showSaveInterventionPolicy = ref<boolean>(false);

const chartWidthDiv = ref(null);
const chartSize = useDrilldownChartSize(chartWidthDiv);
const cancelRunId = computed(() => props.node.state.inProgressPostForecastId || props.node.state.inProgressOptimizeId);

const activePolicyGroups = computed(() =>
	knobs.value.interventionPolicyGroups.filter((ele) => !!ele.relativeImportance)
);

const inactivePolicyGroups = computed(() =>
	knobs.value.interventionPolicyGroups.filter((ele) => !ele.relativeImportance)
);

const showSpinner = computed<boolean>(
	() => props.node.state.inProgressOptimizeId !== '' || props.node.state.inProgressPostForecastId !== ''
);

const datasetId = computed(() => {
	if (!selectedOutputId.value) return '';
	const output = props.node.outputs.find((o) => o.id === selectedOutputId.value);
	return output?.value?.[0]?.datasetId ?? '';
});

const displayOptimizationResultMessage = ref(true);

// Checks for disabling run button:
const isCriteriaReady = computed(() => {
	const activeConstraintGroups = knobs.value.constraintGroups.filter((ele) => ele.isActive);
	return activeConstraintGroups.length !== 0 && activeConstraintGroups.every((ele) => ele.targetVariable);
});

const selectedInterventionPolicy = ref<InterventionPolicy | null>(null);
const isInterventionReady = computed(
	() =>
		activePolicyGroups.value.length > 0 &&
		!!selectedInterventionPolicy.value &&
		!isInterventionPolicyBlank(selectedInterventionPolicy.value)
);

const isEndTimeValid = computed(() =>
	activePolicyGroups.value.every((ele) => {
		if (
			[OptimizationInterventionObjective.startTime, OptimizationInterventionObjective.paramValueAndStartTime].includes(
				ele.optimizationType
			)
		) {
			return ele.endTime <= knobs.value.endTime;
		}
		return true;
	})
);

const isRunDisabled = computed(() => !isCriteriaReady.value || !isInterventionReady.value || !isEndTimeValid.value);

const criteriaReadyTooltip = computed(() => (!isCriteriaReady.value ? 'Success criteria must be filled in. \n' : ''));
const interventionReadyTooltip = computed(() =>
	!isInterventionReady.value ? 'Must contain at least one active intervention policy.\n' : ''
);
const endTimeTooltip = computed(() =>
	!isEndTimeValid.value ? 'End time must be greater than or equal to all intervention policy end times. \n' : ''
);

const runButtonMessage = computed(() =>
	isRunDisabled.value ? `${criteriaReadyTooltip.value} ${interventionReadyTooltip.value} ${endTimeTooltip.value}` : ''
);

const presetType = computed(() => {
	if (
		knobs.value.numSamples === speedValues.numSamplesToSimModel &&
		knobs.value.solverMethod === speedValues.method &&
		knobs.value.maxiter === speedValues.maxiter &&
		knobs.value.maxfeval === speedValues.maxfeval &&
		knobs.value.solverStepSize === speedValues.solverStepSize
	) {
		return CiemssPresetTypes.Fast;
	}
	if (
		knobs.value.numSamples === qualityValues.numSamplesToSimModel &&
		knobs.value.solverMethod === qualityValues.method &&
		knobs.value.maxiter === qualityValues.maxiter &&
		knobs.value.maxfeval === qualityValues.maxfeval
	) {
		return CiemssPresetTypes.Normal;
	}
	return '';
});

const selectedOutputId = ref<string>();

const outputViewSelection = ref(OutputView.Charts);
const outputViewOptions = ref([
	{ value: OutputView.Charts, icon: 'pi pi-image' },
	{ value: OutputView.Data, icon: 'pi pi-list' }
]);
const runResults = ref<{ [runId: string]: any }>({});
const runResultsSummary = ref<{ [runId: string]: any }>({});
const riskResults = ref<{ [runId: string]: any }>({});
const simulationRawContent = ref<{ [runId: string]: CsvAsset | null }>({});
const optimizationResult = ref<any>('');
const optimizeRequestPayload = ref<any>('');
const optimizedInterventionPolicy = ref<InterventionPolicy | null>(null);

const model = ref<Model | null>(null);
const modelParameterOptions = computed<string[]>(() =>
	(model.value?.semantics?.ode?.parameters ?? []).map((p) => p.id)
);
const modelStateAndObsOptions = ref<{ label: string; value: string }[]>([]);

const simulationChartOptions = computed(() => [
	...modelParameterOptions.value,
	...modelStateAndObsOptions.value.map((ele) => ele.label)
]);
const modelConfiguration = ref<ModelConfiguration | null>(null);

const showAdditionalOptions = ref(true);

const onSelection = (id: string) => {
	emit('select-output', id);
};

const updateInterventionPolicyGroupForm = (index: number, config: InterventionPolicyGroupForm) => {
	knobs.value.interventionPolicyGroups[index] = config;
};

const addCriterionGroupForm = () => {
	knobs.value.constraintGroups.push(defaultCriterion);
};

const deleteCriterionGroupForm = (index: number) => {
	knobs.value.constraintGroups.splice(index, 1);
};

const updateCriterionGroupForm = (index: number, config: Criterion) => {
	knobs.value.constraintGroups[index] = config;
};

const toggleAdditionalOptions = () => {
	showAdditionalOptions.value = !showAdditionalOptions.value;
};

const formatJsonValue = (value) => {
	if (typeof value === 'object' && value !== null) {
		return JSON.stringify(value);
	}
	return value;
};

const setPresetValues = (data: CiemssPresetTypes) => {
	if (data === CiemssPresetTypes.Normal) {
		knobs.value.numSamples = qualityValues.numSamplesToSimModel;
		knobs.value.solverMethod = qualityValues.method;
		knobs.value.maxiter = qualityValues.maxiter;
		knobs.value.maxfeval = qualityValues.maxfeval;
	}
	if (data === CiemssPresetTypes.Fast) {
		knobs.value.numSamples = speedValues.numSamplesToSimModel;
		knobs.value.solverMethod = speedValues.method;
		knobs.value.maxiter = speedValues.maxiter;
		knobs.value.maxfeval = speedValues.maxfeval;
		knobs.value.solverStepSize = speedValues.solverStepSize;
	}
};

const speedValues = Object.freeze({
	numSamplesToSimModel: 1,
	method: CiemssMethodOptions.euler,
	maxiter: 0,
	maxfeval: 1,
	solverStepSize: 0.1
});

const qualityValues = Object.freeze({
	numSamplesToSimModel: 100,
	method: CiemssMethodOptions.dopri5,
	maxiter: 5,
	maxfeval: 25
});

const initialize = async () => {
	const modelConfigurationId = props.node.inputs[0].value?.[0];
	if (!modelConfigurationId) return;
	const results = await Promise.all([
		getModelConfigurationById(modelConfigurationId),
		getModelByModelConfigurationId(modelConfigurationId)
	]);
	modelConfiguration.value = results[0];
	model.value = results[1];

	const policyId = props.node.inputs[1]?.value?.[0];
	if (policyId) {
		// FIXME: This should be done in the node this should not be done in the drill down.
		getInterventionPolicyById(policyId).then((interventionPolicy) => {
			selectedInterventionPolicy.value = interventionPolicy;
			if (interventionPolicy) setInterventionPolicyGroups(interventionPolicy);
		});
	}

	const optimizedPolicyId = props.node.state.optimizedInterventionPolicyId;
	if (optimizedPolicyId) {
		optimizedInterventionPolicy.value = await getInterventionPolicyById(optimizedPolicyId);
	}
	const modelStates = model.value?.model.states;
	modelStateAndObsOptions.value = modelStates.map((state: any) => ({
		label: state.id,
		value: `${state.id}_state`
	}));

	// Add obs:
	const modelObs = model.value?.semantics?.ode.observables;
	if (modelObs) {
		modelStateAndObsOptions.value.push(
			...modelObs.map((observable: any) => ({
				label: observable.id,
				value: `${observable.id}_observable`
			}))
		);
	}
};

const setInterventionPolicyGroups = (interventionPolicy: InterventionPolicy) => {
	const state = _.cloneDeep(props.node.state);
	// If already set + not changed since set, do not reset.
	if (
		knobs.value.interventionPolicyGroups.length > 0 &&
		knobs.value.interventionPolicyGroups[0].id === interventionPolicy.id
	) {
		return;
	}
	state.interventionPolicyId = interventionPolicy.id ?? '';

	knobs.value.interventionPolicyGroups = []; // Reset prior to populating.
	if (interventionPolicy.interventions && interventionPolicy.interventions.length > 0) {
		interventionPolicy.interventions.forEach((intervention) => {
			const isNotActive = intervention.dynamicInterventions?.length > 0 || intervention.staticInterventions?.length > 1;
			const newIntervention = _.cloneDeep(blankInterventionPolicyGroup);
			newIntervention.id = interventionPolicy.id;
			newIntervention.intervention = intervention;
			newIntervention.relativeImportance = isNotActive ? 0 : 5;
			newIntervention.startTimeGuess = intervention.staticInterventions[0]?.timestep;
			newIntervention.initialGuessValue = intervention.staticInterventions[0]?.value;
			knobs.value.interventionPolicyGroups.push(newIntervention);
		});
	}
	emit('update-state', state);
};

const runOptimize = async () => {
	if (!modelConfiguration.value?.id) {
		logger.error('no model config id provided');
		return;
	}

	setOutputSettingDefaults();

	const optimizeInterventions: OptimizeInterventions[] = [];
	const listBoundsInterventions: number[][] = [];

	activePolicyGroups.value.forEach((ele) => {
		const paramNames: string[] = [];
		const paramValues: number[] = [];
		const startTime: number[] = [];
		const initialGuess: number[] = [];
		const relativeImportance: number[] = [];

		// Only allowed to optimize on interventions that arent grouped aka staticInterventions' length is 1
		paramNames.push(ele.intervention.staticInterventions[0].appliedTo);
		paramValues.push(ele.intervention.staticInterventions[0].value);
		startTime.push(ele.intervention.staticInterventions[0].timestep);
		relativeImportance.push(ele.relativeImportance);

		if (ele.optimizationType === OptimizationInterventionObjective.startTime) {
			initialGuess.push(ele.startTimeGuess);
			listBoundsInterventions.push([ele.startTime]);
			listBoundsInterventions.push([ele.endTime]);
		} else if (ele.optimizationType === OptimizationInterventionObjective.paramValue) {
			initialGuess.push(ele.initialGuessValue);
			listBoundsInterventions.push([ele.lowerBoundValue]);
			listBoundsInterventions.push([ele.upperBoundValue]);
		} else if (ele.optimizationType === OptimizationInterventionObjective.paramValueAndStartTime) {
			initialGuess.push(ele.startTimeGuess);
			initialGuess.push(ele.initialGuessValue);
			listBoundsInterventions.push([ele.lowerBoundValue]);
			listBoundsInterventions.push([ele.upperBoundValue]);
		} else {
			console.error(`invalid optimization type used:${ele.optimizationType}`);
		}

		optimizeInterventions.push({
			interventionType: ele.optimizationType,
			paramNames,
			startTime,
			paramValues,
			initialGuess,
			objectiveFunctionOption: ele.objectiveFunctionOption,
			relativeImportance: ele.relativeImportance
		});
	});

	// These are interventions to be considered but not optimized over.
	const fixedInterventions: Intervention[] = _.cloneDeep(inactivePolicyGroups.value.map((ele) => ele.intervention));

	const qois: OptimizeQoi[] = [];
	const activeConstraintGroups = knobs.value.constraintGroups.filter((ele) => ele.isActive);
	activeConstraintGroups.forEach((constraintGroup) =>
		qois.push({
			contexts: [constraintGroup.targetVariable],
			method: constraintGroup.qoiMethod,
			riskBound: constraintGroup.threshold,
			isMinimized: constraintGroup.isMinimized
		})
	);

	// riskTolerance to get alpha and divide by 100 to turn into a percent for pyciemss-service.
	const alphas: number[] = activeConstraintGroups.map((ele) => ele.riskTolerance / 100);
	const optimizePayload: OptimizeRequestCiemss = {
		userId: 'no_user_provided',
		engine: 'ciemss',
		modelConfigId: modelConfiguration.value.id,
		timespan: {
			start: 0,
			end: knobs.value.endTime
		},
		optimizeInterventions,
		fixedInterventions,
		qoi: qois,
		boundsInterventions: listBoundsInterventions,
		extra: {
			numSamples: knobs.value.numSamples,
			maxiter: knobs.value.maxiter,
			maxfeval: knobs.value.maxfeval,
			alpha: alphas,
			solverMethod: knobs.value.solverMethod,
			solverStepSize: knobs.value.solverStepSize
		}
	};

	// InferredParameters is to link a calibration run to this optimize call.
	optimizePayload.extra.inferredParameters = modelConfiguration.value.simulationId;
	const optResult = await makeOptimizeJobCiemss(optimizePayload, nodeMetadata(props.node));
	const state = _.cloneDeep(props.node.state);
	state.inProgressOptimizeId = optResult.simulationId;
	state.optimizationRunId = '';
	state.inProgressPostForecastId = '';
	emit('update-state', state);
};

const setOutputSettingDefaults = () => {
	const state = _.cloneDeep(props.node.state);
	// Initialize default selected chart settings when chart settings are not set yet. Return if chart settings are already set.
	if (Array.isArray(state.chartSettings)) return;

	const selectedInterventionVariables: string[] = [];
	const selectedSimulationVariables: string[] = [];

	activePolicyGroups.value.forEach((ele) => {
		ele.intervention.staticInterventions.forEach((staticInt) =>
			selectedInterventionVariables.push(staticInt.appliedTo)
		);
		ele.intervention.dynamicInterventions.forEach((dynamicsInt) =>
			selectedInterventionVariables.push(dynamicsInt.appliedTo)
		);
	});
	knobs.value.constraintGroups.forEach((constraint) => {
		if (constraint.targetVariable) {
			// Use modelStateAndObsOptions to map from value -> label as simulation selection uses S not S_State
			const userSelection = modelStateAndObsOptions.value.find((ele) => ele.value === constraint.targetVariable)?.label;
			if (userSelection) {
				selectedSimulationVariables.push(userSelection);
			}
		}
	});
	state.chartSettings = updateChartSettingsBySelectedVariables(
		[],
		ChartSettingType.INTERVENTION,
		selectedInterventionVariables
	);
	state.chartSettings = updateChartSettingsBySelectedVariables(
		state.chartSettings,
		ChartSettingType.VARIABLE,
		selectedSimulationVariables
	);
	emit('update-state', state);
};

const setOutputValues = async () => {
	const preForecastRunId = knobs.value.preForecastRunId;
	const postForecastRunId = knobs.value.postForecastRunId;

	const preResult = await getRunResultCSV(preForecastRunId, 'result.csv', renameFnGenerator('pre'));
	const postResult = await getRunResultCSV(postForecastRunId, 'result.csv');
	const outputState = getActiveOutput(props.node)?.state as OptimizeCiemssOperationState;
	riskResults.value[knobs.value.postForecastRunId] = setQoIData(postResult, outputState.constraintGroups[0]);

	// FIXME: only show the post optimize data for now...
	simulationRawContent.value[knobs.value.postForecastRunId] = convertToCsvAsset(
		postResult,
		Object.values(pyciemssMap.value)
	);
	runResults.value[preForecastRunId] = preResult;
	runResults.value[postForecastRunId] = postResult;

	const preResultSummary = await getRunResultCSV(preForecastRunId, 'result_summary.csv', renameFnGenerator('pre'));
	const postResultSummary = await getRunResultCSV(postForecastRunId, 'result_summary.csv');

	runResultsSummary.value[preForecastRunId] = preResultSummary;
	runResultsSummary.value[postForecastRunId] = postResultSummary;

	optimizationResult.value = await getRunResult(knobs.value.optimizationRunId, 'optimize_results.json');
	optimizeRequestPayload.value = (await getSimulation(knobs.value.optimizationRunId))?.executionPayload || '';
};

const combinedInterventions = computed(() => {
	// Combine before and after interventions
	const interventions = [
		...knobs.value.interventionPolicyGroups.flatMap((group) => group.intervention),
		...(optimizedInterventionPolicy.value?.interventions || [])
	];
	return interventions;
});
const preProcessedInterventionsData = computed<Dictionary<ReturnType<typeof flattenInterventionData>>>(() =>
	_.groupBy(flattenInterventionData(combinedInterventions.value), 'appliedTo')
);

onMounted(async () => {
	initialize();
});

const preparedSuccessCriteriaCharts = computed(() => {
	const postForecastRunId = props.node.state.postForecastRunId;

	return knobs.value.constraintGroups
		.filter((ele) => ele.isActive)
		.map((constraint) =>
			createSuccessCriteriaChart(
				riskResults.value[postForecastRunId],
				constraint.threshold,
				constraint.isMinimized,
				constraint.riskTolerance,
				{
					title: constraint.name,
					width: chartSize.value.width,
					height: chartSize.value.height,
					xAxisTitle: 'Number of samples',
					yAxisTitle: `${constraint.isMinimized ? 'Max' : 'Min'} value of ${constraint.targetVariable} at all timepoints`,
					legend: true
				}
			)
		);
});

const preparedChartInputs = usePreparedChartInputs(props, runResults, runResultsSummary);
const pyciemssMap = computed(() => preparedChartInputs.value?.pyciemssMap ?? {});
const {
	activeChartSettings,
	chartSettings,
	selectedVariableSettings,
	selectedInterventionSettings,
	selectedComparisonChartSettings,
	comparisonChartsSettingsSelection,
	removeChartSettings,
	updateChartSettings,
	updateActiveChartSettings,
	setActiveChartSettings,
	addEmptyComparisonChart,
	updateComparisonChartSetting
} = useChartSettings(props, emit);

const {
	generateAnnotation,
	getChartAnnotationsByChartId,
	useInterventionCharts,
	useVariableCharts,
	useComparisonCharts
} = useCharts(props.node.id, model, modelConfiguration, preparedChartInputs, chartSize, combinedInterventions, null);
const interventionCharts = useInterventionCharts(selectedInterventionSettings);
const variableCharts = useVariableCharts(selectedVariableSettings, null);
const comparisonCharts = useComparisonCharts(selectedComparisonChartSettings);

// refresh policy
const onSaveForReuse = async () => {
	const policyId = props.node.state.optimizedInterventionPolicyId;
	if (policyId) {
		optimizedInterventionPolicy.value = await getInterventionPolicyById(policyId);
	}
};

// reset drilldown state
const resetState = () => {
	confirm.require({
		header: 'Reset to original optimized state',
		message: 'Are you sure you want to reset the state?',
		accept: () => {
			// Retore to the original output port state
			const outputPort = props.node.outputs.find((output) => output.id === selectedOutputId.value);
			if (outputPort) {
				knobs.value = cloneDeep(outputPort.state as OptimizeCiemssOperationState);
			}
		}
	});
};

watch(
	() => knobs.value,
	async () => {
		const state = _.cloneDeep(props.node.state);
		state.endTime = knobs.value.endTime;
		state.numSamples = knobs.value.numSamples;
		state.solverStepSize = knobs.value.solverStepSize;
		state.solverMethod = knobs.value.solverMethod;
		state.maxiter = knobs.value.maxiter;
		state.maxfeval = knobs.value.maxfeval;
		state.preForecastRunId = knobs.value.preForecastRunId;
		state.postForecastRunId = knobs.value.postForecastRunId;
		state.optimizationRunId = knobs.value.optimizationRunId;
		state.constraintGroups = knobs.value.constraintGroups;
		state.interventionPolicyGroups = knobs.value.interventionPolicyGroups;
		emit('update-state', state);
	},
	{ deep: true }
);

watch(
	() => props.node.active,
	() => {
		// Update selected output
		if (props.node.active) {
			selectedOutputId.value = props.node.active;
			initialize();
			if (props.node.state.postForecastRunId !== '' && props.node.state.preForecastRunId !== '') {
				// The run has finished
				knobs.value = cloneDeep(props.node.state);
				setOutputValues();
			}
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
.wizard .toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--gap-1) var(--gap-4);
	gap: var(--gap-2);
}

.spinner-message {
	align-items: center;
	align-self: center;
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	margin-top: 15rem;
	text-align: center;
}

.info-circle {
	color: var(--text-color-secondary);
	font-size: var(--font-caption);
	margin-left: var(--gap-1);
}
.additional-options {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: var(--gap-1);
	padding: 0 var(--gap-2) var(--gap-4);
	background: var(--surface-200);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	box-shadow: inset 0 0px 4px rgba(0, 0, 0, 0.05);
}

/* Override grid template so output expands when sidebar is closed */
.overlay-container:deep(section.scale main) {
	grid-template-columns: auto 1fr;
}

.result-message-grid {
	display: flex;
	flex-direction: column;
	gap: var(--gap-0-5);
	/* Adjust the gap between rows as needed */
	font-size: var(--font-caption);
	background-color: var(--surface-50);
	border: solid 1px var(--surface-border-light);
	border-radius: var(--border-radius);
	padding: var(--gap-2);
}
/* Select button icon fix */
.select-button .p-button-icon-left {
	color: var(--text-color-secondary);
}

/* Debugging message styles */
.result-message-row {
	display: flex;
	flex-direction: row;
	gap: var(--gap-2);
	overflow: auto;
}

.label {
	font-weight: var(--font-weight-semibold);
	/* Adjust the width of the label column as needed */
}

.value {
	flex-grow: 1;
}

:deep(.failed-run main .content-container) {
	border: 2px solid var(--error-color);
	border-radius: var(--border-radius-big);
	color: var(--error-message-color);
}

.successful-run {
	border: none;
	border-radius: 0;
}

.form-section {
	display: flex;
	flex-direction: column;
	flex-grow: 1;
	gap: var(--gap-1);
	padding: var(--gap-4);
}

.label-and-input {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}

.input-row {
	width: 100%;
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	align-items: center;
	gap: var(--gap-2);
	padding-top: var(--gap-4);

	& > * {
		flex: 1;
	}
}

.constraint-row {
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	align-items: center;
	gap: var(--gap-2);

	& > *:first-child {
		flex: 2;
	}

	& > *:not(:first-child) {
		flex: 1;
	}
}

.center-label {
	align-content: center;
}

/* Notebook */
.notebook-section {
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);
	width: calc(50vw - 4rem);
	padding: var(--gap-4);
	background: var(--surface-100);
	border-right: 1px solid var(--surface-border-light);
}

.output-settings-panel {
	padding: var(--gap-4);
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}

.empty-state-chart {
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);
	justify-content: center;
	align-items: center;
	height: 12rem;
	margin: var(--gap-6);
	padding: var(--gap-4);
	background: var(--surface-100);
	color: var(--text-color-secondary);
}
</style>
