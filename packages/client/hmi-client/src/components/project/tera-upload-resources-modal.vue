<template>
	<tera-modal v-if="visible" @modal-mask-clicked="() => emit('close')">
		<template #header>
			<h4>Upload resources</h4>
		</template>
		<template #default>
			<section class="main-section">
				<section>
					<label class="subheader">Add documents, models or datasets to your project here.</label>
					<div class="supported-resources py-3">
						<div><i class="pi pi-file" /><span>Documents</span><span>(PDF, md, txt)</span></div>
						<div><i class="pi pi-share-alt" /><span>Models</span><span>(AMR, sbml, vensim, stella)</span></div>
						<div><dataset-icon /><span>Datasets</span><span>(csv, netcdf)</span></div>
					</div>
					<tera-drag-and-drop-importer
						ref="dragAndDropImporter"
						:accept-types="[
							AcceptedTypes.PDF,
							AcceptedTypes.CSV,
							AcceptedTypes.TXT,
							AcceptedTypes.MD,
							AcceptedTypes.PY,
							AcceptedTypes.R,
							AcceptedTypes.JL,
							AcceptedTypes.NC,
							AcceptedTypes.JSON,
							AcceptedTypes.XML,
							AcceptedTypes.SBML,
							AcceptedTypes.MDL,
							AcceptedTypes.XMILE,
							AcceptedTypes.ITMX,
							AcceptedTypes.STMX,
							AcceptedTypes.MODELCONFIG
						]"
						:accept-extensions="[
							AcceptedExtensions.PDF,
							AcceptedExtensions.CSV,
							AcceptedExtensions.TXT,
							AcceptedExtensions.MD,
							AcceptedExtensions.PY,
							AcceptedExtensions.R,
							AcceptedExtensions.JL,
							AcceptedExtensions.NC,
							AcceptedExtensions.JSON,
							AcceptedExtensions.XML,
							AcceptedExtensions.SBML,
							AcceptedExtensions.MDL,
							AcceptedExtensions.XMILE,
							AcceptedExtensions.ITMX,
							AcceptedExtensions.STMX,
							AcceptedExtensions.MODELCONFIG
						]"
						:import-action="processFiles"
						:progress="progress"
						@import-completed="importCompleted"
						@imported-files-updated="(value) => (importedFiles = value)"
					></tera-drag-and-drop-importer>
				</section>
			</section>
		</template>
		<template #footer>
			<Button label="Upload" class="p-button-primary" @click="upload" />
			<Button label="Cancel" class="p-button-secondary" outlined @click="() => emit('close')" />
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import TeraModal from '@/components/widgets/tera-modal.vue';
import Button from 'primevue/button';
import { AcceptedExtensions, AcceptedTypes } from '@/types/common';
import { uploadCodeToProject } from '@/services/code';
import { useProjects } from '@/composables/project';
import type { Dataset, DocumentAsset } from '@/types/Types';
import { AssetType, ProvenanceType } from '@/types/Types';
import { uploadDocumentAssetToProject } from '@/services/document-assets';
import { createNewDatasetFromFile } from '@/services/dataset';
import useAuthStore from '@/stores/auth';
import { ref, watch, onMounted, nextTick } from 'vue';
import TeraDragAndDropImporter from '@/components/extracting/tera-drag-n-drop-importer.vue';
import { useToastService } from '@/services/toast';
import { extractPDF } from '@/services/knowledge';
import DatasetIcon from '@/assets/svg/icons/dataset.svg?component';
import { uploadArtifactToProject } from '@/services/artifact';
import { createModel, createModelAndModelConfig, processAndAddModelToProject, validateAMRFile } from '@/services/model';
import { createProvenance, RelationshipType } from '@/services/provenance';

const props = defineProps<{
	visible: boolean;
	files?: File[]; // For passing files dragged onto the resources panel into the uploader
}>();
const emit = defineEmits(['close']);

const progress = ref(0);
const results = ref<{ id: string; name: string; assetType: AssetType }[] | null>(null);
const urlToUpload = ref('');
const isImportGithubFileModalVisible = ref(false);
const importedFiles = ref<File[]>([]);

// Handle any files that are dragged onto the resources panel
const dragAndDropImporter = ref();
const pendingFiles = ref<File[] | null>(null);

// When files arrive, store them if we can't process them yet
watch(
	() => props.files,
	async (newFiles) => {
		if (newFiles?.length) {
			pendingFiles.value = newFiles;
			await nextTick();
			tryAddFiles();
		}
	}
);

// Try to add files if we have both files and a mounted importer
function tryAddFiles() {
	if (pendingFiles.value?.length && dragAndDropImporter.value) {
		dragAndDropImporter.value.addFiles(pendingFiles.value);
		pendingFiles.value = null;
	}
}

// Once mounted, try to process any pending files
onMounted(() => {
	tryAddFiles();
});

