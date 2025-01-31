<template>
	<main>
		<!-- Top banner -->
		<div class="scrollable">
			<header>
				<section>
					<h3>AI-assisted modeling for scientific decision-making</h3>
					<p>
						Pair your expertise with AI to accelerate scientific modeling and simulation. Build on existing models and
						data to simulate and communicate complex real-world scenarios.
					</p>
				</section>

				<section @click="showVideo = true">
					<span>▶</span>
					<span>Terarium overview</span>
					<span>A demonstration of basic features</span>
				</section>

				<tera-modal v-if="showVideo" @close="showVideo = false" @modal-mask-clicked="showVideo = false">
					<video controls height="500px">
						<source src="https://videos.terarium.ai/terarium.mp4" type="video/mp4" />
						<track src="" kind="captions" srclang="en" label="English" />
						Your browser does not support the video tag.
					</video>
				</tera-modal>
			</header>

			<!-- Tab section: My projects, Public projects, Sample projects -->
			<section class="menu">
				<TabView @tab-change="tabChange" :active-index="activeTabIndex" :key="activeTabIndex">
					<TabPanel v-for="(tab, i) in TabTitles" :header="tab" :key="i">
						<section class="filter-and-sort">
							<span class="search">
								<tera-input-text
									v-model="searchProjectsQuery"
									placeholder="Search for projects"
									id="searchProject"
									:icon="isSearchLoading ? 'pi pi-spin pi-spinner' : 'pi pi-search'"
									class="search-input"
									@keydown.enter="searchedProjects"
								/>
								<Button
									label="Search"
									:disabled="searchProjectsQuery.length === 0 || isSearchLoading"
									@click="searchedProjects"
								/>
							</span>
							<Dropdown
								v-if="view === ProjectsView.Cards"
								v-model="selectedSort"
								:options="sortOptions"
								class="sort-options-dropdown"
							/>
							<MultiSelect
								v-if="view === ProjectsView.Table"
								:modelValue="selectedColumns"
								:options="columns"
								:maxSelectedLabels="1"
								:selected-items-label="`{0} columns displayed`"
								optionLabel="header"
								@update:modelValue="onToggle"
								placeholder="Add or remove columns"
								class="p-inputtext-sm"
							/>
							<div class="ml-auto flex gap-3">
								<SelectButton :model-value="view" @change="selectChange" :options="viewOptions" option-value="value">
									<template #option="slotProps">
										<span class="p-button-label">{{ slotProps.option.value }}</span>
									</template>
								</SelectButton>
								<Button
									icon="pi pi-upload"
									class="secondary-button"
									label="Upload project"
									@click="openUploadProjectModal"
								/>
								<Button icon="pi pi-plus" label="New project" @click="openCreateProjectModal" />
							</div>
						</section>
						<div v-if="!isLoadingProjects && isEmpty(searchedAndFilterProjects)" class="no-projects">
							<Vue3Lottie :animationData="EmptySeed" :height="200" :width="200" />
							<p class="mt-4">
								<template v-if="tab === TabTitles.MyProjects">Get started by creating a new project</template>
								<template v-if="tab === TabTitles.SampleProjects">Sample projects coming soon</template>
								<template v-if="tab === TabTitles.PublicProjects">You don't have any shared projects</template>
							</p>
						</div>
						<ul v-else-if="view === ProjectsView.Cards" class="project-cards-grid">
							<template v-if="cloningProjects.length && !isLoadingProjects">
								<li v-for="item in cloningProjects" :key="item.id">
									<tera-project-card v-if="item.id" :project="item" :is-copying="true" />
								</li>
							</template>
							<template v-if="isLoadingProjects">
								<li v-for="i in 3" :key="i">
									<tera-project-card />
								</li>
							</template>
							<li v-else v-for="project in searchedAndFilterProjects" :key="project.id">
								<tera-project-card
									v-if="project.id"
									:project="project"
									@click="openProject(project.id)"
									@copied-project="tabChange({ index: 0 })"
								/>
							</li>
						</ul>
						<tera-project-table
							v-else-if="view === ProjectsView.Table"
							:projects="searchedAndFilterProjects"
							:selected-columns="selectedColumns"
							:search-query="searchProjectsQuery"
							@open-project="openProject"
							@open-asset="openAsset"
						/>
					</TabPanel>
				</TabView>
			</section>

			<!-- Upload project modal -->
			<tera-upload-project-modal
				:visible="isUploadProjectModalVisible"
				@project-added="useProjects().getAll()"
				@close="isUploadProjectModalVisible = false"
			/>
		</div>
	</main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import Button from 'primevue/button';
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import { useRouter } from 'vue-router';
import { RouteName } from '@/router/routes';
import { isEmpty } from 'lodash';
import TeraProjectTable from '@/components/home/tera-project-table.vue';
import TeraProjectCard from '@/components/home/tera-project-card.vue';
import { useProjects } from '@/composables/project';
import Dropdown from 'primevue/dropdown';
import MultiSelect from 'primevue/multiselect';
import SelectButton from 'primevue/selectbutton';
import { useProjectMenu } from '@/composables/project-menu';
import {
	AssetType,
	ClientEventType,
	ProgressState,
	Project,
	ProjectAsset,
	ProjectSearchResult,
	TerariumAssetEmbeddingType
} from '@/types/Types';
import { ProjectWithKnnData } from '@/types/Project';
import { Vue3Lottie } from 'vue3-lottie';
import EmptySeed from '@/assets/images/lottie-empty-seed.json';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import { useNotificationManager } from '@/composables/notificationManager';
import teraUploadProjectModal from '@/components/project/tera-upload-project-modal.vue';
import { findProjects } from '@/services/project';
import { useToastService } from '@/services/toast';
import TeraModal from '@/components/widgets/tera-modal.vue';

