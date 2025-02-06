<template>
	<tera-drilldown
		:node="node"
		v-bind="$attrs"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
		@update:selection="onSelection"
	>
		<!-- Wizard -->
		<section :tabName="DrilldownTabs.Wizard" class="wizard">
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Calibration settings"
				content-width="420px"
			>
				<template #content>
					<div class="toolbar">
						<Button
							label="Reset"
							outlined
							@click="resetState"
							severity="secondary"
							:disabled="isEmpty(node.outputs[0].value)"
						/>
						<span class="flex gap-2">
							<tera-pyciemss-cancel-button class="mr-auto" :simulation-run-id="cancelRunId" />
							<span v-tooltip="runButtonMessage">
								<Button label="Run" icon="pi pi-play" @click="runCalibrate" :disabled="isRunDisabled" />
							</span>
						</span>
					</div>

					<!-- Mapping section -->
					<section class="form-section">
						<h5 class="mb-1">Mapping</h5>
						<p class="mb-2 p-text-secondary text-sm">
							Select a subset of output variables of the model and individually associate them to columns in the
							dataset.
						</p>

						<!-- Mapping table: Time variables -->
						<div class="input-row mapping-input">
							<div class="label-and-input">
								<label class="column-header">Model: Timeline variable</label>
								<tera-input-text disabled model-value="timestamp" />
							</div>
							<div class="label-and-input">
								<label class="column-header">Dataset: Timeline variable</label>
								<Dropdown
									class="w-full"
									:placeholder="mappingDropdownPlaceholder"
									v-model="knobs.timestampColName"
									:options="datasetColumns?.map((ele) => ele.name)"
									@change="updateTimeline()"
								/>
							</div>
						</div>

						<!-- Mapping table: Other variables -->
						<DataTable class="mapping-table" :value="mapping">
							<Column field="modelVariable">
								<template #header>
									<span class="column-header">Model: Other variables</span>
								</template>
								<template #body="{ data, field }">
									<Dropdown
										class="w-full"
										:placeholder="mappingDropdownPlaceholder"
										v-model="data[field]"
										:options="modelOptions?.map((option) => option.id)"
										@change="updateMapping()"
									/>
								</template>
							</Column>
							<Column field="datasetVariable">
								<template #header>
									<span class="column-header">Dataset: Other variables</span>
								</template>
								<template #body="{ data, field }">
									<Dropdown
										class="w-full"
										:placeholder="mappingDropdownPlaceholder"
										v-model="data[field]"
										:options="datasetColumns?.map((ele) => ele.name)"
										@change="updateMapping()"
									/>
								</template>
							</Column>
							<Column field="deleteRow">
								<template #header>
									<span class="column-header"></span>
								</template>
								<template #body="{ index }">
									<Button class="p-button-sm p-button-text" icon="pi pi-trash" @click="deleteMapRow(index)" />
								</template>
							</Column>
						</DataTable>

						<div class="flex justify-content-between">
							<Button class="p-button-sm p-button-text" icon="pi pi-plus" label="Add mapping" @click="addMapping" />
							<Button
								class="p-button-sm p-button-text"
								icon="pi pi-sparkles"
								label="Auto map"
								@click="getAutoMapping"
							/>
							<Button class="ml-auto p-button-sm p-button-text" label="Delete all mapping" @click="deleteAllMappings" />
						</div>
					</section>

					<!-- Mapping section -->
					<section class="form-section">
						<h5 class="mb-1">Calibration settings</h5>
						<div class="input-row">
							<tera-timestep-calendar
								disabled
								v-if="model && modelConfig"
								label="Start time"
								:start-date="modelConfig.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								:model-value="0"
							/>
							<tera-timestep-calendar
								v-if="model && modelConfig"
								label="End time"
								:start-date="modelConfig.temporalContext"
								:calendar-settings="getCalendarSettingsFromModel(model)"
								v-model="knobs.endTime"
							/>
						</div>
						<div class="spacer m-2" />
						<p class="mb-1">Preset (optional)</p>
						<div class="label-and-input">
							<Dropdown
								v-model="presetType"
								placeholder="Select an option"
								:options="[CiemssPresetTypes.Fast, CiemssPresetTypes.Normal]"
								@update:model-value="setPresetValues"
							/>
						</div>
						<label class="p-text-secondary text-sm flex align-items-center gap-2 my-1">
							<i class="pi pi-info-circle" />
							This impacts solver method, iterations and learning rate.
						</label>
						<div class="mt-1 additional-settings">
							<div class="label-and-input">
								<label>Number of Samples</label>
								<tera-input-number inputId="integeronly" v-model="knobs.numSamples" @update:model-value="updateState" />
							</div>
							<div class="spacer m-4" />

							<h6 class="mb-2">ODE solver options</h6>

							<div class="input-row">
								<div class="label-and-input">
									<label for="5">Solver method</label>
									<Dropdown
										id="5"
										v-model="knobs.method"
										:options="[CiemssMethodOptions.dopri5, CiemssMethodOptions.rk4, CiemssMethodOptions.euler]"
										@update:model-value="updateState"
									/>
								</div>
								<div class="label-and-input">
									<label for="num-steps">Solver step size</label>
									<tera-input-number
										:disabled="![CiemssMethodOptions.rk4, CiemssMethodOptions.euler].includes(knobs.method)"
										:min="0"
										v-model="knobs.stepSize"
									/>
								</div>
							</div>
							<div class="spacer m-4" />
							<h6 class="mb-2">Inference Options</h6>
							<div class="input-row">
								<div class="label-and-input">
									<label for="num-iterations">Number of solver iterations</label>
									<tera-input-number
										inputId="integeronly"
										v-model="knobs.numIterations"
										@update:model-value="updateState"
									/>
								</div>
								<div class="label-and-input">
									<label for="learning-rate">Learning rate</label>
									<tera-input-number
										inputId="numberonly"
										v-model="knobs.learningRate"
										@update:model-value="updateState"
									/>
								</div>
								<div class="label-and-input">
									<label>Inference algorithm</label>
									<tera-input-text disabled model-value="SVI" />
								</div>
								<div class="label-and-input">
									<label>Loss function</label>
									<tera-input-text disabled model-value="ELBO" />
								</div>
								<div class="label-and-input mb-3">
									<label>Optimizer method</label>
									<tera-input-text disabled model-value="ADAM" />
								</div>
							</div>
						</div>
					</section>

					<section v-if="interventionPolicy && model" class="form-section">
						<h5>Intervention Policies</h5>
						<tera-intervention-summary-card
							v-for="(intervention, index) in interventionPolicy.interventions"
							:intervention="intervention"
							:start-date="modelConfig?.temporalContext"
							:calendar-settings="getCalendarSettingsFromModel(model)"
							:key="index"
						/>
					</section>
					<div class="spacer m-7" />
				</template>
			</tera-slider-panel>
		</section>

		<!-- Notebook section -->
		<section :tabName="DrilldownTabs.Notebook" class="notebook-section">
			<p class="m-3">The notebook is under construction.</p>
		</section>

		<!-- Output section -->
		<template #preview>
			<tera-drilldown-section
				:is-loading="isLoading"
				:show-slot-while-loading="true"
				:loading-progress="props.node.state.currentProgress"
				:is-blank="!showOutputSection"
				:blank-message="'Click \'Run\' to begin calibrating'"
			>
				<template #header-controls-left>
					<h5 v-if="configuredModelConfig?.name" class="ml-3">{{ configuredModelConfig.name }}</h5>
				</template>
				<template #header-controls-right>
					<Button
						label="Save for re-use"
						severity="secondary"
						class="mr-3"
						outlined
						:disabled="!configuredModelConfig"
						@click="showSaveModal = true"
					/>
				</template>
				<tera-notebook-error
					v-if="!isEmpty(node.state?.errorMessage?.traceback) && !isLoading"
					v-bind="node.state.errorMessage"
				/>
				<tera-operator-output-summary
					v-if="node.state.summaryId && !isLoading"
					class="p-3"
					:summary-id="node.state.summaryId"
				/>
				<!-- Loss section -->
				<Accordion
					v-if="lossValues.length > 0 || isLoading"
					:active-index="lossActiveIndex"
					@update:active-index="updateLossTab"
					class="px-2"
				>
					<AccordionTab header="Loss">
						<!-- Loss chart -->
						<div ref="lossChartContainer">
							<vega-chart
								expandable
								v-if="lossValues.length > 0 || isLoading"
								ref="lossChartRef"
								:are-embed-actions-visible="true"
								:visualization-spec="lossChartSpec"
							/>
						</div>
					</AccordionTab>
				</Accordion>
				<div v-if="!isLoading">
					<section class="pb-3" v-if="modelConfig && csvAsset">
						<div class="mx-4" ref="chartWidthDiv"></div>
						<Accordion multiple :active-index="currentActiveIndicies" class="px-2">
							<!-- Parameter distributions section -->
							<AccordionTab v-if="selectedParameterSettings.length > 0" header="Parameter distributions">
								<template v-for="setting of selectedParameterSettings" :key="setting.id">
									<div class="flex flex-column">
										<vega-chart
											v-if="parameterDistributionCharts[setting.id]"
											expandable
											:are-embed-actions-visible="true"
											:visualization-spec="parameterDistributionCharts[setting.id].histogram"
										/>
										<table class="distribution-table">
											<thead>
												<tr>
													<th scope="col"></th>
													<th scope="col">
														{{ parameterDistributionCharts[setting.id].header[0] }}
													</th>
													<th scope="col">
														{{ parameterDistributionCharts[setting.id].header[1] }}
													</th>
												</tr>
											</thead>
											<tbody>
												<tr>
													<th scope="row">Mean</th>
													<td>{{ parameterDistributionCharts[setting.id].mean[0] }}</td>
													<td>{{ parameterDistributionCharts[setting.id].mean[1] }}</td>
												</tr>
												<tr>
													<th scope="row">Variance</th>
													<td>{{ parameterDistributionCharts[setting.id].variance[0] }}</td>
													<td>{{ parameterDistributionCharts[setting.id].variance[1] }}</td>
												</tr>
											</tbody>
										</table>
									</div>
								</template>
							</AccordionTab>
							<!-- Section: Interventions over time -->
							<AccordionTab v-if="selectedInterventionSettings.length > 0" header="Interventions over time">
								<template v-for="setting of selectedInterventionSettings" :key="setting.id">
									<vega-chart
										expandable
										:are-embed-actions-visible="true"
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
							<AccordionTab header="Errors" v-if="errorData.length > 0 && selectedErrorVariableSettings.length > 0">
								<vega-chart
									v-if="errorData.length > 0 && selectedErrorVariableSettings.length > 0"
									:expandable="onExpandErrorChart"
									:are-embed-actions-visible="true"
									:visualization-spec="errorChart"
								/>
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
					<section v-else-if="!modelConfig" class="emptyState">
						<img src="@assets/svg/seed.svg" alt="" draggable="false" />
						<p class="helpMessage">Connect a model configuration and dataset</p>
					</section>
				</div>
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
						@delete-annotation="deleteAnnotation"
						@update-settings="updateActiveChartSettings"
						@close="setActiveChartSettings(null)"
					/>
				</template>
				<template #content>
					<div class="output-settings-panel">
						<!-- Parameter distributions -->
						<tera-chart-settings
							:title="'Parameter distributions'"
							:settings="chartSettings"
							:type="ChartSettingType.DISTRIBUTION_COMPARISON"
							:select-options="Object.keys(pyciemssMap).filter((c) => modelPartTypesMap[c] === 'parameter')"
							:selected-options="selectedParameterSettings.map((s) => s.selectedVariables[0])"
							@open="setActiveChartSettings($event)"
							@remove="removeChartSettings"
							@selection-change="updateChartSettings"
						/>
						<Divider />
						<!-- Interventions over time -->
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
						<!-- Variables over time -->
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
						<!-- Errors -->
						<tera-chart-settings
							:title="'Error'"
							:settings="chartSettings"
							:type="ChartSettingType.ERROR_DISTRIBUTION"
							:select-options="
								Object.keys(pyciemssMap)
									.filter((c) => ['state', 'observable'].includes(modelPartTypesMap[c]))
									.filter((c) => selectedOutputMapping.find((s) => s.modelVariable === c))
							"
							:selected-options="selectedErrorVariableSettings.map((s) => s.selectedVariables[0])"
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
							:select-options="
								Object.keys(pyciemssMap).filter((c) =>
									['state', 'observable', 'parameter'].includes(modelPartTypesMap[c])
								)
							"
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
		:initial-name="configuredModelConfig?.name"
		:is-visible="showSaveModal"
		:assets="[
			{ id: configuredModelConfig?.id ?? '', type: AssetType.ModelConfiguration },
			{ id: outputDatasetId, type: AssetType.Dataset }
		]"
		@close-modal="showSaveModal = false"
		@on-save="onSaveAsModelConfiguration"
		:simulation-id="node.state.forecastId"
	/>
