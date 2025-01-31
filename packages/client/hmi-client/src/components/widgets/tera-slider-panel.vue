<template>
	<tera-slider
		:content-width="contentWidth"
		:tab-width="tabWidth"
		:min-tab-width="minTabWidth"
		:direction="arrowDirection"
		:is-open="isOpen"
	>
		<template v-slot:content>
			<aside @scroll="onScroll">
				<header v-if="!documentViewer" :class="{ shadow: isScrolled }">
					<Button
						:icon="`pi ${directionMap[arrowDirection].iconOpen}`"
						@click="emit('update:isOpen', false)"
						text
						rounded
						size="large"
					/>
					<slot name="header" />
					<h4>{{ header }}</h4>
				</header>
				<header class="document-viewer-header" v-else>
					<Button
						:icon="`pi ${directionMap[arrowDirection].iconOpen}`"
						@click="emit('update:isOpen', false)"
						text
						rounded
						size="large"
					/>
				</header>
				<div class="content-wrapper">
					<slot name="content" />
				</div>
			</aside>
			<slot name="overlay" />
		</template>
		<template v-slot:tab>
			<header :class="`tab ${arrowDirection}`">
				<Button
					:icon="`pi ${directionMap[arrowDirection].iconClosed}`"
					@click="emit('update:isOpen', true)"
					text
					rounded
					size="large"
				/>
				<h5>{{ header }}</h5>
				<Badge v-if="indicatorValue" :value="indicatorValue" />
			</header>
			<slot name="tab" />
		</template>
		<template v-if="$slots.footerButtons" v-slot:footerButtons>
			<slot name="footerButtons" />
		</template>
	</tera-slider>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import Badge from 'primevue/badge';
import Button from 'primevue/button';
import TeraSlider from './tera-slider.vue';

const props = defineProps({
	// slider props
	isOpen: {
		type: Boolean,
		default: true
	},
	direction: {
		type: String,
		default: 'left'
	},
	contentWidth: {
		type: String,
		default: '240px'
	},
	tabWidth: {
		type: String,
		default: '40px'
	},
	minTabWidth: {
		type: String,
		default: '40px'
	},
	// slider-panel props
	header: {
		type: String,
		default: ''
	},
	indicatorValue: {
		type: Number,
		default: 0
	},
	documentViewer: {
		type: Boolean,
		default: false
	}
});

const emit = defineEmits(['update:isOpen']);

const directionMap = {
	left: {
		iconOpen: 'pi-angle-double-left',
		iconClosed: 'pi-angle-double-right'
	},
	right: {
		iconOpen: 'pi-angle-double-right',
		iconClosed: 'pi-angle-double-left'
	}
};

const arrowDirection = computed(() => props.direction);

/* This is for adding a subtle shadow to the header when the panel is scrolled */
const isScrolled = ref(false);
const onScroll = (event: Event) => {
	// Check if the panel is scrolled beyond a certain point
	isScrolled.value = (event.target as HTMLElement).scrollTop > 0;
};
</script>

<style scoped>
aside {
	display: flex;
	flex-direction: column;
	height: 100%;
}

header {
	position: sticky;
	top: 0;
	z-index: 3;
	display: flex;
	align-items: center;
	flex-direction: row-reverse;
	justify-content: space-between;
	padding: var(--gap-2);
	padding-left: var(--gap-4);
	gap: var(--gap-4);
	&.shadow {
		box-shadow: 0 1px 4px 0 rgba(0, 0, 0, 0.1);
	}
}

header:not(.tab) {
	background-color: rgba(255, 255, 255, 0.8);
	backdrop-filter: blur(3px);
}

/* This is a minimalist header with just the collapse button */
header.document-viewer-header {
	position: absolute;
	right: var(--gap-2);
	width: 4rem;
	height: 3.25rem;
	background: var(--surface-0);
}

.content-wrapper {
	flex: 1;
}

/* Makes the slider light grey - apply this class to this component when needed */
.input-config {
	& header {
		background: color-mix(in srgb, var(--surface-100) 80%, transparent 20%);
	}

	& :deep(.content),
	& :deep(.tab) {
		background-color: var(--surface-100);
	}

	/** Override default accordion styles */
	&:deep(.p-accordion-header-link) {
		background-color: var(--surface-100);
	}

	&:deep(.p-accordion-content) {
		background-color: var(--surface-100);
	}
}

/* Don't nest this rule, it makes it easier for the parent to mutate when needed. */
.input-config.open {
	padding-bottom: 4rem;
}

.tab {
	flex-direction: column;
	padding: var(--gap-2);
}

h5 {
	writing-mode: vertical-lr;
}

.p-button.p-button-icon-only.p-button-rounded {
	height: var(--gap-10);
}

.p-badge {
	background-color: var(--surface-200);
	color: var(--text-color-primary);
	font-size: 1rem;
	min-width: 2rem;
	height: 2rem;
	line-height: 2rem;
	font-weight: var(--font-weight);
}
</style>
