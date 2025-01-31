<template>
	<tera-drilldown
		v-bind="$attrs"
		:node="node"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
		class="drilldown"
	>
		<tera-drilldown-section :tabName="DrilldownTabs.Wizard" class="input-config">
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Simulation ensemble settings"
				content-width="420px"
			>
				<template #content>
					<div class="toolbar">
						<p>Click Run to start the simulation.</p>
						<span class="flex gap-2">
							<tera-pyciemss-cancel-button class="mr-auto" :simulation-run-id="cancelRunId" />
							<Button label="Run" icon="pi pi-play" @click="runEnsemble" :disabled="false" />
						</span>
					</div>

					<Accordion :multiple="true" :active-index="activeAccordionIndicies">
						<!-- Mapping -->
						<AccordionTab header="Mapping">
							<p class="subheader">All variables mapped should be normalized (could use observables)</p>
							<template v-if="knobs.mapping.length > 0">
								<table class="w-full mb-2">
									<tbody>
										<tr>
											<th>Ensemble variables</th>
											<th v-for="(element, i) in listModelLabels" :key="i">
												{{ element }}
											</th>
										</tr>
										<tr v-for="(ele, indx) in knobs.mapping" :key="indx">
											<td>
												<tera-input-text v-model="ele.newName" auto-focus class="w-full" placeholder="Add a name" />
											</td>
											<td v-for="key in modelConfigurationIds" :key="key">
												<Dropdown
													class="w-full"
													:options="allModelOptions[key]"
													v-model="ele.modelConfigurationMappings[key]"
													placeholder="Select"
													@change="updateMapping()"
												/>
											</td>
											<td>
												<Button class="p-button-sm" icon="pi pi-times" rounded text @click="deleteMappingRow(ele.id)" />
											</td>
										</tr>
									</tbody>
								</table>
							</template>
							<section>
								<Button
									v-if="!showAddMappingInput"
									outlined
									:style="{ marginRight: 'auto' }"
									label="Add mapping"
									size="small"
									severity="secondary"
									icon="pi pi-plus"
									@click="
										newSolutionMappingKey = '';
										showAddMappingInput = true;
									"
								/>
								<div v-if="showAddMappingInput" class="flex">
									<tera-input-text
										v-model="newSolutionMappingKey"
										auto-focus
										class="w-full"
										placeholder="Add a name"
										@keydown.enter.stop.prevent="
											addMapping();
											showAddMappingInput = false;
										"
									/>
									<span class="flex gap-2">
										<Button
											class="p-button-sm p-button-outlined ml-2"
											severity="secondary"
											icon="pi pi-times"
											label="Cancel"
											@click="
												newSolutionMappingKey = '';
												showAddMappingInput = false;
											"
										/>
										<Button
											:disabled="!newSolutionMappingKey"
											class="p-button-sm p-button-outlined ml-2"
											icon="pi pi-check"
											label="Add"
											@click="
												addMapping();
												showAddMappingInput = false;
											"
										/>
									</span>
								</div>
							</section>
						</AccordionTab>

						<!-- Model weights -->
						<AccordionTab header="Model weights">
							<p class="subheader">
								This encodes your relative confidence for each model. These are the alpha parameters of a Dirichlet
								distribution.
							</p>
							<div v-if="!_.isEmpty(modelConfigIdToNameMap) && !_.isEmpty(knobs.weights)" class="model-weights">
								<table class="p-datatable-table">
									<tbody class="p-datatable-tbody">
										<tr v-for="key in modelConfigurationIds" :key="key">
											<td>
												{{ modelConfigIdToNameMap[key] }}
											</td>
											<td>
												<tera-signal-bars
													label="Relative certainty"
													:min-option="1"
													v-model="knobs.weights[key]"
													@change="updateWeights()"
												/>
											</td>
										</tr>
									</tbody>
								</table>
							</div>
						</AccordionTab>

						<!-- Other Settings -->
						<AccordionTab header="Other Settings">
							<div class="form-section" v-if="isSidebarOpen">
								<div class="input-row">
									<div class="label-and-input">
										<label>Start time</label>
										<tera-input-number class="w-12" disabled :model-value="0" />
									</div>
									<div class="label-and-input">
										<label>End time</label>
										<tera-input-number v-model="knobs.endTime" />
									</div>
								</div>
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
								<!-- Number of Samples & Method -->
								<div class="input-row">
									<div class="label-and-input">
										<label>Number of samples</label>
										<tera-input-number v-model="knobs.numSamples" inputId="integeronly" :min="1" />
									</div>
									<div class="label-and-input">
										<label>Solver method</label>
										<Dropdown
											v-model="knobs.method"
											:options="[CiemssMethodOptions.dopri5, CiemssMethodOptions.rk4, CiemssMethodOptions.euler]"
										/>
									</div>
									<div class="label-and-input">
										<label>Solver step size</label>
										<tera-input-number
											:disabled="![CiemssMethodOptions.rk4, CiemssMethodOptions.euler].includes(knobs.method)"
											:min="0"
											v-model="knobs.stepSize"
										/>
									</div>
								</div>
							</div>
						</AccordionTab>
					</Accordion>
				</template>
			</tera-slider-panel>
		</tera-drilldown-section>

		<tera-drilldown-section :tabName="DrilldownTabs.Notebook" class="input-config">
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Simulation ensemble settings"
				content-width="420px"
			>
				<template #content>
					<div class="mt-3 ml-4 mr-2">Under construction. Use the wizard for now.</div>
				</template>
			</tera-slider-panel>
		</tera-drilldown-section>

		<template #preview>
			<tera-drilldown-section
				title="Simulation output"
				:options="outputs"
				v-model:output="selectedOutputId"
				is-selectable
				:is-loading="showSpinner"
				@update:selection="onSelection"
			>
				<template #header-controls-right>
					<Button class="mr-3" label="Save for re-use" severity="secondary" outlined @click="showSaveDataset = true" />
				</template>
				<tera-notebook-error v-if="!_.isEmpty(node.state?.errorMessage?.traceback)" v-bind="node.state.errorMessage" />
				<section ref="outputPanel">
					<tera-simulate-chart
						v-for="(cfg, index) of node.state.chartConfigs"
						:key="index"
						:run-results="runResults"
						:chartConfig="{ selectedRun: selectedRunId, selectedVariable: cfg }"
						has-mean-line
						:size="chartSize"
						@configuration-change="chartProxy.configurationChange(index, $event)"
						@remove="chartProxy.removeChart(index)"
						show-remove-button
					/>
					<Button
						class="add-chart"
						text
						:outlined="true"
						@click="chartProxy.addChart()"
						label="Add chart"
						icon="pi pi-plus"
					/>
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
				<template #content>
					<div class="mt-3 ml-4 mr-2">Under construction.</div>
				</template>
				<!-- TODO Chart options here -->
			</tera-slider-panel>
		</template>
	</tera-drilldown>
	<tera-save-simulation-modal
		:is-visible="showSaveDataset"
		@close-modal="showSaveDataset = false"
		:simulation-id="node.state.forecastId"
		:assets="[{ id: selectedRunId, type: AssetType.Dataset }]"
	/>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { ref, computed, watch, onMounted } from 'vue';
