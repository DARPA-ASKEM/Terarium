<template>
	<tera-asset
		v-bind="$attrs"
		:id="assetId"
		:name="dataset?.name"
		:feature-config="featureConfig"
		:is-naming-asset="isRenaming"
		:is-loading="isDatasetLoading"
		:overflow-hidden="selectedTabIndex === 1"
		:selected-tab-index="selectedTabIndex"
		show-table-of-contents
		@close-preview="emit('close-preview')"
		@tab-change="(e) => (selectedTabIndex = e.index)"
	>
		<template #name-input>
			<tera-input-text
				v-if="isRenaming"
				v-model.lazy="newName"
				placeholder="Dataset name"
				@keyup.enter="updateDatasetName"
				@keyup.esc="updateDatasetName"
				auto-focus
			/>
			<div v-if="isRenaming" class="flex flex-nowrap ml-1 mr-3">
				<Button icon="pi pi-check" rounded text @click="updateDatasetName" />
			</div>
		</template>
		<template #edit-buttons v-if="!featureConfig.isPreview">
			<Button
				icon="pi pi-ellipsis-v"
				class="p-button-icon-only p-button-text p-button-rounded"
				@click="toggleOptionsMenu"
			/>
			<ContextMenu ref="optionsMenu" :model="optionsMenuItems" popup :pt="optionsMenuPt" />
			<div class="btn-group">
				<tera-asset-enrichment :asset-type="AssetType.Dataset" :assetId="assetId" @finished-job="fetchDataset" />
				<Button label="Reset" severity="secondary" outlined @click="reset" />
				<Button label="Save as..." severity="secondary" outlined @click="showSaveModal = true" />
				<Button label="Save" :disabled="isSaved" @click="updateDatasetContent" />
			</div>
		</template>
		<section>
			<div class="pl-4 ml-3 pt-1 pb-3">
				<p>
					<span class="font-semibold inline-block w-10rem">Dataset ID</span>
					<code class="inline">{{ datasetInfo.id }}</code>
				</p>
				<p>
					<span class="font-semibold inline-block w-10rem">Dataset filenames</span>
					{{ datasetInfo.fileNames }}
				</p>
			</div>
			<Accordion multiple :active-index="[0, 1, 2, 3, 4]">
				<AccordionTab header="Description">
					<section class="description">
						<tera-show-more-text :text="description" :lines="5" />
						<template v-if="datasetType">
							<label class="p-text-secondary">Dataset type</label>
							<p>{{ datasetType }}</p>
						</template>
						<template v-if="author">
							<label class="p-text-secondary">Author</label>
							<p>{{ author }}</p>
						</template>
					</section>
				</AccordionTab>
				<!-- <AccordionTab header="Charts">TBD</AccordionTab> -->
				<AccordionTab header="Column information" v-if="!isClimateData && !isClimateSubset">
					<ul>
						<li v-for="(column, index) in columnInformation" :key="index">
							<tera-column-info
								:column="column"
								:feature-config="{ isPreview: false }"
								@update-column="updateColumn(index, $event.key, $event.value)"
							/>
						</li>
					</ul>
				</AccordionTab>
				<template v-else-if="dataset?.metadata">
					<AccordionTab header="Preview">
						<img :src="image" alt="" />
						<tera-carousel
							v-if="isClimateSubset && dataset.metadata?.preview"
							:labels="dataset.metadata.preview.map(({ year }) => year)"
						>
							<div v-for="item in dataset.metadata.preview" :key="item">
								<img :src="item.image" alt="Preview" />
							</div>
						</tera-carousel>
					</AccordionTab>
					<AccordionTab header="Metadata">
						<div v-for="(value, key) in dataset.metadata" :key="key" class="row">
							<template v-if="key.toString() !== 'preview'">
								<div class="col key">
									{{ snakeToCapitalized(key.toString()) }}
								</div>
								<div class="col">
									<ul v-if="typeof value === 'object'">
										<li v-for="(item, index) in Object.values(value)" :key="index">
											{{ item }}
										</li>
									</ul>
									<ul v-else-if="Array.isArray(value)">
										<li v-for="(item, index) in value" :key="index">
											{{ item }}
										</li>
									</ul>
									<template v-else>
										{{ value }}
									</template>
								</div>
							</template>
						</div>
					</AccordionTab>
				</template>
				<AccordionTab header="Data" v-if="!isEmpty(datasetInfo.fileNames)">
					<tera-progress-spinner v-if="!rawContent" :font-size="2" is-centered />
					<tera-dataset-datatable v-else :rows="100" :raw-content="rawContent" />
				</AccordionTab>
			</Accordion>
		</section>
	</tera-asset>
	<!-- TODO: Add create dataset support to save modal -->
	<tera-save-asset-modal
		:asset="transientDataset"
		:asset-type="AssetType.Dataset"
		:initial-name="transientDataset?.name"
		:is-visible="showSaveModal"
		:open-on-save="isWorkflow ? false : true"
		@close-modal="showSaveModal = false"
		@on-save="onModalSave"
	/>
