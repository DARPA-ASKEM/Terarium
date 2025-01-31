<template>
	<nav
		class="resource-panel-container"
		@dragover.prevent="handleDragOver"
		@dragleave.prevent="handleDragLeave"
		@drop.prevent="handleDrop"
		:class="{ dragging: isDragging }"
	>
		<header>
			<InputText
				v-model="searchAsset"
				class="resource-panel-search"
				placeholder="Filter"
				id="searchAsset"
				@focus="inputFocused = true"
				@blur="inputFocused = false"
			/>
			<Button
				class="upload-resources-button"
				size="small"
				label="Upload"
				severity="secondary"
				outlined
				@click="isUploadResourcesModalVisible = true"
			/>
			<tera-upload-resources-modal
				:visible="isUploadResourcesModalVisible"
				:files="droppedFiles"
				@close="isUploadResourcesModalVisible = false"
			/>
		</header>
		<Button
			class="asset-button"
			:active="pageType === ProjectPages.OVERVIEW"
			plain
			text
			size="small"
			@click="emit('open-asset', overview)"
		>
			<span>
				<vue-feather class="p-button-icon-left" type="layout" size="1rem" stroke="rgb(16, 24, 40)" />
				<span class="p-button-label">Overview</span>
			</span>
		</Button>
		<Accordion
			style="background: transparent"
			v-if="!isEmpty(assetItemsMap) && !useProjects().projectLoading.value"
			:multiple="true"
			:active-index="Array.from(activeAccordionTabs)"
			@tab-open="
				activeAccordionTabs.add($event.index);
				saveAccordionTabsState();
			"
			@tab-close="
				activeAccordionTabs.delete($event.index);
				saveAccordionTabsState();
			"
		>
			<AccordionTab v-for="[type, assetItems] in assetItemsMap" :key="type">
				<template #header>
					<div class="flex justify-space-between w-full">
						<header class="flex align-items-center w-full">
							{{ capitalize(type) }}s
							<aside>({{ assetItems.length }})</aside>
						</header>
						<Button
							v-if="type === AssetType.Workflow"
							class="new-button"
							icon="pi pi-plus"
							label="New"
							size="small"
							text
							@click.stop="emit('open-new-workflow')"
						/>
					</div>
				</template>
				<!-- These are all the resources. They're buttons because they're click and draggable. -->
				<Button
					v-for="assetItem in assetItems"
					:key="assetItem.assetId"
					:active="assetItem.assetId === assetId && assetItem.pageType === pageType"
					:title="`${assetItem.assetName} — ${getElapsedTimeText(assetItem.assetCreatedOn)}`"
					class="asset-button"
					plain
					text
					size="small"
					@click="
						emit('open-asset', {
							assetId: assetItem.assetId,
							pageType: assetItem.pageType
						})
					"
					@mouseover="activeAssetId = assetItem.assetId"
					@mouseleave="activeAssetId = undefined"
					@focus="activeAssetId = assetItem.assetId"
					@focusout="activeAssetId = undefined"
				>
					<span
						:draggable="
							pageType === AssetType.Workflow &&
							(assetItem.pageType === AssetType.Model ||
								assetItem.pageType === AssetType.Dataset ||
								assetItem.pageType === AssetType.Document)
						"
						@dragstart="startDrag({ assetId: assetItem.assetId, pageType: assetItem.pageType })"
						@dragend="endDrag"
						:class="isEqual(draggedAsset, assetItem) ? 'dragged-asset' : ''"
						fallback-class="original-asset"
						force-fallback
					>
						<tera-asset-icon :asset-type="assetItem.pageType as AssetType" />
						<span class="p-button-label">{{ assetItem.assetName }}</span>
					</span>
					<!-- This 'x' only shows while hovering over the row -->
					<i
						v-if="activeAssetId && activeAssetId === assetItem.assetId"
						class="pi pi-times removeResourceButton"
						@click.stop="
							assetToDelete = assetItem;
							isRemovalModal = true;
						"
					/>
				</Button>
				<section v-if="assetItems.length == 0" class="empty-resource">Empty</section>
			</AccordionTab>
		</Accordion>
		<!-- Drag & drop to upload files overlay -->
		<div v-if="isDragging" class="drag-overlay">
			<span class="pi pi-upload" style="font-size: 2rem" />
			<p>Drop resources here</p>
		</div>

		<div v-if="useProjects().projectLoading.value" class="skeleton-container">
			<Skeleton v-for="i in 10" :key="i" width="85%" />
		</div>

		<tera-modal v-if="isRemovalModal" @modal-mask-clicked="isRemovalModal = false" @modal-enter-press="removeAsset">
			<template #header>
				<h4>Confirm remove</h4>
			</template>
			<template #default>
				<p class="remove">
					Removing <em>{{ assetToDelete?.assetName }}</em> will permanently remove it from
					<em>{{ useProjects().activeProject.value?.name }}</em
					>.
				</p>
			</template>
			<template #footer>
				<Button label="Remove" class="p-button-danger" @click="removeAsset" />
				<Button label="Cancel" severity="secondary" outlined @click="isRemovalModal = false" />
			</template>
		</tera-modal>
	</nav>
