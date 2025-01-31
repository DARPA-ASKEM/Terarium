<template>
	<tera-drilldown
		:node="node"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<div :tabName="Tabs.Wizard">
			<tera-drilldown-section>
				<!-- LLM generated overview -->
				<div class="flex px-4 pt-3 pb-0 gap-2">
					<Textarea
						v-model="goalQuery"
						autoResize
						rows="1"
						placeholder="What is your goal? (Optional)"
						class="w-full"
						style="min-height: 33px"
						@keydown.stop
						@click.stop
						@keyup.enter="onClickCompare"
					/>
					<Button
						class="flex-shrink-0"
						label="Compare"
						icon="pi pi-sparkles"
						size="small"
						:loading="isProcessingComparison"
						@click.stop="onClickCompare"
					/>
				</div>
				<section class="comparison-overview">
					<Accordion multiple :activeIndex="currentActiveIndices">
						<AccordionTab header="Overview">
							<div v-if="isProcessingComparison" class="loading-spinner">
								<tera-progress-spinner is-centered :font-size="3" class="secondary-text">
									Analyzing models metadata to generate a detailed comparison analysis...
								</tera-progress-spinner>
							</div>
							<p v-html="overview" v-else class="markdown-text ml-4 mr-4" />
						</AccordionTab>
					</Accordion>
				</section>
				<!-- Model comparison table -->
				<div class="p-datatable-wrapper mb-2">
					<table class="p-datatable-table p-datatable-scrollable-table">
						<thead class="p-datatable-thead">
							<tr>
								<th class="first-field">&nbsp;</th>
								<th v-for="model in modelsToCompare" :key="model.id" class="text-lg">
									{{ model.header.name }}
								</th>
							</tr>
						</thead>
						<tbody v-if="fields" class="p-datatable-tbody">
							<tr>
								<td class="first-field">Diagram</td>
								<td v-for="(model, index) in modelsToCompare" :key="index">
									<tera-model-diagram :model="model" class="diagram" />
								</td>
							</tr>
							<tr v-for="field in fields" :key="field">
								<td class="first-field">{{ formatField(field) }}</td>
								<td v-for="(card, index) in modelCardsToCompare" :key="index">
									<template v-if="!card?.[field]"> N/A </template>
									<template v-else-if="Array.isArray(card[field])">
										<ul class="bullet-list">
											<li class="bullet-list-item" v-for="(item, k) in card[field]" :key="k">{{ item }}</li>
										</ul>
									</template>
									<template v-else-if="typeof card[field] === 'object'">
										<template v-for="(entry, j) in Object.entries(card[field])" :key="j">
											<div class="label">{{ formatField(entry[0]) }}:</div>
											<template v-if="Array.isArray(entry[1])">
												<ul class="bullet-list">
													<li class="bullet-list-item" v-for="(item, k) in entry[1]" :key="k">{{ item }}</li>
												</ul>
											</template>
											<div class="value" v-else>{{ entry[1] }}</div>
											<template v-if="j < Object.entries(card[field]).length - 1">
												<br />
											</template>
										</template>
									</template>
									<template v-else>{{ card[field] }}</template>
								</td>
							</tr>
						</tbody>
					</table>
				</div>
				<!-- Comparison context -->
				<Accordion
					v-if="!isConceptComparisonEmpty && !isConceptComparisonLoading"
					:activeIndex="comparisonContextActiveIndexes"
					class="comparison-context"
					multiple
				>
					<AccordionTab header="Concept context comparison" v-if="!isContextComparisonEmpty">
						<tera-csv-table :csv-text="conceptComparison.concept_context_comparison!" class="mb-4" />
					</AccordionTab>
					<AccordionTab header="Tabular concept comparison" v-if="!isTabularComparisonEmpty">
						<template v-for="(value, pair) in conceptComparison.tabular_comparison" :key="pair">
							<h6 class="ml-4">Tabular comparison {{ pair }}</h6>
							<tera-csv-table :csv-text="value" class="mb-4" />
						</template>
					</AccordionTab>
					<AccordionTab header="Concept graph comparison" v-if="!isGraphComparisonEmpty">
						<template v-for="(value, pair) in conceptComparison.concept_graph_comparison" :key="pair">
							<h6 class="ml-4">Concept comparison {{ pair }}</h6>
							<img :src="`data:image/png;base64,${value}`" :alt="`Concept comparison ${pair}`" class="ml-4" />
						</template>
					</AccordionTab>
				</Accordion>
				<div v-if="isConceptComparisonLoading" class="loading-spinner my-3 mx-4">
					<tera-progress-spinner is-centered :font-size="3" class="max-w-25rem">
						Creating a context, tabular, and graph concept comparison between the {{ modelIds.length }} models...
					</tera-progress-spinner>
				</div>
				<div class="mb-6" />
			</tera-drilldown-section>
		</div>
		<tera-columnar-panel :tabName="Tabs.Notebook">
			<!--TODO: The notebook input and buttons works well here, but it the html/css
				organization should be refactored here (same for tera-model-edit)-->
			<tera-drilldown-section class="notebook-section">
				<tera-notebook-jupyter-input
					:kernelManager="kernelManager"
					:defaultOptions="sampleAgentQuestions"
					@llm-output="appendCode"
					@llm-thought-output="(data: any) => llmThoughts.push(data)"
					@question-asked="llmThoughts = []"
					:context-language="contextLanguage"
				>
					<template #toolbar-right-side>
						<Button label="Reset" outlined severity="secondary" size="small" @click="resetNotebook" />
						<Button icon="pi pi-play" label="Run" size="small" @click="runCode" :disabled="isEmpty(code)" />
					</template>
				</tera-notebook-jupyter-input>
				<v-ace-editor
					v-model:value="code"
					@init="initializeAceEditor"
					lang="python"
					theme="chrome"
					style="flex-grow: 1; width: 100%"
					class="ace-editor"
					:options="{ showPrintMargin: false }"
				/>
			</tera-drilldown-section>
			<tera-drilldown-preview :is-loading="isLoadingStructuralComparisons && isEmpty(structuralComparisons)">
				<ul>
					<li v-for="(image, index) in structuralComparisons" :key="index">
						<label>Comparison {{ index + 1 }}: {{ getTitle(index) }}</label>
						<Image id="img" :src="image" :alt="`Structural comparison ${index + 1}`" preview />
					</li>
				</ul>

				<!-- Legend -->
				<template #footer v-if="isLoadingStructuralComparisons || !isEmpty(structuralComparisons)">
					<div class="legend flex align-items-center gap-4">
						<span class="flex gap-3">
							<span class="flex align-items-center gap-2">
								<span class="legend-circle subdued">Name</span>
								<span>State variable nodes</span>
							</span>
							<span class="flex align-items-center gap-2">
								<span class="legend-square subdued">Name</span>
								<span>Transition nodes</span>
							</span>
						</span>
						<span class="flex gap-4">
							<span class="legend-line blue">Model 1</span>
							<span class="legend-line green">Model 2</span>
							<span class="legend-line orange">Common to both models</span>
							<span class="legend-line red">Equal or related transitions</span>
						</span>
					</div>
				</template>
			</tera-drilldown-preview>
		</tera-columnar-panel>
	</tera-drilldown>
