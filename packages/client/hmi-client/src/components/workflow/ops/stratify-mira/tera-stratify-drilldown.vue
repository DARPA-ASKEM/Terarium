<template>
	<tera-drilldown
		:node="node"
		:is-draft="isDraft"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
		@update:selection="onSelection"
		v-bind="$attrs"
	>
		<div :tabName="StratifyTabs.Wizard" class="input-section">
			<tera-drilldown-section class="px-3 wizard-section">
				<template #header-controls-left>
					<section>
						<h5>Stratification settings</h5>
						<p v-if="node.state.hasCodeBeenRun" class="code-executed-warning">
							Note: Code has been executed which may not be reflected here.
						</p>
					</section>
				</template>
				<template #header-controls-right>
					<Button size="small" severity="secondary" outlined label="Reset" @click="resetModel" class="mr-1" />
					<Button
						:loading="isStratifyInProgress"
						:label="isStratifyInProgress ? 'Loading...' : 'Stratify'"
						size="small"
						@click="stratifyModel"
					/>
				</template>
				<tera-stratification-group-form
					class="mt-2"
					:model-node-options="modelNodeOptions"
					:config="node.state.strataGroup"
					@update-self="updateStratifyGroupForm"
				/>
			</tera-drilldown-section>
		</div>
		<div :tabName="StratifyTabs.Notebook">
			<tera-drilldown-section class="notebook-section">
				<tera-notebook-jupyter-input
					:kernel-manager="kernelManager"
					:default-options="sampleAgentQuestions"
					:context-language="'python3'"
					@llm-output="(data: any) => processLLMOutput(data)"
					@llm-thought-output="(data: any) => llmThoughts.push(data)"
					@question-asked="updateLlmQuery"
				>
					<template #toolbar-right-side>
						<Button
							:loading="isStratifyInProgress"
							:label="isStratifyInProgress ? 'Loading...' : 'Run'"
							size="small"
							icon="pi pi-play"
							@click="runCodeStratify"
							:disabled="isEmpty(codeText)"
						/>
					</template>
				</tera-notebook-jupyter-input>
				<v-ace-editor
					v-model:value="codeText"
					@init="initialize"
					lang="python"
					theme="chrome"
					style="flex-grow: 1; width: 100%"
					class="ace-editor"
					:options="{ showPrintMargin: false }"
				/>
			</tera-drilldown-section>
		</div>
		<template #preview>
			<tera-drilldown-preview
				title="Preview"
				@update:selection="onSelection"
				v-model:output="selectedOutputId"
				is-selectable
			>
				<tera-notebook-error
					v-if="executeResponse.status === OperatorStatus.ERROR"
					:name="executeResponse.name"
					:value="executeResponse.value"
					:traceback="executeResponse.traceback"
				/>
				<tera-model v-else-if="outputAmr" is-workflow is-save-for-reuse :assetId="outputAmr.id" />
				<template v-else>
					<tera-progress-spinner v-if="isStratifyInProgress" is-centered :font-size="2">
						Processing...
					</tera-progress-spinner>
					<tera-operator-placeholder class="flex flex-column h-full justify-content-center" v-else :node="node" />
				</template>
			</tera-drilldown-preview>
		</template>
	</tera-drilldown>
</template>

<script setup lang="ts">
import '@/ace-config';
import { isEmpty, cloneDeep, debounce, isEqual, last } from 'lodash';
import TeraDrilldownPreview from '@/components/drilldown/tera-drilldown-preview.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraNotebookError from '@/components/drilldown/tera-notebook-error.vue';
import TeraNotebookJupyterInput from '@/components/llm/tera-notebook-jupyter-input.vue';
import TeraModel from '@/components/model/tera-model.vue';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import TeraStratificationGroupForm from '@/components/workflow/ops/stratify-mira/tera-stratification-group-form.vue';
import { KernelSessionManager } from '@/services/jupyter';
import { createModelFromOld, getModel } from '@/services/model';
import { getModelIdFromModelConfigurationId } from '@/services/model-configurations';
import type { Model } from '@/types/Types';
import { AMRSchemaNames } from '@/types/common';
import { OperatorStatus, WorkflowNode } from '@/types/workflow';
import { logger } from '@/utils/logger';
import Button from 'primevue/button';
import { v4 as uuidv4 } from 'uuid';
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { VAceEditor } from 'vue3-ace-editor';
import { VAceEditorInstance } from 'vue3-ace-editor/types';
import { blankStratifyGroup, StratifyGroup, StratifyOperationStateMira } from './stratify-mira-operation';

