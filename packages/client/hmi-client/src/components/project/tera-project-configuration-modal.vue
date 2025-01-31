<template>
	<tera-modal @modal-mask-clicked="emit('close-modal')" @modal-enter-press="applyConfiguration">
		<template #header>
			<h4>{{ modalTitle }}</h4>
		</template>
		<template #default>
			<div class="modal-content-container">
				<section>
					<form @submit.prevent>
						<div>
							<label for="new-project-name">Project name <span>(Required)</span></label>
							<Textarea
								id="new-project-name"
								rows="1"
								v-model="title"
								autoResize
								placeholder="What do you want to call your project?"
								required
								class="w-full"
								style="min-height: 35px"
							/>
						</div>
						<div>
							<label for="new-project-description">Description <span>(Optional)</span></label>
							<Textarea
								id="new-project-description"
								rows="8"
								v-model="description"
								placeholder="Add a short description"
								:style="{ width: '100%', resize: 'none', maxHeight: '278px', minHeight: '32px' }"
							/>
						</div>
					</form>
				</section>
				<section>
					<label class="label-spacing">Select a thumbnail image for your project</label>
					<!-- <div>
						<span class="p-input-icon-left">
							<i class="pi pi-search" />
							<InputText
								placeholder="Search images..."
								v-model="imageSearch"
								icon="pi pi-search"
								disabled
							/>
						</span>
					</div>
					<p>
						The concepts mentioned in your project title and description will be used to suggest
						relevant thumbnail images.
					</p> -->
					<div class="select-thumbnail-panel">
						<ul>
							<li>
								<input type="radio" name="thumbnail" id="thumbnail-default" v-model="thumbnail" value="default" />
								<label for="thumbnail-default">
									<img src="@assets/images/project-thumbnails/default.png" alt="Default thumbnail image" />
								</label>
							</li>
							<li v-for="i in 53" :key="i">
								<input type="radio" name="thumbnail" :id="`thumbnail-0${i}`" v-model="thumbnail" :value="`0${i}`" />
								<label :for="`thumbnail-0${i}`">
									<img
										:src="getImage(`project-thumbnails/0${i}.png`) ?? DefaultThumbnail"
										:alt="`Thumbnail image 0{$i}`"
									/>
								</label>
							</li>
						</ul>
					</div>
				</section>
			</div>
		</template>
		<template #footer>
			<Button
				@click="applyConfiguration"
				:loading="isApplyingConfiguration"
				:label="isApplyingConfiguration ? 'Loading' : confirmText"
				size="large"
				:disabled="isTitleInvalid"
			/>
			<Button severity="secondary" outlined @click="emit('close-modal')" label="Cancel" size="large" />
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import Textarea from 'primevue/textarea';
import TeraModal from '@/components/widgets/tera-modal.vue';
import Button from 'primevue/button';
import { useProjects } from '@/composables/project';
import { cloneDeep, isEmpty } from 'lodash';
import { useRouter } from 'vue-router';
import { RouteName } from '@/router/routes';
import { Project } from '@/types/Types';
import getImage from '@/assets/utils';
import DefaultThumbnail from '@/assets/images/project-thumbnails/default.png';

const props = defineProps<{
	confirmText: string;
	modalTitle: string;
	project: Project | null;
}>();

const emit = defineEmits(['close-modal']);

const router = useRouter();

const title = ref(props.project?.name ?? '');
const isTitleInvalid = computed(() => isEmpty(title.value));
const description = ref(props.project?.description ?? '');
const isApplyingConfiguration = ref(false);
const thumbnail = ref('default');

async function createProject() {
	isApplyingConfiguration.value = true;
	const project = await useProjects().create(title.value, description.value, thumbnail.value);
	if (project?.id) {
		emit('close-modal');
		await router.push({ name: RouteName.Project, params: { projectId: project.id } });
	}
}

function updateProjectConfiguration() {
	if (props.project) {
		const updatedProject = cloneDeep(props.project);
		updatedProject.name = title.value;
		updatedProject.description = description.value;
		updatedProject.thumbnail = thumbnail.value;
		isApplyingConfiguration.value = true;
		useProjects().update(updatedProject);
		emit('close-modal');
	}
}

function applyConfiguration() {
	if (props.project) updateProjectConfiguration();
	else createProject();
}
</script>

<style scoped>
:deep(.content) {
	display: flex;
	gap: var(--gap-4);
}

section {
	flex: 1;
	min-width: 0;
}

form {
	display: flex;
	flex-direction: column;
	width: 100%;
	gap: var(--gap-4);
}

label span {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
	margin-left: var(--gap-4);
	text-transform: lowercase;
}

p {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
}

ul {
	display: flex;
	flex-wrap: wrap;
	gap: var(--gap-2);
	list-style: none;
}

li {
	flex: 10rem;
	border: 2px solid transparent;
	flex-basis: 25%;

	input {
		display: none;
	}
	&:has(input:checked) {
		border-color: var(--primary-color);
	}

	label {
		cursor: pointer;
		margin: 0;
	}

	img {
		aspect-ratio: 208/133;
		width: 100%;
	}
}

/*
	Doesn't rely on the margin-bottom: 1rem rule in tera-modal.vue
	Should probably switch everything to use gap (like here) at some point
 */
:deep(.content input),
:deep(.content textarea) {
	margin-bottom: 0;
}
:deep(.content textinput) {
	margin-bottom: 0;
}

.select-thumbnail-panel {
	background: var(--surface-50);
	padding: var(--gap-4);
	border-radius: var(--border-radius);
	border: 1px solid var(--surface-border-light);
	max-height: 100%;
	max-width: 100%;
	overflow-y: scroll;
	width: 50vw;
	height: 20vw;
	margin-top: var(--gap-2);
}
.modal-content-container {
	display: flex;
	gap: var(--gap-8);
}
</style>
