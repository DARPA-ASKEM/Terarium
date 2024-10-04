<template>
	<tera-progress-spinner v-if="fetchingDocument" is-centered :font-size="2" />
	<main v-else>
		<template v-if="document">
			<h6>
				<span class="truncate-after-three-lines">{{ documentName }}</span>
			</h6>
			<tera-operator-placeholder :node="node" />
			<Button label="Open" @click="emit('open-drilldown')" severity="secondary" outlined />
		</template>
		<template v-else>
			<Dropdown
				class="w-full p-dropdown-sm"
				:options="documents"
				option-label="assetName"
				placeholder="Select a document"
				@update:model-value="onDocumentChange"
			/>
			<tera-operator-placeholder :node="node" />
		</template>
	</main>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { cloneDeep, isEmpty } from 'lodash';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import type { DocumentAsset, ProjectAsset } from '@/types/Types';
import { AssetType } from '@/types/Types';
import { useProjects } from '@/composables/project';
import { getDocumentAsset } from '@/services/document-assets';
import { WorkflowNode } from '@/types/workflow';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import { DocumentOperationState } from './document-operation';

const emit = defineEmits(['open-drilldown', 'update-state', 'append-output']);
const props = defineProps<{
	node: WorkflowNode<DocumentOperationState>;
}>();

const documents = useProjects().getActiveProjectAssets(AssetType.Document);
const document = ref<DocumentAsset | null>(null);
const fetchingDocument = ref(false);
const documentName = ref<DocumentAsset['name']>('');

onMounted(async () => {
	if (props.node.state.documentId) {
		// Quick get the name from the project
		documentName.value = useProjects().getAssetName(props.node.state.documentId) || '';

		// Fetch the document
		fetchingDocument.value = true;
		document.value = await getDocumentAsset(props.node.state.documentId);

		// If the name is different, update the name
		if (document.value && documentName.value !== document.value.name && !isEmpty(document.value.name)) {
			documentName.value = document.value.name;
		}
	}
	fetchingDocument.value = false;
});

async function onDocumentChange(chosenProjectDocument: ProjectAsset) {
	if (chosenProjectDocument?.assetId) {
		fetchingDocument.value = true;
		document.value = await getDocumentAsset(chosenProjectDocument.assetId);
		documentName.value = document.value?.name;
		fetchingDocument.value = false;
	}
}

watch(
	() => document.value,
	async () => {
		if (document.value?.id) {
			const state = cloneDeep(props.node.state);
			state.documentId = document.value.id;
			emit('update-state', state);

			const outputs = props.node.outputs;
			const documentPort = outputs.find((port) => port.type === 'documentId');

			if (!documentPort || !documentPort.value) {
				emit('append-output', {
					type: 'documentId',
					label: documentName.value,
					value: [
						{
							documentId: document.value.id
						}
					]
				});
			}
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
/* Supported by Chromium, Safari, Webkit, Edge and others. Not supported by IE and Opera Mini */
.truncate-after-three-lines {
	display: -webkit-box;
	-webkit-line-clamp: 3;
	-webkit-box-orient: vertical;
	overflow: hidden;
}
</style>
