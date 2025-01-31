<template>
	<tera-tooltip
		:custom-position="hoveredTransitionPosition"
		:show-tooltip="!isEmpty(hoveredTransitionId)"
		:has-arrow="false"
		title=""
	>
		<tera-resizable-panel v-if="!featureConfig?.isPreview" class="diagram-container">
			<Toolbar>
				<template #start>
					<span>
						<Button @click="resetZoom" label="Reset zoom" size="small" severity="secondary" outlined />
						<span class="how-to-zoom kbd-shortcut-sm"><kbd>Ctrl</kbd>+<kbd>scroll</kbd>&nbsp;to zoom</span>
					</span>
				</template>
				<template #end>
					<span>
						<SelectButton
							v-if="model && isStratified"
							class="p-button-sm"
							:model-value="stratifiedView"
							@change="$event.value && toggleCollapsedView($event.value)"
							:options="stratifiedViewOptions"
							option-value="value"
						>
							<template #option="slotProps">
								<i :class="`${slotProps.option.icon} p-button-icon-left`" />
								<span class="p-button-label">{{ slotProps.option.value }}</span>
							</template>
						</SelectButton>
					</span>
				</template>
			</Toolbar>
			<figure ref="graphElement" class="graph-element"></figure>
			<ul class="legend text-sm" v-if="!isEmpty(graphLegendLabels)">
				<li v-for="(label, index) in graphLegendLabels" :key="index">
					<div class="legend-circle" :style="`background: ${graphLegendColors[index]}`" />
					{{ label }}
				</li>
			</ul>
		</tera-resizable-panel>
		<figure v-else-if="model" ref="graphElement" class="graph-element preview" :key="`preview_${model.id}`"></figure>

		<tera-progress-spinner v-if="mmt.templates.length === 0" class="spinner" is-centered :font-size="2">
			Loading...
		</tera-progress-spinner>

		<tera-stratified-matrix-modal
			v-if="selectedTransitionId"
			:id="selectedTransitionId"
			:mmt="mmt"
			:mmt-params="mmtParams"
			:stratified-matrix-type="StratifiedMatrix.Rates"
			@close-modal="selectedTransitionId = ''"
		/>
		<template #tooltip-content>
			<tera-stratified-matrix-preview
				ref="tooltipContentRef"
				v-if="isStratified && !isEmpty(hoveredTransitionId)"
				:mmt="mmt"
				:mmt-params="mmtParams"
				:id="hoveredTransitionId"
				:stratified-matrix-type="StratifiedMatrix.Rates"
			/>
		</template>
	</tera-tooltip>
</template>

<script setup lang="ts">
import { isEmpty, isEqual, debounce } from 'lodash';
import { ref, watch, computed, nextTick, onMounted, onUnmounted } from 'vue';
import Button from 'primevue/button';
import SelectButton from 'primevue/selectbutton';
import Toolbar from 'primevue/toolbar';
import TeraStratifiedMatrixModal from '@/components/model/petrinet/model-configurations/tera-stratified-matrix-modal.vue';
import TeraStratifiedMatrixPreview from '@/components/model/petrinet/model-configurations/tera-stratified-matrix-preview.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import TeraResizablePanel from '@/components/widgets/tera-resizable-panel.vue';
import TeraTooltip from '@/components/widgets/tera-tooltip.vue';
import { isStratifiedModel, emptyMiraModel, convertToIGraph } from '@/model-representation/mira/mira';
import { MiraModel, MiraTemplateParams, ObservableSummary, MMT } from '@/model-representation/mira/mira-common';
import { NestedPetrinetRenderer } from '@/model-representation/petrinet/nested-petrinet-renderer';
import { PetrinetRenderer } from '@/model-representation/petrinet/petrinet-renderer';
import { getModelRenderer } from '@/model-representation/service';
import { NodeType } from '@/services/graph';
import { getModelType, getMMT } from '@/services/model';
import { AMRSchemaNames, type FeatureConfig } from '@/types/common';
import { StratifiedMatrix } from '@/types/Model';
import type { Model } from '@/types/Types';
import { observeElementSizeChange } from '@/utils/observer';
import { svgToImage } from '@/utils/svg';

const props = defineProps<{
	model: Model;
	mmtData?: MMT;
	featureConfig?: FeatureConfig;
}>();

