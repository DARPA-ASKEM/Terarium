<template>
	<tera-drilldown
		:node="node"
		v-bind="$attrs"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<template #sidebar>
			<tera-slider-panel v-model:is-open="isDocViewerOpen" header="Document Viewer" content-width="100%">
				<template #content>
					<tera-drilldown-section :is-loading="isFetchingPDF">
						<tera-pdf-embed ref="pdfViewer" v-if="pdfLink" :pdf-link="pdfLink" :title="document?.name || ''" />
						<tera-text-editor v-else-if="docText" :initial-text="docText" />
					</tera-drilldown-section>
				</template>
			</tera-slider-panel>
			<tera-slider-panel v-model:is-open="isInputOpen" header="Input" content-width="100%">
				<template #content>
					<main class="p-3">
						<header class="pb-2">
							<nav class="flex justify-content-between pb-2">
								<span class="flex align-items-center">Specify which equations to use for this model.</span>
								<section class="white-space-nowrap min-w-min">
									<Button class="mr-1" label="Reset" severity="secondary" outlined></Button>
									<Button label="Run" @click="onRun" :diabled="assetLoading" :loading="loadingModel"></Button>
								</section>
							</nav>
							<section class="header-group">
								<Textarea
									v-model="multipleEquations"
									ref="equationTextarea"
									autoResize
									rows="1"
									placeholder="Add one or more LaTex equations, or paste in a screenshot"
									class="w-full"
									:disabled="multipleEquationsDisabled"
								/>
								<Button label="Add" @click="getEquations" class="ml-2" :disabled="isEmpty(multipleEquations)" />
							</section>
						</header>
						<h6 class="py-3">Use {{ includedEquations.length > 1 ? 'these equations' : 'this equation' }}</h6>
						<ul class="blocks-container">
							<li v-for="(equation, i) in includedEquations" :key="i" @click.capture="selectItem(equation, $event)">
								<tera-asset-block
									:is-toggleable="false"
									:is-permitted="false"
									:use-default-style="false"
									:class="['asset-panel', { selected: selectedItem === equation.name }]"
								>
									<template #header>
										<h6>{{ equation.name }} - Page({{ equation.asset.pageNumber }})</h6>
									</template>
									<section>
										<Checkbox
											v-model="equation.includeInProcess"
											@update:model-value="onCheckBoxChange(equation)"
											:binary="true"
										/>
										<div class="block-container">
											<tera-math-editor
												v-if="equation.asset.text"
												:latex-equation="equation.asset.text"
												:is-editable="false"
											/>
											<div v-if="!equation.asset.text" class="no-extract-equation">
												{{ getEquationErrorLabel(equation) }}
											</div>
										</div>
									</section>
									<tera-input-text
										v-if="selectedItem === equation.name"
										v-model="equation.asset.text"
										placeholder="Add an expression with LaTeX"
										@update:model-value="emit('update-state', clonedState)"
									/>
								</tera-asset-block>
							</li>
						</ul>
						<h6 class="py-3">Other equations extracted from document</h6>
						<ul class="blocks-container">
							<li v-for="(equation, i) in notIncludedEquations" :key="i" @click.capture="selectItem(equation, $event)">
								<tera-asset-block
									:is-toggleable="false"
									:is-permitted="false"
									:use-default-style="false"
									:class="['asset-panel', { selected: selectedItem === equation.name }]"
								>
									<template #header>
										<h6>{{ equation.name }} - Page({{ equation.asset.pageNumber }})</h6>
									</template>
									<section>
										<Checkbox
											class="flex-shrink-0"
											v-model="equation.includeInProcess"
											@update:model-value="onCheckBoxChange(equation)"
											:binary="true"
										/>
										<div class="block-container">
											<tera-math-editor
												v-if="equation.asset.text"
												:latex-equation="equation.asset.text"
												:is-editable="false"
											/>
											<div v-if="!equation.asset.text" class="no-extract-equation">
												{{ getEquationErrorLabel(equation) }}
											</div>
										</div>
									</section>
									<tera-input-text
										v-if="selectedItem === equation.name"
										v-model="equation.asset.text"
										placeholder="Add an expression with LaTeX"
										@update:model-value="emit('update-state', clonedState)"
									/>
								</tera-asset-block>
							</li>
						</ul>
					</main>
				</template>
			</tera-slider-panel>
			<tera-slider-panel v-model:is-open="isOutputOpen" header="Output" content-width="100%">
				<template #content>
					<tera-drilldown-preview>
						<tera-model v-if="selectedModel" is-workflow :assetId="selectedModel.id" @on-save="onModelSaveEvent" />
						<tera-operator-placeholder v-else :node="node" class="h-100" />
					</tera-drilldown-preview>
				</template>
			</tera-slider-panel>
		</template>
	</tera-drilldown>
