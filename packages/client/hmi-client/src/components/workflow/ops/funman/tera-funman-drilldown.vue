<template>
	<tera-drilldown
		ref="drilldownRef"
		v-bind="$attrs"
		:node="node"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<tera-slider-panel
			:tabName="DrilldownTabs.Wizard"
			class="input-config"
			v-model:is-open="isSliderOpen"
			header="Validate configuration settings"
			content-width="440px"
		>
			<template #content>
				<div class="top-toolbar">
					<div class="btn-group">
						<Button v-if="showSpinner" label="Stop" icon="pi pi-stop" @click="stop" />
						<Button :loading="showSpinner" label="Run" icon="pi pi-play" @click="run" />
					</div>
				</div>
				<main>
					<Accordion multiple :active-index="toolbarActiveIndicies">
						<AccordionTab>
							<template #header>
								Model checks
								<i class="pi pi-info-circle pl-2" v-tooltip="validateParametersToolTip" />
							</template>
							<p class="mb-3 secondary-text">
								Check the state space of the model to see how the parameter space is partitioned into satisfiable and
								unsatisfiable regions separated by decision boundaries.
							</p>
							<ul>
								<li>
									<section class="shadow-1 pt-2">
										<header class="flex w-full gap-3 mb-2">
											<tera-toggleable-input v-model="knobs.compartmentalConstraint.name" tag="h3" class="nudge-left" />
											<div class="ml-auto flex align-items-center">
												<label class="mr-2">Active</label>
												<InputSwitch class="mr-3" v-model="knobs.compartmentalConstraint.isActive" />
											</div>
										</header>
										<katex-element class="expression-constraint inset" :expression="expression" />
										<katex-element
											class="expression-constraint inset"
											:expression="stringToLatexExpression(`${stateIds.join('+')} = ${massScientificNotation}`)"
										/>
									</section>
								</li>
								<li v-for="(cfg, index) in node.state.constraintGroups" :key="index" class="shadow-1">
									<tera-constraint-group-form
										:config="cfg"
										:index="index"
										:state-ids="stateIds"
										:parameter-ids="parameterIds"
										:observable-ids="observableIds"
										@update-self="updateConstraintGroupForm(index, $event.key, $event.value)"
										@delete-self="deleteConstraintGroupForm(index)"
									/>
								</li>
							</ul>
							<Button
								class="my-2"
								text
								icon="pi pi-plus"
								label="Add constraint"
								size="small"
								@click="addConstraintForm"
							/>
						</AccordionTab>
						<AccordionTab>
							<template #header>
								Settings
								<i class="pi pi-info-circle pl-2" v-tooltip="validateParametersToolTip" />
							</template>
							<section class="flex flex-column gap-2">
								<label>Preset (optional)</label>
								<Dropdown
									:model-value="presetType"
									placeholder="Select an option"
									:options="Object.values(PresetTypes)"
									@update:model-value="setPresetValues"
								/>
								<label>Select parameters of interest</label>
								<MultiSelect
									ref="columnSelect"
									class="w-full"
									:model-value="variablesOfInterest"
									:options="knobs.requestParameters"
									option-label="name"
									option-disabled="disabled"
									:show-toggle-all="false"
									placeholder="Select variables"
									@update:model-value="onToggleVariableOfInterest"
								/>
								<span class="timespan mt-3">
									<div>
										<label>Start time</label>
										<tera-input-number class="w-12" v-model="knobs.currentTimespan.start" />
									</div>
									<div>
										<label>End time</label>
										<tera-input-number class="w-12" v-model="knobs.currentTimespan.end" />
									</div>
									<div>
										<label>Number of timesteps</label>
										<tera-input-number class="w-12" v-model="knobs.numSteps" />
									</div>
								</span>
								<label class="mt-3">Timepoints</label>
								<code class="inset">
									{{ stepList.map((step) => Number(step.toFixed(3))).join(', ') }}
								</code>
								<label class="mt-3">Tolerance</label>
								<div class="input-tolerance fadein animation-ease-in-out animation-duration-350">
									<tera-input-number v-model="knobs.tolerance" />
									<Slider v-model="knobs.tolerance" :min="0.01" :max="1" :step="0.01" class="w-full mr-2" />
								</div>
							</section>
						</AccordionTab>
					</Accordion>
					<div class="spacer mb-6"></div>
				</main>
			</template>
		</tera-slider-panel>
		<tera-slider-panel
			class="input-config"
			:tabName="DrilldownTabs.Notebook"
			v-model:is-open="isSliderOpen"
			header="Raw request"
			content-width="500px"
		>
			<template #header>
				<div class="ml-auto flex gap-2">
					<Button
						outlined
						severity="secondary"
						label="Sync with Wizard"
						icon="pi pi-refresh"
						:disabled="isEqual(request, JSON.parse(rawRequest))"
						@click="syncNotebookWithWizard"
					/>
					<Button :loading="showSpinner" label="Run" icon="pi pi-play" @click="run" />
				</div>
			</template>
			<template #content>
				<span class="notebook-message">Edits here will not be reflected in the wizard.</span>
				<v-ace-editor
					v-model:value="rawRequest"
					lang="json"
					theme="chrome"
					style="width: 100%; height: 100%; border-radius: 0"
					class="ace-editor"
					:options="{ showPrintMargin: false }"
				/>
			</template>
		</tera-slider-panel>
		<template #preview>
			<tera-drilldown-preview
				:is-loading="showSpinner"
				:loading-progress="props.node.state.currentProgress"
				:loading-message="message"
				class="p-4"
			>
				<template v-if="!isEmpty(node.state.runId)">
					<header class="flex align-items-start">
						<div>
							<h4>{{ validatedModelConfiguration?.name }}</h4>
							<span class="secondary-text">{{ formatShort(validatedModelConfiguration?.createdOn) }}</span>
						</div>
						<div class="btn-group">
							<Button label="Add to report" outlined severity="secondary" disabled />
							<Button label="Save for reuse" outlined severity="secondary" @click="showSaveModal = true" />
						</div>
					</header>
					<template v-if="drilldownRef?.selectedTab === DrilldownTabs.Wizard">
						<Accordion multiple :active-index="parameterAndStateActiveIndicies">
							<AccordionTab header="Summary">
								<span class="ml-4">Summary text</span>
							</AccordionTab>
							<AccordionTab>
								<template #header> State variables<i class="pi pi-info-circle" /></template>
								<span class="ml-4" v-if="isEmpty(processedFunmanResult.boxes)"> No boxes were generated. </span>
								<template v-else-if="!isEmpty(selectedStateCharts)">
									<vega-chart
										v-for="(stateChart, index) in selectedStateCharts"
										:key="index"
										:visualization-spec="stateChart"
										:are-embed-actions-visible="false"
										expandable
									/>
								</template>
								<span class="ml-4" v-else> To view state charts select some in the Output settings. </span>
							</AccordionTab>
							<AccordionTab>
								<template #header>Parameters<i class="pi pi-info-circle" /></template>
								<span class="ml-4" v-if="isEmpty(processedFunmanResult.boxes)"> No boxes were generated. </span>
								<vega-chart
									v-else-if="!isEmpty(parameterCharts)"
									:visualization-spec="parameterCharts"
									:are-embed-actions-visible="false"
									expandable
									@chart-click="onParameterChartClick"
								/>
								<span class="ml-4" v-else> To view parameter charts select some in the Output settings. </span>
							</AccordionTab>
							<AccordionTab
								v-if="!isEmpty(calibratedConfigObservables) || !isEmpty(observableCharts)"
								header="Observables"
							>
								<tera-model-part
									v-if="!isEmpty(calibratedConfigObservables)"
									class="pl-4"
									:part-type="PartType.OBSERVABLE"
									:items="calibratedConfigObservables"
									:feature-config="{ isPreview: true }"
								/>
								<template v-if="!isEmpty(observableCharts)">
									<vega-chart
										v-for="(observableChart, index) in selectedObservableCharts"
										:key="index"
										:visualization-spec="observableChart"
										:are-embed-actions-visible="false"
										expandable
									/>
								</template>
							</AccordionTab>
							<AccordionTab header="Diagram">
								<tera-model-diagram v-if="model" :model="model" />
							</AccordionTab>
						</Accordion>
						<template v-if="model && validatedModelConfiguration && configuredMmt">
							<tera-initial-table
								:model="model"
								:model-configuration="validatedModelConfiguration"
								:model-configurations="[]"
								:mmt="configuredMmt"
								:mmt-params="mmtParams"
								:feature-config="{ isPreview: true }"
							/>
							<tera-parameter-table
								:model="model"
								:model-configuration="validatedModelConfiguration"
								:model-configurations="[]"
								:mmt="configuredMmt"
								:mmt-params="mmtParams"
								:feature-config="{ isPreview: true }"
							/>
						</template>
					</template>
					<v-ace-editor
						v-else-if="drilldownRef?.selectedTab === DrilldownTabs.Notebook"
						v-model:value="rawResponse"
						lang="json"
						theme="chrome"
						style="width: 100%; height: 100%; border-radius: 0"
						class="ace-editor"
						:options="{ showPrintMargin: false }"
					/>
				</template>
				<tera-operator-placeholder v-else class="h-full" :node="node" />
			</tera-drilldown-preview>
		</template>
		<template #sidebar-right>
			<tera-slider-panel
				v-model:is-open="isOutputSettingsPanelOpen"
				direction="right"
				class="input-config"
				header="Output settings"
				content-width="360px"
			>
				<template #content>
					<div class="output-settings-panel">
						<h5>State variables</h5>
						<tera-chart-control
							class="w-full"
							:chart-config="{
								selectedRun: 'fixme',
								selectedVariable: selectedStateSettings.map((s) => s.selectedVariables[0])
							}"
							multi-select
							:show-remove-button="false"
							:variables="stateOptions"
							@configuration-change="updateSelectedStates"
						/>
						<tera-chart-settings-item
							v-for="settings of chartSettings.filter((setting) => setting.type === ChartSettingType.VARIABLE)"
							:key="settings.id"
							:settings="settings"
							@open="activeChartSettings = settings"
							@remove="removeChartSetting"
						/>
						<hr />
						<h5>Parameters</h5>
						<tera-chart-control
							class="w-full"
							:chart-config="{
								selectedRun: 'fixme',
								selectedVariable: selectedParameterSettings.map((s) => s.selectedVariables[0])
							}"
							multi-select
							:show-remove-button="false"
							:variables="parameterOptions"
							@configuration-change="updateSelectedParameters"
						/>
						<tera-chart-settings-item
							v-for="settings of chartSettings.filter(
								(setting) => setting.type === ChartSettingType.DISTRIBUTION_COMPARISON
							)"
							:key="settings.id"
							:settings="settings"
							@open="activeChartSettings = settings"
							@remove="removeChartSetting"
						/>
						<hr />
						<h5>Observable</h5>
						<tera-chart-control
							class="w-full"
							:chart-config="{
								selectedRun: 'fixme',
								selectedVariable: selectedObservableSettings.map((s) => s.selectedVariables[0])
							}"
							multi-select
							:show-remove-button="false"
							:variables="observableOptions"
							@configuration-change="updateSelectedObservables"
						/>
						<tera-chart-settings-item
							v-for="settings of chartSettings.filter(
								(setting) => setting.type === ChartSettingType.VARIABLE_OBSERVABLE
							)"
							:key="settings.id"
							:settings="settings"
							@open="activeChartSettings = settings"
							@remove="removeChartSetting"
						/>
					</div>
					<div class="flex align-items-center gap-2 ml-4 mb-3">
						<Checkbox v-model="onlyShowLatestResults" binary @change="renderCharts" />
						<label>Only show furthest results</label>
					</div>
					<div class="flex align-items-center gap-2 ml-4 mb-4">
						<Checkbox v-model="focusOnModelChecks" binary @change="updateStateCharts" />
						<label>Focus on model checks</label>
					</div>
				</template>
			</tera-slider-panel>
		</template>
	</tera-drilldown>
	<tera-save-asset-modal
		:initial-name="validatedModelConfiguration?.name"
		:is-visible="showSaveModal"
		is-updating-asset
		:asset="{ ...(validatedModelConfiguration as ModelConfiguration), temporary: false }"
		:asset-type="AssetType.ModelConfiguration"
		@close-modal="showSaveModal = false"
		@on-save="onSaveForReuse"
		@on-update="onSaveForReuse"
	/>
