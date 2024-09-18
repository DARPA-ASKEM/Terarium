<template>
	<Button
		label="Enrich metadata with AI assistant"
		icon="pi pi-sparkles"
		:loading="isLoading"
		severity="secondary"
		outlined
		@click="isModalVisible = true"
	/>
	<tera-modal v-if="isModalVisible" @modal-mask-clicked="isModalVisible = false">
		<template #header>
			<h4>Enrich metadata</h4>
		</template>
		<p>
			The AI assistant can enrich the metadata of this {{ assetType }}. Select a document or generate the information
			without additional context.
		</p>
		<ul>
			<li v-for="document in documents" :key="document.id" :class="document.id ? '' : 'mb-3'">
				<RadioButton inputId="document.id" name="document.id" v-model="selectedResourceId" :value="document.id" />
				<label :for="document.id">{{ document.name }}</label>
			</li>
		</ul>
		<template #footer>
			<div class="btn-group">
				<Button label="Cancel" severity="secondary" outlined @click="closeDialog" />
				<Button label="Enrich" :disabled="isDialogDisabled" @click="confirm" />
			</div>
			<!--TODO: Overwrite is how we enrich, handle appending content in another pass-->
			<div class="flex items-center">
				<Checkbox v-model="overwriteContent" inputId="overwriteContent" binary disabled />
				<div class="ml-3">
					<label for="overwriteContent">Overwrite existing content</label>
					<p class="text-subdued">If unselected, new content will be appended</p>
				</div>
			</div>
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { extractPDF, extractVariables, profileDataset } from '@/services/knowledge';
import {
	createProvenance,
	getRelatedArtifacts,
	mapAssetTypeToProvenanceType,
	RelationshipType
} from '@/services/provenance';
import type { ClientEvent, DocumentAsset, ProjectAsset, TaskResponse, TerariumAsset } from '@/types/Types';
import { AssetType, ClientEventType, ProvenanceType, TaskStatus } from '@/types/Types';
import { isDocumentAsset } from '@/utils/asset';
import Button from 'primevue/button';
import RadioButton from 'primevue/radiobutton';
import Checkbox from 'primevue/checkbox';
import { computed, ref, watch } from 'vue';
import { logger } from '@/utils/logger';
import { modelCard } from '@/services/goLLM';
import { useProjects } from '@/composables/project';
import TeraModal from '@/components/widgets/tera-modal.vue';
import { useClientEvent } from '@/composables/useClientEvent';

const props = defineProps<{
	assetType: AssetType;
	assetId: TerariumAsset['id'];
}>();

const emit = defineEmits(['finished-job']);

// Listen for the task completion event for models
useClientEvent(ClientEventType.TaskGollmModelCard, (event: ClientEvent<TaskResponse>) => {
	const { modelId } = event.data?.additionalProperties || {};
	const { status } = event.data || {};

	if (props.assetType !== AssetType.Model || modelId !== props.assetId) {
		return;
	}

	isLoading.value = ![TaskStatus.Success, TaskStatus.Failed, TaskStatus.Cancelled].includes(status);
});

enum DialogType {
	ENRICH,
	EXTRACT
}

const dialogType = ref<DialogType>(DialogType.ENRICH);
const isLoading = ref(false);
const isModalVisible = ref(false);
const overwriteContent = ref(true);

const selectedResourceId = ref<string>('');
const relatedDocuments = ref<Array<{ name: string; id: string }>>([]);

// Disable the dialog action button if no resources are selected
// and the dialog type is not enrichment
const isDialogDisabled = computed(() => {
	if (dialogType.value === DialogType.ENRICH) return false;
	return !selectedResourceId.value;
});