</template>

<script setup lang="ts">
import { cloneDeep, debounce, isEmpty } from 'lodash';
import { v4 as uuidv4 } from 'uuid';
import markdownit from 'markdown-it';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownPreview from '@/components/drilldown/tera-drilldown-preview.vue';
import TeraModelDiagram from '@/components/model/petrinet/tera-model-diagram.vue';
import { compareModels } from '@/services/goLLM';
import { KernelSessionManager } from '@/services/jupyter';
import { getModel } from '@/services/model';
import { ClientEvent, ClientEventType, type Model, TaskResponse, TaskStatus } from '@/types/Types';
import { OperatorStatus, WorkflowNode, WorkflowPortStatus } from '@/types/workflow';
import { logger } from '@/utils/logger';
import Button from 'primevue/button';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { VAceEditor } from 'vue3-ace-editor';
import { VAceEditorInstance } from 'vue3-ace-editor/types';
import TeraNotebookJupyterInput from '@/components/llm/tera-notebook-jupyter-input.vue';
import Image from 'primevue/image';
import { saveCodeToState } from '@/services/notebook';
import { addImage, deleteImages, getImages } from '@/services/image';
import TeraColumnarPanel from '@/components/widgets/tera-columnar-panel.vue';
import { b64DecodeUnicode } from '@/utils/binary';
import { useClientEvent } from '@/composables/useClientEvent';
import Textarea from 'primevue/textarea';
import { CompareModelsConceptsResponse, getCompareModelConcepts } from '@/services/concept';
import TeraCsvTable from '@/components/widgets/tera-csv-table.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { ModelComparisonOperationState } from './model-comparison-operation';

