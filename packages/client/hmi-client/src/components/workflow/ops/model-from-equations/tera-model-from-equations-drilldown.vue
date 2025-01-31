<template>
	<tera-drilldown
		:node="node"
		v-bind="$attrs"
		@update:selection="onSelection"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<template #sidebar>
			<tera-slider-panel
				v-if="document"
				v-model:is-open="isDocViewerOpen"
				header="Document viewer"
				content-width="100%"
				:document-viewer="true"
			>
				<template #content>
					<tera-drilldown-section :is-loading="isFetchingPDF">
						<tera-pdf-embed ref="pdfViewer" v-if="pdfLink" :pdf-link="pdfLink" :title="document?.name || ''" />
						<tera-text-editor v-else-if="docText" :initial-text="docText" />
					</tera-drilldown-section>
				</template>
			</tera-slider-panel>
			<tera-slider-panel class="input-config" v-model:is-open="isInputOpen" header="Input" content-width="100%">
				<template #content>
					<main class="p-3">
						<header class="pb-2">
							<nav class="flex justify-content-between pb-2">
								<span v-if="document" class="flex align-items-center"
									>Specify which equations to use for this model.</span
								>
								<span v-else class="flex align-items-center"
									>Connect a document or enter equations manually below.</span
								>
								<section class="flex align-items-center min-w-min">
									<RadioButton
										class="ml-3"
										:model-value="runType"
										:input-id="uniqueId()"
										:name="RunType.mira"
										:value="RunType.mira"
										@click="switchRunType(RunType.mira)"
									/>
									<label for="static" class="ml-2 text-sm">MIRA</label>
									<RadioButton
										:model-value="runType"
										:input-id="uniqueId()"
										:name="RunType.skema"
										:value="RunType.skema"
										class="ml-3"
										@click="switchRunType(RunType.skema)"
									/>
									<label for="dynamic" class="ml-2 mr-3 text-sm">SKEMA</label>
									<Button
										class="h-3rem mr-1"
										label="Run"
										@click="onRun(runType)"
										:disabled="includedEquations.length < 1"
									/>
								</section>
							</nav>
							<section
								class="input-container"
								@dragenter.prevent="dragEnterCount++"
								@dragleave.prevent="dragEnterCount--"
								@dragover.prevent
								@drop.prevent.stop="handleDrop"
							>
								<div v-if="pastedImage" class="flex gap-3">
									<div v-if="includedEquations.length == 0" class="flex gap-2 align-items-start">
										<img
											:src="'data:image/png;base64,' + pastedImage"
											alt="Pasted image"
											height="120"
											class="pasted-image"
										/>
										<Button
											icon="pi pi-times"
											rounded
											text
											@click="
												pastedImage = null;
												multipleEquations = '';
											"
										/>
									</div>
									<div
										v-if="
											isEmpty(multipleEquations) &&
											pastedImage &&
											includedEquations.length === 0 &&
											notIncludedEquations.length === 0
										"
										class="flex align-items-center gap-2"
									>
										<span class="pi pi-spinner pi-spin secondary-text"></span>
										<span class="secondary-text">Converting to LaTeX</span>
									</div>
								</div>
								<div class="input-group">
									<!-- Add visual feedback for drag state -->
									<div v-if="dragEnterCount > 0" class="drag-overlay">Drop image here</div>
									<Textarea
										v-model="multipleEquations"
										autoResize
										rows="1"
										placeholder="Add one or more LaTex equations, or paste in a screenshot"
										class="w-full"
										:disabled="multipleEquationsDisabled"
									/>
									<Button
										label="Add"
										icon="pi pi-plus"
										size="small"
										@click="getEquations"
										text
										class="ml-2"
										:disabled="isEmpty(multipleEquations)"
									/>
								</div>
							</section>
						</header>
						<h6 v-if="includedEquations.length > 0" class="py-3">
							Use {{ includedEquations.length > 1 ? 'these equations' : 'this equation' }}
						</h6>
						<p v-if="isEmpty(includedEquations) && document" class="secondary-text mt-3">No equations selected</p>
						<ul class="blocks-container">
							<li v-for="(equation, i) in includedEquations" :key="i" @click.capture="selectItem(equation, $event)">
								<tera-asset-block
									:is-toggleable="false"
									:is-permitted="false"
									:use-default-style="false"
									:class="['asset-panel', { selected: selectedItem === equation.name }]"
								>
									<template #header>
										<h6 v-if="equation.asset.pageNumber">Page {{ equation.asset.pageNumber }}</h6>
										<h6 v-else-if="equation.asset.isEditedByAI">Edited by AI</h6>
										<h6 v-else>Manually entered</h6>
									</template>
									<section>
										<section class="checkbox-container">
											<Checkbox
												v-model="equation.includeInProcess"
												@update:model-value="onCheckBoxChange(equation)"
												:binary="true"
											/>
										</section>
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
										<Button
											v-if="selectedItem !== equation.name"
											icon="pi pi-pencil"
											text
											severity="secondary"
											size="small"
											class="ml-auto"
										/>
									</section>
									<Textarea
										v-if="selectedItem === equation.name"
										v-model="equation.asset.text"
										autoResize
										rows="1"
										placeholder="Add an expression with LaTeX"
										class="w-full"
									/>
								</tera-asset-block>
							</li>
							<!-- <p v-if="isEmpty(includedEquations) && !pastedImage" class="secondary-text">No equations selected</p> -->
						</ul>
						<div class="spacer mb-5" />
						<h6 v-if="notIncludedEquations.length > 0" class="pb-3">Other equations extracted from document</h6>
						<ul class="blocks-container">
							<li v-for="(equation, i) in notIncludedEquations" :key="i" @click.capture="selectItem(equation, $event)">
								<tera-asset-block
									:is-toggleable="false"
									:is-permitted="false"
									:use-default-style="false"
									:class="['asset-panel', { selected: selectedItem === equation.name }]"
								>
									<template #header>
										<h6 v-if="equation.asset.pageNumber">Page {{ equation.asset.pageNumber }}</h6>
										<h6 v-else-if="equation.asset.isEditedByAI">Edited by AI</h6>
										<h6 v-else>Manually entered</h6>
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
										<Button
											v-if="selectedItem !== equation.name"
											icon="pi pi-pencil"
											text
											severity="secondary"
											size="small"
											class="ml-auto"
										/>
									</section>
									<Textarea
										v-if="selectedItem === equation.name"
										v-model="equation.asset.text"
										autoResize
										rows="1"
										placeholder="Add an expression with LaTeX"
										class="w-full"
									/>
								</tera-asset-block>
							</li>
						</ul>
					</main>
				</template>
			</tera-slider-panel>
			<tera-slider-panel
				:direction="outputArrowDirection"
				v-model:is-open="isOutputOpen"
				header="Output"
				content-width="100%"
			>
				<template #content>
					<!--The isOutputOpen condition enables the model diagram within tera-model to render properly
						since we need some sort of width available-->
					<tera-drilldown-preview v-if="isOutputOpen" :is-loading="isModelLoading">
						<tera-model v-if="selectedModel" is-workflow is-save-for-reuse :asset-id="selectedModel.id" />
						<tera-operator-placeholder v-else :node="node" class="h-100">
							<p v-if="isModelLoading">Model is being created...</p>
							<p v-else>Select equations to create a model</p>
						</tera-operator-placeholder>
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
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import type { Card, DocumentAsset, Model } from '@/types/Types';
import { cloneDeep, isEmpty, uniqueId } from 'lodash';
import { equationsToAMR, getCleanedEquations, type EquationsToAMRRequest } from '@/services/knowledge';
import { downloadDocumentAsset, getDocumentAsset, getDocumentFileAsText } from '@/services/document-assets';
import { equationsFromImage } from '@/services/goLLM';
import { getModel, updateModel } from '@/services/model';
import { useProjects } from '@/composables/project';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import TeraModel from '@/components/model/tera-model.vue';
import TeraMathEditor from '@/components/mathml/tera-math-editor.vue';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraPdfEmbed from '@/components/widgets/tera-pdf-embed.vue';
import TeraTextEditor from '@/components/documents/tera-text-editor.vue';
import { logger } from '@/utils/logger';
import RadioButton from 'primevue/radiobutton';
import { ModelFromEquationsState, EquationBlock } from './model-from-equations-operation';

