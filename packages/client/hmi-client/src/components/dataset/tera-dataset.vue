<template>
	<tera-asset
		show-table-of-contents
		:id="assetId"
		:is-loading="isDatasetLoading"
		:overflow-hidden="selectedTabIndex === 1"
		:selected-tab-index="selectedTabIndex"
		@tab-change="(e) => (selectedTabIndex = e.index)"
		:name="dataset?.name"
		@rename="updateDatasetName"
	>
		<template #edit-buttons>
			<Button
				icon="pi pi-ellipsis-v"
				class="p-button-icon-only p-button-text p-button-rounded"
				@click="toggleOptionsMenu"
			/>
			<ContextMenu ref="optionsMenu" :model="optionsMenuItems" popup :pt="optionsMenuPt" />
			<div class="ml-auto flex gap-2">
				<tera-asset-enrichment :asset-type="AssetType.Dataset" :assetId="assetId" @finished-job="fetchDataset" />
				<Button label="Reset" severity="secondary" outlined @click="reset" />
				<Button label="Save as..." severity="secondary" outlined @click="showSaveModal = true" disabled />
				<Button label="Save" :disabled="isSaved" @click="updateDatasetContent" />
			</div>
		</template>
		<Accordion multiple :active-index="currentActiveIndexes">
			<AccordionTab header="Description">
				<Editor v-model="editorContent" />
			</AccordionTab>
			<AccordionTab header="Column information">
				<tera-column-info
					v-for="(column, index) in columnInformation"
					:key="index"
					:column="column"
					@update-column="updateColumn(index, $event.key, $event.value)"
				/>
			</AccordionTab>
			<AccordionTab header="Data" v-if="!isEmpty(dataset?.fileNames)">
				<tera-progress-spinner v-if="!rawContent" :font-size="2" is-centered />
				<tera-dataset-datatable
					v-else
					:rows="100"
					:raw-content="rawContent"
					:columns="dataset?.columns ?? []"
					:row-count="dataset?.metadata?.['total_rows'] ?? 0"
				/>
			</AccordionTab>
		</Accordion>
	</tera-asset>
</template>

<script setup lang="ts">
import { computed, PropType, ref, watch, onMounted } from 'vue';
import { cloneDeep, isEmpty, isEqual } from 'lodash';
import { getRawContent, getDataset, getDownloadURL, updateDataset } from '@/services/dataset';
import { AssetType, type CsvAsset, type Dataset } from '@/types/Types';
import TeraAsset from '@/components/asset/tera-asset.vue';
import Editor from 'primevue/editor';
import { DatasetSource } from '@/types/Dataset';
import { useProjects } from '@/composables/project';
import ContextMenu from 'primevue/contextmenu';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import { logger } from '@/utils/logger';
import TeraAssetEnrichment from '@/components/widgets/tera-asset-enrichment.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import TeraDatasetDatatable from '@/components/dataset/tera-dataset-datatable.vue';
import TeraColumnInfo from '@/components/dataset/tera-column-info.vue';
import { b64DecodeUnicode, b64EncodeUnicode } from '@/utils/binary';

const props = defineProps({
	assetId: {
		type: String,
		required: true
	},
	source: {
		type: String as PropType<DatasetSource>,
		default: DatasetSource.TERARIUM
	}
});

const currentActiveIndexes = ref([1, 2, 3, 4]);
const dataset = ref<Dataset | null>(null);
const transientDataset = ref<Dataset | null>(null);
const showSaveModal = ref(false);
const rawContent = ref<CsvAsset | null>(null);
const isDatasetLoading = ref(false);
const selectedTabIndex = ref(0);

const optionsMenu = ref();
const optionsMenuPt = {
	submenu: {
		class: 'max-h-30rem overflow-y-scroll'
	}
};
const optionsMenuItems = ref<any[]>([]);

const isSaved = computed(() => isEqual(dataset.value, transientDataset.value));
const columnInformation = computed(
	() =>
		transientDataset.value?.columns?.map((column) => ({
			symbol: column.name, // Uneditable
			description: column.description,
			grounding: column?.grounding,
			dataType: column.dataType,
			// Metadata
			name: column.metadata?.name ?? column.name,
			unit: column.metadata?.unit,
			stats: column.metadata?.column_stats // Uneditable
		})) ?? []
);

function updateColumn(index: number, key: string, value: any) {
	if (!transientDataset.value?.columns?.[index]) return;
	if (key === 'unit' || key === 'name') {
		if (!transientDataset.value.columns[index].metadata) {
			transientDataset.value.columns[index].metadata = {};
		}
		transientDataset.value.columns[index].metadata[key] = value;
	} else if (key === 'concept') {
		if (!transientDataset.value.columns[index]?.grounding?.identifiers) {
			transientDataset.value.columns[index].grounding = { identifiers: {} };
		}
		const curie = (value as string).split(':');
		transientDataset.value.columns[index].grounding.identifiers[curie[0]] = curie[1];
	} else {
		transientDataset.value.columns[index][key] = value;
	}
}