const props = defineProps<{
	node: WorkflowNode<ModelComparisonOperationState>;
}>();

const emit = defineEmits(['update-state', 'update-status', 'close']);

const goalQuery = ref(props.node.state.goal);
const isProcessingComparison = ref(false);

const modelIds = computed(() =>
	props.node.inputs.filter((input) => input.status === WorkflowPortStatus.CONNECTED).map((input) => input.value?.[0])
);

enum Tabs {
	Wizard = 'Wizard',
	Notebook = 'Notebook'
}

let editor: VAceEditorInstance['_editor'] | null;
const kernelManager = new KernelSessionManager();
const sampleAgentQuestions = [
	'Compare models',
	'Compare the three models and visualize and display them.',
	'Compare the two models and visualize and display them.'
];
let compareModelsTaskId = '';

const currentActiveIndices = ref([0]);
const modelsToCompare = ref<Model[]>([]);
const modelCardsToCompare = ref<any[]>([]);
const fields = ref<string[]>([]);

const isLoadingStructuralComparisons = ref(false);
const overview = ref<string | null>(null);
const structuralComparisons = ref<string[]>([]);
const code = ref(props.node.state.notebookHistory?.[0]?.code ?? '');
const llmThoughts = ref<any[]>([]);
const isKernelReady = ref(false);
const contextLanguage = ref<string>('python3');
const comparisonPairs = ref(props.node.state.comparisonPairs);

const initializeAceEditor = (editorInstance: any) => {
	editor = editorInstance;
};

function formatField(field: string) {
	const result = field
		.replace(/([A-Z])/g, ' $1')
		.trim()
		.toLowerCase();
	return result.charAt(0).toUpperCase() + result.slice(1);
}

function updateImagesState(operationType: string, newImageId: string | null = null) {
	const state = cloneDeep(props.node.state);
	if (operationType === 'add' && newImageId) state.comparisonImageIds.push(newImageId);
	else if (operationType === 'clear') state.comparisonImageIds = [];
	emit('update-state', state);
}

function updateComparisonTitlesState(operationType: string, pairs: string[][] | null = null) {
	const state = cloneDeep(props.node.state);
	if (operationType === 'add' && pairs !== null) state.comparisonPairs = pairs;
	else if (operationType === 'clear') state.comparisonPairs = [];
	emit('update-state', state);
}

function getTitle(index: number) {
	if (!comparisonPairs.value[index]) return '';
	return `${comparisonPairs.value[index][0].replaceAll('_', ' ')} VS ${comparisonPairs.value[index][1].replaceAll('_', ' ')}`;
}

function updateCodeState() {
	const state = saveCodeToState(props.node, code.value, true);
	emit('update-state', state);
}

