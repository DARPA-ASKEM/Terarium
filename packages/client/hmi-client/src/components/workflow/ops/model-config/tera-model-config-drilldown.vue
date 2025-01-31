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
				:content-width="isSidebarOpen ? 'calc(60vw - 320px)' : '60vw'"
				:documentViewer="true"
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
				<template #content>
					<main class="m-3">
						<div class="flex flex-row gap-1">
							<tera-input-text v-model="filterModelConfigurationsText" placeholder="Filter" class="w-full" />
							<Button
								label="Extract from inputs"
								icon="pi pi-sparkles"
								severity="primary"
								class="white-space-nowrap min-w-min"
								size="small"
								:loading="isLoading"
								:disabled="!props.node.inputs.slice(1, 5).some((input) => input?.value)"
								@click="extractConfigurationsFromInputs"
							/>
						</div>
						<!-- Show a spinner if loading -->
						<section v-if="isLoading" class="processing-new-configuration-tile">
							<p class="secondary-text">Processing...</p>
						</section>

						<!-- Show all configurations -->
						<ul v-if="model?.id">
							<li v-for="configuration in filteredModelConfigurations" :key="configuration.id">
								<tera-model-configuration-item
									:configuration="configuration"
									:selected="selectedConfigId === configuration.id"
									:empty-input-count="missingInputCount(configuration)"
									@click="onSelectConfiguration(configuration)"
									@delete="fetchConfigurations(model.id)"
									@download="downloadModelArchive(configuration)"
									@use="onSelectConfiguration(configuration)"
								/>
							</li>
							<!-- Show a message if nothing found after filtering -->
							<li v-if="filteredModelConfigurations.length === 0">No configurations found</li>
						</ul>
					</main>
				</template>
			</tera-slider-panel>
		</template>
		<tera-drilldown-section :is-loading="initializing" :tabName="DrilldownTabs.Wizard" class="px-3 mb-10">
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
				<AccordionTab v-if="model?.semantics?.ode?.time" header="Temporal context">
					<div class="flex flex-column gap-2 mb-3">
						<span>Assign a date to timestep 0 (optional)</span>
						<Calendar
							class="max-w-20rem"
							:model-value="
								knobs.transientModelConfig.temporalContext ? new Date(knobs.transientModelConfig.temporalContext) : null
							"
							:view="calendarSettings?.view"
							:date-format="calendarSettings?.format"
							showIcon
							iconDisplay="input"
							@date-select="knobs.transientModelConfig.temporalContext = $event"
							show-button-bar
							@clear-click="delete knobs.transientModelConfig.temporalContext"
						/>
					</div>
				</AccordionTab>
				<AccordionTab header="Diagram">
					<tera-model-diagram v-if="model" :model="model" class="mb-2" />
				</AccordionTab>
			</Accordion>
			<template v-if="model">
				<Message v-if="isModelMissingMetadata(model)" class="m-2 info-message">
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
					<Accordion :active-index="observableActiveIndicies" v-if="!isEmpty(observablesList)">
						<AccordionTab header="Observables">
							<tera-model-part
								:part-type="PartType.OBSERVABLE"
								:items="observablesList"
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
							<Button icon="pi pi-play" label="Run" @click="runFromCode" :disabled="isEmpty(codeText)" />
						</template>
					</tera-notebook-jupyter-input>
				</Suspense>
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
import { cloneDeep, debounce, difference, isEmpty, orderBy, omit } from 'lodash';
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
	getModelConfigurationById,
	setInitialExpressions,
	setInitialSource,
	setParameterDistributions,
	setParameterSource,
	updateModelConfiguration
} from '@/services/model-configurations';
import { useToastService } from '@/services/toast';
import type { Initial, Model, ModelConfiguration, TaskResponse } from '@/types/Types';
import { AssetType, ModelParameter } from '@/types/Types';
import type { WorkflowNode } from '@/types/workflow';
import { OperatorStatus } from '@/types/workflow';
import { logger } from '@/utils/logger';
import {
	isModelMissingMetadata,
	getParameters as getAmrParameters,
	getInitials as getAmrInitials,
	createObservablesList,
	PartType
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

const isSidebarOpen = ref(true);
const isEditingDescription = ref(false);
const newDescription = ref('');
const descriptionTextareaRef = ref<ComponentPublicInstance<typeof Textarea> | null>(null);

let originalConfig: ModelConfiguration | null = null;

interface BasicKnobs {
	transientModelConfig: ModelConfiguration;
}

const knobs = ref<BasicKnobs>({
	transientModelConfig: blankModelConfig
});

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

function updateCodeState(code: string = codeText.value, hasCodeRun: boolean = true) {
	const state = saveCodeToState(props.node, code, hasCodeRun, llmQuery.value, llmThoughts.value);
	emit('update-state', state);
}

const initializeEditor = (editorInstance: any) => {
	editor = editorInstance;
};

const extractConfigurationsFromInputs = async () => {
	const state = cloneDeep(props.node.state);
	if (!model.value?.id) {
		return;
	}

	// We only want to run extractions on documents and datasets that have not previously been run
	const configExtractionDocumentIds = modelConfigurations.value
		.map((config) => config.extractionDocumentId)
		.filter(Boolean);

	const newDocumentIds = difference(documentIds.value, configExtractionDocumentIds);
	if (!isEmpty(newDocumentIds)) {
		const promiseList = [] as Promise<TaskResponse | null>[];
		newDocumentIds.forEach((documentId) => {
			promiseList.push(
				configureModelFromDocument(`${documentId}`, model.value?.id as string, props.node.workflowId, props.node.id)
			);
		});
		const responsesRaw = await Promise.all(promiseList);
		responsesRaw.forEach((resp) => {
			if (resp) {
				state.modelConfigTaskIds.push(resp.id);
			}
		});
	}

	const newDatasetIds = difference(datasetIds.value, configExtractionDocumentIds);
	if (!isEmpty(newDatasetIds)) {
		const matrixStr = generateModelDatasetConfigurationContext(mmt.value, mmtParams.value);
		const promiseList = [] as Promise<TaskResponse | null>[];
		newDatasetIds.forEach((datasetId) => {
			promiseList.push(
				configureModelFromDataset(
					model.value?.id as string,
					`${datasetId}`,
					matrixStr,
					props.node.workflowId,
					props.node.id
				)
			);
		});
		const responsesRaw = await Promise.all(promiseList);
		responsesRaw.forEach((resp) => {
			if (resp) {
				state.modelConfigTaskIds.push(resp.id);
			}
		});
	}
	emit('update-state', state);
};

const selectedOutputId = ref<string>('');
const selectedConfigId = computed(() => props.node.outputs.find((o) => o.id === selectedOutputId.value)?.value?.[0]);

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

const observables = computed(() => model.value?.semantics?.ode?.observables ?? []);
const observablesList = computed(() => createObservablesList(observables.value));

const modelConfigurations = ref<ModelConfiguration[]>([]);

const initializing = ref(false);
const isFetching = ref(false);
const isLoading = ref(false);

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
	const inferredParameterList = modelConfiguration.inferredParameterList?.length ?? 0;
	const total = amrInitials.value.length + amrParameters.value.length;
	const amount = total - getTotalInput(modelConfiguration) - inferredParameterList;
	return getMissingInputsMessage(amount, total);
};