</template>

<script setup lang="ts">
import TeraAssetIcon from '@/components/widgets/tera-asset-icon.vue';
import TeraModal from '@/components/widgets/tera-modal.vue';
import { useProjects } from '@/composables/project';
import { useDragEvent } from '@/services/drag-drop';
import { ProjectPages } from '@/types/Project';
import { AssetType } from '@/types/Types';
import { AssetItem, AssetRoute } from '@/types/common';
import { generateProjectAssetsMap, getNonNullSetOfVisibleItems } from '@/utils/map-project-assets';
import { capitalize, isEmpty, isEqual } from 'lodash';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Skeleton from 'primevue/skeleton';
import { computed, ref } from 'vue';
import { getElapsedTimeText } from '@/utils/date';
import TeraUploadResourcesModal from '@/components/project/tera-upload-resources-modal.vue';

defineProps<{
	pageType: ProjectPages | AssetType;
	assetId: string;
}>();

const emit = defineEmits(['open-asset', 'remove-asset', 'open-new-workflow']);

const overview = { assetId: '', pageType: ProjectPages.OVERVIEW };

const activeAssetId = ref<string | undefined>('');
const isRemovalModal = ref(false);
const draggedAsset = ref<AssetRoute | null>(null);
const assetToDelete = ref<AssetItem | null>(null);
const searchAsset = ref<string>('');
const inputFocused = ref(false);
const isUploadResourcesModalVisible = ref(false);

const assetItemsMap = computed(() => generateProjectAssetsMap(searchAsset.value));
const assetItemsKeysNotEmpty = computed(() => getNonNullSetOfVisibleItems(assetItemsMap.value));
const activeAccordionTabs = ref(
	new Set(
		localStorage.getItem('activeResourceBarTabs')?.split(',').map(Number) ??
			assetItemsKeysNotEmpty.value ?? [0, 1, 2, 3, 4, 5, 6]
	)
);

function removeAsset() {
	if (assetToDelete.value) {
		const { assetId, pageType } = assetToDelete.value;
		emit('remove-asset', { assetId, pageType } as AssetRoute); // Pass as AssetRoute
		isRemovalModal.value = false;
	}
}

function saveAccordionTabsState() {
	localStorage.setItem('activeResourceBarTabs', Array.from(activeAccordionTabs.value).join());
}

const { setDragData, deleteDragData } = useDragEvent();

function startDrag(assetRoute: AssetRoute) {
	const { assetId, pageType } = assetRoute;
	setDragData('initAssetNode', { assetId, assetType: pageType });
	draggedAsset.value = assetRoute;
}

function endDrag() {
	deleteDragData('assetNode');
	draggedAsset.value = null;
}

// Drag and drop files over panel to upload
const isDragging = ref(false);

function handleDragOver() {
	isDragging.value = true;
}

function handleDragLeave() {
	isDragging.value = false;
}

