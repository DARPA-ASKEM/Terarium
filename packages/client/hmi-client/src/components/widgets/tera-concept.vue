<template>
	<main>
		<h6>Concept</h6>
		<p v-if="isPreview">{{ names }}</p>
		<AutoComplete
			v-else
			multiple
			optionLabel="name"
			size="small"
			v-model="concepts"
			:suggestions="resultsDKG"
			@complete="searchDKG"
			@item-select="saveConcepts"
			@keyup.enter="saveConcepts"
			@blur="saveConcepts"
		/>
	</main>
</template>

<script setup lang="ts">
import { isEmpty } from 'lodash';
import { computed, ref, watch } from 'vue';
import AutoComplete, { AutoCompleteCompleteEvent } from 'primevue/autocomplete';
import type { DKG, Grounding } from '@/types/Types';
import {
	getDKGFromGroundingModifier,
	getDKGFromGroundingIdentifier,
	parseCurieToIdentifier,
	searchCuriesEntities,
	parseListDKGToGroundingModifiers
} from '@/services/concept';

defineProps<{
	isPreview?: boolean;
}>();

// Define the v-model of the component
const grounding = defineModel<Grounding>();

const resultsDKG = ref<DKG[]>([]);
async function searchDKG(event: AutoCompleteCompleteEvent) {
	resultsDKG.value = await searchCuriesEntities(event.query);
}

const concepts = ref<DKG[]>([]);
function saveConcepts() {
	const newGrounding = { ...(grounding.value as Grounding), identifiers: {}, modifiers: {} };

	if (!isEmpty(concepts.value)) {
		// Split the list of concepts into identifier (first item) and context (rest of the items)
		const [identifierConcept, ...modifiersConcepts] = concepts.value;

		if (identifierConcept) {
			newGrounding.identifiers = parseCurieToIdentifier(identifierConcept.curie);
		}

		if (!isEmpty(modifiersConcepts)) {
			newGrounding.modifiers = parseListDKGToGroundingModifiers(modifiersConcepts);
		}
	}

	grounding.value = newGrounding;
}

const names = computed(() =>
	isEmpty(concepts.value)
		? ''
		: concepts
				.value!.map((dkg) => dkg?.name ?? dkg?.curie)
				.filter(Boolean)
				.join(', ')
);

watch(
	() => grounding.value,
	(newGrounding) => {
		if (newGrounding) {
			// Reset the list of concepts
			concepts.value = [];

			// Add the identifier concept first
			if (!isEmpty(newGrounding.identifiers)) {
				getDKGFromGroundingIdentifier(newGrounding.identifiers).then((dkg) => {
					concepts.value.push(dkg);
				});
			}

			// Then add the context concepts
			if (!isEmpty(newGrounding.modifiers)) {
				getDKGFromGroundingModifier(newGrounding.modifiers).then((dkgList) => {
					concepts.value.push(...dkgList);
				});
			}
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
main {
	align-items: center;
	display: flex;
	gap: var(--gap-2);
	width: 100%;
}

h6 {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
	font-weight: var(--font-weight);
}

:deep(.p-autocomplete) {
	height: 2rem;
	min-width: 10rem;
	width: 100%;

	ul {
		width: 100%;
	}
}

/* To match the other input height */
:deep(.p-autocomplete-multiple-container),
:deep(.p-autocomplete-token) {
	padding: 2px var(--gap-2);
}

:deep(.p-autocomplete-token-label) {
	font-size: var(--font-caption);
}

:deep(.p-autocomplete-input-token input) {
	font-size: var(--font-caption);
	width: 9rem;
}

:deep(.p-autocomplete:not(.p-focus) .p-autocomplete-input-token input) {
	width: 1rem;
}
</style>