import Button from 'primevue/button';
import AccordionTab from 'primevue/accordiontab';
import Accordion from 'primevue/accordion';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import Dropdown from 'primevue/dropdown';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraPyciemssCancelButton from '@/components/pyciemss/tera-pyciemss-cancel-button.vue';
import TeraSimulateChart from '@/components/workflow/tera-simulate-chart.vue';
import {
	getRunResultCiemss,
	makeEnsembleCiemssSimulation,
	CiemssMethodOptions
} from '@/services/models/simulation-service';
import { getModelConfigurationById, getObservables, getInitials } from '@/services/model-configurations';
import { chartActionsProxy, drilldownChartSize, nodeMetadata } from '@/components/workflow/util';
import type { WorkflowNode } from '@/types/workflow';
import { AssetType, type EnsembleSimulationCiemssRequest } from '@/types/Types';
import { RunResults } from '@/types/SimulateConfig';
import { DrilldownTabs, CiemssPresetTypes } from '@/types/common';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import TeraSignalBars from '@/components/widgets/tera-signal-bars.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraSaveSimulationModal from '@/components/project/tera-save-simulation-modal.vue';
import { v4 as uuidv4 } from 'uuid';
import {
	SimulateEnsembleCiemssOperationState,
	SimulateEnsembleMappingRow,
	SimulateEnsembleWeights,
	speedValues,
	normalValues
} from './simulate-ensemble-ciemss-operation';
import { formatSimulateModelConfigurations } from './simulate-ensemble-util';