const droppedFiles = ref<File[]>([]); // For passing files dragged onto the resources panel into the uploader

function handleDrop(event: DragEvent) {
	isDragging.value = false;
	if (!event.dataTransfer) return;

	const files = Array.from(event.dataTransfer.files) as File[];
	if (files.length > 0) {
		droppedFiles.value = files;
		isUploadResourcesModalVisible.value = true;
	}
}
</script>

<style scoped>
nav {
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);
}

.resource-panel-container {
	height: 100%;
}
nav > header {
	padding-left: var(--gap-2);
	padding-right: var(--gap-2);
	display: flex;
	flex-direction: row;

	& > * {
		align-self: stretch;
		height: 100%;
	}
}

.empty-resource {
	margin-left: 2.5rem;
	font-size: var(--font-caption);
	color: var(--text-color-subdued);
}
.clear-icon {
	position: absolute;
	right: 48px;
	margin-top: 0.65rem;
	height: var(--gap-2);
}
.clear-icon .pi-times {
	font-size: var(--gap-3);
	color: var(--text-color-subdued);
	cursor: pointer;
	z-index: 100;
}
.icon {
	fill: var(--text-color-primary);
	overflow: visible;
}

.removeResourceButton {
	color: var(--text-color-subdued);
	margin-right: var(--gap-3);
	font-size: 0.75rem;
}

.removeResourceButton:hover {
	color: var(--text-color-danger);
}

.new-button {
	&:deep() {
		padding: 0 var(--gap-1);
	}

	&:deep(.pi-plus) {
		font-size: 0.75rem;
	}
}

.dragged-asset {
	background-color: var(--surface-highlight);
	border-radius: var(--border-radius);
}

:deep(.p-accordion .p-accordion-content) {
	display: flex;
	flex-direction: column;
	padding: 0 0 var(--gap-4);
}

:deep(.p-accordion .p-accordion-header .p-accordion-header-link) {
	font-size: var(--font-body-small);
	padding: var(--gap-2) var(--gap-4);
}

:deep(.p-accordion .p-accordion-header .p-accordion-header-link aside) {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
	margin-left: var(--gap-1);
}

:deep(.asset-button.p-button) {
	display: inline-flex;
	overflow: hidden;
	padding: 0;
	border-radius: 0;
	/* Remove the border-radius to end neatly with the border of the sidebar */
}

:deep(.asset-button.p-button > span) {
	display: inline-flex;
	width: 100%;
	padding: var(--gap-1-5) var(--gap-4);
	overflow: hidden;
}

:deep(.asset-button.p-button[active='true']) {
	background-color: var(--surface-highlight);

	&::after {
		position: absolute;
		content: ' ';
		border-left: 4px solid var(--primary-color);
		height: 100%;
	}
}

:deep(.asset-button.p-button .p-button-label) {
	overflow: hidden;
	text-align: left;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-weight: var(--font-weight);
}

.loading-spinner {
	display: flex;
	flex: 1;
	justify-content: center;
	align-items: center;
	color: var(--primary-color);
	flex-grow: 1;
}

.pi-spinner {
	font-size: 4rem;
}

p.remove {
	max-width: 40rem;
	& em {
		font-weight: var(--font-weight-semibold);
	}
}

.resource-panel-search {
	width: 100%;
	margin-left: var(--gap-2);
}

:deep(.p-button-icon-left.icon) {
	margin-right: var(--gap-2);
}

.skeleton-container {
	display: flex;
	flex-direction: column;
	align-items: center;
	row-gap: 0.5rem;
}

.upload-resources-button {
	margin: 0 var(--gap-2);
	justify-content: center;

	& :deep(.p-button-label) {
		flex-grow: 0;
	}
}

.drag-overlay {
	position: absolute;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	background: var(--surface-highlight);
	border: dashed 2px var(--primary-color);
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);
	align-items: center;
	justify-content: center;
	pointer-events: none;
	padding: var(--gap-4);
	z-index: 10;
	opacity: 0.9;
}
</style>