const toggleOptionsMenu = (event) => {
	optionsMenu.value.toggle(event);
};

async function downloadFileFromDataset(fileName: string | null = null) {
	if (!dataset.value?.id) return;

	if (!fileName) fileName = dataset.value?.fileNames?.[0] ?? null;
	if (!fileName) return;

	const presignedURL = (await getDownloadURL(dataset.value.id, fileName)) ?? null;
	if (presignedURL) {
		const link = document.createElement('a');
		link.href = presignedURL.url;
		link.download = fileName;
		document.body.appendChild(link);
		link.click();
		document.body.removeChild(link);
	}
}

async function updateDatasetContent() {
	if (!transientDataset.value) return;
	if (!useProjects().hasEditPermission()) {
		logger.error('You do not have permission to edit this dataset.'); // FIXME: Disable asset editing options if user does not have permission
		return;
	}
	await updateDataset(transientDataset.value);
	logger.info('Saved changes.');
	await useProjects().refresh();
	await fetchDataset();
}

function updateDatasetName(name: string) {
	if (!transientDataset.value) return;
	transientDataset.value.name = name;
	updateDatasetContent();
}

function reset() {
	transientDataset.value = cloneDeep(dataset.value);
}

const fetchDataset = async () => {
	dataset.value = await getDataset(props.assetId);
	reset(); // Prepare transientDataset for editing

	// Remove download options from previous dataset
	optionsMenuItems.value = optionsMenuItems.value.filter((item) => item.label !== 'Download');
	// Add download options to the ellipsis menu
	if (dataset.value?.fileNames) {
		optionsMenuItems.value.unshift({
			label: 'Download',
			icon: 'pi pi-download',
			items: dataset.value.fileNames.map((fileName) => ({
				icon: 'pi pi-file',
				label: fileName,
				command: () => downloadFileFromDataset(fileName)
			}))
		});
	}
	prepareDescription();
};

onMounted(async () => {
	const addProjectMenuItems = (await useProjects().getAllExceptActive()).map((project) => ({
		label: project.name,
		command: async () => {
			const response = await useProjects().addAsset(AssetType.Dataset, props.assetId, project.id);
			if (response) logger.info(`Added asset to ${project.name}`);
		}
	}));
	if (addProjectMenuItems.length === 0) return;
	optionsMenuItems.value.splice(1, 0, {
		icon: 'pi pi-plus',
		label: 'Add to project',
		items: addProjectMenuItems
	});
});

// Whenever assetId changes, fetch dataset with that ID
watch(
	() => props.assetId,
	async () => {
		if (props.assetId) {
			// Empty the dataset and rawContent so previous data is not shown
			dataset.value = null;
			rawContent.value = null;
			isDatasetLoading.value = true;
			await fetchDataset();
			isDatasetLoading.value = false;
			if (dataset.value) rawContent.value = await getRawContent(dataset.value); // Whenever we change the dataset, we need to fetch the rawContent
			prepareDescription();
		}
	},
	{ immediate: true }
);

// Editor for the description
const editorContent = ref('');
let fallbackDescription = '';

const card = computed(() => {
	if (dataset.value?.metadata?.data_card) {
		const cardWithUnknowns = dataset.value.metadata?.data_card;
		const cardWithUnknownsArr = Object.entries(cardWithUnknowns);

		for (let i = 0; i < cardWithUnknownsArr.length; i++) {
			const key = cardWithUnknownsArr[i][0];
			if (cardWithUnknowns[key] === 'UNKNOWN') {
				cardWithUnknowns[key] = null;
			}
		}
		return cardWithUnknowns;
	}
	return null;
});

function prepareDescription() {
	if (!dataset.value) return;

	fallbackDescription = `
	${dataset.value?.fileNames ? `<p>File name(s): ${dataset.value?.fileNames}</p>` : ''}
	<p>${dataset.value?.description}</p>
	<p>${card.value?.DESCRIPTION}</p>
	${card.value?.DATASET_TYPE ? `<p>Dataset type: ${card.value.DATASET_TYPE}</p>` : ''}
	${card.value?.AUTHOR_NAME ? `<p>Author: ${card.value.AUTHOR_NAME}</p>` : ''}
	`;

	editorContent.value = dataset.value?.metadata?.description
		? b64DecodeUnicode(dataset.value.metadata.description)
		: fallbackDescription;
}

watch(editorContent, () => {
	if (!transientDataset.value) return;
	if (editorContent.value !== dataset.value?.description) {
		transientDataset.value = {
			...transientDataset.value,
			metadata: { ...transientDataset.value.metadata, description: b64EncodeUnicode(editorContent.value) }
		};
	}
});
</script>

<style scoped>
.description {
	margin-left: var(--gap-6);

	label + * {
		margin-bottom: var(--gap-4);
		margin-top: var(--gap-2);
	}
}
</style>