const props = defineProps<{
	node: WorkflowNode<SimulateEnsembleCiemssOperationState>;
}>();
const emit = defineEmits(['select-output', 'update-state', 'close']);

interface BasicKnobs {
	mapping: SimulateEnsembleMappingRow[];
	weights: SimulateEnsembleWeights;
	numSamples: number;
	method: CiemssMethodOptions;
	stepSize: number;
	endTime: number;
}

const knobs = ref<BasicKnobs>({
	mapping: props.node.state.mapping,
	weights: props.node.state.weights,
	numSamples: props.node.state.numSamples,
	method: props.node.state.method,
	stepSize: props.node.state.stepSize,
	endTime: props.node.state.endTime
});

const activeAccordionIndicies = ref([0, 1, 2]);
const isSidebarOpen = ref(true);
const isOutputSettingsPanelOpen = ref(false);
const showSpinner = ref(false);
const showSaveDataset = ref(false);
const showAddMappingInput = ref(false);
const listModelLabels = ref<string[]>([]);

const presetType = computed(() => {
	if (
		knobs.value.numSamples === speedValues.numSamples &&
		knobs.value.method === speedValues.method &&
		knobs.value.stepSize === speedValues.stepSize
	) {
		return CiemssPresetTypes.Fast;
	}
	if (knobs.value.numSamples === normalValues.numSamples && knobs.value.method === normalValues.method) {
		return CiemssPresetTypes.Normal;
	}
	return '';
});

// List of each observible + state for each model.
const allModelOptions = ref<{ [key: string]: string[] }>({});
const modelConfigurationIds: string[] = props.node.inputs.map((ele) => ele.value?.[0]).filter(Boolean);
const modelConfigIdToNameMap = ref<Record<string, string>>({});

const newSolutionMappingKey = ref<string>('');
const runResults = ref<RunResults>({});
const cancelRunId = computed(() => props.node.state.inProgressForecastId);
// Preview selection
const outputs = computed(() => {
	if (!_.isEmpty(props.node.outputs)) {
		return [
			{
				label: 'Select outputs',
				items: props.node.outputs
			}
		];
	}
	return [];
});
const selectedOutputId = ref<string>();
const selectedRunId = ref<string>('');

const outputPanel = ref(null);
const chartSize = computed(() => drilldownChartSize(outputPanel.value));
const chartProxy = chartActionsProxy(props.node, (state: SimulateEnsembleCiemssOperationState) => {
	emit('update-state', state);
});

const onSelection = (id: string) => {
	emit('select-output', id);
};

const setPresetValues = (data: CiemssPresetTypes) => {
	if (data === CiemssPresetTypes.Normal) {
		knobs.value.numSamples = normalValues.numSamples;
		knobs.value.method = normalValues.method;
	}
	if (data === CiemssPresetTypes.Fast) {
		knobs.value.numSamples = speedValues.numSamples;
		knobs.value.method = speedValues.method;
		knobs.value.stepSize = speedValues.stepSize;
	}
};

const addMapping = () => {
	// create empty configuration mappings
	const configMappings = {};
	modelConfigurationIds.forEach((id) => {
		configMappings[id as string] = '';
	});

	knobs.value.mapping.push({
		id: uuidv4(),
		newName: newSolutionMappingKey.value,
		modelConfigurationMappings: configMappings
	});

	const state = _.cloneDeep(props.node.state);
	state.mapping = knobs.value.mapping;
	emit('update-state', state);
};

const deleteMappingRow = (id: string) => {
	knobs.value.mapping = knobs.value.mapping.filter((ele) => ele.id !== id);
	const state = _.cloneDeep(props.node.state);
	state.mapping = knobs.value.mapping;
	emit('update-state', state);
};