const props = defineProps<{
	node: WorkflowNode<StratifyOperationStateMira>;
}>();
const emit = defineEmits(['append-output', 'update-state', 'close', 'select-output']);

enum StratifyTabs {
	Wizard = 'Wizard',
	Notebook = 'Notebook'
}

const amr = ref<Model | null>(null);
const outputAmr = ref<Model | null>(null);
const executeResponse = ref({
	status: OperatorStatus.DEFAULT,
	name: '',
	value: '',
	traceback: ''
});
const modelNodeOptions = ref<string[]>([]);

const isDraft = ref(false);

const isStratifyInProgress = ref(false);

const selectedOutputId = ref<string>();

const kernelManager = new KernelSessionManager();

let editor: VAceEditorInstance['_editor'] | null;
const codeText = ref('');
const llmQuery = ref('');
const llmThoughts = ref<any[]>([]);

const sampleAgentQuestions = [
	'Stratify my model by the ages young and old',
	'Stratify my model by the locations Toronto and Montreal where Toronto and Montreal cannot interact',
	'Stratify my model by the locations Toronto and Montreal where populations within any state are able to travel between Toronto and Montreal',
	'What is cartesian_control in stratify?'
];

const updateLlmQuery = (query: string) => {
	llmThoughts.value = [];
	llmQuery.value = query;
};

const updateStratifyGroupForm = (config: StratifyGroup) => {
	const state = cloneDeep(props.node.state);
	state.strataGroup = config;
	emit('update-state', state);
};

const processLLMOutput = (data: any) => {
	codeText.value = data.content.code;
	saveCodeToState(data.content.code, false);
};

const resetModel = () => {
	if (!amr.value) return;
	kernelManager
		.sendMessage('reset_request', {})
		.register('reset_response', handleResetResponse)
		.register('model_preview', handleModelPreview);
};

const handleResetResponse = (data: any) => {
	if (data.content.success) {
		updateStratifyGroupForm(blankStratifyGroup);

		codeText.value = '';
		saveCodeToState('', false);

		logger.info('Model reset');
	} else {
		logger.error('Error resetting model');
	}
};

const stratifyModel = () => {
	if (!amr.value) return;
	isStratifyInProgress.value = true;

	// Sanity check states vs parameters
	const conceptsToStratify: string[] = [];
	const parametersToStratify: string[] = [];
	const strataOption = props.node.state.strataGroup;
	const { modelStates, modelParameters } = getStatesAndParameters(amr.value);

	strataOption.selectedVariables.forEach((v) => {
		if (modelStates.includes(v)) conceptsToStratify.push(v);
		if (modelParameters.includes(v)) parametersToStratify.push(v);
	});

	const messageContent = {
		key: strataOption.name,
		strata: strataOption.groupLabels.split(',').map((d) => d.trim()),
		concepts_to_stratify: conceptsToStratify,
		params_to_stratify: parametersToStratify,
		cartesian_control: strataOption.cartesianProduct,
		structure: strataOption.useStructure ? null : []
	};
	kernelManager.sendMessage('reset_request', {}).register('reset_response', () => {
		kernelManager
			.sendMessage('stratify_request', messageContent)
			.register('stratify_response', (data: any) => {
				const executedCode = data.content.executed_code;
				saveCodeToState(executedCode, false);
			})
			.register('model_preview', async (data: any) => {
				await handleModelPreview(data);
			});
	});
};

