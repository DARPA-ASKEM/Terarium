<template>
	<tera-drilldown
		v-bind="$attrs"
		:node="node"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
		hide-dropdown
	>
		<template #sidebar>
			<tera-slider-panel
				v-if="pdfData.length"
				v-model:is-open="isDocViewerOpen"
				header="Document viewer"
				content-width="700px"
			>
				<template #content>
					<tera-drilldown-section :is-loading="isFetchingPDF">
						<tera-pdf-panel :pdfs="pdfData" ref="pdfPanelRef" />
					</tera-drilldown-section>
				</template>
			</tera-slider-panel>
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isSidebarOpen"
				header="Configurations"
				content-width="360px"
			>
				<template #header>
					<div class="flex gap-2">
						<tera-input-text v-model="filterModelConfigurationsText" placeholder="Filter" class="w-full" />
						<Button
							label="Extract from inputs"
							icon="pi pi-sparkles"
							severity="primary"
							class="white-space-nowrap min-w-min"
							size="small"
							:loading="isExtracting"
							:disabled="!props.node.inputs.slice(1, 5).some((input) => input?.value)"
							@click="extractConfigurationsFromInputs"
						/>
					</div>
					<tera-progress-spinner v-if="isFetchingConfigs && !isEmpty(modelConfigurations)" is-inline>
						<span class="refresh-message"> Refreshing configurations... </span>
					</tera-progress-spinner>
				</template>
				<template #content>
					<tera-progress-spinner
						v-if="isFetchingConfigs && isEmpty(modelConfigurations)"
						class="h-full"
						is-centered
						:font-size="2"
					>
						Loading configurations...
					</tera-progress-spinner>
					<!-- Show all configurations -->
					<ul v-else-if="model?.id">
						<li v-for="configuration in filteredModelConfigurations" :key="configuration.id">
							<tera-model-configuration-item
								:configuration="configuration"
								:selected="selectedConfigId === configuration.id"
								:empty-input-count="missingInputCount(configuration)"
								@click="onSelectConfiguration(configuration)"
								@delete="fetchConfigurations"
								@download="downloadModelArchive(configuration)"
								@use="onSelectConfiguration(configuration)"
							/>
						</li>
						<!-- Show a message if nothing found after filtering -->
						<li class="ml-4 flex flex-1 items-center" v-if="filteredModelConfigurations.length === 0">
							No configurations found
						</li>
					</ul>
				</template>
			</tera-slider-panel>
		</template>
		<tera-drilldown-section
			:is-loading="knobs.transientModelConfig.id !== selectedConfigId"
			:tabName="DrilldownTabs.Wizard"
			class="px-3 mb-10"
		>
			<template #header-controls-left>
				<tera-toggleable-input
					v-if="typeof knobs.transientModelConfig.name === 'string'"
					v-model="knobs.transientModelConfig.name"
					tag="h4"
				/>
			</template>
			<template #header-controls-right>
				<Button
					label="Reset"
					outlined
					severity="secondary"
					:disabled="!isModelConfigChanged"
					@click="resetConfiguration"
				/>
				<Button label="Save as" outlined severity="secondary" @click="showSaveModal = true" />
				<Button :disabled="isSaveDisabled" label="Save" @click="onSaveConfiguration" />
			</template>
			<Accordion multiple :activeIndex="currentActiveIndexes">
				<AccordionTab>
					<template #header>
						<h5 class="btn-content">Description</h5>
						<Button v-if="!isEditingDescription" class="start-edit" text rounded @click.stop="onEditDescription">
							<i class="pi pi-pencil" />
						</Button>
						<span v-else class="confirm-cancel">
							<Button icon="pi pi-times" text @click.stop="isEditingDescription = false" />
							<Button icon="pi pi-check" text @click.stop="onConfirmEditDescription" />
						</span>
					</template>
					<p class="description text mb-3" v-if="!isEditingDescription">
						{{ knobs.transientModelConfig.description }}
					</p>
					<Textarea
						v-else
						ref="descriptionTextareaRef"
						class="context-item"
						placeholder="Enter a description"
						v-model="newDescription"
					/>
				</AccordionTab>
				<AccordionTab v-if="model?.semantics?.ode?.time" header="Context">
					<div class="flex flex-column gap-2">
						<h5>Temporal Context</h5>
						<span>Assign a date to timestep 0 (optional)</span>
						<Calendar
							class="max-w-30rem"
							:model-value="
								knobs.transientModelConfig.temporalContext ? new Date(knobs.transientModelConfig.temporalContext) : null
							"
							:view="calendarSettings?.view"
							:date-format="calendarSettings?.format"
							showIcon
							iconDisplay="input"
							@date-select="knobs.transientModelConfig.temporalContext = $event"
						/>
					</div>
				</AccordionTab>
				<AccordionTab header="Diagram">
					<tera-model-diagram v-if="model" :model="model" class="mb-2" />
				</AccordionTab>
			</Accordion>
			<template v-if="model">
				<Message v-if="isModelMissingMetadata(model)" class="m-2">
					Some metadata is missing from these values. This information can be added manually to the attached model.
				</Message>
				<template v-if="!isEmpty(knobs.transientModelConfig)">
					<tera-initial-table
						v-if="!isEmpty(configuredMmt.initials)"
						:model="model"
						:model-configuration="knobs.transientModelConfig"
						:model-configurations="filteredModelConfigurations"
						:mmt="configuredMmt"
						:mmt-params="mmtParams"
						@update-expressions="setInitialExpressions(knobs.transientModelConfig, $event)"
						@update-source="setInitialSource(knobs.transientModelConfig, $event.id, $event.value)"
					/>
					<tera-parameter-table
						v-if="!isEmpty(configuredMmt.parameters)"
						:model="model"
						:model-configuration="knobs.transientModelConfig"
						:model-configurations="filteredModelConfigurations"
						:mmt="configuredMmt"
						:mmt-params="mmtParams"
						@update-parameters="setParameterDistributions(knobs.transientModelConfig, $event)"
						@update-source="setParameterSource(knobs.transientModelConfig, $event.id, $event.value)"
					/>
					<Accordion :active-index="observableActiveIndicies" v-if="!isEmpty(calibratedConfigObservables)">
						<AccordionTab header="Observables">
							<tera-model-part
								class="pl-4"
								:items="calibratedConfigObservables"
								:feature-config="{ isPreview: true }"
							/>
						</AccordionTab>
					</Accordion>
					<!-- vertical spacer at end of page -->
					<div class="p-5"></div>
				</template>
			</template>
		</tera-drilldown-section>
		<tera-columnar-panel :tabName="DrilldownTabs.Notebook">
			<tera-drilldown-section class="notebook-section">
				<div class="toolbar">
					<Suspense>
						<tera-notebook-jupyter-input
							:kernel-manager="kernelManager"
							:defaultOptions="sampleAgentQuestions"
							:maxChars="60"
							:context-language="contextLanguage"
							@llm-output="(data: any) => appendCode(data, 'code')"
							@llm-thought-output="(data: any) => updateThoughts(data)"
							@question-asked="updateLlmQuery"
						>
							<template #toolbar-right-side>
								<tera-input-text v-model="knobs.transientModelConfig.name" placeholder="Configuration Name" />
								<Button icon="pi pi-play" label="Run" @click="runFromCode" />
							</template>
						</tera-notebook-jupyter-input>
					</Suspense>
				</div>
				<v-ace-editor
					v-model:value="codeText"
					@init="initializeEditor"
					lang="python"
					theme="chrome"
					style="flex-grow: 1; width: 100%"
					class="ace-editor"
				/>
			</tera-drilldown-section>
			<tera-drilldown-preview title="Output Preview">
				<tera-notebook-error
					v-if="executeResponse.status === OperatorStatus.ERROR"
					:name="executeResponse.name"
					:value="executeResponse.value"
					:traceback="executeResponse.traceback"
				/>
				<p class="executed-code" v-if="executeResponse.status !== OperatorStatus.ERROR">{{ notebookResponse }}</p>
			</tera-drilldown-preview>
		</tera-columnar-panel>
	</tera-drilldown>

	<tera-save-asset-modal
		:initial-name="knobs.transientModelConfig.name"
		:is-visible="showSaveModal"
		:asset="knobs.transientModelConfig"
		:asset-type="AssetType.ModelConfiguration"
		@close-modal="showSaveModal = false"
		@on-save="onSaveAsModelConfiguration"
		@on-update="onSaveAsModelConfiguration"
	/>

	<!-- Matrix effect easter egg  -->
	<canvas id="matrix-canvas" />