const { isProjectConfigDialogVisible, menuProject } = useProjectMenu();

const { notificationItems } = useNotificationManager();

const cloningProjects = computed(() => {
	const items: any = [];
	notificationItems.value.forEach((item) => {
		if (item.type === ClientEventType.CloneProject && item.status === ProgressState.Running) {
			const project = (useProjects().allProjects.value ?? []).find((p) => p.id === item.assetId);
			items.push(project);
		}
	});
	return items;
});

const activeTabIndex = ref(0);
const showVideo = ref(false);
const isUploadProjectModalVisible = ref(false);
const searchProjectsQuery = ref('');
const searchProjectsResults = ref<ProjectSearchResult[]>([]);
const isSearchLoading = ref(false);

enum ProjectsView {
	Cards = 'Cards',
	Table = 'Table'
}

enum TabTitles {
	MyProjects = 'My projects',
	PublicProjects = 'Public projects',
	SampleProjects = 'Sample projects'
}

const selectedSort = ref('Last updated (descending)');
const sortOptions = [
	'Last updated (descending)',
	'Last updated (ascending)',
	'Creation date (descending)',
	'Creation date (ascending)',
	'Alphabetical'
];

const view = ref(ProjectsView.Cards);
const viewOptions = ref([
	{ value: ProjectsView.Cards, icon: 'pi pi-credit-card' },
	{ value: ProjectsView.Table, icon: 'pi pi-list' }
]);

function selectChange(event) {
	if (event.value) view.value = event.value;
}

function tabChange(event) {
	activeTabIndex.value = event.index;
}

const searchedAndFilterProjects = computed(() => {
	let tabProjects = useProjects().allProjects.value;
	if (!tabProjects) return [];

	if (activeTabIndex.value === 0) {
		tabProjects = tabProjects.filter(
			({ userPermission, publicProject }) =>
				// I can edit the project, or I can view a non-public project
				['creator', 'writer'].includes(userPermission ?? '') || (userPermission === 'reader' && !publicProject)
		);
	} else if (activeTabIndex.value === 1) {
		tabProjects = tabProjects.filter(({ publicProject, sampleProject }) => publicProject === true && !sampleProject);
	} else if (activeTabIndex.value === 2) {
		tabProjects = tabProjects.filter(({ sampleProject }) => sampleProject === true);
	}

	// If there are no search we can return the filtered and sorted projects
	if (isEmpty(searchProjectsResults.value)) {
		return filterAndSortProjects(tabProjects) as ProjectWithKnnData[];
	}

	return searchProjectsResults.value
		.map((result: ProjectSearchResult) => {
			const project = tabProjects.find(({ id }) => id === result.projectId);
			if (!project) return null;

			// Only display Project Overview as search results and sort the assets by descending score
			result.assets = result.assets
				?.filter((asset) => {
					const isNotAProject = asset.assetType !== AssetType.Project;
					const isProjectOverview =
						asset.assetType === AssetType.Project && asset.embeddingType === TerariumAssetEmbeddingType.Overview;
					return isNotAProject || isProjectOverview;
				})
				.sort((a, b) => b.score - a.score);
			return { ...project, ...result } as ProjectWithKnnData;
		})
		.filter((project) => !!project); // Remove null values
});