</template>

<script setup lang="ts">
import { isEmpty, cloneDeep, isEqual } from 'lodash';
import { computed, ref, watch, onMounted } from 'vue';
import { logger } from '@/utils/logger';
import { formatShort } from '@/utils/date';
import Button from 'primevue/button';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import Slider from 'primevue/slider';
import MultiSelect from 'primevue/multiselect';
import Checkbox from 'primevue/checkbox';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Dropdown from 'primevue/dropdown';
import InputSwitch from 'primevue/inputswitch';
import VegaChart from '@/components/widgets/VegaChart.vue';

import '@/ace-config';
import { VAceEditor } from 'vue3-ace-editor';
import { saveCodeToState } from '@/services/notebook';

import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraModelPart from '@/components/model/model-parts/tera-model-part.vue';
import TeraInitialTable from '@/components/model/petrinet/tera-initial-table.vue';
import TeraParameterTable from '@/components/model/petrinet/tera-parameter-table.vue';
import TeraModelDiagram from '@/components/model/petrinet/tera-model-diagram.vue';

import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';

import TeraChartControl from '@/components/workflow/tera-chart-control.vue';
import TeraChartSettingsItem from '@/components/widgets/tera-chart-settings-item.vue';
import TeraSaveAssetModal from '@/components/project/tera-save-asset-modal.vue';