const selectedConfigMissingInputCount = computed(() => {
	if (initializing.value) {
		return '';
	}
	const inferredParameterAmount = knobs.value.transientModelConfig.inferredParameterList?.length ?? 0;
	const amount = getMissingInputAmount(knobs.value.transientModelConfig) - inferredParameterAmount;
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

	const state = cloneDeep(props.node.state);
	useToastService().success('', 'Created model configuration');
	emit('append-output', {
		type: ModelConfigOperation.outputs[0].type,
		label: data.name,
		value: data.id,
		isSelected: false,
		state: omit(state, ['transientModelConfig'])
	});
};

const onSaveAsModelConfiguration = (data: ModelConfiguration) => {
	useToastService().success('', 'Created model configuration');
	const state = cloneDeep(props.node.state);
	emit('append-output', {
		type: ModelConfigOperation.outputs[0].type,
		label: data.name,
		value: data.id,
		isSelected: false,
		state: omit(state, ['transientModelConfig'])
	});
	showSaveModal.value = false;
};

const onSaveConfiguration = async () => {
	if (!model.value || isSaveDisabled.value) return;
	const modelConfig = cloneDeep(knobs.value.transientModelConfig);

	const data = await updateModelConfiguration(modelConfig);
	if (!data) {
		logger.error('Failed to update model configuration');
		return;
	}
	initialize();
	useProjects().refresh();
	logger.success('Saved model configuration');
};

const fetchConfigurations = async (modelId: string) => {
	isFetching.value = true;
	modelConfigurations.value = await getModelConfigurationsForModel(modelId);
	isFetching.value = false;
};

// Fill the form with the config data
const initialize = async (overwriteWithState: boolean = false) => {
	initializing.value = true;
	const state = props.node.state;
	const modelId = props.node.inputs[0].value?.[0];
	if (!modelId) return;
	fetchConfigurations(modelId);

	model.value = await getModel(modelId);
	if (model.value) {
		calendarSettings.value = getCalendarSettingsFromModel(model.value);
		const response = await getMMT(model.value);
		if (response) {
			mmt.value = response.mmt;
			mmtParams.value = response.template_params;
		}
	}

	if (!state.transientModelConfig.id) {
		// Apply a configuration if one hasn't been applied yet
		applyConfigValues(modelConfigurations.value[0]);
	} else {
		originalConfig = await getModelConfigurationById(selectedConfigId.value);
		if (!overwriteWithState) {
			knobs.value.transientModelConfig = cloneDeep(originalConfig);
		} else {
			knobs.value.transientModelConfig = cloneDeep(state.transientModelConfig);
		}
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

	initializing.value = false;
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
};

const onSelectConfiguration = async (config: ModelConfiguration) => {
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

const applyConfigValues = (config: ModelConfiguration) => {
	// Update output port:
	if (!config.id) {
		logger.error('Model configuration not found');
		return;
	}
	const listOfConfigIds: string[] = props.node.outputs.map((output) => output.value?.[0]);
	// Check if this output already exists
	if (listOfConfigIds.includes(config.id)) {
		// Select the existing output
		const output = props.node.outputs.find((ele) => ele.value?.[0] === config.id);
		emit('select-output', output?.id);
	}
	// If the output does not already exist
	else {
		const state = cloneDeep(props.node.state);
		emit('append-output', {
			type: ModelConfigOperation.outputs[0].type,
			label: config.name,
			value: config.id,
			isSelected: false,
			state: omit(state, ['transientModelConfig'])
		});
	}
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

const updateThoughts = (data: any) => {
	llmThoughts.value.push(data);
	const llmResponse = llmThoughts.value.findLast((thought) => thought?.msg_type === 'llm_response');
	// If the last thought is a LLM response, update the notebook response
	if (llmResponse) {
		notebookResponse.value = llmResponse.content.text;
	}
};

watch(
	() => props.node.state.modelConfigTaskIds,
	(newValue, oldValue) => {
		if (newValue.length > 0) {
			isLoading.value = true;
		} else if (newValue.length !== oldValue.length) {
			isLoading.value = false;
			const modelId = props.node.inputs[0].value?.[0];
			if (!modelId) return;
			fetchConfigurations(modelId);
		}
	}
);

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

onMounted(() => {
	// setting as true will overwrite the model config with the current state value
	if (props.node.active) {
		selectedOutputId.value = props.node.active;
		initialize(true);
	}

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
	() => {
		if (props.node.active) {
			selectedOutputId.value = props.node.active;
			initialize();
		}
	}
);

onUnmounted(() => {
	kernelManager.shutdown();
});
</script>

<style scoped>
.processing-new-configuration-tile {
	display: flex;
	flex-direction: row;
	align-items: center;
	padding: var(--gap-4);
	background-color: var(--surface-0);
	margin-top: var(--gap-3);
	border-left: 4px solid var(--surface-300);
}

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
		padding-top: var(--gap-4);
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

.secondary-text {
	color: var(--text-color-subdued);
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
.info-message {
	border-color: var(--surface-border);
}
</style>