</template>

<script setup lang="ts">
import { AssetBlock, WorkflowNode } from '@/types/workflow';
import Button from 'primevue/button';
import Checkbox from 'primevue/checkbox';
import Textarea from 'primevue/textarea';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownPreview from '@/components/drilldown/tera-drilldown-preview.vue';
import TeraAssetBlock from '@/components/widgets/tera-asset-block.vue';
import { computed, onMounted, onUnmounted, onBeforeUnmount, ref, watch } from 'vue';
import { downloadDocumentAsset, getDocumentAsset, getDocumentFileAsText } from '@/services/document-assets';
import type { Card, ClientEvent, DocumentAsset, Model } from '@/types/Types';
import { cloneDeep, isEmpty } from 'lodash';
import { equationsToAMR, type EquationsToAMRRequest } from '@/services/knowledge';
import { enrichModelMetadata, equationsFromImage } from '@/services/goLLM';
import { getModel, updateModel } from '@/services/model';
import { useProjects } from '@/composables/project';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraModel from '@/components/model/tera-model.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraMathEditor from '@/components/mathml/tera-math-editor.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraPdfEmbed from '@/components/widgets/tera-pdf-embed.vue';
import TeraTextEditor from '@/components/documents/tera-text-editor.vue';

import { ClientEventType, ProgressState } from '@/types/Types';
import { subscribe, unsubscribe } from '@/services/ClientEventService';
import type { ExtractionStatusUpdate } from '@/types/common';
import { ModelFromEquationsOperation, ModelFromEquationsState, EquationBlock } from './model-from-equations-operation';

const emit = defineEmits(['close', 'update-state', 'append-output', 'select-output']);
const props = defineProps<{
	node: WorkflowNode<ModelFromEquationsState>;
}>();

const selectedOutputId = ref<string>('');

const clonedState = ref<ModelFromEquationsState>({
	equations: [],
	text: '',
	modelFramework: 'petrinet',
	modelId: null
});

const includedEquations = computed(() =>
	clonedState.value.equations.filter((equation) => equation.includeInProcess === true)
);
const notIncludedEquations = computed(() =>
	clonedState.value.equations.filter((equation) => equation.includeInProcess === false)
);

const pdfViewer = ref();

const selectedItem = ref('');

const selectItem = (equation, event) => {
	selectedItem.value = equation.name;
	if (pdfViewer.value) {
		pdfViewer.value.goToPage(equation.asset.pageNumber);
	}

	// Prevent the child’s click handler from firing
	event.stopImmediatePropagation();
};

const pdfLink = ref<string | null>();
const docText = ref<string | null>();
const isFetchingPDF = ref(false);

const document = ref<DocumentAsset | null>();
const assetLoading = ref(false);
const loadingModel = ref(false);
const selectedModel = ref<Model | null>(null);
const card = ref<Card | null>(null);
const goLLMCard = computed<any>(() => document.value?.metadata?.gollmCard);

const isGeneratingCard = ref(false);
const multipleEquations = ref<string>('');
const multipleEquationsDisabled = ref(false);

const isDocViewerOpen = ref(true);
const isInputOpen = ref(true);
const isOutputOpen = ref(true);

const equationTextarea = ref();
const documentEquations = ref<AssetBlock<EquationBlock>[]>();