function emptyImages() {
	deleteImages(props.node.state.comparisonImageIds); // Delete images from S3
	updateImagesState('clear'); // Then their ids can be removed from the state
	updateComparisonTitlesState('clear');
	structuralComparisons.value = [];
}

function resetNotebook() {
	code.value = '';
	emptyImages();
	updateCodeState();
}

function onUpdateGoalQuery(goal: string) {
	const state = cloneDeep(props.node.state);
	goalQuery.value = goal;
	state.goal = goal;
	emit('update-state', state);
}

function runCode() {
	const messageContent = {
		silent: false,
		store_history: false,
		user_expressions: {},
		allow_stdin: true,
		stop_on_error: false,
		code: editor?.getValue() as string
	};
	isLoadingStructuralComparisons.value = true;
	emptyImages();
	updateCodeState();

	kernelManager.sendMessage('get_comparison_pairs_request', {}).register('any_get_comparison_pairs_reply', (data) => {
		const pairs = data.msg.content?.return?.comparison_pairs;
		const state = cloneDeep(props.node.state);
		if (pairs.length) {
			updateComparisonTitlesState('add', pairs);
			comparisonPairs.value = pairs;
		} else if (state.comparisonPairs.length) {
			comparisonPairs.value = state.comparisonPairs;
		}
	});

	kernelManager
		.sendMessage('execute_request', messageContent)
		.register('display_data', (data) => {
			const newImageId = uuidv4();
			const newImage = `data:image/png;base64,${data.content.data['image/png']}`;
			structuralComparisons.value.push(newImage);
			addImage(newImageId, newImage);
			updateImagesState('add', newImageId);
			isLoadingStructuralComparisons.value = false;
		})
		.register('error', (data) => {
			logger.error(`${data.content.ename}: ${data.content.evalue}`);
			isLoadingStructuralComparisons.value = false;
		});
}

function appendCode(data: any) {
	const newCode = data.content.code;
	if (newCode) code.value = newCode;
	else logger.error('No code to append');
}

async function buildJupyterContext() {
	if (modelsToCompare.value.length < 2) {
		logger.warn('Cannot build Jupyter context without models');
		return;
	}
	try {
		const jupyterContext = {
			context: 'mira',
			language: 'python3',
			context_info: {
				models: modelsToCompare.value.map((model) => ({
					model_id: model.id,
					name: model.header.name.replace(/[^a-zA-Z0-9_]/g, '_') // Acceptable characters for python variable name
				}))
			}
		};
		if (jupyterContext) {
			if (kernelManager.jupyterSession !== null) {
				kernelManager.shutdown();
			}
			await kernelManager.init('beaker_kernel', 'Beaker Kernel', jupyterContext);
			isKernelReady.value = true;
		}
	} catch (error) {
		logger.error(`Error initializing Jupyter session: ${error}`);
	}
}

function hasNonEmptyValue(obj) {
	if (!obj) return false;
	return Object.values(obj).some((value) => !isEmpty(value));
}

