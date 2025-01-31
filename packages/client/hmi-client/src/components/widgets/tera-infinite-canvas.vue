<template>
	<main ref="canvasRef">
		<svg class="canvas-layer background-layer" ref="backgroundLayerRef" :width="width" :height="height">
			<defs>
				<slot name="backgroundDefs" />
			</defs>
			<g ref="svgRef">
				<slot name="background" />
			</g>
		</svg>
		<div class="canvas-layer data-layer" ref="dataLayerRef">
			<slot name="data" />
		</div>
		<div class="canvas-layer foreground-layer" :class="{ disable: isDisabled }">
			<slot name="foreground" />
		</div>
	</main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import * as d3 from 'd3';

const props = withDefaults(
	defineProps<{
		background?: string | undefined;
		debugMode?: boolean;
		scaleExtent?: [number, number];
		lastTransform?: { k: number; x: number; y: number };
		isDisabled?: boolean;
	}>(),
	{
		background: 'dots',
		debugMode: false,
		scaleExtent: () => [0.1, 10],
		lastTransform: undefined
	}
);
//
const emit = defineEmits(['save-transform']);

let x: d3.ScaleLinear<number, number, never>;
let y: d3.ScaleLinear<number, number, never>;
let xAxis: d3.Axis<d3.NumberValue>;
let yAxis: d3.Axis<d3.NumberValue>;
let gX: d3.Selection<SVGGElement, any, null, any>;
let gY: d3.Selection<SVGGElement, any, null, any>;
let currentTransform: d3.ZoomTransform;
let zoom: d3.ZoomBehavior<Element, unknown>;

const width = ref(0);
const height = ref(0);
const canvasRef = ref<HTMLElement>();
const dataLayerRef = ref<HTMLDivElement>();
const backgroundLayerRef = ref<SVGElement>();
const svgRef = ref<SVGElement>();

function handleZoom(e: any, container: d3.Selection<SVGGElement, any, null, any>) {
	container.attr('transform', e.transform);

	d3.select(dataLayerRef.value as HTMLDivElement)
		.style('transform', `translate(${e.transform.x}px, ${e.transform.y}px) scale(${e.transform.k})`)
		.style('transform-origin', '0 0');

	if (props.debugMode) {
		gX.call(xAxis.scale(e.transform.rescaleX(x)));
		gY.call(yAxis.scale(e.transform.rescaleY(y)));
	}

	currentTransform = e.transform;

	if (props.background === 'dots') {
		const background = d3.select(svgRef.value as SVGGElement);
		background
			.select('#dotPattern')
			.style(
				'patternTransform',
				`translate(${currentTransform.x}, ${currentTransform.y}) scale(${currentTransform.k})`
			);
	}
}

function handleZoomEnd() {
	emit('save-transform', { x: currentTransform.x, y: currentTransform.y, k: currentTransform.k });
	if (props.background === 'dots') {
		const background = d3.select(svgRef.value as SVGGElement);
		background
			.select('#dotPattern')
			.style(
				'patternTransform',
				`translate(${currentTransform.x}, ${currentTransform.y}) scale(${currentTransform.k})`
			);
	}
}

function setZoom(tx: number, ty: number, k: number) {
	const svg = d3.select(backgroundLayerRef.value as SVGGElement); // Parent SVG
	svg
		.transition()
		.duration(1500)
		.call(zoom.transform as any, d3.zoomIdentity.translate(tx, ty).scale(k));
}

defineExpose({
	setZoom
});

function updateDimensions() {
	// Update dimensions
	width.value = canvasRef.value?.clientWidth ?? window.innerWidth;
	height.value = canvasRef.value?.clientHeight ?? window.innerHeight;

	if (props.debugMode) {
		// Update debug values
		x = d3
			.scaleLinear()
			.domain([-1, width.value + 1])
			.range([-1, width.value + 1]);
		y = d3
			.scaleLinear()
			.domain([-1, height.value + 1])
			.range([-1, height.value + 1]);
		xAxis = d3
			.axisBottom(x)
			.ticks(((width.value + 2) / (height.value + 2)) * 20)
			.tickSize(height.value)
			.tickPadding(8 - height.value);
		yAxis = d3
			.axisRight(y)
			.ticks(10)
			.tickSize(width.value)
			.tickPadding(8 - width.value);

		if (currentTransform) {
			gX.call(xAxis.scale(currentTransform.rescaleX(x)));
			gY.call(yAxis.scale(currentTransform.rescaleY(y)));
		}
	}
}

const resizeObserver = new ResizeObserver(() => updateDimensions());

onMounted(() => {
	const svg = d3.select(backgroundLayerRef.value as SVGGElement); // Parent SVG
	const svgContainer = d3.select(svgRef.value as SVGGElement); // Pan/zoom area

	// Zoom config is applied and event handler
	zoom = d3
		.zoom()
		.filter((evt: any) => {
			const classStr = d3.select(evt.target).attr('class') || '';
			if ((classStr && classStr.includes('canvas-layer')) || evt.type === 'wheel') {
				return true;
			}
			return false;
		})
		.scaleExtent(props.scaleExtent)
		.on('zoom', (e) => {
			handleZoom(e, svgContainer);
		})
		.on('end', handleZoomEnd);
	svg.call(zoom as any).on('dblclick.zoom', null);

	// Initializes/watches resize of component so SVG layer fills it
	updateDimensions();
	if (canvasRef.value) resizeObserver.observe(canvasRef.value);

	// Draw dot pattern in background
	svg
		.append('defs')
		.append('pattern')
		.attr('id', 'dotPattern')
		.attr('width', 12)
		.attr('height', 12)
		.attr('patternUnits', 'userSpaceOnUse')
		.append('circle')
		.attr('fill', 'var(--surface-border-light)')
		.attr('cx', 12)
		.attr('cy', 12)
		.attr('r', 2);

	svg
		.append('rect')
		.attr('x', 0)
		.attr('y', 0)
		.attr('width', '100%')
		.attr('height', '100%')
		.attr('pointer-events', 'none')
		.attr('fill', 'url(#dotPattern)')
		.style('mix-blend-mode', 'darken');

	// Assign debug grid
	if (props.debugMode) {
		gX = svg.append('g').attr('class', 'axis axis--x').call(xAxis);
		gY = svg.append('g').attr('class', 'axis axis--y').call(yAxis);
	}

	// Initialize starting position
	if (props.lastTransform) {
		const tr = props.lastTransform;
		zoom.scaleTo(svg as any, tr.k);
		zoom.translateTo(svg as any, -tr.x / tr.k, -tr.y / tr.k);
	} else {
		// Default position - triggers handleZoom which in turn sets currentTransform
		svg.transition().call(zoom.transform as any, d3.zoomIdentity);
	}
});
</script>

<style scoped>
main {
	width: 100%;
	min-height: 100%;
}

main > * {
	position: absolute;
}

.background-layer {
	cursor: grab;
	background-color: var(--surface-0);
}

.background-layer:deep(.tick line) {
	color: var(--surface-border-light);
	stroke-width: 1px;
}

.background-layer:deep(.axis.axis--y),
.background-layer:deep(.tick text) {
	display: block;
}

svg:active {
	cursor: grabbing;
}

.foreground-layer {
	position: relative;
	display: flex;
	flex-direction: column;
}

.disable {
	height: 100%;
}
</style>
