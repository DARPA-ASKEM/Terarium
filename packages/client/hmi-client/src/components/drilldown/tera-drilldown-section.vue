<template>
	<section>
		<header v-if="hasHeaderSlots" :class="{ shadow: hasScrolled }">
			<div>
				<slot name="header-controls-left" />
			</div>
			<div>
				<slot name="header-controls-right" />
			</div>
		</header>
		<main ref="main" @scroll="handleScroll">
			<slot v-if="!isLoading || showSlotWhileLoading" />
			<tera-progress-spinner v-if="isLoading" :font-size="2" is-centered>
				<span v-if="!loadingMessage">Processing... </span>
				<span v-if="loadingProgress">{{ loadingProgress }}% </span>
				<span v-if="displayMessage">{{ loadingMessage }}</span>
			</tera-progress-spinner>
			<div v-else-if="isBlank" class="empty-state">
				<Vue3Lottie :animationData="EmptySeed" :height="150" :width="150" loop autoplay />
				<p>{{ blankMessage }}</p>
			</div>
		</main>
		<footer v-if="slots.footer">
			<slot name="footer" />
		</footer>
	</section>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, useSlots } from 'vue';
import { Vue3Lottie } from 'vue3-lottie';
import EmptySeed from '@/assets/images/lottie-empty-seed.json';
import TeraProgressSpinner from '../widgets/tera-progress-spinner.vue';

const props = defineProps<{
	isLoading?: boolean;
	showSlotWhileLoading?: boolean;
	isBlank?: boolean;
	blankMessage?: string;
	loadingMessage?: string;
	loadingProgress?: number;
}>();

const slots = useSlots();
const hasHeaderSlots = computed(() => !!slots['header-controls-left'] || !!slots['header-controls-right']);
const displayMessage = computed(() => (props.loadingProgress || props.loadingProgress === 0) && props.loadingMessage);

/* This is for adding a shadow to the header if user has scrolled */
const main = ref<HTMLElement | null>(null);
const hasScrolled = ref(false);
const handleScroll = () => {
	if (main.value) {
		hasScrolled.value = main.value.scrollTop > 20; // Change 20 to whatever threshold you deem appropriate
	}
};

onMounted(() => {
	if (main.value) {
		main.value.addEventListener('scroll', handleScroll);
	}
});

onUnmounted(() => {
	if (main.value) {
		main.value.removeEventListener('scroll', handleScroll);
	}
});
</script>

<style scoped>
footer {
	display: flex;
	justify-content: flex-end;
	gap: var(--gap-2);
}

header {
	display: inline-flex;
	justify-content: space-between;
	padding: var(--gap-3) 0;
	gap: var(--gap-3);
}

header > div {
	display: inline-flex;
	gap: var(--gap-1);
	align-items: center;

	&:first-child {
		flex: 1;
	}
}
header.shadow {
	box-shadow:
		0px 10px 6px -11px var(--surface-500),
		0px 9px 9px -11px var(--surface-400);
}

section {
	display: flex;
	flex-direction: column;
	gap: var(--gap-1);
	overflow: hidden;
	height: 100%;
}
main {
	display: flex;
	flex-direction: column;
	flex-grow: 1;
	overflow-y: auto;

	.empty-state {
		width: 100%;
		height: 100%;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: var(--gap-4);
		text-align: center;
		pointer-events: none;
	}
}

.notebook-section {
	background-color: var(--surface-disabled);
	border-right: 1px solid var(--surface-border);
	padding: var(--gap-4);
}
</style>
