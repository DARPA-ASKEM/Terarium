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
			For configuration
			<Dropdown
				placeholder="Select..."
				:options="modelConfigurations"
				:model-value="card.selectedConfigurationId"
				@update:model-value="emit('update', { selectedConfigurationId: $event })"
				option-label="name"
				option-value="id"
			/>
			rank interventions based on the
			<Dropdown
				:options="rankOptions"
				:model-value="card.rank"
				option-label="label"
				option-value="value"
				@update:model-value="emit('update', { rank: $event })"
			/>
			value of
			<Dropdown
				placeholder="Select..."
				:options="variables"
				:model-value="card.selectedVariable"
				@update:model-value="emit('update', { selectedVariable: $event })"
			/>
			at
			<Dropdown
				:options="timepointOptions"
				:model-value="card.timepoint"
				option-label="label"
				option-value="value"
				@update:model-value="emit('update', { timepoint: $event })"
			/>
			timepoint.
		</p>
		<footer v-if="!card.selectedConfigurationId || !card.selectedVariable">
			Please select a configuration and a variable.
		</footer>
	</div>
</template>

<script setup lang="ts">
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import { ModelConfiguration } from '@/types/Types';
import { CriteriaOfInterestCard, RankOption, TimepointOption } from './compare-datasets-operation';

const timepointOptions = [
	{ label: 'the last', value: TimepointOption.LAST },
	{ label: 'the first', value: TimepointOption.FIRST }
];

const rankOptions = [
	{ label: 'minimum', value: RankOption.MINIMUM },
	{ label: 'maximum', value: RankOption.MAXIMUM }
];

const emit = defineEmits(['delete', 'update']);
defineProps<{
	card: CriteriaOfInterestCard;
	modelConfigurations: ModelConfiguration[];
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

.p-dropdown {
	height: 2rem;
	margin-bottom: var(--gap-1);
}

footer {
	font-size: var(--font-caption);
	color: var(--text-color-secondary);
}
</style>