import type {
	FunmanInterval,
	FunmanPostQueriesRequest,
	Model,
	ModelParameter,
	TimeSpan,
	ModelConfiguration,
	Observable
} from '@/types/Types';
import { AssetType } from '@/types/Types';
import {
	type ProcessedFunmanResult,
	type FunmanConstraintsResponse,
	processFunman,
	cancelQueries,
	makeQueries
} from '@/services/models/funman-service';
import { createFunmanStateChart, createFunmanParameterCharts } from '@/services/charts';
import { WorkflowNode } from '@/types/workflow';
import { getRunResult } from '@/services/models/simulation-service';
import type { MiraModel, MiraTemplateParams } from '@/model-representation/mira/mira-common';
import { emptyMiraModel, makeConfiguredMMT } from '@/model-representation/mira/mira';
import { getAsConfiguredModel, getModelConfigurationById } from '@/services/model-configurations';
import { useToastService } from '@/services/toast';
import { pythonInstance } from '@/python/PyodideController';
import TeraConstraintGroupForm from '@/components/workflow/ops/funman/tera-constraint-group-form.vue';
import { DrilldownTabs, type ChartSetting, ChartSettingType } from '@/types/common';
import { stringToLatexExpression, getModel, getMMT } from '@/services/model';
import { toScientificNotation } from '@/utils/number';
import { removeChartSettingById, updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import { nodeOutputLabel } from '@/components/workflow/util';
import { formatJSON } from '@/services/code';
import TeraDrilldownPreview from '@/components/drilldown/tera-drilldown-preview.vue';
import { PartType } from '@/model-representation/service';
import { FunmanOperationState, Constraint, ConstraintType, CompartmentalConstraint } from './funman-operation';

const props = defineProps<{
	node: WorkflowNode<FunmanOperationState>;
}>();

const emit = defineEmits(['select-output', 'update-state', 'close']);

enum PresetTypes {
	Fast = 'Fast',
	Precise = 'Precise'
}

interface ModelParameterUI {
	name: string;
	label: string;
	interval: { lb?: number; ub?: number };
	disabled: boolean;
}

interface BasicKnobs {
	tolerance: number;
	currentTimespan: TimeSpan;
	numSteps: number;
	compartmentalConstraint: CompartmentalConstraint;
	requestParameters: ModelParameterUI[];
}

const drilldownRef = ref();

const knobs = ref<BasicKnobs>({
	tolerance: 0,
	currentTimespan: { start: 0, end: 0 },
	numSteps: 0,
	compartmentalConstraint: { name: '', isActive: false },
	requestParameters: []
});
const fastPreset: BasicKnobs = {
	tolerance: 0.5,
	currentTimespan: { start: 0, end: 100 },
	numSteps: 5,
	compartmentalConstraint: { name: 'Compartmental constraint', isActive: true },
	requestParameters: []
};
const precisePreset: BasicKnobs = {
	tolerance: 0.01,
	currentTimespan: { start: 0, end: 100 },
	numSteps: 100,
	compartmentalConstraint: { name: 'Compartmental constraint', isActive: true },
	requestParameters: []
};

const MAX = 99999999999;
const toast = useToastService();
const validateParametersToolTip =
	'Validate the configuration of the model using functional model analysis (FUNMAN). \n \n The parameter space regions defined by the model configuration are evaluated to satisfactory or unsatisfactory depending on whether they generate model outputs that are within a given set of time-dependent constraints';

const showSpinner = ref(false);
const isSliderOpen = ref(true);

const mass = ref('0');
const model = ref<Model | null>();
let configuredInputModel: Model | null = null;

const toolbarActiveIndicies = ref([0, 1]);
const parameterAndStateActiveIndicies = ref([0, 1, 2, 3]);

const stateIds = ref<string[]>([]);
const parameterIds = ref<string[]>([]);
const observableIds = ref<string[]>([]);

const message = computed(() =>
	props.node.state.isRequestUnresponsive ? "Process is stuck in Funman, click 'Stop' to cancel." : null
);

const variablesOfInterest = ref();
const onToggleVariableOfInterest = (event: any[]) => {
	variablesOfInterest.value = event;
	const namesOfInterest = event.map((d) => d.name);
	knobs.value.requestParameters.forEach((d) => {
		d.label = namesOfInterest.includes(d.name) ? 'all' : 'any';
	});
	const state = cloneDeep(props.node.state);
	state.requestParameters = cloneDeep(knobs.value.requestParameters);
	emit('update-state', state);
};

const presetType = computed(() => {
	if (
		knobs.value.tolerance === fastPreset.tolerance &&
		knobs.value.numSteps === fastPreset.numSteps &&
		isEqual(knobs.value.requestParameters, fastPreset.requestParameters)
	) {
		return PresetTypes.Fast;
	}
	if (
		knobs.value.tolerance === precisePreset.tolerance &&
		knobs.value.numSteps === precisePreset.numSteps &&
		isEqual(knobs.value.requestParameters, precisePreset.requestParameters)
	) {
		return PresetTypes.Precise;
	}
	return '';
});

const stepList = computed(() => {
	const { start, end } = knobs.value.currentTimespan;
	const steps = knobs.value.numSteps;

	const stepSize = (end - start) / steps;
	return [start, ...Array.from({ length: steps - 1 }, (_, i) => (i + 1) * stepSize), end];
});

const rawRequest = ref('');
const rawResponse = ref('');

const request = computed<FunmanPostQueriesRequest | null>(() => {
	if (!configuredInputModel || !model.value?.id) {
		return null;
	}

	const constraints = props.node.state.constraintGroups
		?.map((constraintGroup) => {
			if (!constraintGroup.isActive) return null;
			const { name, constraintType, variables, timepoints } = constraintGroup;
			// Use inputted weights when linearly constrained otherwise use implicit weights
			const weights =
				constraintType === ConstraintType.LinearlyConstrained
					? constraintGroup.weights
					: Array<number>(variables.length).fill(1.0);

			// Increasing/descreasing (monotonicity)
			if (constraintType === ConstraintType.Increasing || constraintType === ConstraintType.Decreasing) {
				return {
					soft: true,
					name,
					timepoints,
					additive_bounds: { lb: 0.0, original_width: MAX },
					variables,
					weights:
						constraintGroup.constraintType === ConstraintType.Increasing
							? weights.map((d) => Math.abs(d))
							: weights.map((d) => -Math.abs(d)),
					derivative: true
				};
			}

			// Use bounds needed that are saved in the UI
			const interval: FunmanInterval = {};
			if (constraintType === ConstraintType.LessThan || constraintType === ConstraintType.LessThanOrEqualTo) {
				interval.ub = constraintGroup.interval.ub;
			} else if (
				constraintType === ConstraintType.GreaterThan ||
				constraintType === ConstraintType.GreaterThanOrEqualTo
			) {
				interval.lb = constraintGroup.interval.lb;
			} else if (constraintType === ConstraintType.LinearlyConstrained) {
				interval.lb = constraintGroup.interval.lb;
				interval.ub = constraintGroup.interval.ub;
			}

			if (constraintType === ConstraintType.LessThanOrEqualTo) {
				interval.closed_upper_bound = true;
			}

			timepoints.closed_upper_bound = true;

			return {
				name,
				variables,
				weights,
				additive_bounds: interval,
				timepoints
			};
		})
		.filter(Boolean); // Removes falsey values

	return {
		model: configuredInputModel,
		request: {
			constraints,
			parameters: knobs.value.requestParameters.map(({ disabled, ...rest }) => rest), // Remove the disabled property from the request (it's only used for UI)
			structure_parameters: [
				{
					name: 'schedules',
					schedules: [{ timepoints: stepList.value }]
				}
			],
			config: {
				use_compartmental_constraints: knobs.value.compartmentalConstraint.isActive,
				normalization_constant:
					knobs.value.compartmentalConstraint.isActive && configuredInputModel.semantics ? parseFloat(mass.value) : 1,
				normalize: false,
				tolerance: knobs.value.tolerance,
				// FIXME: Set to max logging for debugging and checking liveliness , remove when stable. Oct 2024
				verbosity: 0
			}
		}
	};
});

async function run() {
	const requestToUse =
		drilldownRef.value?.selectedTab === DrilldownTabs.Wizard ? request.value : JSON.parse(rawRequest.value);

	if (drilldownRef.value?.selectedTab === DrilldownTabs.Notebook) {
		updateCodeState(true);
	}

	if (!requestToUse || !model.value?.id || !configuredInputModel) {
		toast.error('', 'No Model provided for request');
		return;
	}
	const response = await makeQueries(
		requestToUse,
		model.value.id,
		nodeOutputLabel(
			props.node,
			`Validated ${configuredInputModel.header.name} Result (${drilldownRef.value?.selectedTab.toLowerCase()})`
		)
	);
	// Setup the in-progress id
	const state = cloneDeep(props.node.state);
	state.inProgressId = response.id;
	emit('update-state', state);
}

async function stop() {
	const state = cloneDeep(props.node.state);
	await cancelQueries(state.inProgressId);
	showSpinner.value = false;
	// Clean up the in-progress id
	state.inProgressId = '';
	emit('update-state', state);
}

function updateCodeState(hasCodeRun: boolean) {
	const state = saveCodeToState(props.node, rawRequest.value, hasCodeRun);
	emit('update-state', state);
}

function syncNotebookWithWizard() {
	rawRequest.value = formatJSON(JSON.stringify(request.value));
	updateCodeState(false);
}

const addConstraintForm = () => {
	const state = cloneDeep(props.node.state);
	state.constraintGroups.push({
		name: `Constraint ${state.constraintGroups.length + 1}`,
		isActive: true,
		timepoints: { lb: 0, ub: 100 },
		interval: { lb: 0, ub: 100 },
		constraint: Constraint.State,
		variables: [],
		weights: [],
		constraintType: ConstraintType.LessThan
	});
	emit('update-state', state);
};

const deleteConstraintGroupForm = (index: number) => {
	const state = cloneDeep(props.node.state);
	state.constraintGroups.splice(index, 1);
	emit('update-state', state);
};

const updateConstraintGroupForm = (index: number, key: string, value: any) => {
	const state = cloneDeep(props.node.state);

	// Changing constraint resets settings
	if (key === 'constraint') {
		state.constraintGroups[index].variables = [];
		state.constraintGroups[index].weights = [];
		state.constraintGroups[index].interval = { lb: 0, ub: 100 };
	}

	// Update changes
	state.constraintGroups[index][key] = value;

	// Make sure weights makes sense
	const weightLength = state.constraintGroups[index].weights.length;
	const variableLength = state.constraintGroups[index].variables.length;
	if (weightLength !== variableLength) {
		state.constraintGroups[index].weights = Array<number>(variableLength).fill(1.0);
	}
	emit('update-state', state);
};

const setModelOptions = async () => {
	if (!configuredInputModel) return;

	const renameReserved = (v: string) => {
		const reserved = ['lambda'];
		if (reserved.includes(v)) return `${v}_`;
		return v;
	};

	// Calculate mass
	const semantics = configuredInputModel.semantics;
	const modelInitials = semantics?.ode.initials;
	const modelMassExpression = modelInitials?.map((d) => renameReserved(d.expression)).join(' + ');

	const parametersMap = {};
	semantics?.ode.parameters?.forEach((d) => {
		// FIXME: may need to sample distributions if value is not available
		parametersMap[renameReserved(d.id)] = d.value || 0;
	});

	mass.value = await pythonInstance.evaluateExpression(modelMassExpression as string, parametersMap);

	const ode = configuredInputModel.semantics?.ode;
	if (ode) {
		if (ode.initials) stateIds.value = ode.initials.map((s) => s.target);
		if (ode.parameters) parameterIds.value = ode.parameters.map((d) => d.id);
		if (ode.observables) observableIds.value = ode.observables.map((d) => d.id);
	}

	const state = cloneDeep(props.node.state);
	knobs.value.numSteps = state.numSteps;
	knobs.value.currentTimespan = cloneDeep(state.currentTimespan);
	knobs.value.tolerance = state.tolerance;
	knobs.value.compartmentalConstraint = state.compartmentalConstraint;

	if (configuredInputModel.semantics?.ode.parameters) {
		setRequestParameters(configuredInputModel.semantics?.ode.parameters);
		variablesOfInterest.value = knobs.value.requestParameters.filter((d: any) => d.label === 'all');
	} else {
		toast.error('', 'Provided model has no parameters');
	}

	state.requestParameters = cloneDeep(knobs.value.requestParameters);
	emit('update-state', state);
};

// eslint-disable-next-line
const setRequestParameters = (modelParameters: ModelParameter[]) => {
	const previous = props.node.state.requestParameters;
	const labelMap = new Map<string, string>();
	previous.forEach((p) => {
		labelMap.set(p.name, p.label);
	});

	let paramsOfInterestFastPresetCounter = 0;
	modelParameters.forEach((ele) => {
		const name = ele.id;

		const param: ModelParameterUI = {
			name,
			label: (labelMap.get(name) as string) ?? 'any',
			interval: { lb: ele.value, ub: ele.value },
			disabled: false
		};

		if (ele.distribution) {
			param.interval = {
				lb: ele.distribution.parameters.minimum,
				ub: ele.distribution.parameters.maximum
			};
			paramsOfInterestFastPresetCounter++;
		} else {
			param.disabled = true; // Disable if constant
		}

		knobs.value.requestParameters.push(param);
		fastPreset.requestParameters.push(
			// For fast preset just choose at least 2 parameters of interest
			param.disabled || paramsOfInterestFastPresetCounter > 2 ? param : { ...param, label: 'all' }
		);
		precisePreset.requestParameters.push(param.disabled ? param : { ...param, label: 'all' });
	});
};

const onSelection = (id: string) => {
	emit('select-output', id);
};

watch(
	() => props.node.state.inProgressId,
	(id) => {
		if (!id || id === '') {
			showSpinner.value = false;
		} else {
			showSpinner.value = true;
		}
	},
	{ immediate: true }
);

function setPresetValues(selectedPresetType: PresetTypes) {
	if (selectedPresetType === PresetTypes.Fast) {
		knobs.value = { ...knobs.value, ...cloneDeep(fastPreset) };
		variablesOfInterest.value = fastPreset.requestParameters.filter((d: any) => d.label === 'all');
	} else if (selectedPresetType === PresetTypes.Precise) {
		knobs.value = { ...knobs.value, ...cloneDeep(precisePreset) };
		variablesOfInterest.value = precisePreset.requestParameters.filter((d: any) => d.label === 'all');
	}
	const state = cloneDeep(props.node.state);
	state.requestParameters = cloneDeep(knobs.value.requestParameters);
	emit('update-state', state);
}

/* Check for simple parameter changes */
watch(
	() => knobs.value,
	() => {
		const state = cloneDeep(props.node.state);
		state.tolerance = knobs.value.tolerance;
		state.currentTimespan.start = knobs.value.currentTimespan.start;
		state.currentTimespan.end = knobs.value.currentTimespan.end;
		state.numSteps = knobs.value.numSteps;
		state.compartmentalConstraint = knobs.value.compartmentalConstraint;

		emit('update-state', state);
	},
	{ deep: true }
);

onMounted(async () => {
	const modelConfigurationId = props.node.inputs[0].value?.[0];
	if (!modelConfigurationId) return;
	const modelConfiguration = await getModelConfigurationById(modelConfigurationId);
	configuredInputModel = await getAsConfiguredModel(modelConfigurationId);

	setModelOptions();

	model.value = await getModel(modelConfiguration.modelId);
	if (!model.value) return;
	const response = await getMMT(model.value);
	if (!response) return;
	mmt = response.mmt;
	mmtParams.value = response.template_params;

	if (isEmpty(props.node.state.notebookHistory)) {
		syncNotebookWithWizard();
	} else {
		rawRequest.value = props.node.state.notebookHistory[0].code;
	}

	prepareOutput();
});

/*
// Output panel/settings //
*/
let constraintsResponse: FunmanConstraintsResponse[] = [];
let mmt: MiraModel = emptyMiraModel();
let funmanResult: any = {};
const processedFunmanResult = ref<ProcessedFunmanResult>({ boxes: [], trajectories: [], observableTrajectories: [] });

// Model configuration stuff
const validatedModelConfiguration = ref<ModelConfiguration | null>(null);
const showSaveModal = ref(false);
const configuredMmt = ref<MiraModel | null>(null);
const mmtParams = ref<MiraTemplateParams>({});
const calibratedConfigObservables = ref<Observable[]>([]);

const stateCharts = ref<any>([{}]);
const selectedStateCharts = computed(() => {
	const selectedStateIds = selectedStateSettings.value.map((setting) => setting.selectedVariables[0]);
	return stateCharts.value.filter((chart) => selectedStateIds.includes(chart.id));
});
const parameterCharts = ref<any>({});

const observableCharts = ref<any>([{}]);
const selectedObservableCharts = computed(() => {
	const selectedObservableIds = selectedObservableSettings.value.map((setting) => setting.selectedVariables[0]);
	return observableCharts.value.filter((chart) => selectedObservableIds.includes(chart.id));
});

const stateOptions = ref<string[]>([]);
const parameterOptions = ref<string[]>([]);
const observableOptions = ref<string[]>([]);
const onlyShowLatestResults = ref(false);
const focusOnModelChecks = ref(false);

const isOutputSettingsPanelOpen = ref(false);
const activeChartSettings = ref<ChartSetting | null>(null);
const chartSettings = computed(() => props.node.state.chartSettings ?? []);
const selectedStateSettings = computed(() =>
	chartSettings.value.filter((setting) => setting.type === ChartSettingType.VARIABLE)
);
const selectedParameterSettings = computed(() =>
	chartSettings.value.filter((setting) => setting.type === ChartSettingType.DISTRIBUTION_COMPARISON)
);
const selectedObservableSettings = computed(() =>
	chartSettings.value.filter((setting) => setting.type === ChartSettingType.VARIABLE_OBSERVABLE)
);

let selectedBoxId: number = -1;

async function onSaveForReuse() {
	showSaveModal.value = false;
	validatedModelConfiguration.value = await getModelConfigurationById(funmanResult.modelConfigurationId); // Refresh to see updated name
}

// Once a parameter tick is chosen, its corresponding line on the state chart will be highlighted
function onParameterChartClick(eventData: any) {
	// If a tick is clicked it will have a boxId, if the bar is clicked then we reset (show all lines)
	selectedBoxId = eventData.boxId ?? -1;
	updateStateCharts();
}

function updateStateCharts() {
	if (isEmpty(processedFunmanResult.value.trajectories)) return;
	const trajectories = onlyShowLatestResults.value
		? processedFunmanResult.value.trajectories.filter(({ isAtLatestTimestep }) => isAtLatestTimestep)
		: processedFunmanResult.value.trajectories;
	stateCharts.value = funmanResult.model.petrinet.model.states.map(({ id }) =>
		createFunmanStateChart(trajectories, constraintsResponse, id, focusOnModelChecks.value, selectedBoxId)
	);
}

function updateParameterCharts() {
	if (isEmpty(processedFunmanResult.value.boxes)) return;
	const selectedParameterIds = selectedParameterSettings.value.map((setting) => setting.selectedVariables[0]);
	const distributionParameters = funmanResult.request.parameters.filter(
		// TODO: This first conditional may change as funman will return constants soon
		(d: any) => d.interval.lb !== d.interval.ub && selectedParameterIds.includes(d.name)
	);
	const boxes = onlyShowLatestResults.value
		? processedFunmanResult.value.boxes.filter(({ isAtLatestTimestep }) => isAtLatestTimestep)
		: processedFunmanResult.value.boxes;
	parameterCharts.value = createFunmanParameterCharts(distributionParameters, boxes);
}

function updateObservableCharts() {
	if (isEmpty(processedFunmanResult.value.observableTrajectories)) return;
	const observableTrajectories = onlyShowLatestResults.value
		? processedFunmanResult.value.observableTrajectories.filter(({ isAtLatestTimestep }) => isAtLatestTimestep)
		: processedFunmanResult.value.observableTrajectories;
	observableCharts.value = funmanResult.model.petrinet.semantics.ode.observables.map(({ id }) =>
		createFunmanStateChart(observableTrajectories, constraintsResponse, id, focusOnModelChecks.value, selectedBoxId)
	);
}

function updateSelectedStates(event) {
	emit('update-state', {
		...props.node.state,
		chartSettings: updateChartSettingsBySelectedVariables(
			chartSettings.value,
			ChartSettingType.VARIABLE,
			event.selectedVariable
		)
	});
}

function updateSelectedParameters(event) {
	emit('update-state', {
		...props.node.state,
		chartSettings: updateChartSettingsBySelectedVariables(
			chartSettings.value,
			ChartSettingType.DISTRIBUTION_COMPARISON,
			event.selectedVariable
		)
	});
	updateParameterCharts(); // Rerender when we remove/add charts since they are all contained in the same visualization
}

function updateSelectedObservables(event) {
	emit('update-state', {
		...props.node.state,
		chartSettings: updateChartSettingsBySelectedVariables(
			chartSettings.value,
			ChartSettingType.VARIABLE_OBSERVABLE,
			event.selectedVariable
		)
	});
}

function removeChartSetting(chartId: string) {
	const chartType = chartSettings.value.find((setting) => setting.id === chartId)?.type;
	emit('update-state', {
		...props.node.state,
		chartSettings: removeChartSettingById(chartSettings.value, chartId)
	});
	if (chartType === ChartSettingType.DISTRIBUTION_COMPARISON) {
		updateParameterCharts();
	}
}

function renderCharts() {
	updateStateCharts();
	updateParameterCharts();
	updateObservableCharts();
}

async function prepareOutput() {
	if (!props.node.state.runId) return;
	const rawFunmanResult = await getRunResult(props.node.state.runId, 'validation.json');
	if (!rawFunmanResult) {
		logger.error('Failed to fetch funman result');
		return;
	}
	rawResponse.value = formatJSON(rawFunmanResult);
	funmanResult = JSON.parse(rawFunmanResult);
	constraintsResponse = funmanResult.request.constraints;

	const modelConfigurationId: string = funmanResult.modelConfigurationId;
	if (!modelConfigurationId) {
		logger.error('No model configuration id found in funman result');
		return;
	}
	validatedModelConfiguration.value = await getModelConfigurationById(funmanResult.modelConfigurationId);
	if (!validatedModelConfiguration.value) {
		logger.error('Failed to fetch model configuration');
		return;
	}
	configuredMmt.value = makeConfiguredMMT(mmt, validatedModelConfiguration.value);
	calibratedConfigObservables.value = validatedModelConfiguration.value.observableSemanticList.map(
		({ referenceId, states, expression }) => ({
			id: referenceId,
			name: referenceId,
			states,
			expression
		})
	);

	stateOptions.value = funmanResult.model.petrinet.model.states.map(({ id }) => id);
	parameterOptions.value = funmanResult.request.parameters
		.filter((d: any) => d.interval.lb !== d.interval.ub)
		.map(({ name }) => name);
	observableOptions.value = funmanResult.model?.petrinet?.semantics?.ode?.observables.map(({ id }) => id);

	// Initialize default output settings
	const state = cloneDeep(props.node.state);
	state.chartSettings = updateChartSettingsBySelectedVariables([], ChartSettingType.VARIABLE, stateOptions.value);
	state.chartSettings = updateChartSettingsBySelectedVariables(
		state.chartSettings,
		ChartSettingType.DISTRIBUTION_COMPARISON,
		parameterOptions.value
	);
	state.chartSettings = updateChartSettingsBySelectedVariables(
		state.chartSettings,
		ChartSettingType.VARIABLE_OBSERVABLE,
		observableOptions.value
	);
	emit('update-state', state);

	processedFunmanResult.value = processFunman(funmanResult);
	renderCharts();
}

const expression = computed(() =>
	stringToLatexExpression(
		stateIds.value
			.map((s, index) => `${s}${index === stateIds.value.length - 1 ? `\\geq 0` : '\\geq 0 \\newline '}`)
			.join('')
	)
);

const massScientificNotation = computed(() => {
	const notation = toScientificNotation(parseFloat(mass.value));
	return `${notation.mantissa} \\times 10^${notation.exponent}`;
});

watch(
	() => props.node.state.runId,
	() => prepareOutput()
);
</script>

<style scoped>
:deep(.expression-constraint) {
	max-height: 150px;
	overflow: auto;
	margin-top: var(--gap-4);
	margin-bottom: var(--gap-4);
	padding: var(--gap-2) var(--gap-3);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	background: var(--surface-50);
	box-shadow: inset 0px 0px 4px rgba(0, 0, 0, 0.05);
}

.top-toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--gap-1) var(--gap-4);
}

