<template>
	<Dialog
		class="vega-chart-modal"
		modal
		v-model:visible="isExpanded"
		:dismissableMask="true"
		:closable="true"
		:closeOnEscape="true"
		@show="onExpand"
	>
		<template #header>
			<Button
				icon="pi pi-times"
				class="p-dialog-header-icon p-dialog-header-close"
				style="margin-left: auto"
				@click="isExpanded = false"
			/>
		</template>
		<div>
			<div ref="vegaContainerLg"></div>
		</div>
	</Dialog>
	<div class="vega-chart-container">
		<div v-if="!interactive">
			<img v-if="imageDataURL.length > 0" :src="imageDataURL" alt="chart" class="not-interactive" />
		</div>
		<div v-else ref="vegaContainer" />

		<footer v-if="$slots.footer">
			<slot name="footer" />
		</footer>
	</div>
</template>

<script setup lang="ts">
import _ from 'lodash';
import embed, { Config, Result, VisualizationSpec } from 'vega-embed';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import { ref, watch, toRaw, isRef, isReactive, isProxy, computed, h, render, onUnmounted } from 'vue';
import { expressionFunctions } from '@/services/charts';

// This config is default for all charts, but can be overridden by individual chart spec
const defaultChartConfig: Partial<Config> = {
	customFormatTypes: true,
	numberFormatType: 'chartNumberFormatter',
	numberFormat: 'chartNumberFormatter',
	tooltipFormat: {
		numberFormat: 'tooltipFormatter',
		numberFormatType: 'tooltipFormatter'
	}
};

const props = withDefaults(
	defineProps<{
		visualizationSpec: VisualizationSpec;
		areEmbedActionsVisible?: boolean;
		/**
		 * A list of signal names for Vega interval-type selections.
		 * This is equivalent to the name of the `param` in the Vega Lite spec.
		 * The chart will listen to signals for that selection, and emit the selected interval, or null
		 * if the selection is empty.
		 * See https://vega.github.io/vega/docs/api/view/#view_signal for more information.
		 */
		intervalSelectionSignalNames?: string[];
		config?: Config | null;
		/**
		 * Whether to show the expand button.
		 * If a function is provided, it will be called before expanding the chart, and the returned spec will be used for the expanded chart.
		 */
		expandable?: boolean | ((spec: VisualizationSpec) => VisualizationSpec);
		/**
		 * Whether to render interactive chart or png
		 */
		interactive?: boolean;
	}>(),
	{
		areEmbedActionsVisible: true,
		intervalSelectionSignalNames: () => [],
		config: null,
		expandable: false,
		interactive: true
	}
);
const vegaContainer = ref<HTMLElement>();
const vegaVisualization = ref<Result>();
const view = computed(() => vegaVisualization.value?.view);

const vegaContainerLg = ref<HTMLElement>();
const vegaVisualizationExpanded = ref<Result>();
const expandedView = computed(() => vegaVisualizationExpanded.value?.view);

const isExpanded = ref(false);

const interactive = ref(props.interactive);
const imageDataURL = ref('');

const onExpand = async () => {
	if (vegaContainerLg.value) {
		const defaultSize = {
			width: window.innerWidth / 1.5,
			height: window.innerHeight / 1.5
		};
		let spec = deepToRaw(props.visualizationSpec) as any;
		spec.width = defaultSize.width;
		spec.height = defaultSize.height;
		if (typeof props.expandable === 'function') {
			spec = props.expandable(spec);
		}
		vegaVisualizationExpanded.value?.finalize(); // dispose previous visualization before creating a new one
		vegaVisualizationExpanded.value = await createVegaVisualization(vegaContainerLg.value, spec, props.config, {
			actions: props.areEmbedActionsVisible,
			expandable: false
		});
	}
};

const emit = defineEmits<{
	(
		e: 'update-interval-selection',
		signalName: string,
		intervalExtent: { [fieldName: string]: [number, number] } | null
	): void;
	(e: 'chart-click', datum: any | null): void;
	(e: 'done-render'): void;
}>();

/**
 * deepToRaw() is a recursive 'deep' version of Vue's `toRaw` function, which converts
 * Vue created Refs/Proxy objects back to their original form.
 *
 * In certain cases, such as when a nested object (like a Vega Spec) is wrapped in a
 * Vue ref (including when it is returned using `defineModel` on a component like
 * `encoding` is in the `ChartChannelEncodingConfigurator`), each nested object is
 * wrapped in its own `Proxy`. Certain libraries don't deal well with `Proxy` objects
 * and they need to be converted back to raw objects. Vue's `toRaw` function only
 * handles the first level of Proxy objects. This function will recursively go through
 * an object with nested Proxy objects and unwrap them all.
 *
 * This function is taken from https://github.com/vuejs/core/issues/5303
 */