onMounted(async () => {
	window.addEventListener('paste', handlePasteEvent);
	clonedState.value = cloneDeep(props.node.state);
	if (selectedOutputId.value) {
		onSelection(selectedOutputId.value);
	}

	const documentId = props.node.inputs?.[0]?.value?.[0]?.documentId;

	assetLoading.value = true;
	if (documentId) {
		document.value = await getDocumentAsset(documentId);

		isFetchingPDF.value = true;
		const filename = document.value?.fileNames?.[0];
		const isPdf = document.value?.fileNames?.[0]?.endsWith('.pdf');
		if (document.value?.id && filename) {
			if (isPdf) {
				pdfLink.value = await downloadDocumentAsset(document.value.id, filename);
			} else {
				docText.value = await getDocumentFileAsText(document.value.id, filename);
			}
		}
		isFetchingPDF.value = false;
		const state = cloneDeep(props.node.state);
		if (state.equations.length) return;

		if (document.value?.metadata?.equations) {
			documentEquations.value = document.value.metadata.equations.flatMap((page, index) =>
				page.map((equation) => {
					const asset: AssetBlock<EquationBlock> = {
						name: 'Equation',
						includeInProcess: false,
						asset: {
							pageNumber: index + 1,
							text: equation
						}
					};
					return asset;
				})
			);
		}
		if (documentEquations.value && documentEquations.value?.length > 0) {
			clonedState.value.equations = documentEquations.value.map((e, index) => ({
				name: `${e.name} ${index}`,
				includeInProcess: e.includeInProcess,
				asset: { text: e.asset.text, pageNumber: e.asset.pageNumber }
			}));

			state.equations = clonedState.value.equations;
		}

		state.text = document.value?.text ?? '';
		emit('update-state', state);
	}
	assetLoading.value = false;
});

function handlePasteEvent(e) {
	// checks if the user pasted a file or collection of files
	if (e.clipboardData?.files.length) {
		multipleEquationsDisabled.value = true;
		Array.from(e.clipboardData.files).forEach((item) => {
			const reader = new FileReader();
			reader.onload = function ({ target }) {
				if (target && document.value?.id) {
					const base64 = arrayBufferToBase64(target.result);
					// send base64 to gollm
					equationsFromImage(document.value.id, base64).then((response) => {
						const responseJson = JSON.parse(window.atob(response.output)).response;
						multipleEquations.value = responseJson.equations.join('\n');
					});
				}
			};
			if (item instanceof Blob) {
				reader.readAsArrayBuffer(item);
			}
		});
	}
}

function arrayBufferToBase64(buffer) {
	let binary = '';
	const bytes = new Uint8Array(buffer);
	for (let i = 0; i < bytes.byteLength; i++) {
		binary += String.fromCharCode(bytes[i]);
	}
	return window.btoa(binary);
}

onBeforeUnmount(async () => {
	window.removeEventListener('paste', handlePasteEvent);
});

const onSelection = (id: string) => {
	emit('select-output', id);
};

function onCheckBoxChange(equation) {
	const state = cloneDeep(props.node.state);
	const index = state.equations.findIndex((e) => e.name === equation.name);
	state.equations[index].includeInProcess = equation.includeInProcess;
	emit('update-state', state);
}

async function onRun() {
	const equations = clonedState.value.equations
		.filter((e) => e.includeInProcess && !e.asset.extractionError)
		.map((e) => e.asset.text);

	const request: EquationsToAMRRequest = {
		equations,
		framework: clonedState.value.modelFramework,
		documentId: document.value?.id
	};
	const modelId = await equationsToAMR(request);
	if (!modelId) return;

	if (document.value?.id) await generateCard(modelId, document.value.id);

	clonedState.value.modelId = modelId;
	emit('append-output', {
		label: `Output - ${props.node.outputs.length + 1}`,
		state: cloneDeep(clonedState.value),
		isSelected: false,
		type: 'modelId',
		value: [clonedState.value.modelId]
	});
}

async function fetchModel() {
	if (!clonedState.value.modelId) {
		selectedModel.value = null;
		return;
	}
	loadingModel.value = true;
	let model = await getModel(clonedState.value.modelId);

	if (model) {
		if (!model.metadata) {
			model.metadata = {};
		}

		if (!model.metadata?.card && card.value) {
			model.metadata.card = card.value;
		}

		if (!model.metadata?.gollmCard && goLLMCard.value) {
			model.metadata.gollmCard = goLLMCard.value;
		}

		if (useProjects().hasEditPermission()) {
			model = await updateModel(model);
		}
	}
	card.value = model?.metadata?.card ?? null;
	selectedModel.value = model;
	loadingModel.value = false;
}