const emit = defineEmits(['close', 'update-state', 'append-output', 'select-output']);
const props = defineProps<{
	node: WorkflowNode<ModelFromEquationsState>;
}>();

const selectedOutputId = ref<string>('');

enum RunType {
	mira = 'mira',
	skema = 'skema'
}

const runType = ref(RunType.mira);
function switchRunType(type: RunType) {
	runType.value = type;
}

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
const isDocumentLoading = ref(false);
const isModelLoading = ref(false);
const selectedModel = ref<Model | null>(null);
const card = ref<Card | null>(null);
const goLLMCard = computed<any>(() => document.value?.metadata?.gollmCard);

const multipleEquations = ref<string>('');
const multipleEquationsDisabled = ref(false);

const isDocViewerOpen = ref(true);
const isInputOpen = ref(true);
const isOutputOpen = ref(true);

const outputArrowDirection = computed(() => (!isDocViewerOpen.value && !isInputOpen.value ? 'left' : 'right'));

const documentEquations = ref<AssetBlock<EquationBlock>[]>();

onMounted(async () => {
	window.addEventListener('paste', handlePasteEvent);
	clonedState.value = cloneDeep(props.node.state);
	if (selectedOutputId.value) {
		onSelection(selectedOutputId.value);
	}

	const documentId = props.node.inputs?.[0]?.value?.[0]?.documentId;

	if (!selectedModel.value) {
		isOutputOpen.value = false;
	}
	if (documentId) {
		isDocumentLoading.value = true;
		document.value = await getDocumentAsset(documentId);
		isDocumentLoading.value = false;

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
});
const pastedImage = ref<string | null>(null);
function handlePasteEvent(e) {
	// checks if the user pasted a file or collection of files
	if (e.clipboardData?.files.length) {
		multipleEquationsDisabled.value = true;
		Array.from(e.clipboardData.files).forEach((item) => {
			const reader = new FileReader();
			reader.onload = function ({ target }) {
				if (target) {
					const base64 = arrayBufferToBase64(target.result);
					pastedImage.value = base64;
					// send base64 to gollm
					equationsFromImage(base64).then((response) => {
						const responseJson = JSON.parse(window.atob(response.output)).response;
						multipleEquations.value = responseJson.equations.join('\n');
						multipleEquationsDisabled.value = false;
					});
				}
			};
			if (item instanceof Blob) {
				reader.readAsArrayBuffer(item);
			}
		});
	}
}

// drag n drop image to get equations
const dragEnterCount = ref(0);

function handleDrop(e: DragEvent) {
	dragEnterCount.value = 0; // Reset counter on drop
	const file = e.dataTransfer?.files[0];
	if (!file) return;
	multipleEquationsDisabled.value = true;
	const reader = new FileReader();
	reader.onload = ({ target }) => {
		if (target?.result) {
			const base64 = arrayBufferToBase64(target.result);
			pastedImage.value = base64;
			equationsFromImage(base64).then((response) => {
				const responseJson = JSON.parse(window.atob(response.output)).response;
				multipleEquations.value = responseJson.equations.join('\n');
				multipleEquationsDisabled.value = false;
			});
		}
	};
	reader.readAsArrayBuffer(file);
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
	// flush changes
	emit('update-state', clonedState.value);

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

async function onRun(extractionService: 'mira' | 'skema' = 'skema') {
	isOutputOpen.value = true;
	isModelLoading.value = true;
	const equationsText = clonedState.value.equations
		.filter((e) => e.includeInProcess && !e.asset.extractionError)
		.map((e) => e.asset.text);
	const response = await getCleanedEquations(equationsText);
	if (!response || isEmpty(response.cleanedEquations)) {
		logger.error('Error cleaning equations, none were returned.');
		return;
	}
	const { cleanedEquations, wasCleaned } = response;

	const request: EquationsToAMRRequest = {
		equations: cleanedEquations,
		documentId: document.value?.id,
		workflowId: props.node.workflowId,
		nodeId: props.node.id,
		extractionService
	};
	const modelId = await equationsToAMR(request);
	// If there isn't a modelId returned at least show the cleaned equations
	if (modelId) {
		clonedState.value.modelId = modelId;
	}

	// If the equations were cleaned that means these cleaned equations should be added to the input list
	// So uncheck the old ones and check the new cleaned ones
	if (wasCleaned) {
		// Uncheck the equations passed to the request
		clonedState.value.equations.forEach((eq) => {
			if (equationsText.includes(eq.asset.text)) {
				eq.includeInProcess = false;
			}
		});
		// Replace the unchecked equations with the cleaned equations
		clonedState.value.equations.push(
			...cleanedEquations.map((equation, index) => ({
				name: `Equation ${clonedState.value.equations.length + index}`,
				includeInProcess: true,
				asset: { text: equation, isEditedByAI: true }
			}))
		);
	}
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
	isModelLoading.value = true;
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
	isModelLoading.value = false;
}

function getEquations() {
	const newEquations = multipleEquations.value.split('\n');
	newEquations.forEach((equation) => {
		if (!equation.trim().length) return;
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
	border: 1px solid var(--surface-border-light);
	border-left: 4px solid var(--surface-400);
	border-radius: var(--border-radius);
	overflow: auto;
	background: var(--surface-0);
	cursor: pointer;
	&.selected {
		border-left: 4px solid var(--primary-color);
	}
}
.asset-panel:deep(.p-panel-header) {
	padding-bottom: var(--gap-1);
	background: transparent;
}
.asset-panel:deep(.p-panel-content) {
	background: transparent;
}
.asset-panel:hover {
	background: var(--surface-highlight);
}
/* TODO: to be implemented when displaying the extracted equations.
.equation-image {
	border-style: dashed;
	border-color: var(--primary-color);
	border-width: thin;
}
*/

.input-container {
	position: relative;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	background: var(--surface-50);
	border-radius: var(--border-radius-medium);
	border: 1px solid var(--surface-border-light);
	padding: var(--gap-3);
}
.input-group {
	display: flex;
	align-items: center;
	flex-direction: row;
}
.pasted-image {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	margin-bottom: var(--gap-3);
	width: fit-content;
	overflow: hidden;
}
.drag-overlay {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: var(--surface-highlight);
	border: 2px dashed var(--primary-color);
	border-radius: var(--border-radius-medium);
	display: flex;
	align-items: center;
	justify-content: center;
	color: var(--primary-color);
	z-index: 1;
}
.equation-view {
	display: flex;
	gap: var(--gap-2);
	flex-direction: column;
	overflow-y: hidden;
}

.blocks-container,
.block-container {
	overflow-y: auto;
}
.secondary-text {
	color: var(--text-color-subdued);
}
.panel-content section {
	.checkbox-container {
		min-height: 65px;
		display: flex;
		align-items: center;
	}
}

/* PrimeVue Override */

.p-panel {
	box-shadow: none;
}

.p-panel:deep(.p-panel-footer) {
	display: none;
}

:deep(.p-splitbutton .p-button:first-of-type) {
	border-top-right-radius: 0;
	border-bottom-right-radius: 0;
	border-right: 0 none;
	pointer-events: none;
}

:deep(.p-splitbutton .p-button:last-of-type) {
	border-top-left-radius: 0;
	border-bottom-left-radius: 0;
	color: #fff;
}

:deep(.p-panel section) {
	align-items: start;
}

/* fix patch for document viewer */
:deep(.document-viewer-header) {
	width: 3rem !important;
}
</style>
