/*
	Use `activeProject` to get the active project in your component. It is read only and should not be directly modified.
	`activeProject` can be refreshed by calling `getProject`
	Use the functions in this composable to make modifications to the project and to add/remove assets from it.
	Using these functions guarantees that such changes propagate to all components using `activeProject`.
	Using the resource store for project data is no longer needed.
*/

import { activeProject, activeProjectId } from '@/composables/activeProject';
import * as ProjectService from '@/services/project';
import type { PermissionRelationships, Project, ProjectAsset } from '@/types/Types';
import { AssetType } from '@/types/Types';
import { shallowRef } from 'vue';
import { useToastService } from '@/services/toast';

const TIMEOUT_MS = 100;

const projectLoading = shallowRef<boolean>(false);
const allProjects = shallowRef<Project[] | null>(null);
let areProjectsLoaded = false;

export function useProjects() {
	/**
	 * Refreshes the active project from backend if `projectId` is not defined.
	 * Otherwise, get and set the active project to the one specified by `projectId`.
	 *
	 * @param {Project['id']} projectId Id of the project to set as the active project.
	 * @returns {Promise<Project | null>} Active project.
	 */
	async function get(projectId: Project['id']): Promise<Project | null> {
		if (projectId) {
			projectLoading.value = true;
			activeProject.value = await ProjectService.get(projectId);
			projectLoading.value = false;
		} else {
			activeProject.value = null;
		}
		return activeProject.value;
	}

	async function refresh(): Promise<Project | null> {
		if (activeProjectId.value) {
			activeProject.value = await ProjectService.get(activeProjectId.value);
		}
		return activeProject.value;
	}

	/**
	 * Refreshes the list of all projects from backend.
	 * @returns {Promise<Project[]>} List of all projects.
	 */
	async function getAll(): Promise<Project[]> {
		allProjects.value = (await ProjectService.getAll()) as Project[];
		areProjectsLoaded = true;
		return allProjects.value;
	}

	/**
	 * Return all projects except the active project.
	 * @returns Project[]
	 */
	async function getAllExceptActive(): Promise<Project[]> {
		return new Promise((resolve) => {
			const interval = setInterval(() => {
				if (areProjectsLoaded) {
					clearInterval(interval);
					const projects: Project[] = (allProjects.value ?? [])
						.filter((project) => project.id !== activeProjectId.value)
						.sort((a, b) => {
							// sort by name
							const nameA = (a?.name ?? '').toUpperCase();
							const nameB = (b?.name ?? '').toUpperCase();
							if (nameA < nameB) return -1;
							if (nameA > nameB) return 1;
							return 0; // names must be equal
						});
					resolve(projects);
				}
			}, TIMEOUT_MS);
		});
	}

	/**
	 * Return Active Project Name or empty string
	 * @returns string
	 */
	function getActiveProjectName() {
		return activeProject.value?.name ?? '';
	}

	/**
	 * Return all the asset of a certain AssetType from the active project.
	 * @param assetType
	 * @returns ProjectAsset[]
	 */
	function getActiveProjectAssets(assetType: AssetType) {
		return (
			activeProject.value?.projectAssets.filter((asset) => asset.assetType === assetType) ?? ([] as ProjectAsset[])
		);
	}

	/**
	 * If `projectId` is defined, add an asset to that project.
	 * Otherwise, add an asset to the active project and refresh it.
	 *
	 * @param {AssetType} assetType Type of asset to be added, e.g., 'documents'.
	 * @param {string} assetId Id of the asset to be added. This will be the internal id of some asset stored in one of the data service collections.
	 * @param {Project['id']} [projectId] Id of the project to add the asset to.
	 * @returns {Promise<string|null>} Id of the added asset, if successful. Null, otherwise.
	 */
	async function addAsset(
		assetType: AssetType,
		assetId: ProjectAsset['id'],
		projectId?: Project['id']
	): Promise<ProjectAsset['id']> {
		if (!assetId) return undefined;
		const newAssetId = await ProjectService.addAsset(projectId ?? activeProjectId.value, assetType, assetId);
		if (!projectId || projectId === activeProjectId.value) {
			setTimeout(async () => {
				activeProject.value = await ProjectService.get(activeProjectId.value);
			}, TIMEOUT_MS);
		}
		return newAssetId;
	}

	/**
	 * Find an asset in the active project by its assetId.
	 * @param {ProjectAsset['assetId]} assetId
	 * @returns {ProjectAsset | undefined}
	 */
	function findAsset(assetId: ProjectAsset['assetId']): ProjectAsset | undefined {
		return activeProject.value?.projectAssets.find((projectAsset) => projectAsset.assetId === assetId);
	}

	/**
	 * Get the name of an asset from the active project.
	 * @param {ProjectAsset['assetId]} assetId
	 * @returns {ProjectAsset['assetName']}
	 */
	function getAssetName(assetId: ProjectAsset['assetId']): ProjectAsset['assetName'] {
		return findAsset(assetId)?.assetName ?? '';
	}

	/**
	 * If `projectId` is defined, delete an asset from that project.
	 * Otherwise, delete an asset from the active project and refresh it.
	 *
	 * @param {string} assetType Type of asset to be deleted, e.g., 'documents'.
	 * @param {string} assetId Id of the asset to be deleted. This will be the internal id of some asset stored in one of the data service collections.
	 * @param {Project['id']} [projectId] Id of the project to delete the asset from.
	 * @returns {Promise<boolean>} True if the asset was successfully deleted. False, otherwise.
	 */
	async function deleteAsset(assetType: AssetType, assetId: string, projectId?: Project['id']) {
		const deleted = await ProjectService.deleteAsset(projectId ?? activeProjectId.value, assetType, assetId);
		if (!projectId || projectId === activeProjectId.value) {
			setTimeout(async () => {
				activeProject.value = await ProjectService.get(activeProjectId.value);
			}, TIMEOUT_MS);
		}
		return deleted;
	}

	/**
	 * Create a new project and refresh the list of all projects.
	 *
	 * @param {string} name Name of the project.
	 * @param {string} description Short description.
	 * @param {string=default} thumbnail Thumbnail of the project.
	 * @returns {Promise<Project|null>} The created project, or null if none returned by the API.
	 */
	async function create(name: string, description: string, thumbnail = 'default') {
		const created = await ProjectService.create(name, description, thumbnail);
		setTimeout(async () => {
			getAll();
		}, TIMEOUT_MS);
		return created;
	}

	/**
	 * Update a project. If the updated project is the active project, refresh it.
	 */
	async function update(project: Project) {
		await ProjectService.update(project);
		if (project.id === activeProjectId.value) {
			setTimeout(async () => {
				activeProject.value = await ProjectService.get(project.id);
			}, 1000);
		}
		setTimeout(getAll, TIMEOUT_MS);
	}

	/**
	 * Remove a project and refresh the list of all projects. Sets the active project to null.
	 *
	 * @param projectId Id of the project to remove.
	 * @returns Id of the project to remove.
	 */
	async function remove(projectId: Project['id']) {
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

	async function setAccessibility(projectId: Project['id'], isPublic: boolean) {
		const accessibilityChanged = await ProjectService.setAccessibility(projectId, isPublic);

		// update the project accordingly
		if (accessibilityChanged) {
			if (activeProject.value) {
				activeProject.value = { ...activeProject.value, publicProject: isPublic };
			}
			if (allProjects.value) {
				allProjects.value = allProjects.value.map((project) => ({
					...project,
					publicProject: project.id === projectId ? isPublic : project.publicProject
				}));
			}
		}
	}

	/**
	 * Make a project a sample project
	 * @param {Project['id]} projectId - the id of the project to set as a sample project
	 * @param {boolean} isSample - true if the project should be a sample project, false otherwise
	 */
	async function setSample(projectId: Project['id'], isSample: boolean): Promise<boolean> {
		const response = await ProjectService.setSample(projectId, isSample);
		if (response) {
			await getAll();
			return true;
		}
		useToastService().error(undefined, 'Error changing the sample status of the project');
		return false;
	}

	async function getPermissions(projectId: Project['id']): Promise<PermissionRelationships | null> {
		return ProjectService.getPermissions(projectId);
	}

	async function setPermissions(projectId: Project['id'], userId: string, relationship: string) {
		return ProjectService.setPermissions(projectId, userId, relationship);
	}

	async function removePermissions(projectId: Project['id'], userId: string, relationship: string) {
		return ProjectService.removePermissions(projectId, userId, relationship);
	}

	async function updatePermissions(projectId: Project['id'], userId: string, oldRelationship: string, to: string) {
		return ProjectService.updatePermissions(projectId, userId, oldRelationship, to);
	}

	async function clone(id: Project['id']): Promise<Project | null> {
		const cloned = await ProjectService.clone(id);
		if (!cloned || !cloned.id) {
			return null;
		}
		return cloned;
	}

	function hasEditPermission() {
		const project = useProjects().activeProject.value;
		if (project != null && ['creator', 'writer'].includes(project.userPermission ?? '')) {
			return true;
		}
		console.warn('User has no edit permissions');
		return false;
	}

	function hasAssetInActiveProject(id: string) {
		return useProjects().activeProject.value?.projectAssets?.some((asset) => asset.assetId === id);
	}

	return {
		activeProject,
		activeProjectId,
		allProjects,
		projectLoading,
		get,
		getAll,
		getAllExceptActive,
		getActiveProjectAssets,
		getActiveProjectName,
		addAsset,
		findAsset,
		getAssetName,
		deleteAsset,
		create,
		update,
		remove,
		refresh,
		setAccessibility,
		setSample,
		getPermissions,
		hasAssetInActiveProject,
		hasEditPermission,
		setPermissions,
		removePermissions,
		updatePermissions,
		clone
	};
}