</template>

<script setup lang="ts">
import '@/ace-config';
import { ComponentPublicInstance, computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { cloneDeep, debounce, isEmpty, orderBy, omit, isEqual } from 'lodash';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import Textarea from 'primevue/textarea';
import { VAceEditor } from 'vue3-ace-editor';
import { VAceEditorInstance } from 'vue3-ace-editor/types';
import TeraDrilldownPreview from '@/components/drilldown/tera-drilldown-preview.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import TeraNotebookJupyterInput from '@/components/llm/tera-notebook-jupyter-input.vue';
import TeraModelDiagram from '@/components/model/petrinet/tera-model-diagram.vue';
import TeraModelPart from '@/components/model/model-parts/tera-model-part.vue';
import TeraInitialTable from '@/components/model/petrinet/tera-initial-table.vue';
import TeraParameterTable from '@/components/model/petrinet/tera-parameter-table.vue';
import { downloadDocumentAsset, getDocumentAsset, getDocumentFileAsText } from '@/services/document-assets';
import {
	emptyMiraModel,
	generateModelDatasetConfigurationContext,
	makeConfiguredMMT
} from '@/model-representation/mira/mira';
import type { MiraModel, MiraTemplateParams } from '@/model-representation/mira/mira-common';
import { configureModelFromDataset, configureModelFromDocument } from '@/services/goLLM';
import { KernelSessionManager } from '@/services/jupyter';
import { getMMT, getModel, getModelConfigurationsForModel, getCalendarSettingsFromModel } from '@/services/model';
import {
	createModelConfiguration,
	getArchive,
	getModelInitials,
	getMissingInputAmount,
	getModelParameters,
	setInitialExpressions,
	setInitialSource,
	setParameterDistributions,
	setParameterSource,
	updateModelConfiguration
} from '@/services/model-configurations';
import { useToastService } from '@/services/toast';
import type { Initial, Model, ModelConfiguration } from '@/types/Types'; // ., TaskResponse
import { AssetType, ModelParameter, Observable } from '@/types/Types';
import type { WorkflowNode } from '@/types/workflow';
import { OperatorStatus } from '@/types/workflow';
import { logger } from '@/utils/logger';
import {
	isModelMissingMetadata,
	getParameters as getAmrParameters,
	getInitials as getAmrInitials
} from '@/model-representation/service';
import Message from 'primevue/message';
import TeraColumnarPanel from '@/components/widgets/tera-columnar-panel.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import { useConfirm } from 'primevue/useconfirm';
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import { saveCodeToState } from '@/services/notebook';
import TeraSaveAssetModal from '@/components/project/tera-save-asset-modal.vue';
import { useProjects } from '@/composables/project';
import TeraPdfPanel from '@/components/widgets/tera-pdf-panel.vue';
import Calendar from 'primevue/calendar';
import { CalendarSettings } from '@/utils/date';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { DrilldownTabs } from '@/types/common';
import { formatListWithConjunction } from '@/utils/text';
import { DistributionType } from '@/services/distribution';
import {
	blankModelConfig,
	isModelConfigsEqual,
	isModelConfigValuesEqual,
	ModelConfigOperation,
	ModelConfigOperationState
} from './model-config-operation';
import TeraModelConfigurationItem from './tera-model-configuration-item.vue';

const props = defineProps<{
	node: WorkflowNode<ModelConfigOperationState>;
}>();

const emit = defineEmits(['append-output', 'update-state', 'select-output', 'close']);

const isFetchingPDF = ref(false);
const isDocViewerOpen = ref(true);

const currentActiveIndexes = ref([0, 1, 2]);
const observableActiveIndicies = ref([0]);
const pdfData = ref<{ document: any; data: string; isPdf: boolean; name: string }[]>([]);
const pdfPanelRef = ref();
const pdfViewer = computed(() => pdfPanelRef.value?.pdfRef);

const isSidebarOpen = ref(true);
const isEditingDescription = ref(false);
const newDescription = ref('');
const descriptionTextareaRef = ref<ComponentPublicInstance<typeof Textarea> | null>(null);

let originalConfig: ModelConfiguration | null = null;
const modelId = props.node.inputs[0].value?.[0] as string;

interface BasicKnobs {
	transientModelConfig: ModelConfiguration;
}

const knobs = ref<BasicKnobs>({
	transientModelConfig: blankModelConfig
});

const calibratedConfigObservables = computed<Observable[]>(() =>
	knobs.value.transientModelConfig.observableSemanticList.map(({ referenceId, states, expression }) => ({
		id: referenceId,
		name: referenceId,
		states,
		expression
	}))
);

const getTotalInput = (modelConfiguration: ModelConfiguration) =>
	modelConfiguration.initialSemanticList.length + modelConfiguration.parameterSemanticList.length;

// Check if the model configuration is the same as the original
const isModelConfigChanged = computed(() => !isModelConfigsEqual(originalConfig, knobs.value.transientModelConfig));

// Save button is disabled if the model configuration name is empty, the values have changed, or the configuration is the same as the original
const isSaveDisabled = computed(
	() =>
		knobs.value.transientModelConfig.name === '' ||
		isModelConfigsEqual(originalConfig, knobs.value.transientModelConfig) ||
		!isModelConfigValuesEqual(originalConfig, knobs.value.transientModelConfig)
);

const kernelManager = new KernelSessionManager();
let editor: VAceEditorInstance['_editor'] | null;
const buildJupyterContext = () => {
	const contextId = selectedConfigId.value;
	if (!model.value) {
		logger.warn('Cannot build Jupyter context without a model');
		return null;
	}
	return {
		context: 'model_configuration',
		language: 'python3',
		context_info: {
			id: contextId
		}
	};
};
const codeText = ref('# This environment contains the variable "model_config" to be read and updated');
const llmQuery = ref('');
const llmThoughts = ref<any[]>([]);
const notebookResponse = ref();
const executeResponse = ref({
	status: OperatorStatus.DEFAULT,
	name: '',
	value: '',
	traceback: ''
});
const sampleAgentQuestions = [
	'What are the current parameters values?',
	'Update parameter gamma to constant 0.13',
	'Update parameter beta to a uniform distribution with max 0.5 and min 0.2'
];
const contextLanguage = ref<string>('python3');

const appendCode = (data: any, property: string, runUpdatedCode = false) => {
	codeText.value = codeText.value.concat(' \n', data.content[property] as string);
	if (runUpdatedCode) runFromCode();
};

const showSaveModal = ref(false);
const confirm = useConfirm();
const filterModelConfigurationsText = ref('');
const filteredModelConfigurations = computed(() => {
	const searchTerm = filterModelConfigurationsText.value.toLowerCase();
	const filteredConfigurations = modelConfigurations.value.filter(
		(config) =>
			config.name?.toLowerCase().includes(searchTerm) || config.description?.toLowerCase().includes(searchTerm)
	);

	return orderBy(filteredConfigurations, [selectedSortOption.value], ['desc']);
});

const selectedSortOption = ref('createdOn');

const runFromCode = () => {
	const code = editor?.getValue();
	if (!code) return;

	const messageContent = {
		silent: false,
		store_history: false,
		code
	};

	let executedCode = '';

	kernelManager
		.sendMessage('execute_request', messageContent)
		.register('execute_input', (data) => {
			executedCode = data.content.code;
		})
		.register('stream', (data) => {
			notebookResponse.value = data.content.text;
		})
		.register('model_configuration_preview', (data) => {
			if (!data.content) return;
			knobs.value.transientModelConfig = data.content;

			if (executedCode) {
				updateCodeState(executedCode, true);
				createConfiguration();
			}
		})
		.register('any_execute_reply', (data) => {
			let status = OperatorStatus.DEFAULT;
			if (data.msg.content.status === 'ok') status = OperatorStatus.SUCCESS;
			if (data.msg.content.status === 'error') status = OperatorStatus.ERROR;
			executeResponse.value = {
				status,
				name: data.msg.content.ename ? data.msg.content.ename : '',
				value: data.msg.content.evalue ? data.msg.content.evalue : '',
				traceback: data.msg.content.traceback ? data.msg.content.traceback : ''
			};
		});
};

function updateLlmQuery(query: string) {
	llmThoughts.value = [];
	llmQuery.value = query;
}

const updateThoughts = (data: any) => {
	llmThoughts.value.push(data);
	const llmResponse = llmThoughts.value.findLast((thought) => thought?.msg_type === 'llm_response');
	// If the last thought is a LLM response, update the notebook response
	if (llmResponse) {
		notebookResponse.value = llmResponse.content.text;
	}
};

function updateCodeState(code: string = codeText.value, hasCodeRun: boolean = true) {
	const state = saveCodeToState(props.node, code, hasCodeRun, llmQuery.value, llmThoughts.value);
	emit('update-state', state);
}

const initializeEditor = (editorInstance: any) => {
	editor = editorInstance;
};

const extractConfigurationsFromInputs = async () => {
	const state = cloneDeep(props.node.state);

	isExtracting.value = true;

	const matrixStr = generateModelDatasetConfigurationContext(mmt.value, mmtParams.value);

	const promiseList = [
		...documentIds.value.map((documentId) =>
			configureModelFromDocument(documentId, modelId, props.node.workflowId, props.node.id)
		),
		...datasetIds.value.map((datasetId) =>
			configureModelFromDataset(modelId, datasetId, matrixStr, props.node.workflowId, props.node.id)
		)
	];

	const responsesRaw = await Promise.all(promiseList);
	responsesRaw.forEach((resp) => {
		if (resp) {
			state.modelConfigTaskIds.push(resp.id);
		}
	});

	emit('update-state', state);
};

const configIds = computed(() => props.node.outputs.map((output) => output.value?.[0]));
const selectedConfigId = computed(() => props.node.outputs.find(({ id }) => id === props.node.active)?.value?.[0]);

const documentIds = computed(() =>
	props.node.inputs
		.filter((input) => input.type === 'documentId' && input.status === 'connected')
		.map((input) => input.value?.[0]?.documentId)
		.filter((id): id is string => id !== undefined)
);
const datasetIds = computed(() =>
	props.node.inputs
		.filter((input) => input.type === 'datasetId' && input.status === 'connected')
		.map((input) => input.value?.[0])
		.filter((id): id is string => id !== undefined)
);

const modelConfigurations = ref<ModelConfiguration[]>([]);

const isFetchingConfigs = ref(false);
const isExtracting = ref(false);

const amrInitials = ref<Initial[]>([]);
const amrParameters = ref<ModelParameter[]>([]);

const getMissingInputsMessage = (amount, total) => {
	if (!total) return '';
	const percent = (amount / total) * 100;
	return amount ? `Missing values: ${amount}/${total} (${percent.toFixed(0)}%)` : '';
};

const missingInputCount = (modelConfiguration: ModelConfiguration) => {
	if (selectedConfigId.value === modelConfiguration.id) {
		return selectedConfigMissingInputCount.value;
	}
	const total = amrInitials.value.length + amrParameters.value.length;
	const amount = total - getTotalInput(modelConfiguration);
	return getMissingInputsMessage(amount, total);
};

const selectedConfigMissingInputCount = computed(() => {
	if (!knobs.value.transientModelConfig.id) {
		return '';
	}
	const amount = getMissingInputAmount(knobs.value.transientModelConfig);
	const total = getTotalInput(knobs.value.transientModelConfig);
	return getMissingInputsMessage(amount, total);
});

const model = ref<Model | null>(null);
const mmt = ref<MiraModel>(emptyMiraModel());
const mmtParams = ref<MiraTemplateParams>({});
const configuredMmt = ref(makeConfiguredMMT(mmt.value, knobs.value.transientModelConfig));
const calendarSettings = ref<CalendarSettings | null>(null);

const downloadModelArchive = async (configuration: ModelConfiguration = knobs.value.transientModelConfig) => {
	const archive = await getArchive(configuration);
	if (archive) {
		const a = document.createElement('a');
		a.href = URL.createObjectURL(archive);
		a.download = `${configuration.name}.modelconfig`;
		a.click();
		a.remove();
	}
};

const createConfiguration = async () => {
	if (!model.value || isSaveDisabled.value) return;
	const modelConfig = cloneDeep(knobs.value.transientModelConfig);

	const data = await createModelConfiguration(modelConfig);
	if (!data) {
		logger.error('Failed to create model configuration');
		return;
	}
	useToastService().success('', 'Created model configuration');
	appendOutput(data);
};

const onSaveAsModelConfiguration = (config: ModelConfiguration) => {
	modelConfigurations.value.push(config); // Add to list of configurations (no need to call fetchConfigurations again)
	useToastService().success('', 'Created model configuration');
	appendOutput(config);
	showSaveModal.value = false;
};

const onSaveConfiguration = async () => {
	if (isSaveDisabled.value || !originalConfig) return;
	const modelConfig = cloneDeep(knobs.value.transientModelConfig);

	const data = await updateModelConfiguration(modelConfig);
	if (!data) {
		logger.error('Failed to update model configuration');
		return;
	}
	useProjects().refresh();

	// Update the configuration in the list, no need to recall fetchConfigurations
	const configIndex = modelConfigurations.value.findIndex((config) => config.id === data.id);
	if (configIndex !== -1) {
		modelConfigurations.value[configIndex] = data;
		// Sync with original config so changes are recognized as saved in UI
		originalConfig.name = data.name;
		originalConfig.description = data.description;
	}

	logger.success('Saved model configuration');
};

const fetchConfigurations = async () => {
	isFetchingConfigs.value = true;
	modelConfigurations.value = await getModelConfigurationsForModel(modelId);
	isFetchingConfigs.value = false;
};

async function loadOutput(overwriteWithState = false) {
	const state = props.node.state;
	originalConfig = modelConfigurations.value.find(({ id }) => id === selectedConfigId.value) ?? null;

	// Apply the first configuration if one hasn't been applied yet
	if (!state.transientModelConfig.id) {
		applyConfigValues(modelConfigurations.value[0]);
	} else if (overwriteWithState) {
		knobs.value.transientModelConfig = cloneDeep(state.transientModelConfig);
	} else if (originalConfig) {
		knobs.value.transientModelConfig = cloneDeep(originalConfig);
	}

	if (model.value) {
		amrInitials.value = getAmrInitials(model.value);
		amrParameters.value = getAmrParameters(model.value);
		const initials = getModelInitials(knobs.value.transientModelConfig, mmt.value.annotations.name, amrInitials.value);
		if (initials.length) {
			knobs.value.transientModelConfig.initialSemanticList = initials;
		}
		const parameters = getModelParameters(
			knobs.value.transientModelConfig,
			mmt.value.annotations.name,
			amrParameters.value
		);

		parameters.forEach((parameter) => {
			const type = parameter.distribution.type;
			parameter.distribution.type = type === 'Uniform' ? DistributionType.Uniform : type;
		});
		if (parameters.length) {
			knobs.value.transientModelConfig.parameterSemanticList = parameters;
		}
	}

	configuredMmt.value = makeConfiguredMMT(mmt.value, knobs.value.transientModelConfig);

	// Create a new session and context based on model
	try {
		const jupyterContext = buildJupyterContext();
		if (jupyterContext) {
			if (kernelManager.jupyterSession !== null) {
				// when coming from output dropdown change we should shut down first
				kernelManager.shutdown();
			}
			await kernelManager.init('beaker_kernel', 'Beaker Kernel', jupyterContext);
		}
	} catch (error) {
		logger.error(`Error initializing Jupyter session: ${error}`);
	}
}

const onSelectConfiguration = async (config: ModelConfiguration) => {
	if (config.id === selectedConfigId.value) return;
	let tabIndex = 0;
	if (pdfPanelRef.value && config.extractionDocumentId) {
		tabIndex = await pdfPanelRef.value.selectPdf(config.extractionDocumentId);
		await nextTick();
	}

	if (pdfViewer.value && config.extractionPage) {
		pdfViewer.value[tabIndex].goToPage(config.extractionPage);
	}

	const { transientModelConfig } = knobs.value;
	// If no changes were made switch right away
	if (isModelConfigsEqual(originalConfig, transientModelConfig)) {
		applyConfigValues(config);
		return;
	}

	const lostItems: string[] = [];

	if (!isModelConfigValuesEqual(transientModelConfig, originalConfig)) lostItems.push('values');
	if (transientModelConfig.name !== originalConfig?.name) lostItems.push('name');
	if (transientModelConfig.description !== originalConfig?.description) lostItems.push('description');

	confirm.require({
		header: `Unsaved changes`,
		message: `Changes made to the ${formatListWithConjunction(lostItems)} will be lost.`,
		accept: () => {
			applyConfigValues(config);
		},
		acceptLabel: 'Confirm',
		rejectLabel: 'Cancel'
	});
};

function appendOutput(config: ModelConfiguration) {
	const state = cloneDeep(props.node.state);
	emit('append-output', {
		type: ModelConfigOperation.outputs[0].type,
		label: config.name,
		value: config.id,
		isSelected: false,
		state: omit(state, ['transientModelConfig'])
	});
}

const applyConfigValues = (config: ModelConfiguration) => {
	if (!config.id) {
		logger.error('Model configuration not found');
		return;
	}
	// If this output already exists select it
	if (configIds.value.includes(config.id)) {
		const outputId = props.node.outputs.find((ele) => ele.value?.[0] === config.id)?.id;
		if (!outputId) {
			logger.error('Output not found');
			return;
		}
		emit('select-output', outputId);
	}
	// If the output does not already exist append it
	else appendOutput(config);
};

const onEditDescription = async () => {
	isEditingDescription.value = true;
	newDescription.value = knobs.value.transientModelConfig.description ?? '';
	await nextTick();
	descriptionTextareaRef.value?.$el.focus();
};

const onConfirmEditDescription = () => {
	knobs.value.transientModelConfig.description = newDescription.value;
	isEditingDescription.value = false;
};

const resetConfiguration = () => {
	confirm.require({
		header: 'Reset configuration',
		message: 'All edits done to this configuration will be reverted.',
		accept: () => {
			if (originalConfig) knobs.value.transientModelConfig = cloneDeep(originalConfig);
		},
		acceptLabel: 'Confirm',
		rejectLabel: 'Cancel'
	});
};

const debounceUpdateState = debounce(() => {
	console.log('debounced update');
	const state = cloneDeep(props.node.state);
	state.transientModelConfig = knobs.value.transientModelConfig;
	configuredMmt.value = makeConfiguredMMT(mmt.value, knobs.value.transientModelConfig);

	emit('update-state', state);
}, 100);

watch(
	() => knobs.value,
	async () => {
		debounceUpdateState();
	},
	{ deep: true }
);

watch(
	() => props.node.state.modelConfigTaskIds,
	async (oldValue, newValue) => {
		if (!isEqual(oldValue, newValue)) {
			console.log('oldValue', oldValue);
			console.log('newValue', newValue);
			console.log('modelConfigTaskIds changed');
			await fetchConfigurations();
			isExtracting.value = false;
		}
	}
);

onMounted(async () => {
	model.value = await getModel(modelId);
	if (model.value) {
		calendarSettings.value = getCalendarSettingsFromModel(model.value);
		const response = await getMMT(model.value);
		if (response) {
			mmt.value = response.mmt;
			mmtParams.value = response.template_params;
		}
	}

	// Wait for the configurations to load before loading any output
	await fetchConfigurations();
	loadOutput(true);

	if (documentIds.value.length) {
		isFetchingPDF.value = true;
		documentIds.value.forEach(async (id) => {
			const document = await getDocumentAsset(id);
			const name: string = document?.name ?? '';
			const filename = document?.fileNames?.[0];
			const isPdf = !!document?.fileNames?.[0]?.endsWith('.pdf');

			if (document?.id && filename) {
				let data: string | null;
				if (isPdf) {
					data = await downloadDocumentAsset(document.id, filename);
				} else {
					data = await getDocumentFileAsText(document.id, filename);
				}
				if (data !== null) {
					pdfData.value.push({ document, data, isPdf, name });
				}
			}
		});
	}
	isFetchingPDF.value = false;
});

watch(
	() => props.node.active,
	() => loadOutput()
);

onUnmounted(() => {
	kernelManager.shutdown();
});
</script>

<style scoped>
/* When accordions are closed, don't show their filter or edit buttons */
:deep(.p-accordion-tab:not(.p-accordion-tab-active)) .p-accordion-header .p-accordion-header-link .tera-input,
:deep(.p-accordion-tab:not(.p-accordion-tab-active)) .p-accordion-header .p-accordion-header-link button {
	display: none;
}

:deep(.p-datatable-loading-overlay.p-component-overlay) {
	background-color: var(--surface-section);
}

.p-datatable.p-datatable-sm :deep(.p-datatable-tbody > tr > td) {
	padding: 0;
}
.context-item {
	width: 100%;
}

:deep(.p-button:disabled.p-button-outlined) {
	background-color: var(--surface-0) !important;
}

:deep(.p-accordion-content) {
	padding-bottom: var(--gap-2);
}

.notebook-section {
	background-color: var(--surface-200);
	border-right: 1px solid var(--surface-border-light);
	padding: var(--gap-4);
}

.notebook-section:deep(main) {
	gap: var(--gap-2);
	position: relative;
}

.toolbar-right-side {
	position: absolute;
	top: var(--gap-4);
	right: 0;
	gap: var(--gap-2);
	display: flex;
	align-items: center;
}

#matrix-canvas {
	position: fixed;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	z-index: 1000;
	pointer-events: none;
	mix-blend-mode: darken;
	opacity: 1;
	transition: opacity 1s;
}