function deepToRaw<T extends Record<string, any>>(sourceObj: T): T {
	const objectIterator = (input: any): any => {
		if (Array.isArray(input)) {
			return input.map((item) => objectIterator(item));
		}
		if (isRef(input) || isReactive(input) || isProxy(input)) {
			return objectIterator(toRaw(input));
		}
		if (input && typeof input === 'object') {
			return Object.keys(input).reduce((acc, key) => {
				acc[key as keyof typeof acc] = objectIterator(input[key]);
				return acc;
			}, {} as T);
		}
		return input;
	};
	return objectIterator(sourceObj);
}

async function createVegaVisualization(
	container: HTMLElement,
	visualizationSpec: VisualizationSpec,
	config: Config | null,
	options: { actions?: boolean; expandable?: boolean } = {}
) {
	const viz = await embed(
		container,
		{ ...visualizationSpec },
		{
			config: { ...defaultChartConfig, ...config } as Config,
			actions: options.actions === false ? false : undefined,
			expressionFunctions // Register expression functions
		}
	);
	props.intervalSelectionSignalNames.forEach((signalName) => {
		viz.view.addSignalListener(signalName, (name, valueRange: { [fieldName: string]: [number, number] }) => {
			if (valueRange === undefined || Object.keys(valueRange).length === 0) {
				emit('update-interval-selection', name, null);
				return;
			}
			emit('update-interval-selection', name, valueRange);
		});
	});
	viz.view.addEventListener('click', (_event, item) => {
		emit('chart-click', item?.datum ?? null);
	});

	// Add expand button to the vega embed container
	if (options.expandable) {
		const expandButton = h(Button, {
			icon: 'pi pi-expand',
			class: 'expand-button',
			severity: 'secondary',
			rounded: true,
			text: true,
			onClick: () => {
				isExpanded.value = true;
			}
		});
		const div = document.createElement('div');
		render(expandButton, div);
		container.appendChild(div.firstChild as Node);
	}
	return viz;
}

watch(
	[vegaContainer, () => props.visualizationSpec],
	async ([, newSpec], [, oldSpec]) => {
		if (_.isEmpty(newSpec)) return;
		const isEqual = _.isEqual(newSpec, oldSpec);
		if (isEqual && vegaVisualization.value !== undefined) return;
		const spec = deepToRaw(props.visualizationSpec);

		if (interactive.value === false) {
			// render png
			const shadowContainer = document.createElement('div');
			const viz = await embed(
				shadowContainer,
				{ ...spec },
				{
					config: { ...defaultChartConfig, ...props.config } as Config,
					actions: props.areEmbedActionsVisible,
					expressionFunctions // Register expression functions
				}
			);

			// svg or png, svg seems to yield crisper renderings at a cost of a
			// much higher size
			const dataURL = await viz.view.toImageURL('svg');
			imageDataURL.value = dataURL;

			// dispose
			viz.finalize();

			emit('done-render');
		} else {
			// render interactive
			if (!vegaContainer.value) return;
			vegaVisualization.value?.finalize(); // dispose previous visualization before creating a new one
			vegaVisualization.value = await createVegaVisualization(vegaContainer.value, spec, props.config, {
				actions: props.areEmbedActionsVisible,
				expandable: !!props.expandable
			});
			emit('done-render');
		}
	},
	{ immediate: true }
);

onUnmounted(() => {
	vegaVisualization.value?.finalize();
	vegaVisualizationExpanded.value?.finalize();
});

defineExpose({
	view,
	expandedView
});
</script>
<style>
/* Since the modal and the tool tips are placed under <body> tag, we need unscoped styles. */

#vg-tooltip-element {
	/* Make sure this is higher than the z-index of the Dialog(modal) */
	z-index: 2000;
}
.vega-chart-modal.p-dialog {
	.p-dialog-header-icons {
		display: none;
	}
}
</style>
<style scoped>
.vega-chart-container {
	display: flex;
	justify-content: center;
	background: var(--surface-0);
	margin-bottom: var(--gap-4);
	footer {
		padding: var(--gap-3);
	}
}

:deep(.vega-embed .expand-button, .vega-embed .expand-button:focus) {
	padding-right: 0px;
	position: relative;
	top: 0;
	right: 0;
	padding: 6px;
	color: black;
	position: absolute;
	background-color: transparent;
}
:deep(.vega-embed.has-actions .expand-button) {
	right: 28px;
}

/* adjust style, position and rotation of action button */
:deep(.vega-embed.has-actions) {
	padding-right: 0px;
	position: relative;
}
:deep(.vega-embed summary) {
	border: 1px solid transparent;
	opacity: 1;
	box-shadow: none;
}
:deep(.vega-embed summary):hover,
:deep(.vega-embed .expand-button):hover {
	border: 1px solid transparent;
	background: var(--surface-hover);
}
:deep(.vega-embed summary svg) {
	transform: rotate(90deg);
}
:deep(.vega-embed .vega-actions a) {
	font-family: 'Figtree', sans-serif;
	font-weight: 400;
}

.not-interactive {
	pointer-events: none;
}
</style>
