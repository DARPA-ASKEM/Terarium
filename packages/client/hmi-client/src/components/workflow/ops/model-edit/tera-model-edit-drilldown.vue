<template>
	<tera-drilldown
		ref="drilldownRef"
		v-bind="$attrs"
		:node="node"
		:is-draft="isDraft"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<tera-drilldown-section :tabName="DrilldownTabs.Notebook" class="notebook-section">
			<Suspense>
				<tera-notebook-jupyter-input
					:kernel-manager="kernelManager"
					:default-options="sampleAgentQuestions"
					:context-language="contextLanguage"
					@llm-output="(data: any) => appendCode(data, 'code')"
					@llm-thought-output="(data: any) => llmThoughts.push(data)"
					@question-asked="updateLlmQuery"
				>
					<template #toolbar-right-side>
						<Button label="Reset" outlined severity="secondary" size="small" @click="resetModel" />
						<Button
							icon="pi pi-play"
							:label="isUpdatingModel ? 'Loading...' : 'Run'"
							size="small"
							:loading="isUpdatingModel"
							@click="runCode"
							:disabled="isEmpty(codeText)"
						/>
					</template>
				</tera-notebook-jupyter-input>
			</Suspense>
			<v-ace-editor
				v-model:value="codeText"
				@init="initializeAceEditor"
				lang="python"
				theme="chrome"
				style="flex-grow: 1; width: 100%"
				class="ace-editor"
				:options="{ showPrintMargin: false }"
			/>
			<tera-notebook-output :traceback="executeResponseTraceback" />
		</tera-drilldown-section>
		<template #preview v-if="drilldownRef?.selectedTab === DrilldownTabs.Notebook">
			<tera-drilldown-preview
				title="Preview"
				v-model:output="selectedOutputId"
				@update:selection="onSelection"
				:options="outputs"
				is-selectable
			>
				<tera-notebook-error
					v-if="executeErrorResponse.status === OperatorStatus.ERROR"
					:name="executeErrorResponse.name"
					:value="executeErrorResponse.value"
					:traceback="executeErrorResponse.traceback"
				/>
				<tera-model v-else-if="outputModel" is-workflow is-save-for-reuse :assetId="outputModel.id" />
				<tera-progress-spinner v-else-if="isUpdatingModel || !outputModel" is-centered :font-size="2">
					Loading...
				</tera-progress-spinner>
			</tera-drilldown-preview>
		</template>
		<tera-drilldown-section :tabName="DrilldownTabs.Wizard">
			<p class="m-4">Wizard is disabled for now.</p>
		</tera-drilldown-section>
	</tera-drilldown>
</template>

<script setup lang="ts">
import { cloneDeep, isEmpty, isEqual, debounce } from 'lodash';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import '@/ace-config';
import { VAceEditor } from 'vue3-ace-editor';
import { VAceEditorInstance } from 'vue3-ace-editor/types';
import Button from 'primevue/button';
import { DrilldownTabs } from '@/types/common';
import type { Model } from '@/types/Types';
import { OperatorStatus, WorkflowNode, WorkflowOutput } from '@/types/workflow';
import { KernelSessionManager } from '@/services/jupyter';
import { createModel, getModel } from '@/services/model';
import { getModelIdFromModelConfigurationId } from '@/services/model-configurations';
import { saveCodeToState } from '@/services/notebook';
import { logger } from '@/utils/logger';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownPreview from '@/components/drilldown/tera-drilldown-preview.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraModel from '@/components/model/tera-model.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import TeraNotebookOutput from '@/components/drilldown/tera-notebook-output.vue';
import TeraNotebookJupyterInput from '@/components/llm/tera-notebook-jupyter-input.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { nodeOutputLabel } from '@/components/workflow/util';
import { ModelEditOperationState, ModelEditOperation } from './model-edit-operation';

const props = defineProps<{
	node: WorkflowNode<ModelEditOperationState>;
}>();
const emit = defineEmits(['append-output', 'update-state', 'close', 'select-output']);

const outputs = computed(() => {
	if (!isEmpty(props.node.outputs)) {
		return [
			{
				label: 'Select an output',
				items: props.node.outputs
			}
		];
	}
	return [];
});

const isReadyToCreateDefaultOutput = computed(
	() => isEmpty(outputs.value) || (outputs.value.length === 1 && !outputs.value?.[0]?.items[0].value)
);

const drilldownRef = ref();
const selectedOutputId = ref<string>('');
const activeOutput = ref<WorkflowOutput<ModelEditOperationState> | null>(null);
const isUpdatingModel = ref(false);

