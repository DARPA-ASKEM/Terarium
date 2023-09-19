/* 
	Use `activeProject` to get the active project in your component. It is read only and should not be directly modified. 
	`activeProject` can be refreshed by calling `getActiveProject`
	Use the functions in this composable to make modifications to the project and to add/remove assets from it.
	Using these functions guarantees that such changes propogate to all components using `activeProject`.
	Using the resource store for project data is no longer needed.
*/

import { IProject } from '@/types/Project';
import { Component, readonly, shallowRef } from 'vue';
import * as ProjectService from '@/services/project';
import * as ModelService from '@/services/model';
import { AssetType } from '@/types/Types';
import DatasetIcon from '@/assets/svg/icons/dataset.svg?component';

const TIMEOUT_MS = 1000;

const activeProject = shallowRef<IProject | null>(null);
const allProjects = shallowRef<IProject[] | null>(null);

/**
 * Get the icon associated with an Asset
 */
const icons = new Map<string | AssetType, string | Component>([
	[AssetType.Publications, 'file'],
	[AssetType.Models, 'share-2'],
	[AssetType.Datasets, DatasetIcon],
	[AssetType.Simulations, 'settings'],
	[AssetType.Code, 'code'],
	[AssetType.Workflows, 'git-merge'],
	['overview', 'layout']
]);

export function useProjects() {
	async function getActiveProject(projectId: IProject['id']): Promise<IProject | null> {
		if (projectId && !!projectId) {
			activeProject.value = await ProjectService.get(projectId, true);
		} else {
			activeProject.value = null;
		}
		return activeProject.value;
	}

	async function getAllProjects(): Promise<IProject[]> {
		allProjects.value = (await ProjectService.getAll()) as unknown as IProject[];
		return allProjects.value;
	}

	async function addAsset(projectId: string, assetType: string, assetId: string) {
		const newAssetId = await ProjectService.addAsset(projectId, assetType, assetId);
		setTimeout(async () => {
			activeProject.value = await ProjectService.get(projectId as IProject['id'], true);
		}, TIMEOUT_MS);
		return newAssetId;
	}

	async function deleteAsset(projectId: string, assetType: AssetType, assetId: string) {
		const deleted = ProjectService.deleteAsset(projectId, assetType, assetId);
		setTimeout(async () => {
			activeProject.value = await ProjectService.get(projectId as IProject['id'], true);
		}, 1000);
		return deleted;
	}

	async function create(name: string, description: string, username: string) {
		return ProjectService.create(name, description, username);
	}

	async function update(project: IProject) {
		const updated = ProjectService.update(project);
		setTimeout(async () => {
			activeProject.value = await ProjectService.get(project.id, true);
		}, 1000);
		return updated;
	}

	async function remove(projectId: IProject['id']) {
		return ProjectService.remove(projectId);
	}

	async function getPublicationAssets(projectId: IProject['id']) {
		return ProjectService.getPublicationAssets(projectId);
	}

	function getAssetIcon(type: AssetType | string | null): string | Component {
		if (type && icons.has(type)) {
			return icons.get(type) ?? 'circle';
		}
		return 'circle';
	}

	async function addNewModelToProject(modelName: string, projectId: IProject['id']) {
		const modelId = await ModelService.addNewModelToProject(modelName);
		if (modelId) {
			await addAsset(projectId, AssetType.Models, modelId);
		}
		return modelId;
	}

	return {
		activeProject: readonly(activeProject),
		allProjects: readonly(allProjects),
		getActiveProject,
		getAllProjects,
		addAsset,
		deleteAsset,
		create,
		update,
		remove,
		getPublicationAssets,
		getAssetIcon,
		addNewModelToProject
	};
}