.sort-by-label {
	color: var(--text-color-subdued);
	padding-right: var(--gap-2);
}

:deep(.pi-spinner) {
	padding: var(--gap-2);
}

.input-config {
	ul {
		list-style: none;
	}

	li {
		& > * {
			border-bottom: 1px solid var(--gray-300);
			border-right: 1px solid var(--gray-300);
		}

		&:first-child > * {
			border-top: 1px solid var(--gray-300);
			border-top-left-radius: var(--border-radius);
			border-top-right-radius: var(--border-radius);
		}

		&:last-child > * {
			border-bottom-left-radius: var(--border-radius);
			border-bottom-right-radius: var(--border-radius);
		}
	}
}

button.start-edit {
	display: flex;
	gap: var(--gap-3);
	width: fit-content;
	min-width: var(--gap-3);
	padding: var(--gap-2);
	margin-left: var(--gap-1);

	& > .btn-content {
		color: var(--text-color);
	}

	& > .pi {
		color: var(--text-color-subdued);
	}
}

.confirm-cancel {
	display: flex;
	align-items: center;
	gap: var(--gap-1);
	& > span {
		margin-left: var(--gap-2);
	}
}

.refresh-message {
	color: var(--primary-color);
}

.executed-code {
	white-space: pre-wrap;
	padding: var(--gap-4);
}
:deep(.content-wrapper) {
	& > section {
		& > main {
			overflow: hidden;
		}
	}
}
</style>
