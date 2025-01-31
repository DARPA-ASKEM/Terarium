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
			<span class="concept">
				<label>Concept</label>
				<AutoComplete
					size="small"
					placeholder="Search concepts"
					v-model="query"
					:suggestions="results"
					optionLabel="name"
					@complete="async () => (results = await searchCuriesEntities(query))"
					@item-select="
						$emit('update-column', { key: 'concept', value: { curie: $event.value.curie, name: $event.value.name } })
					"
					@keyup.enter="applyValidConcept"
					@blur="applyValidConcept"
				/>
			</span>
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
import { ref, watch } from 'vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraBoxplot from '@/components/widgets/tera-boxplot.vue';
import AutoComplete from 'primevue/autocomplete';
import Dropdown from 'primevue/dropdown';
import { type DKG, ColumnType, type Grounding } from '@/types/Types';
import { getCurieFromGroundingIdentifier, getNameOfCurieCached, searchCuriesEntities } from '@/services/concept';

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

const query = ref('');
const results = ref<DKG[]>([]);

// Used if an option isn't selected from the Autocomplete suggestions but is typed in regularly
function applyValidConcept() {
	// Allows to empty the concept
	if (query.value === '') {
		emit('update-column', { key: 'concept', value: { curie: '', name: '' } });
	}
	// If what was typed was one of the results then choose that result
	else {
		const concept = results.value.find((result) => result.name === query.value);
		if (concept) {
			emit('update-column', { key: 'concept', value: { curie: concept.curie, name: concept.name } });
		}
	}
}

watch(
	() => props.column.grounding?.identifiers,
	async (identifiers) => {
		if (identifiers) query.value = await getNameOfCurieCached(getCurieFromGroundingIdentifier(identifiers));
	},
	{ immediate: true }
);
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

.expression {
	grid-area: expression;
	font-size: var(--font-body-small);
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
:deep(.p-autocomplete-input.p-inputtext) {
	border-radius: var(--border-radius);
}

:deep(.unit .tera-input > main > input) {
	height: 1.25rem;
	font-size: var(--font-caption);
}
:deep(.p-autocomplete-input) {
	height: 2rem;
	font-size: var(--font-caption);
}
</style>