const updateMapping = () => {
	const state = _.cloneDeep(props.node.state);
	state.mapping = knobs.value.mapping;
	emit('update-state', state);
};

const updateWeights = () => {
	const state = _.cloneDeep(props.node.state);
	state.weights = knobs.value.weights;
	emit('update-state', state);
};

const runEnsemble = async () => {
	const modelConfigs = formatSimulateModelConfigurations(knobs.value.mapping, knobs.value.weights);
	const params: EnsembleSimulationCiemssRequest = {
		modelConfigs,
		timespan: {
			start: 0,
			end: knobs.value.endTime
		},
		engine: 'ciemss',
		extra: {
			num_samples: knobs.value.numSamples,
			solver_method: knobs.value.method,
			solver_step_size: knobs.value.stepSize
		}
	};
	const response = await makeEnsembleCiemssSimulation(params, nodeMetadata(props.node));

	const state = _.cloneDeep(props.node.state);
	state.inProgressForecastId = response.simulationId;
	emit('update-state', state);
};

onMounted(async () => {
	if (!modelConfigurationIds) return;
	const allModelConfigurations = await Promise.all(modelConfigurationIds.map((id) => getModelConfigurationById(id)));

	modelConfigIdToNameMap.value = {};
	allModelConfigurations.forEach((config) => {
		modelConfigIdToNameMap.value[config.id as string] = config.name as string;
	});

	allModelOptions.value = {};
	for (let i = 0; i < allModelConfigurations.length; i++) {
		const tempList: string[] = [];
		getInitials(allModelConfigurations[i]).forEach((element) => tempList.push(element.target));
		getObservables(allModelConfigurations[i]).forEach((element) => tempList.push(element.referenceId));
		allModelOptions.value[allModelConfigurations[i].id as string] = tempList;
	}
	listModelLabels.value = allModelConfigurations.map((ele) => ele.name ?? '');

	// initialze weights
	if (_.isEmpty(knobs.value.weights)) {
		allModelConfigurations.forEach((config) => {
			knobs.value.weights[config.id as string] = 1;
		});
	}
});

watch(
	() => props.node.state.inProgressForecastId,
	(id) => {
		if (id === '') showSpinner.value = false;
		else showSpinner.value = true;
	}
);

watch(
	() => props.node.active,
	async () => {
		const output = props.node.outputs.find((d) => d.id === props.node.active);
		if (!output || !output.value) return;

		selectedOutputId.value = output.id;
		selectedRunId.value = output.value[0];
		const forecastId = props.node.state.forecastId;
		if (!forecastId) return;

		const response = await getRunResultCiemss(forecastId, 'result.csv');
		runResults.value = response.runResults;
	},
	{ immediate: true }
);

watch(
	() => knobs.value,
	() => {
		const state = _.cloneDeep(props.node.state);
		state.mapping = knobs.value.mapping;
		state.weights = knobs.value.weights;
		state.endTime = knobs.value.endTime;
		state.numSamples = knobs.value.numSamples;
		state.method = knobs.value.method;
		state.stepSize = knobs.value.stepSize;
		emit('update-state', state);
	},
	{ deep: true }
);

watch(
	() => props.node.state?.forecastId,
	async () => {
		if (!props.node.state?.forecastId) return;

		const response = await getRunResultCiemss(props.node.state?.forecastId, 'result.csv');
		runResults.value = response.runResults;
	}
);
</script>

<style scoped>
.toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--gap-1) var(--gap-4);
	gap: var(--gap-2);
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

.input-config:deep(.content-wrapper) {
	padding-bottom: 0;
}

/* Override grid template so output expands when sidebar is closed */
.overlay-container:deep(section.drilldown main) {
	grid-template-columns: auto 1fr;
}

/* Override top and bottom padding of content-container */
.overlay-container:deep(section.drilldown main .content-container) {
	padding: 0 var(--gap-4);
}

.subheader {
	color: var(--text-color-subdued);
	margin-bottom: var(--gap-4);
}

.model-weights {
	display: flex;
	align-items: start;
}

th {
	text-align: left;
}
</style>