function getEquations() {
	const newEquations = multipleEquations.value.split('\n');
	newEquations.forEach((equation) => {
		const index = clonedState.value.equations.length;
		clonedState.value.equations.push({
			name: `Equation ${index}`,
			includeInProcess: true,
			asset: {
				text: equation
			}
		});
	});
	emit('update-state', clonedState.value);
	multipleEquations.value = '';
	multipleEquationsDisabled.value = false;
}

function getEquationErrorLabel(equation) {
	return equation.asset.extractionError ? "Couldn't extract equation" : '';
}

function onModelSaveEvent(event: any) {
	const state = cloneDeep(props.node.state);
	state.modelId = event.id;
	emit('update-state', state);
	emit('append-output', {
		type: ModelFromEquationsOperation.outputs[0].type,
		label: event.header.name,
		value: [event.id],
		state,
		isSelected: false
	});
}

// generates the model card and fetches the model when finished
async function generateCard(modelId: string, docId: string) {
	isGeneratingCard.value = true;
	await enrichModelMetadata(modelId, docId, true);
	isGeneratingCard.value = false;
	await fetchModel();
}

watch(
	() => props.node.state,
	() => {
		clonedState.value = cloneDeep(props.node.state);
	},
	{ deep: true }
);

// watch for model id changes on state
watch(
	() => clonedState.value.modelId,
	async () => {
		await fetchModel();
	}
);

watch(
	() => props.node.active,
	() => {
		if (props.node.active) {
			selectedOutputId.value = props.node.active;
		}
	},
	{ immediate: true }
);

onMounted(async () => {
	console.log('testz');
	await subscribe(ClientEventType.ExtractionPdf, subscribeToExtraction);
});

async function subscribeToExtraction(event: ClientEvent<ExtractionStatusUpdate>) {
	console.log('event.data.data', event.data.data);
	console.log(' props.node.inputs', props.node.inputs);
	const documentId = props.node.inputs?.[0].value?.[0]?.documentId;
	if (!documentId || !event.data || event.data.data.documentId !== documentId) return;
	const status = event.data.state;
	// // FIXME: adding the 'dispatching' check since there seems to be an issue with the status of the extractions.
	if (status === ProgressState.Complete || event.data.message.includes('Dispatching')) {
		document.value = await getDocumentAsset(documentId);
	}
}

onUnmounted(async () => {
	await unsubscribe(ClientEventType.ExtractionPdf, subscribeToExtraction);
});
</script>

<style scoped>
.no-extract-equation {
	padding: var(--gap-4);
	background: var(--surface-disabled);
	font-size: 12px;
	color: var(--surface-600);
	border-radius: var(--border-radius-small);
}

.asset-panel {
	padding-top: var(--gap-3);
	border-width: 1px 1px 0 1px;
	border-color: var(--surface-border-light);
	border-style: solid;
	border-radius: unset;
	overflow: auto;

	&.selected {
		border-left: var(--gap-1) solid var(--primary-color);
	}
}

.blocks-container li:first-of-type .asset-panel {
	border-top-left-radius: var(--border-radius-medium);
	border-top-right-radius: var(--border-radius-medium);
}

.blocks-container li:last-of-type .asset-panel {
	border-bottom-width: 1px;
	border-bottom-left-radius: var(--border-radius-medium);
	border-bottom-right-radius: var(--border-radius-medium);
}

/* TODO: to be implemented when displaying the extracted equations.
.equation-image {
	border-style: dashed;
	border-color: var(--primary-color);
	border-width: thin;
}
*/

.header-group {
	display: flex;
	padding-right: var(--gap-2);
	flex-direction: row;
	align-items: center;
	justify-content: space-between;
}

.equation-view {
	display: flex;
	gap: var(--gap-small);
	flex-direction: column;
	overflow-y: hidden;
}

.blocks-container,
.block-container {
	overflow-y: auto;
}

/* PrimeVue Override */

.p-panel {
	box-shadow: none;
}

.p-panel:deep(.p-panel-footer) {
	display: none;
}
</style>
