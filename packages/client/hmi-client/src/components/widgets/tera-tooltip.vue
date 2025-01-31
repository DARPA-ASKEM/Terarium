<template>
	<div :class="!customPosition && position">
		<slot />
		<div
			v-if="props.showTooltip"
			class="tooltip-content pointer-events-none"
			:class="[{ 'has-arrow': hasArrow }, arrowClass]"
			:style="tooltipStyle"
		>
			<slot name="tooltip-content" />
		</div>
	</div>
</template>

<script setup lang="ts">
import { PropType, computed } from 'vue';

const props = defineProps({
	position: {
		type: String as PropType<'top' | 'left' | 'right' | 'bottom'>,
		default: 'right'
	},
	hasArrow: {
		type: Boolean,
		default: true
	},
	// Custom configurations, useful if you want similar behavior to a context menu
	// See it used like this in tera-model-diagram.vue
	customPosition: {
		type: Object as PropType<{ x: number; y: number }> | null,
		default: null
	},
	// This assists the customPosition prop to determine where the arrow should be placed
	// Ignore this prop of you're not using customPosition
	customArrowPosition: {
		type: String as PropType<'top' | 'left' | 'right' | 'bottom'>,
		default: 'bottom'
	},
	showTooltip: {
		type: Boolean,
		default: true
	}
});

const tooltipStyle = computed(() => {
	if (props.customPosition) {
		return {
			top: `${props.customPosition.y}px`,
			left: `${props.customPosition.x}px`
		};
	}
	return {};
});

const arrowClass = computed(() => {
	if (props.customPosition) {
		return `${props.customArrowPosition}-arrow`;
	}
	// Arrow should be on the opposite side of where the tooltip leans
	switch (props.position) {
		case 'top':
			return 'bottom-arrow';
		case 'bottom':
			return 'top-arrow';
		case 'left':
			return 'right-arrow';
		default:
			return 'left-arrow';
	}
});
</script>

<style scoped>
div {
	position: relative;

	&:hover > .tooltip-content {
		display: block;
	}

	& > .tooltip-content {
		display: none;
		position: absolute;
		background-color: var(--surface-section);
		padding: var(--gap-2);
		border-radius: var(--border-radius);
		box-shadow: var(--overlay-menu-shadow);
		z-index: 9999;
	}

	/* Position of tooltip box */
	&.top > .tooltip-content {
		bottom: 100%;
		left: 50%;
		transform: translateX(-50%);
		/* TODO: Perhaps make these margins smaller if there is no arrow is present */
		margin-bottom: var(--gap-6);
	}

	&.bottom > .tooltip-content {
		top: 100%;
		left: 50%;
		transform: translateX(-50%);
		margin-top: var(--gap-6);
	}

	&.left > .tooltip-content {
		right: 100%;
		top: 50%;
		transform: translateY(-50%);
		margin-right: var(--gap-6);
	}

	&.right > .tooltip-content {
		left: 100%;
		top: 50%;
		transform: translateY(-50%);
		margin-left: var(--gap-6);
	}

	/* Base rules for arrow */
	& > .has-arrow::after {
		content: '';
		position: absolute;
		width: 1.5rem;
		height: 1.5rem;
		rotate: 45deg;
		background-color: var(--surface-section);
	}

	/* Top arrow */
	&.tooltip-content.top-arrow::after {
		top: -0.75rem;
		border-left: 1px solid var(--surface-border);
		border-top: 1px solid var(--surface-border);
	}

	/* Bottom arrow */
	&.tooltip-content.bottom-arrow::after {
		bottom: -0.75rem;
		border-right: 1px solid var(--surface-border);
		border-bottom: 1px solid var(--surface-border);
	}

	/* Top/bottom arrow */
	&.tooltip-content.top-arrow::after,
	&.tooltip-content.bottom-arrow::after {
		left: 50%;
		translate: -50%;
	}

	/* Left arrow */
	&.tooltip-content.left-arrow::after {
		left: -1.25rem;
		border-left: 1px solid var(--surface-border);
		border-bottom: 1px solid var(--surface-border);
	}

	/* Right arrow */
	&.tooltip-content.right-arrow::after {
		right: -0.25rem;
		border-right: 1px solid var(--surface-border);
		border-top: 1px solid var(--surface-border);
	}

	/* Left/right arrow */
	&.tooltip-content.left-arrow::after,
	&.tooltip-content.right-arrow::after {
		top: 50%;
		transform: translateY(-50%);
	}
}
</style>