const graphElement = ref<HTMLDivElement | null>(null);
const graphLegendLabels = ref<string[]>([]);
const graphLegendColors = ref<string[]>([]);
const selectedTransitionId = ref('');
const modelType = computed(() => getModelType(props.model));
const mmt = ref<MiraModel>(emptyMiraModel());
const mmtParams = ref<MiraTemplateParams>({});

let observableSummary: ObservableSummary = {};
const hoveredTransitionId = ref('');
const hoveredTransitionPosition = ref({ x: 0, y: 0 });
const tooltipContentRef = ref();

enum StratifiedView {
	Expanded = 'Expanded',
	Collapsed = 'Collapsed'
}

const stratifiedView = ref(StratifiedView.Collapsed);
const stratifiedViewOptions = ref([{ value: StratifiedView.Expanded }, { value: StratifiedView.Collapsed }]);
const isStratified = computed(() => isStratifiedModel(mmt.value));

let renderer: PetrinetRenderer | NestedPetrinetRenderer | null = null;

const resetZoom = async () => {
	renderer?.setToDefaultZoom();
};

async function renderGraph() {
	// Sanity guard
	if (mmt.value.templates.length === 0) return;

	// Abstract the container element so we can render off the DOM
	let elem = graphElement.value;
	if (props.featureConfig?.isPreview && graphElement.value) {
		const originalElem = graphElement.value;
		elem = document.createElement('div');
		elem.style.width = `${originalElem.clientWidth}px`;
		elem.style.height = `${originalElem.clientHeight}px`;
	}

	renderer = getModelRenderer(mmt.value, elem as HTMLDivElement, stratifiedView.value === StratifiedView.Collapsed);

	if (renderer.constructor === NestedPetrinetRenderer && renderer.dims?.length) {
		graphLegendLabels.value = renderer.dims;
		graphLegendColors.value = renderer.depthColorList;
	} else {
		graphLegendLabels.value = [];
		graphLegendColors.value = [];
	}

	// If interactive, setup events and handlers
	if (!props.featureConfig?.isPreview) {
		makeGraphInteractive(renderer);
	}

	// Prepare data
	const graphData = convertToIGraph(
		mmt.value,
		observableSummary,
		isStratified.value && stratifiedView.value === StratifiedView.Collapsed
	);

	// Render graph, this will either render to the DOM or a virutal element
	if (renderer) {
		renderer.isGraphDirty = true;
		await renderer.setData(graphData);
		await renderer.render();
	}

	// If not interactive, convert elem buffer into image
	if (props.featureConfig?.isPreview && graphElement.value) {
		const svg = elem?.querySelector('svg') as SVGElement;

		// Carry background from container
		svg.style.background = '#f9fbfa';

		const image = await svgToImage(svg);
		graphElement.value.innerHTML = '';
		graphElement.value.appendChild(image);
		elem = null;
	}
}

// eslint-disable-next-line
function makeGraphInteractive(renderer: PetrinetRenderer | NestedPetrinetRenderer) {
	renderer.on('node-click', (_eventName, _event, selection) => {
		const { id, data } = selection.datum();
		if (data.type === NodeType.Transition && data.isStratified) {
			selectedTransitionId.value = id;
		}
	});

	renderer.on('node-mouse-enter', async (_eventName, _event, selection) => {
		const { id, data } = selection.datum();

		if (data.type === NodeType.Transition && data.isStratified) {
			hoveredTransitionId.value = id;

			const diagramBounds = renderer?.svgEl?.getBoundingClientRect();
			const transitionMatrixBounds = selection.node().getBoundingClientRect();

			await nextTick(); // Wait for tooltip to render to get its dimensions
			const tooltipHeight = tooltipContentRef.value.$el.parentElement.clientHeight;
			const tooltipWidth = tooltipContentRef.value.$el.parentElement.clientWidth;

			if (diagramBounds && transitionMatrixBounds && tooltipHeight && tooltipWidth) {
				const transitionMatrixX = transitionMatrixBounds.left - diagramBounds.left;
				const transitionMatrixY = transitionMatrixBounds.top - diagramBounds.top;
				const transitionMatrixWidth = selection.datum().width;

				// Shift tooltip to the top center of the transition matrix
				const x = transitionMatrixX - (tooltipWidth + transitionMatrixWidth / 2) / 2 + transitionMatrixBounds.width / 2;
				const y = transitionMatrixY - tooltipHeight / 2;

				hoveredTransitionPosition.value = { x, y };
			}
		}
	});

	renderer.on('node-mouse-leave', (_eventName, _event, selection) => {
		const { data } = selection.datum();
		if (data.type === NodeType.Transition && data.isStratified) hoveredTransitionId.value = '';
	});
}