const kernelManager = new KernelSessionManager();
const outputModel = ref<Model | null>(null);
let inputModelId: string | null = null;
let activeOutputModelId: string | null = null;

let editor: VAceEditorInstance['_editor'] | null;
const sampleAgentQuestions = [
	'Add a new transition from S to R with the name vaccine with the rate of v and unit Days.',
	'Add a new transition from I to D. Name the transition death that has a dependency on R. The rate is I*R*u with unit Days',
	'Add a new transition (from nowhere) to S with a rate constant of f with unit Days.',
	'Add a new transition (from nowhere) to S with a rate constant of f with unit Days. The rate depends on R.',
	'Add a new transition from S (to nowhere) with a rate constant of v with unit Days',
	'Add a new transition from S (to nowhere) with a rate constant of v with unit Days. The Rate depends on R',
	'Add an observable titled sample with the expression A * B  * p.',
	'Add a new parameter with id θ and value 0.5.',
	'Rename the state S to Susceptible in the infection transition.',
	'Rename the transition infection to inf.',
	'Change rate law of inf to S * I * z.',
	'Remove all unused parameters',
	'Remove the parameter θ',
	'Specify the time unit of the model to be "day"',
	'Add a new transition that represents the states "Infected", "Hospitalized" controlling the production of the state "WastewaterViralLoad" with rate law "shed_rate * (Infected + Hospitalized)"',
	'Add a new transition that represents the states "Susceptible", "Infected", "Recovered" controlling the degradation of the state "Hospitalized" with rate law "rec_rate * Hospitalized / (Susceptible + Infected + Recovered)"',
	'Add a new transition that represents the states "Infected", "Recovered" controlling the conversion from the state "Susceptible" to the state "Vaccinated" with rate law "vac_rate / (Infected + Recovered)"'
];

const contextLanguage = ref<string>('python3');

const defaultCodeText = '# This environment contains the variable "model" \n# which is displayed on the right';
const codeText = ref(defaultCodeText);
const llmQuery = ref('');
const llmThoughts = ref<any[]>([]);

const isDraft = ref(false);

const executeErrorResponse = ref({
	status: OperatorStatus.DEFAULT,
	name: '',
	value: '',
	traceback: ''
});

const executeResponseTraceback = ref('');

const updateLlmQuery = (query: string) => {
	llmThoughts.value = [];
	llmQuery.value = query;
};

const appendCode = (data: any, property: string) => {
	const newCode = data.content[property] as string;
	if (newCode) {
		codeText.value = (codeText.value ?? defaultCodeText).concat(' \n', newCode);

		if (property === 'executed_code') {
			updateCodeState();
		}
	} else {
		logger.error('No code to append');
	}
};

const syncWithMiraModel = (data: any) => {
	const updatedModel = data.content?.['application/json'];
	if (!updatedModel || !activeOutputModelId) {
		logger.error('Error getting updated model from beaker');
		return;
	}
	updatedModel.id = activeOutputModelId;
	outputModel.value = updatedModel;
	isUpdatingModel.value = false;
	createOutput(outputModel.value as Model);
};

const runCode = () => {
	isUpdatingModel.value = true;
	outputModel.value = null;
	kernelManager.sendMessage('reset_request', {}).register('reset_response', () => {
		const messageContent = {
			silent: false,
			store_history: false,
			user_expressions: {},
			allow_stdin: true,
			stop_on_error: false,
			code: editor?.getValue() as string
		};

		let executedCode = '';

		kernelManager
			.sendMessage('execute_request', messageContent)
			.register('execute_input', (data) => {
				executedCode = data.content.code;
			})
			.register('stream', (data) => {
				if (data?.content?.name === 'stderr' || data?.content?.name === 'stdout') {
					executeResponseTraceback.value = `${executeResponseTraceback.value} ${data.content.text}`;
				}
			})
			.register('model_preview', (data) => {
				if (!data.content) return;
				syncWithMiraModel(data);

				if (executedCode) {
					updateCodeState(executedCode);
				}
			})
			.register('any_execute_reply', (data) => {
				let status = OperatorStatus.DEFAULT;
				if (data.msg.content.status === 'ok') status = OperatorStatus.SUCCESS;
				if (data.msg.content.status === 'error') status = OperatorStatus.ERROR;
				executeErrorResponse.value = {
					status,
					name: data.msg.content.ename ? data.msg.content.ename : '',
					value: data.msg.content.evalue ? data.msg.content.evalue : '',
					traceback: data.msg.content.traceback ? data.msg.content.traceback : ''
				};
			});
	});
};

