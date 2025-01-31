<template>
	<tera-scenario-template
		ref="blankTemplate"
		:header="header"
		:scenario-instance="scenario"
		@save-workflow="emit('save-workflow')"
	>
		<template #inputs>
			<label>Select a document</label>
			<div class="flex" v-for="(document, i) in scenario.documentSpecs" :key="document.id">
				<Dropdown
					class="flex-1 my-1"
					:model-value="document.id"
					:options="documents"
					option-label="assetName"
					option-value="assetId"
					placeholder="Select a document"
					@update:model-value="scenario.setDocumentSpec($event, i)"
				/>
				<Button
					v-if="scenario.documentSpecs.length > 1"
					text
					icon="pi pi-trash"
					size="small"
					@click="scenario.deleteDocumentSpec(i)"
				/>
			</div>
			<div>
				<Button
					class="py-2 mb-3"
					size="small"
					text
					icon="pi pi-plus"
					label="Add more documents"
					@click="scenario.addDocumentSpec()"
				/>
			</div>
			<template v-if="scenario.documentSpecs.length > 1">
				<label>Model selection criteria (optional)</label>
				<Textarea
					:model-value="scenario.modelSelectionCriteria"
					placeholder="What is your goal (optional)"
					@update:model-value="scenario.setModelSelectionCriteria($event)"
					:autoResize="true"
				/>
			</template>
		</template>

		<template #outputs>
			<img :src="modelFromLiteratureImg" alt="Reproduce models from literature template" />
		</template>
	</tera-scenario-template>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useProjects } from '@/composables/project';
import { AssetType } from '@/types/Types';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import Textarea from 'primevue/textarea';
import modelFromLiteratureImg from '@/assets/svg/template-images/reproduce-model-from-literature-thumbnail.svg';
import { ScenarioHeader } from '../base-scenario';
import { ModelFromLiteratureScenario } from './model-from-literature-scenario';
import TeraScenarioTemplate from '../tera-scenario-template.vue';

const header: ScenarioHeader = Object.freeze({
	title: 'Reproduce models from literature template',
	question: 'Reproduce models from literature and compare them to select the best starting point.',
	description:
		'Create models from extracted equations. Configure them using extracted values. Simulate to reproduce results. If multiple models are created, compare them.',
	examples: [
		'Can I reproduce results from this paper?',
		'From recent papers, which is the best model to explore COVID transmission to deer populations?'
	]
});
defineProps<{
	scenario: ModelFromLiteratureScenario;
}>();
const emit = defineEmits(['save-workflow']);

const documents = computed(() => useProjects().getActiveProjectAssets(AssetType.Document));
</script>