async function toggleCollapsedView(view: StratifiedView) {
	stratifiedView.value = view;
	await renderGraph();
}

watch(
	() => [props.model.model, props.model?.semantics, props.mmtData, graphElement.value],
	async (newValue, oldValue) => {
		if (isEqual(newValue, oldValue) || modelType.value === AMRSchemaNames.DECAPODES || graphElement.value === null) {
			return;
		}
		// If no MMT data is provided from the parent component, fetch it from the server
		const mmtData = props.mmtData ?? (await getMMT(props.model));
		if (!mmtData) return;
		mmt.value = mmtData.mmt;
		mmtParams.value = mmtData.template_params;
		observableSummary = mmtData.observable_summary;
		renderGraph();
	},
	{ immediate: true, deep: true }
);

// Create an observer to re-render the graph when it resizes
let graphResizeObserver: ResizeObserver;
onMounted(() => {
	if (graphElement.value && props.featureConfig?.isPreview === false) {
		// FIXME: This debounce prevents the graph from being rendered multiple times in a row.
		// This happens in cases where there is a slight change in width when a scrollbar is shown or hidden. eg. opening/closing the transitions accordion in the model page
		graphResizeObserver = observeElementSizeChange(graphElement.value, debounce(renderGraph, 2000));
	}
});
onUnmounted(() => {
	if (graphResizeObserver) {
		graphResizeObserver.disconnect();
	}
});
</script>

<style scoped>
.diagram-container {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	display: flex;
	flex-direction: column;
}

.preview {
	/* Having both min and max heights prevents height from resizing itself while being dragged on templating canvas
	This resizes on template canvas but not when its in a workflow node?? (tera-model-node)
	FIXME: Will take a look at this again later
	*/
	min-height: 8rem;
	max-height: 8rem;
	background-color: var(--surface-secondary);
	overflow: hidden;
	border: none;
	pointer-events: none;
	position: relative;
}

.p-toolbar {
	background: transparent;
	isolation: isolate;
	padding: var(--gap-2);
	pointer-events: none;
	position: absolute;
	width: 100%;
	z-index: 1;
}

.p-toolbar:deep(> div > span) {
	gap: var(--gap-1);
	display: flex;
	pointer-events: all;
}

/* Let svg dynamically resize when the sidebar opens/closes or page resizes */
:deep(.graph-element svg) {
	background: var(--gray-50) !important;
	border: none !important;
	height: 100%;
	width: 100%;
}

.graph-element {
	background-color: var(--surface-secondary);
	height: 100%;
	cursor: grab;

	&:active {
		cursor: grabbing;
	}
}

.spinner {
	position: absolute;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
}

:deep(.graph-element .p-button) {
	&,
	&:hover {
		background-color: var(--surface-secondary);
	}
}

.how-to-zoom {
	display: flex;
	align-items: center;
	font-size: var(--font-caption);
	background-color: var(--surface-transparent);
	backdrop-filter: blur(4px);
	padding: 0 var(--gap-2);
	border-radius: var(--border-radius);
	pointer-events: none;
	user-select: none;
}

ul.legend {
	backdrop-filter: blur(4px);
	background-color: var(--surface-transparent);
	border-radius: var(--border-radius);
	bottom: 0;
	display: flex;
	gap: var(--gap-4);
	left: 0;
	margin: var(--gap-2);
	margin-bottom: var(--gap-4);
	padding: var(--gap-1) var(--gap-2);
	pointer-events: none;
	position: absolute;

	& > li {
		align-items: center;
		display: flex;
		gap: var(--gap-1);
	}
}

.legend-circle {
	border-radius: 50%;
	display: inline-block;
	height: 1rem;
	width: 1rem;
	margin-right: var(--gap-1);
}
</style>