const handleModelPreview = async (data: any) => {
	if (!amr.value) return;

	const amrResponse = data.content['application/json'] as Model;
	isStratifyInProgress.value = false;
	if (!amrResponse) {
		logger.error('Error getting updated model from beaker');
		return;
	}

	const state = props.node.state;

	// Try to derive a better name
	let newName = amr.value?.header.name ?? 'Model';
	newName += ` (stratified ${state.strataGroup.name} with ${state.strataGroup.groupLabels.split(',').length} groups)`;
	amrResponse.name = newName;
	amrResponse.header.name = newName;

	// Create output
	const modelData = await createModelFromOld(amr.value, amrResponse);
	if (!modelData) return;
	outputAmr.value = modelData;

	emit('append-output', {
		id: uuidv4(),
		label: newName,
		type: 'modelId',
		state: {
			strataGroup: cloneDeep(props.node.state.strataGroup),
			strataCodeHistory: cloneDeep(props.node.state.strataCodeHistory)
		},
		value: [modelData.id]
	});
};

const buildJupyterContext = () => {
	if (!amr.value) {
		logger.warn('Cannot build Jupyter context without a model');
		return null;
	}

	return {
		context: 'mira_model_edit',
		language: 'python3',
		context_info: {
			id: amr.value.id
		}
	};
};

const getStatesAndParameters = (amrModel: Model) => {
	const modelFramework = amrModel.header.schema_name?.toLowerCase();
	const modelStates: string[] = [];
	const modelParameters: string[] = [];

	const model = amrModel.model;
	const semantics = amrModel.semantics;

	const rates = semantics!.ode.rates || [];
	const rateExpressions = rates.map((r) => r.expression);

	if ((modelFramework === AMRSchemaNames.PETRINET || modelFramework === AMRSchemaNames.STOCKFLOW) && semantics?.ode) {
		const { initials, parameters, observables } = semantics.ode;

		initials?.forEach((i) => {
			modelStates.push(i.target);
		});
		parameters?.forEach((p) => {
			// Parameters should be used within transition expressions for them to be "stratifiable"
			// FIXME: This would be more accurate if we parse and check rate expressions' free variables
			// instead of just the rate expression string
			for (let i = 0; i < rateExpressions.length; i++) {
				if (rateExpressions[i]?.includes(p.id)) {
					modelParameters.push(p.id);
					break;
				}
			}
		});
		observables?.forEach((o) => {
			modelStates.push(o.id);
		});
	} else if (modelFramework === AMRSchemaNames.REGNET) {
		model.vertices.forEach((v: any) => {
			modelStates.push(v.name);
		});
		model.parameters.forEach((p: any) => {
			modelParameters.push(p.id);
		});
	} else {
		console.error(`Unknown framework ${modelFramework}`);
		throw new Error(`Unknown framework ${modelFramework}`);
	}
	return { modelStates, modelParameters };
};

const inputChangeHandler = async () => {
	const input = props.node.inputs[0];
	if (!input) return;

	let modelId: string | null = null;
	if (input.type === 'modelId') {
		modelId = input.value?.[0];
	} else if (input.type === 'modelConfigId') {
		modelId = await getModelIdFromModelConfigurationId(input.value?.[0]);
	}
	if (!modelId) return;

	amr.value = await getModel(modelId);
	if (!amr.value) return;

	const { modelStates, modelParameters } = getStatesAndParameters(amr.value);
	modelNodeOptions.value = [...modelStates, ...modelParameters];

	// Create a new session and context based on model
	try {
		const jupyterContext = buildJupyterContext();
		if (jupyterContext) {
			await kernelManager.init('beaker_kernel', 'Beaker Kernel', buildJupyterContext());
		}
	} catch (error) {
		logger.error(`Error initializing Jupyter session: ${error}`);
	}
};

const initialize = (editorInstance: any) => {
	editor = editorInstance;
};