</template>

<script setup lang="ts">
import { cloneDeep, groupBy, intersection, isEmpty, isEqual } from 'lodash';
import * as vega from 'vega';
import { computed, onMounted, ref, shallowRef, watch } from 'vue';
import { useConfirm } from 'primevue/useconfirm';
import Divider from 'primevue/divider';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import DataTable from 'primevue/datatable';
import Dropdown from 'primevue/dropdown';
import Column from 'primevue/column';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import { CalibrateMap, setupCsvAsset, setupModelInput } from '@/services/calibrate-workflow';
import { deleteAnnotation, updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import TeraOperatorOutputSummary from '@/components/operator/tera-operator-output-summary.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraChartSettings from '@/components/widgets/tera-chart-settings.vue';
import TeraChartSettingsPanel from '@/components/widgets/tera-chart-settings-panel.vue';
import {
	CalibrationRequestCiemss,
	ClientEvent,
	ClientEventType,
	CsvAsset,
	ModelConfiguration,
	InterventionPolicy,
	ModelParameter,
	AssetType,
	Dataset,
	Model,
	Observable,
	State
} from '@/types/Types';
import { CiemssPresetTypes, DrilldownTabs, ChartSettingType } from '@/types/common';
import { getActiveOutput, getTimespan, nodeMetadata } from '@/components/workflow/util';
import { useToastService } from '@/services/toast';
import { autoCalibrationMapping } from '@/services/concept';
import {
	getSimulation,
	getRunResultCSV,
	makeCalibrateJobCiemss,
	subscribeToUpdateMessages,
	unsubscribeToUpdateMessages,
	DataArray,
	CiemssMethodOptions
} from '@/services/models/simulation-service';
import { getModelConfigurationById } from '@/services/model-configurations';
import { WorkflowNode } from '@/types/workflow';
import { createForecastChart, AUTOSIZE } from '@/services/charts';
import VegaChart from '@/components/widgets/VegaChart.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraPyciemssCancelButton from '@/components/pyciemss/tera-pyciemss-cancel-button.vue';
import TeraSaveSimulationModal from '@/components/project/tera-save-simulation-modal.vue';
import { useDrilldownChartSize } from '@/composables/useDrilldownChartSize';
import { flattenInterventionData, getInterventionPolicyById } from '@/services/intervention-policy';
import TeraInterventionSummaryCard from '@/components/intervention-policy/tera-intervention-summary-card.vue';
import { getParameters } from '@/model-representation/service';
import TeraTimestepCalendar from '@/components/widgets/tera-timestep-calendar.vue';
import { getDataset, getFileName } from '@/services/dataset';
import { getCalendarSettingsFromModel } from '@/services/model';
import { useCharts } from '@/composables/useCharts';
import { useChartSettings } from '@/composables/useChartSettings';
import { parseCsvAsset } from '@/utils/csv';
import type { CalibrationOperationStateCiemss } from './calibrate-operation';
import { renameFnGenerator, getErrorData, usePreparedChartInputs, getSelectedOutputMapping } from './calibrate-utils';
import { isInterventionPolicyBlank } from '../intervention-policy/intervention-policy-operation';

const isSidebarOpen = ref(true);

const props = defineProps<{
	node: WorkflowNode<CalibrationOperationStateCiemss>;
}>();
const emit = defineEmits(['close', 'select-output', 'update-state']);
const toast = useToastService();
const confirm = useConfirm();

interface BasicKnobs {
	numIterations: number;
	numSamples: number;
	endTime: number;
	stepSize: number;
	learningRate: number;
	method: CiemssMethodOptions;
	timestampColName: string;
}

const knobs = ref<BasicKnobs>({
	numIterations: props.node.state.numIterations ?? 1000,
	numSamples: props.node.state.numSamples ?? 100,
	endTime: props.node.state.endTime ?? 100,
	stepSize: props.node.state.stepSize ?? 1,
	learningRate: props.node.state.learningRate ?? 0.1,
	method: props.node.state.method ?? CiemssMethodOptions.dopri5,
	timestampColName: props.node.state.timestampColName ?? ''
});

const presetType = computed(() => {
	if (
		knobs.value.numSamples === speedPreset.numSamples &&
		knobs.value.method === speedPreset.method &&
		knobs.value.stepSize === speedPreset.stepSize
	) {
		return CiemssPresetTypes.Fast;
	}
	if (knobs.value.numSamples === qualityPreset.numSamples && knobs.value.method === qualityPreset.method) {
		return CiemssPresetTypes.Normal;
	}
	return '';
});

const outputDatasetId = computed(() => {
	if (!selectedOutputId.value) return '';
	const output = props.node.outputs.find((o) => o.id === selectedOutputId.value);
	return output?.value?.[0]?.datasetId ?? '';
});

const speedPreset = Object.freeze({
	numSamples: 1,
	method: CiemssMethodOptions.euler,
	numIterations: 10,
	learningRate: 0.1,
	stepSize: 0.1
});

const qualityPreset = Object.freeze({
	numSamples: 100,
	method: CiemssMethodOptions.dopri5,
	numIterations: 1000,
	learningRate: 0.03
});

// Model variables checked in the model will be options in the mapping dropdown
const modelOptions = ref<(State | Observable)[] | undefined>();

const modelParameters = ref<ModelParameter[]>([]);

const isOutputSettingsPanelOpen = ref(false);

const dataset = shallowRef<Dataset | null>(null);
const datasetColumns = computed(() =>
	dataset.value?.columns?.filter((col) => col.fileName === currentDatasetFileName.value)
);
const csvAsset = shallowRef<CsvAsset | undefined>(undefined);
const groundTruthData = computed<DataArray>(() => parseCsvAsset(csvAsset.value as CsvAsset));

const lossActiveIndex = ref<number | null>(0);
const currentActiveIndicies = ref([0, 1, 2, 3, 4]);

const modelConfig = ref<ModelConfiguration | null>(null);
const model = ref<Model | null>(null);

const modelVarUnits = ref<{ [key: string]: string }>({});
const modelPartTypesMap = ref<{ [key: string]: string }>({});

const modelConfigId = computed<string | undefined>(() => props.node.inputs[0]?.value?.[0]);
const datasetId = computed<string | undefined>(() => props.node.inputs[1]?.value?.[0]);
const policyInterventionId = computed(() => props.node.inputs[2].value?.[0]);
const interventionPolicy = ref<InterventionPolicy | null>(null);

const cancelRunId = computed(
	() =>
		props.node.state.inProgressForecastId ||
		props.node.state.inProgressCalibrationId ||
		props.node.state.inProgressPreForecastId
);
const currentDatasetFileName = ref<string>();

const runResult = ref<DataArray>([]);
const runResultPre = ref<DataArray>([]);
const runResultSummary = ref<DataArray>([]);
const runResultSummaryPre = ref<DataArray>([]);
const showSaveModal = ref(false);
const configuredModelConfig = ref<ModelConfiguration | null>(null);

const isLoading = computed<boolean>(
	() =>
		props.node.state.inProgressCalibrationId !== '' ||
		props.node.state.inProgressPreForecastId !== '' ||
		props.node.state.inProgressForecastId !== ''
);

const mapping = ref<CalibrateMap[]>(props.node.state.mapping);

const mappingDropdownPlaceholder = computed(() => {
	if (!isEmpty(modelOptions.value) && !isEmpty(datasetColumns.value)) return 'Select variable';
	return 'Please wait...';
});

const showOutputSection = computed(
	() =>
		lossValues.value.length > 0 ||
		isLoading.value ||
		!isEmpty(props.node.state?.errorMessage?.traceback) ||
		selectedOutputId.value
);

const updateState = () => {
	const state = cloneDeep(props.node.state);
	state.numSamples = knobs.value.numSamples;
	state.method = knobs.value.method;
	state.numIterations = knobs.value.numIterations;
	state.learningRate = knobs.value.learningRate;
	emit('update-state', state);
};

const setPresetValues = (data: CiemssPresetTypes) => {
	if (data === CiemssPresetTypes.Normal) {
		knobs.value.numSamples = qualityPreset.numSamples;
		knobs.value.method = qualityPreset.method;
		knobs.value.numIterations = qualityPreset.numIterations;
		knobs.value.learningRate = qualityPreset.learningRate;
	} else if (data === CiemssPresetTypes.Fast) {
		knobs.value.numSamples = speedPreset.numSamples;
		knobs.value.method = speedPreset.method;
		knobs.value.numIterations = speedPreset.numIterations;
		knobs.value.learningRate = speedPreset.learningRate;
		knobs.value.stepSize = speedPreset.stepSize;
	}
};

// reset drilldown state
const resetState = () => {
	confirm.require({
		header: 'Reset to original calibration state',
		message: 'Are you sure you want to reset the state?',
		accept: () => {
			// Restore to the original output port state
			const outputPort = props.node.outputs.find((output) => output.id === selectedOutputId.value);
			if (outputPort?.state) {
				knobs.value = cloneDeep(outputPort.state as CalibrationOperationStateCiemss);
				mapping.value = outputPort.state.mapping as CalibrateMap[];
			}
		}
	});
};

// Checks for disabling run button:
const isMappingFilled = computed(
	() => mapping.value.find((ele) => ele.datasetVariable && ele.modelVariable) && knobs.value.timestampColName
);

const areNodeInputsFilled = computed(() => datasetId.value && modelConfigId.value);

const isRunDisabled = computed(
	() =>
		!isMappingFilled.value ||
		!areNodeInputsFilled.value ||
		isLoading.value ||
		(!!interventionPolicy.value && isInterventionPolicyBlank(interventionPolicy.value))
);

const mappingFilledTooltip = computed(() =>
	!isMappingFilled.value ? 'Must contain a Timestamp column and at least one filled in mapping. \n' : ''
);
const nodeInputsFilledTooltip = computed(() =>
	!areNodeInputsFilled.value ? 'Must a valid dataset and model configuration\n' : ''
);
const isLoadingTooltip = computed(() => (isLoading.value ? 'Must wait for the current run to finish\n' : ''));

const runButtonMessage = computed(() =>
	isRunDisabled.value ? `${mappingFilledTooltip.value} ${nodeInputsFilledTooltip.value} ${isLoadingTooltip.value}` : ''
);

const selectedOutputId = ref<string>();
const lossChartContainer = ref(null);
const lossChartSize = useDrilldownChartSize(lossChartContainer);
const chartWidthDiv = ref(null);
const chartSize = useDrilldownChartSize(chartWidthDiv);

const groupedInterventionOutputs = computed(() =>
	groupBy(flattenInterventionData(interventionPolicy.value?.interventions ?? []), 'appliedTo')
);

const selectedOutputMapping = computed(() => getSelectedOutputMapping(props.node));
const selectedOutputTimestampColName = computed(() => getActiveOutput(props.node)?.state?.timestampColName ?? '');

const errorData = computed<DataArray>(() =>
	getErrorData(
		groundTruthData.value,
		runResult.value,
		selectedOutputMapping.value,
		selectedOutputTimestampColName.value,
		pyciemssMap.value
	)
);

const preparedChartInputs = usePreparedChartInputs(
	props,
	runResult,
	runResultSummary,
	runResultPre,
	runResultSummaryPre
);
const pyciemssMap = computed(() => preparedChartInputs.value?.pyciemssMap ?? {});
const {
	activeChartSettings,
	chartSettings,
	selectedVariableSettings,
	selectedParameterSettings,
	selectedInterventionSettings,
	selectedErrorVariableSettings,
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
	useComparisonCharts,
	useErrorChart,
	useParameterDistributionCharts
} = useCharts(
	props.node.id,
	model,
	modelConfig,
	preparedChartInputs,
	chartSize,
	computed(() => interventionPolicy.value?.interventions ?? []),
	selectedOutputMapping
);
const parameterDistributionCharts = useParameterDistributionCharts(selectedParameterSettings);
const interventionCharts = useInterventionCharts(selectedInterventionSettings);
const variableCharts = useVariableCharts(selectedVariableSettings, groundTruthData);
const comparisonCharts = useComparisonCharts(selectedComparisonChartSettings);
const { errorChart, onExpandErrorChart } = useErrorChart(selectedErrorVariableSettings, errorData);

const LOSS_CHART_DATA_SOURCE = 'lossData'; // Name of the streaming data source
const lossChartRef = ref<InstanceType<typeof VegaChart>>();
const lossChartSpec = ref();
const lossValues = ref<{ [key: string]: number }[]>([]);
const updateLossChartSpec = (data: string | Record<string, any>[], size: { width: number; height: number }) => {
	lossChartSpec.value = createForecastChart(
		null,
		{
			data: Array.isArray(data) ? data : { name: data },
			variables: ['loss'],
			timeField: 'iter'
		},
		null,
		{
			title: '',
			width: size.width,
			height: 100,
			xAxisTitle: 'Solver iterations',
			yAxisTitle: 'Loss',
			autosize: AUTOSIZE.FIT,
			fitYDomain: true
		}
	);
};

const initDefaultChartSettings = (state: CalibrationOperationStateCiemss) => {
	// TODO: Determine the most interesting parameters to show to the user.
	// https://github.com/DARPA-ASKEM/terarium/issues/6284
	const defaultSelectedParam = [];
	const mappedModelVariables = mapping.value
		.filter((c) => ['state', 'observable'].includes(modelPartTypesMap.value[c.modelVariable]))
		.map((c) => c.modelVariable);

	// update variable chart settings only if they do not exist
	if (!state.chartSettings?.some((c) => c.type === ChartSettingType.VARIABLE)) {
		state.chartSettings = updateChartSettingsBySelectedVariables(
			state.chartSettings ?? [],
			ChartSettingType.VARIABLE,
			mappedModelVariables
		);
	}
	state.chartSettings = updateChartSettingsBySelectedVariables(
		state.chartSettings,
		ChartSettingType.ERROR_DISTRIBUTION,
		mappedModelVariables
	);
	state.chartSettings = updateChartSettingsBySelectedVariables(
		state.chartSettings,
		ChartSettingType.DISTRIBUTION_COMPARISON,
		defaultSelectedParam
	);
	state.chartSettings = updateChartSettingsBySelectedVariables(
		state.chartSettings,
		ChartSettingType.INTERVENTION,
		intersection(Object.keys(groupedInterventionOutputs.value), [...defaultSelectedParam, ...mappedModelVariables])
	);
};

const updateLossTab = (activeTab: number | undefined) => {
	lossActiveIndex.value = activeTab ?? null;
};

const runCalibrate = async () => {
	lossActiveIndex.value = 0; // ensure loss tab open on run
	if (!modelConfigId.value || !datasetId.value || !currentDatasetFileName.value) return;

	const formattedMap: { [index: string]: string } = {};
	formattedMap[knobs.value.timestampColName] = 'timestamp';
	// If the user has done any mapping populate formattedMap
	if (mapping.value[0].datasetVariable !== '') {
		mapping.value.forEach((ele) => {
			formattedMap[ele.datasetVariable] = ele.modelVariable;
		});
	}

	// Reset loss buffer
	lossValues.value = [];

	const state = cloneDeep(props.node.state);

	// Create request
	const calibrationRequest: CalibrationRequestCiemss = {
		modelConfigId: modelConfigId.value,
		dataset: {
			id: datasetId.value,
			filename: currentDatasetFileName.value,
			mappings: formattedMap
		},
		extra: {
			solver_method: knobs.value.method,
			solver_step_size: knobs.value.stepSize,
			lr: knobs.value.learningRate,
			num_iterations: knobs.value.numIterations
		},
		timespan: getTimespan(
			dataset.value as Dataset,
			knobs.value.timestampColName,
			knobs.value.endTime // Default is simulation End Time
		),
		engine: 'ciemss'
	};

	if (policyInterventionId.value) {
		calibrationRequest.policyInterventionId = policyInterventionId.value;
	}

	const response = await makeCalibrateJobCiemss(calibrationRequest, nodeMetadata(props.node));

	if (response?.simulationId) {
		state.inProgressCalibrationId = response?.simulationId;
		state.currentProgress = 0;
		state.inProgressForecastId = '';
		state.inProgressPreForecastId = '';
		state.timestampColName = knobs.value.timestampColName;

		initDefaultChartSettings(state);
		emit('update-state', state);
	}
};

const messageHandler = (event: ClientEvent<any>) => {
	const data = { iter: lossValues.value.length, loss: event.data.loss };
	lossValues.value.push(data);
	if (!lossChartRef.value?.view) return;
	lossChartRef.value.view.change(LOSS_CHART_DATA_SOURCE, vega.changeset().insert(data)).resize().run();
};

const onSelection = (id: string) => {
	emit('select-output', id);
};

const updateTimeline = () => {
	const state = cloneDeep(props.node.state);
	state.timestampColName = knobs.value.timestampColName;
	emit('update-state', state);
};

/**
 * Mapping functions
 */

// Used from button to add new entry to the mapping object
function addMapping() {
	mapping.value.push({
		modelVariable: '',
		datasetVariable: ''
	});

	const state = cloneDeep(props.node.state);
	state.mapping = mapping.value;

	emit('update-state', state);
}

const updateMapping = () => {
	const state = cloneDeep(props.node.state);
	state.mapping = mapping.value;
	emit('update-state', state);
};

function deleteAllMappings() {
	mapping.value = [];

	const state = cloneDeep(props.node.state);
	state.mapping = mapping.value;

	emit('update-state', state);
}

function deleteMapRow(index: number) {
	mapping.value.splice(index, 1);
	const state = cloneDeep(props.node.state);
	state.mapping = mapping.value;

	emit('update-state', state);
}

async function getAutoMapping() {
	if (!modelOptions.value || isEmpty(modelOptions.value)) {
		toast.error('', 'No model states or observables to map with');
		return;
	}
	if (!datasetColumns.value || isEmpty(datasetColumns.value)) {
		toast.error('', 'No dataset columns to map with');
		return;
	}
	mapping.value = await autoCalibrationMapping(modelOptions.value, datasetColumns.value);
	const state = cloneDeep(props.node.state);
	state.mapping = mapping.value;
	emit('update-state', state);
}

/* End of Mapping functions */

const initialize = async () => {
	// Update Wizard form fields with current selected output state
	const state = cloneDeep(props.node.state);
	knobs.value = {
		numIterations: state.numIterations ?? 1000,
		numSamples: state.numSamples ?? 100,
		endTime: state.endTime ?? 100,
		stepSize: state.stepSize ?? 1,
		learningRate: state.learningRate ?? 0.1,
		method: state.method ?? CiemssMethodOptions.dopri5,
		timestampColName: state.timestampColName ?? ''
	};

	// Model configuration input
	const modelInput = await setupModelInput(modelConfigId.value);
	modelConfig.value = modelInput.modelConfiguration ?? null;
	model.value = modelInput.model ?? null;
	modelOptions.value = modelInput.modelOptions;
	modelParameters.value = model.value ? getParameters(model.value) : [];
	modelVarUnits.value = modelInput.modelPartUnits ?? {};
	modelPartTypesMap.value = modelInput.modelPartTypes ?? {};

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

	getConfiguredModelConfig();

	// look for timestamp col in dataset if it has not yet filled in.
	if (knobs.value.timestampColName === '') {
		const timeCol = datasetColumns.value?.find((ele) => ele.name?.toLocaleLowerCase().startsWith('time'));
		if (timeCol && timeCol.name !== undefined) {
			knobs.value.timestampColName = timeCol.name;
		}
	}
};

const onSaveAsModelConfiguration = async () => {
	await getConfiguredModelConfig();
};

const getConfiguredModelConfig = async () => {
	const selectedOutput = props.node.outputs.find((output) => output.id === selectedOutputId.value);
	const configuredModelId = selectedOutput?.value?.[0]?.modelConfigId;
	if (configuredModelId) {
		configuredModelConfig.value = await getModelConfigurationById(configuredModelId);
	}
};

onMounted(() => {
	initialize();
});

watch(
	() => ({ ...knobs.value }),
	(newValue, oldValue) => {
		if (isEqual(newValue, oldValue)) return;
		const state = cloneDeep(props.node.state);
		state.numIterations = knobs.value.numIterations;
		state.numSamples = knobs.value.numSamples;
		state.endTime = knobs.value.endTime;
		state.timestampColName = knobs.value.timestampColName;
		emit('update-state', state);
	},
	{ deep: true }
);

watch(
	[() => props.node.state.inProgressCalibrationId, lossChartSize],
	([id, size]) => {
		if (id === '') {
			updateLossChartSpec(lossValues.value, size);
			unsubscribeToUpdateMessages([id], ClientEventType.SimulationPyciemss, messageHandler);
		} else {
			updateLossChartSpec(LOSS_CHART_DATA_SOURCE, size);
			subscribeToUpdateMessages([id], ClientEventType.SimulationPyciemss, messageHandler);
		}
	},
	{ immediate: true }
);

watch(
	() => props.node.active,
	async () => {
		// Update selected output
		if (props.node.active) {
			selectedOutputId.value = props.node.active;
			await initialize();
			// Fetch saved intermediate state
			const simulationObj = await getSimulation(props.node.state.calibrationId);
			if (simulationObj?.updates) {
				lossValues.value = simulationObj?.updates
					.sort((a, b) => a.data.progress - b.data.progress)
					.map((d, i) => ({
						iter: i,
						loss: d.data.loss
					}));
				updateLossChartSpec(lossValues.value, lossChartSize.value);
			}

			const state = props.node.state;
			runResult.value = await getRunResultCSV(state.forecastId, 'result.csv');
			runResultSummary.value = await getRunResultCSV(state.forecastId, 'result_summary.csv');

			runResultPre.value = await getRunResultCSV(state.preForecastId, 'result.csv', renameFnGenerator('pre'));
			runResultSummaryPre.value = await getRunResultCSV(
				state.preForecastId,
				'result_summary.csv',
				renameFnGenerator('pre')
			);
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

<style scoped>
.wizard .toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--gap-1) var(--gap-4);
	gap: var(--gap-2);
}

/* In Wizard mode, override grid template so output expands when sidebar is closed */
.overlay-container:deep(section.scale main) {
	grid-template-columns: auto 1fr;
}

/* Notebook */
.notebook-section {
	width: calc(50vw - 4rem);
	background: var(--surface-100);
	border-right: 1px solid var(--surface-border-light);
}

/* Mapping table */
.mapping-table:deep(td) {
	border: none !important;
	padding: 0 var(--gap-2) var(--gap-2) 0 !important;
	background: var(--surface-100);
}

.mapping-table:deep(th) {
	border: none !important;
	padding: 0 var(--gap-1) var(--gap-2) 0 !important;
	width: 50%;
	background: var(--surface-100);
}

th {
	text-align: left;
}

.column-header {
	color: var(--text-color-primary);
	font-size: var(--font-body-small);
	font-weight: var(--font-weight);
	padding-top: var(--gap-2);
}

.emptyState {
	align-items: center;
	align-self: center;
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	margin-top: 15rem;
	text-align: center;
}

.helpMessage {
	color: var(--text-color-subdued);
	font-size: var(--font-body-small);
	margin-top: var(--gap-4);
	width: 90%;
}

img {
	width: 20%;
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
	gap: var(--gap-1);

	:deep(input) {
		text-align: left;
	}
}
.info-circle {
	color: var(--text-color-secondary);
	font-size: var(--font-caption);
	margin-left: var(--gap-1);
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

/** Make inputs align with mapping table */
.mapping-input {
	width: calc(100% - 40px);
}

.loss-chart {
	background: var(--surface-a);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius-medium);
}

.distribution-table {
	position: relative;
	top: -1rem;
	margin-left: auto;
	margin-right: auto;
	margin-bottom: var(--gap-6);
	width: 100%;
	border-collapse: collapse;
	thead {
		background-color: var(--surface-100);
	}
	tr {
		height: 1.75rem;
	}
	tbody tr {
		border-bottom: 1px solid var(--surface-border-light);
	}
	td,
	th {
		text-align: center;
	}
}

.output-settings-panel {
	padding: var(--gap-4);
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}

.output-section-empty-state {
	width: 100%;
	height: 100%;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: var(--gap-4);
	text-align: center;
	pointer-events: none;
}

.additional-settings {
	background: var(--surface-200);
	padding: var(--gap-3);
	border-radius: var(--border-radius-medium);
	box-shadow: inset 0 0 4px var(--surface-border);
}

input {
	text-align: left;
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