async function processFiles(files: File[], description: string) {
	return files.map(async (file) => {
		switch (file.name.split('.').pop()) {
			case AcceptedExtensions.CSV:
			case AcceptedExtensions.NC:
				return processDataset(file, description);
			case AcceptedExtensions.PDF:
			case AcceptedExtensions.TXT:
			case AcceptedExtensions.MD:
				return processDocument(file);
			case AcceptedExtensions.PY:
			case AcceptedExtensions.R:
			case AcceptedExtensions.JL:
				return processCode(file);
			case AcceptedExtensions.JSON:
				return processAMRJson(file);
			case AcceptedExtensions.XML:
			case AcceptedExtensions.SBML:
			case AcceptedExtensions.MDL:
			case AcceptedExtensions.XMILE:
			case AcceptedExtensions.ITMX:
			case AcceptedExtensions.STMX:
				return processModel(file);
			case AcceptedExtensions.MODELCONFIG:
				return processModelConfigAndModel(file);
			default:
				return { id: '', assetType: '' };
		}
	});
}

/**
 * Process a python, R or Julia file into a code asset
 * @param file
 */
async function processCode(file: File) {
	const newCode = await uploadCodeToProject(file, progress);
	return { id: newCode?.id ?? '', assetType: AssetType.Code };
}

/**
 * Process a pdf, txt, md file into an artifact
 * @param file
 */
async function processDocument(file: File) {
	// This is pdf, txt, md files
	const document: DocumentAsset | null = await uploadDocumentAssetToProject(
		file,
		useAuthStore().user?.id ?? '',
		'',
		progress
	);
	return { id: document?.id ?? '', assetType: AssetType.Document, name: file.name };
}

/**
 * Process a csv file into a dataset
 * @param file
 * @param description
 */
async function processDataset(file: File, description: string) {
	const addedDataset: Dataset | null = await createNewDatasetFromFile(
		progress,
		file,
		useAuthStore().user?.id ?? '',
		description
	);
	return { id: addedDataset?.id ?? '', assetType: AssetType.Dataset };
}

/*
 * Process an AMR file into a model asset
 * @param file
 */
async function processAMRJson(file: File) {
	// Check if the file is an AMR file
	const amr = await validateAMRFile(file);
	if (amr) {
		const model = await createModel(amr);
		return { id: model?.id ?? '', assetType: AssetType.Model };
	}
	// Ignore none AMR json files for now
	return { id: '', assetType: '' };
}

/**
 * Process a model file into a model asset
 * @param file
 */
async function processModel(file: File) {
	// Upload file as an artifact, create an empty model, and link them
	const artifact = await uploadArtifactToProject(file, useAuthStore().user?.id ?? '', '', progress);
	if (!artifact) return { id: '', assetType: '' };
	const newModelId = await processAndAddModelToProject(artifact);
	await createProvenance({
		relation_type: RelationshipType.EXTRACTED_FROM,
		left: newModelId ?? '',
		left_type: ProvenanceType.Model,
		right: artifact.id ?? '',
		right_type: ProvenanceType.Artifact
	});
	return { id: newModelId ?? '', assetType: AssetType.Model };
}

async function processModelConfigAndModel(file: File) {
	const model = await createModelAndModelConfig(file, progress);
	return { id: model?.id ?? '', assetType: AssetType.Model };
}

function importCompleted(newResults: { id: string; name: string; assetType: AssetType }[] | null) {
	results.value = newResults?.filter((r) => r.id !== '') ?? [];
}

async function upload() {
	if (results.value) {
		const createAssetsPromises = (results.value ?? []).map(({ id, assetType }) =>
			useProjects().addAsset(assetType, id)
		);
		const createdAssets = await Promise.all(createAssetsPromises);
		createdAssets.forEach((_, index) => {
			const { name, id } = (results.value ?? [])[index];
			if (name && name.toLowerCase().endsWith('.pdf')) {
				extractPDF(id);
			}
		});
		emit('close');
		const resourceMsg = createdAssets.length > 1 ? 'resources were' : 'resource was';
		useToastService().success('Success!', `${createdAssets.length} ${resourceMsg} successfully added to this project`);
		importedFiles.value = [];
		results.value = null;
	} else if (urlToUpload.value) {
		isImportGithubFileModalVisible.value = true;
	} else {
		useToastService().error('Error!', 'No files were selected');
	}
}
</script>

<style scoped>
.main-section {
	display: flex;
	flex-direction: column;
	gap: 1rem;
}

.main-section section {
	display: flex;
	flex-direction: column;
	width: 65vw;
	min-width: 32rem;
	max-width: 48rem;
}

.supported-resources {
	display: flex;
	justify-content: space-between;
	margin-bottom: var(--gap-1);

	div {
		display: flex;
		align-items: end;
		gap: var(--gap-1);
		font-size: 0.75rem;
	}
	span:first-of-type {
		font-weight: 700;
	}
	span:last-of-type {
		color: #667985;
	}
}

:deep(.upload-from-github-url) {
	margin-bottom: 0;
}
</style>
