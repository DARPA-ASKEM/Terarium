/* 
	Use `activeProject` to get the active project in your component. It is read only and should not be directly modified. 
	`activeProject` can be refreshed by calling `getProject`
	Use the functions in this composable to make modifications to the project and to add/remove assets from it.
	Using these functions guarantees that such changes propogate to all components using `activeProject`.
	Using the resource store for project data is no longer needed.
*/

import { IProject } from '@/types/Project';
import { computed, shallowRef } from 'vue';
import * as ProjectService from '@/services/project';
import { AssetType } from '@/types/Types';

const TIMEOUT_MS = 100;

const activeProject = shallowRef<IProject | null>(null);
const allProjects = shallowRef<IProject[] | null>(null);
const activeProjectId = computed<string>(() => activeProject.value?.id ?? '');

export function useProjects() {
	// refresh the current activeProject if `projectId` is not defined
	// otherwise get and set the current project to the one specified by `projectId`
	async function get(projectId?: IProject['id']): Promise<IProject | null> {
		if (projectId) {
			activeProject.value = await ProjectService.get(projectId, true);
		} else {
			activeProject.value = await ProjectService.get(activeProjectId.value, true);
		}
		return activeProject.value;
	}

	async function getAll(): Promise<IProject[]> {
		allProjects.value = (await ProjectService.getAll()) as unknown as IProject[];
		return allProjects.value;
	}

	async function addAsset(assetType: string, assetId: string, projectId?: string) {
		const newAssetId = await ProjectService.addAsset(
			projectId ?? activeProjectId.value,
			assetType,
			assetId
		);
		if (!projectId || projectId === activeProjectId.value) {
			setTimeout(async () => {
				activeProject.value = await ProjectService.get(activeProjectId.value, true);
			}, TIMEOUT_MS);
		}
		return newAssetId;
	}

	async function deleteAsset(assetType: AssetType, assetId: string, projectId?: string) {
		const deleted = await ProjectService.deleteAsset(
			projectId ?? activeProjectId.value,
			assetType,
			assetId
		);
		if (!projectId || projectId === activeProjectId.value) {
			setTimeout(async () => {
				activeProject.value = await ProjectService.get(activeProjectId.value, true);
			}, TIMEOUT_MS);
		}
		return deleted;
	}

	async function create(name: string, description: string, username: string) {
		const created = await ProjectService.create(name, description, username);
		setTimeout(async () => {
			getAll();
		}, TIMEOUT_MS);
		return created;
	}

	async function update(project: IProject) {
		const updated = await ProjectService.update(project);
		setTimeout(async () => {
			activeProject.value = await ProjectService.get(project.id, true);
		}, 1000);
		return updated;
	}

	async function remove(projectId: IProject['id']) {
		const removed = await ProjectService.remove(projectId);
		setTimeout(async () => {
			getAll();
		}, TIMEOUT_MS);
		// `toString()` shouldn't be necessary but for some reason `activeProjectId.value` evaluates to a number
		if (removed && projectId === activeProjectId.value.toString()) {
			// removed project was the active project; set active project to null
			activeProject.value = null;
		}
		return removed;
	}

	async function getPublicationAssets(projectId: IProject['id']) {
		return ProjectService.getPublicationAssets(projectId);
	}

	return {
		activeProject,
		allProjects,
		get,
		getAll,
		addAsset,
		deleteAsset,
		create,
		update,
		remove,
		getPublicationAssets
	};
}