const runCodeStratify = () => {
	const code = editor?.getValue();
	if (!code) return;
	isStratifyInProgress.value = true;

	const messageContent = {
		silent: false,
		store_history: false,
		user_expressions: {},
		allow_stdin: true,
		stop_on_error: false,
		code
	};

	let executedCode = '';

	kernelManager.sendMessage('reset_request', {}).register('reset_response', () => {
		kernelManager
			.sendMessage('execute_request', messageContent)
			.register('execute_input', (data) => {
				executedCode = data.content.code;
			})
			.register('stream', (data) => {
				console.log('stream', data);
			})
			.register('model_preview', (data) => {
				// TODO: https://github.com/DARPA-ASKEM/terarium/issues/2305
				// currently no matter what kind of code is run we always get a `model_preview` response.
				// We may want to compare the response model with the existing model to see if the response model
				// has been stratified - if not then don't save the model or the code.
				handleModelPreview(data);

				if (executedCode) {
					saveCodeToState(executedCode, true);
					isDraft.value = false;
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
	});
};

// FIXME: Copy pasted in 3 locations, could be written cleaner and in a service. Migrate it to use saveCodeToState from @/services/notebook
const saveCodeToState = (code: string, hasCodeBeenRun: boolean) => {
	const state = cloneDeep(props.node.state);
	state.hasCodeBeenRun = hasCodeBeenRun;
	// for now only save the last code executed, may want to save all code executed in the future
	const codeHistoryLength = props.node.state.strataCodeHistory.length;
	const timestamp = Date.now();
	const llm = { llmQuery: llmQuery.value, llmThoughts: llmThoughts.value };
	if (codeHistoryLength > 0) {
		state.strataCodeHistory[0] = { code, timestamp, ...llm };
	} else {
		state.strataCodeHistory.push({ code, timestamp, ...llm });
	}
	emit('update-state', state);
};

const onSelection = (id: string) => {
	emit('select-output', id);
};

// check if user has made changes to the code
const hasCodeChange = () => {
	if (props.node.state.strataCodeHistory.length) {
		isDraft.value = !isEqual(codeText.value, props.node.state.strataCodeHistory?.[0]?.code);
	} else {
		isDraft.value = !isEqual(codeText.value, '');
	}
};
const checkForCodeChange = debounce(hasCodeChange, 100);

watch(
	() => codeText.value,
	() => checkForCodeChange()
);

watch(
	() => props.node.active,
	async () => {
		if (props.node.active) {
			selectedOutputId.value = props.node.active;

			const output = props.node.outputs.find((d) => d.id === selectedOutputId.value);
			if (!output) {
				console.error(`cannot find active output ${selectedOutputId.value}`);
				return;
			}
			const modelIdToLoad = output.value?.[0];
			outputAmr.value = await getModel(modelIdToLoad);
			codeText.value = last(props.node.state.strataCodeHistory)?.code ?? '';
		}
	},
	{ immediate: true }
);

// Set model, modelNodeOptions
watch(
	() => props.node.inputs[0],
	async () => {
		await inputChangeHandler();
	},
	{ immediate: true }
);

onMounted(() => {
	const codeHistoryLength = props.node.state.strataCodeHistory.length;
	if (codeHistoryLength > 0) {
		codeText.value = props.node.state.strataCodeHistory[codeHistoryLength - 1].code;
	}
});

onUnmounted(() => {
	kernelManager.shutdown();
});
</script>

<style scoped>
/* set width of wizard section */
:deep(main):has(.input-section) {
	grid-template-columns: auto;
}
/* set width of notebook section */
:deep(main):has(.notebook-section) {
	grid-template-columns: 40% 60%;
}

.notebook-section:deep(main) {
	gap: var(--gap-2);
	position: relative;
}

.code-executed-warning {
	background-color: var(--error-message-background);
	border-radius: var(--border-radius);
	color: var(--error-message-color);
	padding: var(--gap-2-5);
}

.form-section {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}

.wizard-section {
	background-color: var(--surface-disabled);
	border-right: 1px solid var(--surface-border-light);
}
</style>