function openCreateProjectModal() {
	isProjectConfigDialogVisible.value = true;
	menuProject.value = null;
}

function openUploadProjectModal() {
	isUploadProjectModalVisible.value = true;
}

type DateType = 'createdOn' | 'updatedOn' | 'deletedOn';

function sortProjectByDates(projects: Project[], dateType: DateType, sorting: 'ASC' | 'DESC') {
	return projects.sort((a, b) => {
		const dateValueA = a[dateType]?.toString();
		const dateValueB = b[dateType]?.toString();
		const dateA = dateValueA ? new Date(dateValueA) : new Date(0);
		const dateB = dateValueB ? new Date(dateValueB) : new Date(0);
		return sorting === 'ASC' ? dateA.getTime() - dateB.getTime() : dateB.getTime() - dateA.getTime();
	});
}

function filterAndSortProjects(projects: Project[]) {
	if (projects) {
		if (selectedSort.value === 'Alphabetical') {
			return projects.sort((a, b) => (a.name ?? '').toLowerCase().localeCompare((b.name ?? '').toLowerCase()));
		}
		if (selectedSort.value === 'Last updated (descending)') {
			return sortProjectByDates(projects, 'updatedOn', 'DESC');
		}
		if (selectedSort.value === 'Last updated (ascending)') {
			return sortProjectByDates(projects, 'updatedOn', 'ASC');
		}
		if (selectedSort.value === 'Creation date (descending)') {
			return sortProjectByDates(projects, 'createdOn', 'DESC');
		}
		if (selectedSort.value === 'Creation date (ascending)') {
			return sortProjectByDates(projects, 'createdOn', 'ASC');
		}
	}
	return [];
}

// Table view
const columns = ref([
	{ field: 'name', header: 'Project title' },
	{ field: 'description', header: 'Description' },
	{ field: 'userName', header: 'Author' },
	{ field: 'stats', header: 'Stats' },
	{ field: 'createdOn', header: 'Created on' },
	{ field: 'updatedOn', header: 'Last updated' }
]);

const selectedColumns = ref(columns.value);
const onToggle = (val) => {
	selectedColumns.value = columns.value.filter((col) => val.includes(col));
};

const router = useRouter();

const isLoadingProjects = computed(() => !useProjects().allProjects.value);

function openProject(projectId: string) {
	router.push({ name: RouteName.Project, params: { projectId } });
}

function openAsset(projectId: Project['id'], assetId: ProjectAsset['id'], assetType: ProjectAsset['assetType']) {
	router.push({
		name: RouteName.Project,
		params: {
			projectId,
			assetId,
			pageType: assetType.toString()
		}
	});
}

watch(cloningProjects, () => {
	if (cloningProjects.value.length === 0) {
		useProjects().getAll();
	}
});

async function searchedProjects() {
	// If the search query is empty, show all projects
	if (isEmpty(searchProjectsQuery.value)) {
		searchProjectsResults.value = [];
		return;
	}

	isSearchLoading.value = true;
	findProjects(searchProjectsQuery.value)
		.then((response) => {
			// If no projects found, display a toast message
			if (isEmpty(response)) {
				useToastService().info('No projects found', 'Try searching for something else', 5000);
			} else {
				// Display search results using the table view
				searchProjectsResults.value = response;
				view.value = ProjectsView.Table;
			}
		})
		.finally(() => {
			isSearchLoading.value = false;
		});
}
</script>

<style scoped>
main > .scrollable {
	width: 100%;
	display: flex;
	flex-direction: column;
	overflow: auto;
}

