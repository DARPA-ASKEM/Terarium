<template>
	<div ref="lineGraphic">
		<svg :height="parentHeight">
			<line
				v-if="distribution.type === DistributionType.Constant"
				x1="1"
				:y1="parentHeight / 2"
				:x2="width"
				:y2="parentHeight / 2"
				stroke="var(--surface-border-dark)"
			/>
			<!-- Range -->
			<line
				v-if="distribution.type === DistributionType.Uniform"
				:x1="rectX + 4"
				:y1="parentHeight / 2"
				:x2="rectX + rectWidth - 4"
				:y2="parentHeight / 2"
				stroke="black"
				stroke-width="4"
				stroke-dasharray="0 10"
				stroke-linecap="round"
			/>
			<circle
				v-if="distribution.type === DistributionType.Uniform"
				:cx="rectX + 5"
				:cy="parentHeight / 2"
				r="4"
				stroke="black"
				fill="black"
			/>
			<circle
				v-if="distribution.type === DistributionType.Uniform"
				:cx="rectX + rectWidth - 5"
				:cy="parentHeight / 2"
				r="4"
				stroke="black"
				fill="black"
			/>
			<!-- One point -->
			<circle v-else :cx="circlePoint + 5 || 5" :cy="parentHeight / 2" r="4" stroke="black" fill="black" />
		</svg>
	</div>
</template>

<script setup lang="ts">
import * as d3 from 'd3';
import { onMounted, ref } from 'vue';
import { DistributionType } from '@/services/distribution';

const props = defineProps<{
	maxValue: number;
	minValue: number;
	distribution: { type: DistributionType; parameters: any };
}>();
const lineGraphic = ref<HTMLElement>();
const parentHeight = ref<number>(0);
const width = ref<string>('100%');
const rectWidth = ref<number>(0);
const rectX = ref<number>(0);
const circlePoint = ref<number>(0);

const max: number = props.maxValue;
const min: number = props.minValue;

const isUniformType = () => props.distribution.type === DistributionType.Uniform;

onMounted(() => {
	let range = [1, 100];
	const parentWidth = lineGraphic.value?.getBoundingClientRect().width ?? 0;
	parentHeight.value = lineGraphic.value?.getBoundingClientRect().height ?? 0;
	if (parentWidth) {
		range = [0, parentWidth];
	}

	const scale = d3.scaleLinear().domain([min, max]).range(range);

	if (!isUniformType()) {
		const maxScaled = scale(max);
		const cx = scale(props.distribution.parameters.value);
		circlePoint.value = cx > maxScaled ? maxScaled - 5 : cx;
	} else {
		const barMin = scale(props.distribution.parameters.minimum);
		const barMax = scale(props.distribution.parameters.maximum);
		rectWidth.value = barMax - barMin;
		rectX.value = barMin;
	}
});
</script>

<style scoped></style>