</template>

<script setup lang="ts">
import { computed, PropType, ref, watch } from 'vue';
import { cloneDeep, isEmpty, isEqual } from 'lodash';
import { snakeToCapitalized } from '@/utils/text';
import {
	downloadRawFile,
	getClimateDataset,
	getClimateDatasetPreview,
	getDataset,
	getDownloadURL,
	updateDataset
} from '@/services/dataset';
import { AssetType, type CsvAsset, type Dataset, PresignedURL } from '@/types/Types';
import TeraAsset from '@/components/asset/tera-asset.vue';
import type { FeatureConfig } from '@/types/common';
import { DatasetSource } from '@/types/Dataset';
import { useProjects } from '@/composables/project';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraShowMoreText from '@/components/widgets/tera-show-more-text.vue';
import ContextMenu from 'primevue/contextmenu';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import { logger } from '@/utils/logger';
import TeraCarousel from '@/components/widgets/tera-carousel.vue';
import TeraAssetEnrichment from '@/components/widgets/tera-asset-enrichment.vue';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import TeraDatasetDatatable from '@/components/dataset/tera-dataset-datatable.vue';
import TeraColumnInfo from '@/components/dataset/tera-column-info.vue';
import TeraSaveAssetModal from '@/components/project/tera-save-asset-modal.vue';

const props = defineProps({
	assetId: {
		type: String,
		required: true
	},
	featureConfig: {
		type: Object as PropType<FeatureConfig>,
		default: { isPreview: false } as FeatureConfig
	},
	source: {
		type: String as PropType<DatasetSource>,
		default: DatasetSource.TERARIUM
	},
	isWorkflow: {
		type: Boolean,
		default: false
	}
});

const emit = defineEmits(['close-preview', 'on-save']);

const dataset = ref<Dataset | null>(null);
const transientDataset = ref<Dataset | null>(null);
const newName = ref('');
const isRenaming = ref(false);
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
const optionsMenuItems = ref([
	{
		icon: 'pi pi-pencil',
		label: 'Rename',
		command() {
			isRenaming.value = true;
			newName.value = dataset.value?.name ?? '';
		}
	},
	{
		icon: 'pi pi-plus',
		label: 'Add to project',
		items:
			useProjects()
				.allProjects.value?.filter((project) => project.id !== useProjects().activeProject.value?.id)
				.map((project) => ({
					label: project.name,
					command: async () => {
						const response = await useProjects().addAsset(AssetType.Dataset, props.assetId, project.id);
						if (response) logger.info(`Added asset to ${project.name}`);
					}
				})) ?? []
	},
	{
		icon: 'pi pi-download',
		label: 'Download',
		command: async () => {
			const presignedUrl: PresignedURL | null = await downloadFileFromDataset();
			if (presignedUrl) {
				window.open(presignedUrl.url, '_blank');
			}
			emit('close-preview');
		}
	}
]);

// Save modal
function onModalSave(event: any) {
	showSaveModal.value = false;
	if (props.isWorkflow) {
		emit('on-save', event);
	}
}

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

const datasetInfo = computed(() => {
	const information = {
		id: '',
		fileNames: ''
	};
	if (dataset.value) {
		information.id = dataset.value.id ?? '';
		information.fileNames = dataset.value.fileNames?.join(', ') ?? '';
	}
	return information;
});

const isClimateData = computed(() => dataset.value?.esgfId);
const isClimateSubset = computed(() => dataset.value?.metadata?.format === 'netcdf');
const datasetType = computed(() => card.value?.DATASET_TYPE ?? '');