header {
	align-items: center;
	background-image: url('@/assets/svg/terarium-logo-outline.svg'),
		radial-gradient(105.92% 916.85% at 101.3% -5.92%, #75d5c8 0%, white 100%);
	background-repeat: no-repeat;
	background-size: 25%, 100%;
	background-position:
		right 240px top -60px,
		100%;
	display: flex;
	padding: var(--gap-6);

	h3 {
		font-size: 24px;
		margin-bottom: var(--gap-3);
	}

	p {
		line-height: 1.5;
		max-width: 66ch;
	}

	svg {
		color: var(--primary-color);
		margin-right: 0.5rem;
	}

	section:last-of-type {
		align-items: center;
		aspect-ratio: 16 / 9;
		background-blend-mode: multiply;
		background-image: radial-gradient(circle, var(--primary-color), #0f483b), url('@/assets/images/video-thumbnail.png');
		background-position: center;
		background-size: cover;
		border-radius: var(--border-radius);
		cursor: pointer;
		display: flex;
		flex-direction: column;
		height: 11rem;
		justify-content: center;
		margin-left: auto;
		padding: var(--gap-8);
		position: relative;
		text-align: center;

		span:nth-of-type(1) {
			align-items: center;
			aspect-ratio: 1 / 1;
			background-color: var(--surface-0);
			border-radius: 50%;
			color: var(--primary-color);
			display: flex;
			flex: 1;
			flex-direction: column;
			font-size: var(--gap-8);
			justify-content: center;
			margin: var(--gap-4) 0;
			padding-left: var(--gap-1);
			padding-top: var(--gap-0-5);
			transition: all 0.2s;
			width: var(--gap-12);
		}

		&:hover .play-symbol {
			background-color: var(--primary-color-lighter);
		}

		span:nth-of-type(2) {
			color: var(--surface-0);
			font-size: var(--gap-6);
			font-weight: var(--font-weight-semibold);
			margin-bottom: var(--gap-1);
		}

		span:nth-of-type(3) {
			color: var(--surface-50);
		}
	}
}

.menu {
	display: flex;
	flex-direction: column;
	flex: 1;
}

.p-tabview {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.p-tabview:deep(.p-tabview-nav-container) {
	position: sticky;
	top: 0;
	z-index: 1;
}

.p-tabview:deep(.p-tabview-nav li .p-tabview-nav-link:focus) {
	background-color: transparent;
}

.p-tabview:deep(.p-tabview-panels) {
	padding: 0;
	flex: 1;
	background-color: #f9f9f9;
}

.p-dropdown,
.p-multiselect {
	min-width: 17rem;
	display: flex;
	align-items: center;
	padding-left: 0.5rem;
}

.filter-and-sort {
	position: sticky;
	z-index: 1;
	background-color: #f4f4f4;
	border-top: 1px solid var(--surface-border-light);
	border-bottom: 1px solid var(--surface-border-light);
	padding: var(--gap-3) var(--gap-4);
	display: flex;
	gap: var(--gap-3);
	/* Accommodate for height of projects tabs*/
	top: 42px;
}

.search {
	display: flex;
	flex: 1;
	max-width: 50rem;

	& > .search-input {
		flex: 1;

		&:deep(main) {
			border-top-right-radius: 0;
			border-bottom-right-radius: 0;
		}
	}

	&:deep(.p-button) {
		border-top-left-radius: 0;
		border-bottom-left-radius: 0;
	}
}

.sort-options-dropdown {
	height: 40px;
	padding-left: 0.5rem;
}
.project-cards-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
	gap: 16px;
	padding: 16px;
	list-style: none;
}

.no-projects {
	margin-top: 8rem;
	color: var(--text-color-subdued);
}
.no-projects > * {
	margin: auto;
	text-align: center;
}

.no-projects > img {
	height: 10rem;
	margin-bottom: 2rem;
}

a {
	color: var(--primary-color);
}

.new-project-button {
	padding: 0;
}
.secondary-button {
	background-color: var(--text-color-secondary);
}
.secondary-button:hover {
	background-color: color-mix(in srgb, var(--text-color-secondary) 90%, var(--surface-0) 10%);
}
.close-button {
	width: 14px;
	height: 14px;
	cursor: pointer;
	opacity: 50%;
}

.close-button:hover {
	opacity: 100%;
}
</style>