const resetModel = () => {
	if (!outputModel.value) return;

	kernelManager
		.sendMessage('reset_request', {})
		.register('reset_response', handleResetResponse)
		.register('model_preview', syncWithMiraModel);
};

const handleResetResponse = (data: any) => {
	if (data.content.success) {
		// updateStratifyGroupForm(blankStratifyGroup);

		codeText.value = defaultCodeText;
		updateCodeState('', false);

		logger.info('Model reset');
	} else {
		logger.error('Error resetting model');
	}
};

const initializeAceEditor = (editorInstance: any) => {
	editor = editorInstance;
};

function updateCodeState(code: string = codeText.value, hasCodeRun: boolean = true) {
	const state = saveCodeToState(props.node, code, hasCodeRun, llmQuery.value, llmThoughts.value);
	emit('update-state', state);
}

const createOutput = async (modelToSave: Model) => {
	// If it's the original model, use that otherwise create a new one
	const modelData = isReadyToCreateDefaultOutput.value ? modelToSave : await createModel(modelToSave);
	if (!modelData) return;

	const modelLabel = isReadyToCreateDefaultOutput.value
		? (modelData.name as string)
		: nodeOutputLabel(props.node, 'Output'); // Just label the original model with its name

	emit('append-output', {
		label: modelLabel,
		type: ModelEditOperation.outputs[0].type,
		state: cloneDeep(props.node.state),
		value: [modelData.id]
	});

	isDraft.value = false;
};

const buildJupyterContext = () => {
	if (!inputModelId) {
		logger.warn('Cannot build Jupyter context without a model');
		return null;
	}
	return {
		context: 'mira_model_edit',
		language: 'python3',
		context_info: {
			id: inputModelId
		}
	};
};

const handleOutputChange = async () => {
	// Switch to model from output
	activeOutputModelId = activeOutput.value?.value?.[0];
	if (!activeOutputModelId) return;
	codeText.value = props.node.state.notebookHistory?.[0]?.code ?? defaultCodeText;
	outputModel.value = await getModel(activeOutputModelId);
};

const onSelection = (id: string) => {
	emit('select-output', id);
};

// check if user has made changes to the code
const hasCodeChange = () => {
	if (props.node.state.notebookHistory.length) {
		isDraft.value = !isEqual(codeText.value, props.node.state.notebookHistory?.[0]?.code);
	} else {
		isDraft.value = !isEqual(codeText.value, defaultCodeText);
	}
};
const checkForCodeChange = debounce(hasCodeChange, 500);

watch(
	() => codeText.value,
	() => checkForCodeChange()
);

// Updates output selection
watch(
	() => props.node.active,
	async () => {
		// Update selected output
		if (props.node.active) {
			selectedOutputId.value = props.node.active;
			activeOutput.value = props.node.outputs.find((d) => d.id === selectedOutputId.value) ?? null;
			handleOutputChange();
		}
	},
	{ immediate: true }
);

onMounted(async () => {
	// Save input model id to use throughout the component
	const input = props.node.inputs[0];
	if (!input) return;

	// Get input model id
	if (input.type === 'modelId') {
		inputModelId = input.value?.[0];
	} else if (input.type === 'modelConfigId') {
		inputModelId = await getModelIdFromModelConfigurationId(input.value?.[0]);
	}
	if (!inputModelId) return;

	// By default, the first output option is the original model
	if (isReadyToCreateDefaultOutput.value) {
		// Get model
		const originalModel = await getModel(inputModelId);
		if (!originalModel) return;
		// Set default output which is the input (original model)
		createOutput(originalModel);
	}

	// Create a session and context based on model
	try {
		const jupyterContext = buildJupyterContext();
		if (jupyterContext) {
			await kernelManager.init('beaker_kernel', 'Beaker Kernel', jupyterContext);
		}
	} catch (error) {
		logger.error(`Error initializing Jupyter session: ${error}`);
	}
});

onUnmounted(() => {
	kernelManager.shutdown();
});
</script>

<style scoped>
.input {
	width: 95%;
}

.code-container {
	display: flex;
	flex-direction: column;
}

.notebook-section:deep(main) {
	gap: var(--gap-2);
	position: relative;
}

.input-small {
	padding: var(--gap-2);
	width: 100%;
}

.code-executed-warning {
	background-color: #ffe6e6;
	border-radius: var(--border-radius);
	color: #cc0000;
	padding: var(--gap-2-5);
}

.right-side {
	display: flex;
	justify-content: right;
}
</style>
