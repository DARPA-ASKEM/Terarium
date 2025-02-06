<template>
	<Button
		label="Enrich metadata"
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
		<p class="mb-2">The AI assistant can enrich the metadata of this {{ assetType }}.</p>
		<p>Select a document or generate the information without additional context.</p>
		<ul>
			<li class="mb-0">
				<label for="no-document">
					<RadioButton inputId="no-document" name="no-document" v-model="selectedResourceId" value="" />
					Generate information without context
				</label>
			</li>
			<li v-for="document in documents" :key="document.id" :class="document.id ? '' : 'mb-3'">
				<label :for="document.id">
					<RadioButton :inputId="document.id" name="document.id" v-model="selectedResourceId" :value="document.id" />
					{{ document.name }}
				</label>
			</li>
		</ul>
		<template #footer>
			<div class="btn-group">
				<Button label="Cancel" severity="secondary" outlined @click="closeDialog" />
				<Button label="Enrich" @click="confirm" />
			</div>
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { getRelatedArtifacts, mapAssetTypeToProvenanceType } from '@/services/provenance';
import {
	AssetType,
	ClientEvent,
	ClientEventType,
	DocumentAsset,
	ProjectAsset,
	ProvenanceType,
	TaskResponse,
	TaskStatus,
	TerariumAsset
} from '@/types/Types';
import { isDocumentAsset } from '@/utils/asset';
import Button from 'primevue/button';
import RadioButton from 'primevue/radiobutton';
import { computed, ref, watch } from 'vue';
import { datasetCard, enrichModelMetadata } from '@/services/goLLM';
import { useProjects } from '@/composables/project';
import TeraModal from '@/components/widgets/tera-modal.vue';
import { useClientEvent } from '@/composables/useClientEvent';

const props = defineProps<{
	assetType: AssetType.Model | AssetType.Dataset;
	assetId: TerariumAsset['id'];
}>();

const isLoading = computed(() => taskId.value !== '');
const isModalVisible = ref(false);

const emit = defineEmits(['finished-job']);
const taskId = ref<string>('');
const enrichEventHandler = async (event: ClientEvent<TaskResponse>) => {
	if (taskId.value !== event.data?.id) return;
	if ([TaskStatus.Success, TaskStatus.Cancelled, TaskStatus.Failed].includes(event.data.status)) {
		taskId.value = '';
		emit('finished-job');
	}
};
useClientEvent(ClientEventType.TaskGollmEnrichModel, enrichEventHandler);
useClientEvent(ClientEventType.TaskGollmEnrichDataset, enrichEventHandler);

const selectedResourceId = ref<string>('');
const relatedDocuments = ref<Array<{ name: string; id: string }>>([]);

const documents = computed<{ name: string; id: string }[]>(() =>
	useProjects()
		.getActiveProjectAssets(AssetType.Document)
		.map((projectAsset: ProjectAsset) => ({
			name: projectAsset.assetName,
			id: projectAsset.assetId
		}))
);

function closeDialog() {
	isModalVisible.value = false;
}

async function confirm() {
	closeDialog();
	await sendForEnrichment();
	getRelatedDocuments();
}

async function sendForEnrichment() {
	if (props.assetId) {
		let taskRes: TaskResponse;
		if (props.assetType === AssetType.Model) {
			// Build enrichment job ids list (profile asset, align model, etc...)
			taskRes = await enrichModelMetadata(props.assetId, selectedResourceId.value, true);
		} else {
			taskRes = await datasetCard(props.assetId, selectedResourceId.value);
		}
		taskId.value = taskRes.id;
	}
}

function getRelatedDocuments() {
	const provenanceType = mapAssetTypeToProvenanceType(props.assetType);
	if (!provenanceType) return;

	getRelatedArtifacts(props.assetId, provenanceType, [ProvenanceType.Document]).then((nodes) => {
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
	gap: var(--gap-2);
	flex-direction: column;
}

ul {
	display: flex;
	flex-direction: column;
	margin-top: var(--gap-4);
	gap: var(--gap-2);
	padding: var(--gap-4) 0;

	li {
		display: flex;
		align-items: center;
	}

	li:first-child {
		margin-bottom: var(--gap-2);
	}

	label {
		cursor: pointer;
	}

	/* Add margin between the input and the copy */
	label > :last-child {
		margin-right: var(--gap-2);
	}
}

.p-dialog aside > * {
	margin-top: var(--gap-4);
}

.p-dialog aside label {
	margin: 0 var(--gap-4) 0 var(--gap-2);
}

.btn-group {
	display: flex;
	align-items: center;
	gap: var(--gap-2);
	margin-left: auto;
}
</style>