const image = ref<string | undefined>(undefined);

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
const description = computed(() => dataset.value?.description?.concat('\n', card.value?.DESCRIPTION ?? '') ?? '');
const author = computed(() => card.value?.AUTHOR_NAME ?? '');

function updateColumn(index: number, key: string, value: any) {
	if (!transientDataset.value?.columns?.[index]) return;
	if (key === 'unit' || key === 'name') {
		if (!transientDataset.value.columns[index].metadata) {
			transientDataset.value.columns[index].metadata = {};
		}
		transientDataset.value.columns[index].metadata[key] = value;
	} else if (key === 'concept') {
		// Only one identifier is supported for now
		if (!transientDataset.value.columns[index]?.grounding?.identifiers) {
			transientDataset.value.columns[index].grounding = { identifiers: [] };
		}
		// Replaces first element of identifiers array
		transientDataset.value.columns[index].grounding?.identifiers?.shift();
		transientDataset.value.columns[index].grounding?.identifiers?.unshift(value);
	} else {
		transientDataset.value.columns[index][key] = value;
	}
}

const toggleOptionsMenu = (event) => {
	optionsMenu.value.toggle(event);
};

/**
 * Downloads the first file of the dataset from S3 directly
 * @param dataset
 */
async function downloadFileFromDataset(): Promise<PresignedURL | null> {
	if (dataset.value) {
		const { id, fileNames } = dataset.value;
		if (id && fileNames && fileNames.length > 0 && !isEmpty(fileNames[0])) {
			return (await getDownloadURL(id, fileNames[0])) ?? null;
		}
	}
	return null;
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
	fetchDataset();
}

async function updateDatasetName() {
	if (transientDataset.value && !isEmpty(newName.value)) {
		transientDataset.value.name = newName.value;
		await updateDatasetContent();
	}
	isRenaming.value = false;
}

function reset() {
	transientDataset.value = cloneDeep(dataset.value);
}

const fetchDataset = async () => {
	if (props.source === DatasetSource.TERARIUM) {
		dataset.value = await getDataset(props.assetId);
	} else if (props.source === DatasetSource.ESGF) {
		dataset.value = await getClimateDataset(props.assetId);
	}
	reset(); // Prepare transientDataset for editing

	if (dataset.value?.esgfId && !image.value) {
		image.value = await getClimateDatasetPreview(dataset.value.esgfId);
	}
};

function getRawContent() {
	// If it's an ESGF dataset or a NetCDF file, we don't want to download the raw content
	if (!dataset.value || dataset.value.esgfId || dataset.value.metadata?.format === 'netcdf') return;
	// We are assuming here there is only a single csv file.
	if (
		dataset.value.fileNames &&
		!isEmpty(dataset.value.fileNames) &&
		!isEmpty(dataset.value.fileNames[0]) &&
		dataset.value.fileNames[0].endsWith('.csv')
	) {
		downloadRawFile(props.assetId, dataset.value.fileNames[0]).then((res) => {
			rawContent.value = res;
		});
	}
}

// Whenever assetId changes, fetch dataset with that ID
watch(
	() => props.assetId,
	async () => {
		isRenaming.value = false;
		if (props.assetId) {
			// Empty the dataset and rawContent so previous data is not shown
			dataset.value = null;
			rawContent.value = null;
			isDatasetLoading.value = true;
			await fetchDataset();
			isDatasetLoading.value = false;
			if (dataset.value) {
				getRawContent(); // Whenever we change the dataset, we need to fetch the rawContent
			}
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
.column-information-table {
	border: 1px solid var(--surface-border-light);
}
.btn-group {
	display: flex;
	align-items: center;
	gap: var(--gap-small);
	margin-left: auto;
}

li {
	padding-bottom: var(--gap-2);
	border-bottom: 1px solid var(--surface-border);
}

.description {
	display: flex;
	flex-direction: column;
	gap: var(--gap-small);
	margin-left: 1.5rem;
}

.row {
	display: flex;
	justify-content: space-between;
	border-bottom: 1px solid var(--surface-border);
	padding: var(--gap-small) 0;
}

.key {
	font-weight: bold;
}

.col {
	flex: 1;
}

/* Add gaps beneath open accordions */
:deep(.p-accordion-content) {
	padding-bottom: var(--gap-6) !important;
}
</style>
