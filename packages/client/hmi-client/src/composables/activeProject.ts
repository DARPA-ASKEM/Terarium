import { computed, shallowRef, watch } from 'vue';
import type { Project } from '@/types/Types';

export const activeProject = shallowRef<Project | null>(null);
export const activeProjectId = computed<string>(
	() => activeProject?.value?.id ?? localStorage.getItem('activeProjectId') ?? ''
);

watch(
	activeProject,
	(newProject) => {
		localStorage.setItem('activeProjectId', newProject?.id ?? '');
	},
	{ immediate: true }
);
