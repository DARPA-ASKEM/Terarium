<template>
	<tera-drilldown-section :is-loading="isLoading">
		<div class="content-container">
			<main>
				<slot />
			</main>
		</div>
		<template #footer v-if="slots.footer">
			<slot name="footer" />
		</template>
	</tera-drilldown-section>
</template>

<script setup lang="ts">
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import { useSlots } from 'vue';

defineProps<{
	isLoading?: boolean;
}>();

const slots = useSlots();
</script>

<style scoped>
.content-container {
	display: flex;
	flex-direction: column;
	gap: var(--gap-1);
	flex-grow: 1;
	overflow: hidden;
}

header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding-bottom: 0.5rem;
}

.content-container > main {
	overflow-y: auto;
	flex-grow: 1;
	padding-top: 0;
	padding-left: 0;
}

main {
	display: flex;
	flex-direction: column;
	flex-grow: 1;
	overflow-y: auto;
}
</style>
