<template>
	<section class="flex flex-column">
		<span class="flex align-items-center gap-3">
			<h6>{{ id }}</h6>
			<span v-if="!isTimePart" class="name">
				<template v-if="featureConfig.isPreview">{{ nameText }}</template>
				<tera-input-text
					v-else
					placeholder="Add a name"
					v-model="nameText"
					@change="$emit('update-item', { key: 'name', value: nameText })"
				/>
			</span>
			<span class="unit" :class="{ time: isTimePart }">
				<template v-if="input || output">
					<span><label>Input:</label> {{ input }}</span>
					<span><label>Output:</label> {{ output }}</span>
				</template>
				<!--amr_to_mmt doesn't like unit expressions with spaces, removing them here before they are saved to the amr-->
				<template v-else-if="showUnit">
					<template v-if="featureConfig.isPreview"><label>Unit</label>{{ unitExpression }}</template>
					<!-- we use a dropdown for units with time semantic-->
					<Dropdown
						v-else-if="isTimePart"
						v-model="unitExpression"
						placeholder="Add a time unit"
						option-label="label"
						option-value="value"
						:options="[
							{ label: 'Days', value: CalendarDateType.DATE },
							{ label: 'Months', value: CalendarDateType.MONTH },
							{ label: 'Years', value: CalendarDateType.YEAR }
						]"
						@change="$emit('update-item', { key: 'unitExpression', value: unitExpression })"
					/>
					<tera-input-text
						v-else
						label="Unit"
						placeholder="Add a unit"
						:characters-to-reject="[' ']"
						v-model="unitExpression"
						@change="$emit('update-item', { key: 'unitExpression', value: unitExpression })"
					/>
				</template>
			</span>

			<span v-if="!featureConfig.isPreview && !isTimePart" class="flex ml-auto gap-3">
				<!-- Three states of description buttons: Hide / Show / Add description -->
				<Button
					v-if="(descriptionText && showDescription) || (!descriptionText && showDescription)"
					text
					size="small"
					label="Hide description"
					@click="showDescription = false"
				/>
				<Button
					v-else-if="!showDescription"
					text
					size="small"
					:label="descriptionText ? 'Show description' : 'Add description'"
					@click="showDescription = true"
				/>
				<span v-if="showConcept" class="concept">
					<label>Concept</label>
					<template v-if="featureConfig.isPreview">{{ query }}</template>
					<AutoComplete
						v-else
						size="small"
						placeholder="Search concepts"
						v-model="query"
						:suggestions="results"
						optionLabel="name"
						@complete="async () => (results = await searchCuriesEntities(query))"
						@item-select="$emit('update-item', { key: 'concept', value: $event.value.curie })"
						@keyup.enter="applyValidConcept"
						@blur="applyValidConcept"
					/>
				</span>
			</span>
		</span>
		<katex-element
			v-if="expression"
			class="expression"
			:expression="stringToLatexExpression(expression)"
			:throw-on-error="false"
		/>
		<span v-if="!isTimePart" class="description" :class="{ 'mt-1': showDescription }">
			<template v-if="featureConfig.isPreview">{{ descriptionText }}</template>
			<tera-input-text
				v-else-if="showDescription"
				placeholder="Add a description"
				v-model="descriptionText"
				@change="$emit('update-item', { key: 'description', value: descriptionText })"
			/>
		</span>
	</section>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import AutoComplete from 'primevue/autocomplete';
import Button from 'primevue/button';
import { stringToLatexExpression } from '@/services/model';
import type { DKG } from '@/types/Types';
import { getCurieFromGroundingIdentifier, getNameOfCurieCached, searchCuriesEntities } from '@/services/concept';
import type { FeatureConfig } from '@/types/common';
import Dropdown from 'primevue/dropdown';
import { CalendarDateType } from '@/types/common';
import { PartType } from '@/model-representation/service';

const props = defineProps<{
	description?: string;
	name?: string;
	unitExpression?: string;
	templateId?: string;
	id?: string;
	grounding?: any;
	expression?: string;
	input?: any;
	output?: any;
	featureConfig: FeatureConfig;
	partType: PartType;
}>();

const emit = defineEmits(['update-item']);

const nameText = ref(props.name);
const unitExpression = ref(props.unitExpression);
const descriptionText = ref(props.description);
const showDescription = ref(!!descriptionText.value);

const query = ref('');
const results = ref<DKG[]>([]);

// If we are in preview mode and there is no content, show nothing
const showUnit = computed(
	() => !(props.featureConfig.isPreview && !unitExpression.value) && props.partType !== PartType.TRANSITION
);
const showConcept = computed(() => !(props.featureConfig.isPreview && !query.value));

const isTimePart = props.partType === PartType.TIME;

// Used if an option isn't selected from the Autocomplete suggestions but is typed in regularly
function applyValidConcept() {
	// Allows to empty the concept
	if (query.value === '') {
		emit('update-item', { key: 'concept', value: '' });
	}
	// If what was typed was one of the results then choose that result
	else {
		const concept = results.value.find((result) => result.name === query.value);
		if (concept) {
			emit('update-item', { key: 'concept', value: concept.curie });
		}
	}
}

watch(
	() => props.grounding?.identifiers,
	async (identifiers) => {
		if (identifiers) query.value = await getNameOfCurieCached(getCurieFromGroundingIdentifier(identifiers));
	},
	{ immediate: true }
);

watch(
	() => props.unitExpression,
	(newUnitExpression) => {
		unitExpression.value = newUnitExpression;
	}
);

watch(
	() => props.name,
	(newName) => {
		nameText.value = newName;
	}
);

watch(
	() => props.description,
	(newDescription) => {
		showDescription.value = !!newDescription;
		descriptionText.value = newDescription;
	}
);
</script>

<style scoped>
section {
	font-size: var(--font-caption);
}

label,
.description {
	color: var(--text-color-subdued);
}

h6::after {
	content: '|';
	color: var(--text-color-light);
	margin-left: var(--gap-2);
}

.unit,
.concept {
	display: flex;
	align-items: center;
	gap: var(--gap-1);
}

.unit:not(.time) {
	overflow: auto;
}

.expression {
	padding-top: var(--gap-1);
	align-content: center;
	min-height: 2rem;
	max-height: 12rem;
	overflow: auto;
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