// Generate once the comparison task has been completed
function generateOverview(output: string) {
	const comparison = JSON.parse(b64DecodeUnicode(output)).response;
	let markdown = '';
	const mdi = markdownit();
	markdown += mdi.render(`# ${comparison.title}`);
	if (comparison.summary) {
		markdown += mdi.render(`## Summary`);
		markdown += mdi.render(`${comparison.summary}`);
	}
	if (comparison.structuralComparison && !isEmpty(comparison.structuralComparison)) {
		markdown += mdi.render(`## Structural Comparisons`);
		if (hasNonEmptyValue(comparison.structuralComparison.states)) {
			markdown += mdi.render(`### States:`);
			let paragraph = '';
			if (comparison.structuralComparison.states.common) {
				paragraph += `${comparison.structuralComparison.states.common} `;
			}
			if (comparison.structuralComparison.states.unique) {
				paragraph += `${comparison.structuralComparison.states.unique} `;
			}
			if (comparison.structuralComparison.states.conclusion) {
				paragraph += `${comparison.structuralComparison.states.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
		if (hasNonEmptyValue(comparison.structuralComparison.parameters)) {
			markdown += mdi.render(`### Parameters:`);
			let paragraph = '';
			if (comparison.structuralComparison.parameters.common) {
				paragraph += `${comparison.structuralComparison.parameters.common} `;
			}
			if (comparison.structuralComparison.parameters.unique) {
				paragraph += `${comparison.structuralComparison.parameters.unique} `;
			}
			if (comparison.structuralComparison.parameters.conclusion) {
				paragraph += `${comparison.structuralComparison.parameters.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
		if (hasNonEmptyValue(comparison.structuralComparison.transitions)) {
			markdown += mdi.render(`### Transitions:`);
			let paragraph = '';
			if (comparison.structuralComparison.transitions.common) {
				paragraph += `${comparison.structuralComparison.transitions.common} `;
			}
			if (comparison.structuralComparison.transitions.unique) {
				paragraph += `${comparison.structuralComparison.transitions.unique} `;
			}
			if (comparison.structuralComparison.transitions.conclusion) {
				paragraph += `${comparison.structuralComparison.transitions.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
		if (hasNonEmptyValue(comparison.structuralComparison.observables)) {
			markdown += mdi.render(`### Observables:`);
			let paragraph = '';
			if (comparison.structuralComparison.observables.common) {
				paragraph += `${comparison.structuralComparison.observables.common} `;
			}
			if (comparison.structuralComparison.observables.unique) {
				paragraph += ` ${comparison.structuralComparison.observables.unique} `;
			}
			if (comparison.structuralComparison.observables.conclusion) {
				paragraph += ` ${comparison.structuralComparison.observables.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
	}
	if (comparison.metadataComparison && !isEmpty(comparison.metadataComparison)) {
		markdown += mdi.render(`## Metadata Comparisons`);
		if (hasNonEmptyValue(comparison.metadataComparison.details)) {
			markdown += mdi.render(`### Details:`);
			let paragraph = '';
			if (comparison.metadataComparison.details.common) {
				paragraph += `${comparison.metadataComparison.details.common} `;
			}
			if (comparison.metadataComparison.details.unique) {
				paragraph += `${comparison.metadataComparison.details.unique} `;
			}
			if (comparison.metadataComparison.details.conclusion) {
				paragraph += `${comparison.metadataComparison.details.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
		if (hasNonEmptyValue(comparison.metadataComparison.uses)) {
			markdown += mdi.render(`### Uses:`);
			let paragraph = '';
			if (comparison.metadataComparison.uses.common) {
				paragraph += `${comparison.metadataComparison.uses.common} `;
			}
			if (comparison.metadataComparison.uses.unique) {
				paragraph += `${comparison.metadataComparison.uses.unique} `;
			}
			if (comparison.metadataComparison.uses.conclusion) {
				paragraph += `${comparison.metadataComparison.uses.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
		if (hasNonEmptyValue(comparison.metadataComparison.biasRisksLimitations)) {
			markdown += mdi.render(`### Bias, Risks, and Limitations:`);
			let paragraph = '';
			if (comparison.metadataComparison.biasRisksLimitations.common) {
				paragraph += `${comparison.metadataComparison.biasRisksLimitations.common} `;
			}
			if (comparison.metadataComparison.biasRisksLimitations.unique) {
				paragraph += `${comparison.metadataComparison.biasRisksLimitations.unique} `;
			}
			if (comparison.metadataComparison.biasRisksLimitations.conclusion) {
				paragraph += `${comparison.metadataComparison.biasRisksLimitations.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
		if (hasNonEmptyValue(comparison.metadataComparison.testing)) {
			markdown += mdi.render(`### Testing and Validation:`);
			let paragraph = '';
			if (comparison.metadataComparison.testing.common) {
				paragraph += `${comparison.metadataComparison.testing.common} `;
			}
			if (comparison.metadataComparison.testing.unique) {
				paragraph += `${comparison.metadataComparison.testing.unique} `;
			}
			if (comparison.metadataComparison.testing.conclusion) {
				paragraph += `${comparison.metadataComparison.testing.conclusion}`;
			}
			markdown += mdi.render(paragraph);
		}
	}
	overview.value = markdown;
	emit('update-status', OperatorStatus.DEFAULT); // This is a custom way of granting a default status to the operator, since it has no output
}

function updatePreviousRunId() {
	const state = cloneDeep(props.node.state);
	state.previousRunId = uuidv4();
	emit('update-state', state);
}

function onClickCompare() {
	// If there is no previous run ID, or the node has already run, update the previousRunId
	if (!props.node.state.previousRunId || props.node.state.hasRun) {
		updatePreviousRunId();
	}
	processCompareModels();
}

// Create a task to compare the models
const processCompareModels = async () => {
	isProcessingComparison.value = true;

	// Add a unique ID to the request to avoid caching
	if (!props.node.state.previousRunId) updatePreviousRunId();
	const request = `
  		RequestID: ${props.node.state.previousRunId}
  		${goalQuery.value}
		`;

	const taskRes = await compareModels(modelIds.value, request, props.node.workflowId, props.node.id);
	compareModelsTaskId = taskRes.id;

	if (taskRes.status === TaskStatus.Success) {
		generateOverview(taskRes.output);
	}

	const state = cloneDeep(props.node.state);
	state.hasRun = true;
	emit('update-state', state);
	isProcessingComparison.value = false;
};

/* Concept comparison */

const conceptComparison = ref<CompareModelsConceptsResponse>({});
const isConceptComparisonLoading = ref(false);
const isContextComparisonEmpty = computed(() => isEmpty(conceptComparison.value.concept_context_comparison));
const isTabularComparisonEmpty = computed(() => isEmpty(conceptComparison.value.tabular_comparison));
const isGraphComparisonEmpty = computed(() => isEmpty(conceptComparison.value.concept_graph_comparison));
const isConceptComparisonEmpty = computed(
	() => isContextComparisonEmpty.value && isTabularComparisonEmpty.value && isGraphComparisonEmpty.value
);
const comparisonContextActiveIndexes = ref([0, 1, 2]);

function processConceptComparison() {
	if (isEmpty(modelIds.value)) return;
	isConceptComparisonLoading.value = true;
	conceptComparison.value = {};
	getCompareModelConcepts(modelIds.value, props.node.workflowId, props.node.id)
		.then((response) => {
			if (response) conceptComparison.value = response;
		})
		.catch((error) => {
			logger.error(`Error comparing concepts: ${error}`);
		})
		.finally(() => {
			isConceptComparisonLoading.value = false;
		});
}

/* End of concept comparison */

// Listen for the task completion event
useClientEvent(ClientEventType.TaskGollmCompareModel, (event: ClientEvent<TaskResponse>) => {
	if (!event.data || event.data.id !== compareModelsTaskId) return;

	if ([TaskStatus.Queued, TaskStatus.Running, TaskStatus.Cancelling].includes(event.data.status)) {
		isProcessingComparison.value = true;
	} else if (event.data.status === TaskStatus.Success) {
		generateOverview(event.data.output);
		isProcessingComparison.value = false;
	} else if ([TaskStatus.Failed, TaskStatus.Cancelled].includes(event.data.status)) {
		isProcessingComparison.value = false;
	}
});

onMounted(async () => {
	if (props.node.state.hasRun) {
		processCompareModels();
	}

	// Run asynchronously the concept comparison of the models
	processConceptComparison();

	if (!isEmpty(props.node.state.comparisonImageIds)) {
		isLoadingStructuralComparisons.value = true;
		structuralComparisons.value = await getImages(props.node.state.comparisonImageIds);
		isLoadingStructuralComparisons.value = false;
	}

	modelsToCompare.value = (await Promise.all(modelIds.value.map(async (modelId) => getModel(modelId)))).filter(
		Boolean
	) as Model[];
	modelCardsToCompare.value = modelsToCompare.value.map(({ metadata }) => metadata?.gollmCard);
	fields.value = [...new Set(modelCardsToCompare.value.flatMap((card) => (card ? Object.keys(card) : [])))];

	buildJupyterContext();
});

onUnmounted(() => {
	kernelManager.shutdown();
});

watch(goalQuery, debounce(onUpdateGoalQuery, 1000));
</script>

<style scoped>
.p-datatable-wrapper {
	margin: 0 var(--gap-6);
}

table {
	table-layout: fixed;
	width: 100%;

	& td,
	th {
		vertical-align: top;
		text-align: left;
		padding: var(--gap-2);
		overflow: auto;
		text-overflow: ellipsis;
	}

	& td {
		border-top: 1px solid var(--surface-border-light);
	}

	& .value {
		padding-bottom: 1rem;
	}
}

.input:deep(input) {
	background-image: url('@assets/svg/icons/message.svg');
	background-size: 1rem;
	background-position: 12px;
	background-repeat: no-repeat;
	text-indent: 30px;
}

ul {
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);

	& > li {
		display: flex;
		flex-direction: column;
		gap: var(--gap-1);

		& > span {
			width: fit-content;
			margin-right: var(--gap-12);
		}
	}
}

/* TODO: Improve this pattern later same in (tera-model-input) */

.notebook-section:deep(main) {
	gap: var(--gap-2);
	position: relative;
}

.comparison-overview,
.comparison-context {
	padding: var(--gap-2);
	margin: var(--gap-2) var(--gap-4);

	& + & {
		margin-top: var(--gap-4);
	}
}

.comparison-context h6 {
	&:not(:first-of-type) {
		margin-top: var(--gap-8);
	}

	& + * {
		margin-top: var(--gap-4);
	}
}

.subdued {
	color: var(--text-color-secondary);
}

.diagram {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
}

.legend {
	font-size: var(--font-caption);
	flex: 1;
	margin-bottom: var(--gap-4);
	overflow-x: auto;
	padding: 0 var(--gap-4);

	.legend-circle {
		padding: var(--gap-2) var(--gap-4);
		background-color: var(--surface-0);
		border: 1px solid var(--surface-border);
		border-radius: 50%;
		font-family: 'Times New Roman', Times, serif;
	}

	.legend-square {
		padding: var(--gap-1) var(--gap-4);
		background-color: var(--surface-0);
		border: 1px solid var(--surface-border);
		font-family: 'Times New Roman', Times, serif;
	}

	.legend-line {
		position: relative;
	}

	.legend-line::before {
		content: '';
		position: absolute;
		top: 50%;
		left: 0;
		width: 2px;
		height: 24px;
		transform: translate(-10px, -10px);
	}
	.legend-line.red::before {
		background-color: red;
	}
	.legend-line.orange::before {
		background-color: orange;
	}
	.legend-line.blue::before {
		background-color: blue;
	}
	.legend-line.green::before {
		background-color: lightgreen;
	}
}

.label {
	font-weight: var(--font-weight-semibold);
}

.bullet-list {
	display: block !important;
	list-style: disc outside;
	margin-left: var(--gap-4);
	padding-left: var(--gap-4);
}
.bullet-list-item {
	display: list-item !important;
}

.secondary-text {
	color: var(--text-color-secondary);
}
.loading-spinner {
	display: flex;
	justify-content: center;
	align-items: center;
	height: 100%;
	background: var(--surface-50);
	border-radius: var(--border-radius);
	border: 1px solid var(--surface-border-light);
	padding: var(--gap-4);
}
.first-field {
	width: 16rem;
	font-weight: 600;
}
</style>
