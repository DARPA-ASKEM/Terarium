<template>
	<div class="column-info-card">
		<section class="entries">
			<span class="name">
				<h6>{{ column.symbol }}</h6>
				<tera-input-text
					placeholder="Add a name"
					:model-value="column.name ?? ''"
					@update:model-value="$emit('update-column', { key: 'name', value: $event })"
				/>
			</span>
			<span class="unit">
				<tera-input-text
					label="Unit"
					placeholder="Add a unit"
					:characters-to-reject="[' ']"
					:model-value="column.unit ?? ''"
					@update:model-value="$emit('update-column', { key: 'unit', value: $event })"
				/>
			</span>
			<span class="data-type">
				<label>Data type</label>
				<Dropdown
					placeholder="Select a data type"
					:model-value="column.dataType ?? ''"
					@update:model-value="$emit('update-column', { key: 'dataType', value: $event })"
					:options="Object.values(ColumnType)"
				/>
			</span>
			<tera-concept class="concept" v-model="grounding" />
			<span class="description">
				<tera-input-text
					placeholder="Add a description"
					:model-value="column.description ?? ''"
					@update:model-value="$emit('update-column', { key: 'description', value: $event })"
				/>
			</span>
		</section>
		<tera-boxplot class="flex-1" v-if="column.stats" :stats="column.stats" />
	</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import TeraConcept from '@/components/widgets/tera-concept.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraBoxplot from '@/components/widgets/tera-boxplot.vue';
import Dropdown from 'primevue/dropdown';
import { ColumnType, type Grounding } from '@/types/Types';

type ColumnInfo = {
	symbol?: string;
	dataType?: ColumnType;
	description?: string;
	grounding?: Grounding;
	// Metadata
	unit?: string;
	name?: string;
	stats?: any;
};

const props = defineProps<{
	column: ColumnInfo;
}>();

const emit = defineEmits(['update-column']);

const grounding = computed({
	get: () => props.column.grounding,
	set: (newGrounding) => emit('update-column', { key: 'grounding', value: newGrounding })
});
</script>

<style scoped>
.column-info-card {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	background: var(--surface-0);
	padding: var(--gap-3) var(--gap-4);
	border-left: 4px solid var(--surface-300);
	margin-bottom: var(--gap-2);
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
	transition: all 0.15s;
}
.column-info-card:hover {
	background-color: var(--surface-highlight);
	border-left-color: var(--primary-color);
}
section.entries {
	display: grid;
	grid-template-areas:
		'name name unit data-type concept'
		'expression expression expression expression expression '
		'description description description description description';
	grid-template-columns: auto max-content max-content max-content max-content;
	grid-auto-flow: dense;
	gap: var(--gap-1) var(--gap-2);
	align-items: center;
	font-size: var(--font-caption);
	overflow: auto;

	& > *:empty {
		display: none;
	}
}

label {
	color: var(--text-color-subdued);
	text-wrap: nowrap;
}

h6 {
	grid-area: symbol;
	justify-self: center;
	&::after {
		content: '|';
		color: var(--text-color-light);
		margin-left: var(--gap-2);
	}
}

.name {
	grid-area: name;
	display: flex;
	align-items: center;
	gap: var(--gap-2);
}

.description {
	grid-area: description;
	color: var(--text-color-subdued);
}

.unit {
	grid-area: unit;
	margin-right: var(--gap-6);
}

.data-type {
	grid-area: data-type;
	margin-right: var(--gap-6);
}

.concept {
	grid-area: concept;
}

.unit,
.data-type,
.concept {
	display: flex;
	align-items: center;
	gap: var(--gap-1);
}

:deep(.p-dropdown > span) {
	height: 1.75rem;
	font-size: var(--font-caption);
}

:deep(.unit .tera-input > main > input) {
	height: 1.25rem;
	font-size: var(--font-caption);
}
</style>
