<template>
	<div class="critera-of-interest-card">
		<header class="flex">
			<tera-toggleable-input
				:model-value="card.name"
				tag="h6"
				@update:model-value="emit('update', { name: $event })"
				style="left: -0.5rem"
			/>
			<Button class="ml-auto" text icon="pi pi-trash" @click="emit('delete')" />
		</header>
		<p>
			Rank the intervention policies that can
			<Dropdown
				:options="Object.values(RankOption)"
				:model-value="card.rank"
				@update:model-value="emit('update', { rank: $event })"
			/>
			value of
			<Dropdown
				placeholder="Select..."
				:options="variables"
				:model-value="card.selectedVariable"
				@update:model-value="emit('update', { selectedVariable: $event })"
			/>
			<Dropdown
				:options="Object.values(TimepointOption)"
				:model-value="card.timepoint"
				@update:model-value="emit('update', { timepoint: $event })"
			/>
		</p>
		<footer v-if="!card.selectedVariable">Please select a variable.</footer>
	</div>
</template>

<script setup lang="ts">
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import { CriteriaOfInterestCard, RankOption, TimepointOption } from './compare-datasets-operation';

const emit = defineEmits(['delete', 'update']);
defineProps<{
	card: CriteriaOfInterestCard;
	variables: string[];
}>();
</script>

<style scoped>
.critera-of-interest-card {
	background-color: var(--surface-0);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius-medium);
	padding: var(--gap-2) var(--gap-4);
	gap: var(--gap-2);
	display: flex;
	flex-direction: column;
}

p {
	display: flex;
	flex-wrap: wrap;
	gap: var(--gap-1);
	align-items: center;
}

.p-dropdown {
	height: 2rem;
	margin-bottom: var(--gap-1);
}

footer {
	font-size: var(--font-caption);
	color: var(--text-color-secondary);
}
</style>