// FIXME: If we are keeping extractions, something like this may help when we add them back in
// const dialogActionCopy = computed(() => {
// 	let result: string = '';
// 	if (dialogType.value === DialogType.ENRICH) {
// 		result = props.assetType === AssetType.Model ? 'Enrich description' : 'Generate descriptions';
// 	} else if (dialogType.value === DialogType.EXTRACT) {
// 		result = 'Extract variables';
// 	}
// 	if (isEmpty(selectedResources.value)) {
// 		return result;
// 	}
// 	return `Use Document to ${result.toLowerCase()}`;
// });

const documents = computed<{ name: string; id: string }[]>(() => [
	{ name: 'No document', id: '' }, // Empty string is falsey
	...useProjects()
		.getActiveProjectAssets(AssetType.Document)
		.map((projectAsset: ProjectAsset) => ({
			name: projectAsset.assetName,
			id: projectAsset.assetId
		}))
]);

function closeDialog() {
	isModalVisible.value = false;
}

const confirm = async () => {
	isLoading.value = true;
	closeDialog();

	// Await for enrichment/extraction so once we call finished-job the refetched dataset will have the new data
	if (dialogType.value === DialogType.ENRICH) {
		await sendForEnrichment();
	} else if (dialogType.value === DialogType.EXTRACT) {
		await sendForExtractions();
	}

	isLoading.value = false;
	emit('finished-job');
	getRelatedDocuments();
};

const sendForEnrichment = async () => {
	// Build enrichment job ids list (profile asset, align model, etc...)
	if (props.assetId && props.assetType === AssetType.Model) {
		await modelCard(props.assetId, selectedResourceId.value);
	} else if (props.assetType === AssetType.Dataset) {
		await profileDataset(props.assetId, selectedResourceId.value);
	}
};

const sendForExtractions = async () => {
	// Dataset extraction
	if (props.assetType === AssetType.Dataset) {
		await extractPDF(selectedResourceId.value);
		await createProvenance({
			relation_type: RelationshipType.EXTRACTED_FROM,
			left: props.assetId!,
			left_type: mapAssetTypeToProvenanceType(props.assetType),
			right: selectedResourceId.value,
			right_type: ProvenanceType.Document
		});

		logger.info('Provenance created after extraction', { showToast: false });
	}

	// Model extraction
	if (props.assetType === AssetType.Model && selectedResourceId) {
		await extractVariables(selectedResourceId.value, [props.assetId]);
	}
};

async function getRelatedDocuments() {
	const provenanceType = mapAssetTypeToProvenanceType(props.assetType);
	if (!provenanceType) return;

	await getRelatedArtifacts(props.assetId, provenanceType, [ProvenanceType.Document]).then((nodes) => {
		const provenanceNodes = nodes ?? [];
		relatedDocuments.value =
			(provenanceNodes.filter((node) => isDocumentAsset(node)) as DocumentAsset[]).map(({ id, name }) => ({
				id: id ?? '',
				name: name ?? ''
			})) ?? [];
	});
}

watch(
	() => props.assetId,
	() => getRelatedDocuments(),
	{ immediate: true }
);
</script>

<style scoped>
main {
	display: flex;
	gap: var(--gap-small);
	flex-direction: column;
}

ul {
	display: flex;
	flex-direction: column;
	margin-top: var(--gap-4);
	gap: var(--gap-2);
	padding: var(--gap-4) 0;

	& > li {
		display: flex;
		align-items: center;
		gap: var(--gap);
	}
}

.no-documents {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.no-documents-img {
	width: 30%;
	padding: 10px;
}

.no-documents-text {
	padding: 5px;
	font-size: var(--font-body-medium);
	font-family: var(--font-family);
	font-weight: 500;
	color: var(--text-color-secondary);
	text-align: left;
}

.p-dialog aside > * {
	margin-top: var(--gap);
}

.p-dialog aside label {
	margin: 0 var(--gap) 0 var(--gap-small);
}

.btn-group {
	display: flex;
	align-items: center;
	gap: var(--gap-2);
	margin-left: auto;
}

.text-subdued {
	color: var(--text-color-subdued);
}
</style>