.btn-group {
	display: flex;
	align-items: center;
	gap: var(--gap-2);
	margin-left: auto;
}

.pi-info-circle {
	margin-left: var(--gap-2);
}

.secondary-text {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
}

.notebook-section:deep(main) {
	gap: var(--gap-2);
	position: relative;
}

code {
	background-color: var(--gray-200);
	color: var(--text-color-subdued);
	border-radius: var(--border-radius);
	border: 1px solid var(--surface-border);
	padding: var(--gap-2) var(--gap-3);
	overflow-wrap: break-word;
	font-size: var(--font-caption);
	max-height: 10rem;
	overflow: auto;
}

.inset {
	box-shadow: inset 0px 0px 4px rgba(0, 0, 0, 0.05);
}

.timespan {
	display: flex;
	align-items: end;
	gap: var(--gap-1);
	overflow: auto;

	& > div {
		display: flex;
		flex-direction: column;
		gap: var(--gap-2);
	}
}

.input-tolerance {
	display: flex;
	width: 100%;
	align-items: center;
	align-self: stretch;
	gap: var(--gap-6);
}

ul {
	list-style-type: none;
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);

	& section {
		display: flex;
		padding: var(--gap-4);
		flex-direction: column;
		background: var(--surface-0);
		border: 1px solid var(--surface-border-light);
		border-radius: var(--border-radius);
	}
}

.p-accordion {
	padding: 0 var(--gap-2);
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

/* Override grid template so output expands when sidebar is closed */
.overlay-container:deep(section.scale main) {
	grid-template-columns: auto 1fr;
}

.input-config:deep(.content-wrapper) {
	padding-bottom: 0;
}

.notebook-message {
	padding-left: var(--gap-4);
	font-size: var(--font-caption);
}

.nudge-left {
	margin-left: -0.5rem;
}
</style>
