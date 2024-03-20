/**
 * Project
 */

import API from '@/api/api';
import { logger } from '@/utils/logger';
import DatasetIcon from '@/assets/svg/icons/dataset.svg?component';
import { Component } from 'vue';
import * as EventService from '@/services/event';
import {
	AssetType,
	EventType,
	PermissionRelationships,
	Project,
	ProjectAsset
} from '@/types/Types';

/**
 * Create a project
 * @param name Project['name']
 * @param [description] Project['description']
 * @param [userId] Project['userId']
 * @return Project|null - the appropriate project, or null if none returned by API
 */
async function create(
	name: Project['name'],
	description: Project['description'] = '',
	userId: Project['userId'] = ''
): Promise<Project | null> {
	try {
		const project: Project = {
			name,
			description,
			userId,
			projectAssets: [] as ProjectAsset[]
		};
		const response = await API.post(`/projects`, project);
		const { status, data } = response;
		if (status !== 201) return null;
		return data ?? null;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

async function update(project: Project): Promise<Project | null> {
	try {
		const { id, name, description, overviewContent } = project;
		const response = await API.put(`/projects/${id}`, {
			id,
			name,
			description,
			overviewContent: btoa(overviewContent)
		});
		const { status, data } = response;
		if (status !== 200) {
			return null;
		}
		return data ?? null;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

/**
 * Remove a project (soft-delete)
 * @param projectId {Project["id"]} - the id of the project to be removed
 * @return boolean - if the removal was successful
 */
async function remove(projectId: Project['id']): Promise<boolean> {
	try {
		const { status } = await API.delete(`/projects/${projectId}`);
		return status === 200;
	} catch (error) {
		logger.error(error);
		return false;
	}
}

/**
 * Get all projects
 * @return Array<Project>|null - the list of all projects, or null if none returned by API
 */
async function getAll(): Promise<Project[] | null> {
	try {
		const response = await API.get(`/projects`);
		const { status, data } = response;
		if (status !== 200 || !data) return null;
		return (data as Project[]).reverse();
	} catch (error) {
		logger.error(error);
		return null;
	}
}

/**
 * Add project asset
 * @projectId string - represents the project id wherein the asset will be added
 * @assetType string - represents the type of asset to be added, e.g., 'documents'
 * @assetId string - represents the id of the asset to be added. This will be the internal id of some asset stored in one of the data service collections
 * @return any|null - some result if success, or null if none returned by API
 */
async function addAsset(projectId: string, assetType: string, assetId: string) {
	// FIXME: handle cases where assets is already added to the project
	const url = `/projects/${projectId}/assets/${assetType}/${assetId}`;
	const response = await API.post(url);

	await EventService.create(
		EventType.AddResourcesToProject,
		projectId,
		JSON.stringify({
			assetType,
			assetId
		})
	);
	return response?.data ?? null;
}

/**
 * Delete a project asset
 * @projectId Project["id"] - represents the project id wherein the asset will be added
 * @assetType AssetType - represents the type of asset to be added, e.g., 'documents'
 * @assetId string | number - represents the id of the asset to be added. This will be the internal id of some asset stored in one of the data service collections
 * @return boolean
 */
async function deleteAsset(
	projectId: Project['id'],
	assetType: AssetType,
	assetId: string | number
): Promise<boolean> {
	try {
		const url = `/projects/${projectId}/assets/${assetType}/${assetId}`;
		const { status } = await API.delete(url);
		return status >= 200 && status < 300;
	} catch (error) {
		logger.error(error);
		return false;
	}
}

/**
 * Get a project per id
 * @param projectId - Project['id']
 * @return Project|null - the appropriate project, or null if none returned by API
 */
async function get(projectId: Project['id']): Promise<Project | null> {
	try {
		const { status, data } = await API.get(`/projects/${projectId}`);
		if (status !== 200) return null;
		return (data as Project) ?? null;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

async function setAccessibility(projectId: Project['id'], isPublic: boolean): Promise<boolean> {
	try {
		const response = await API.put(`projects/set-public/${projectId}/${isPublic}`);
		return response?.status === 200;
	} catch (error) {
		logger.error(`The project was not made ${isPublic ? 'public' : 'restricted'}, ${error}`);
		return false;
	}
}

async function getPermissions(projectId: Project['id']): Promise<PermissionRelationships | null> {
	try {
		const { status, data } = await API.get(`projects/${projectId}/permissions`);
		if (status !== 200) {
			return null;
		}
		return data ?? null;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

async function setPermissions(
	projectId: Project['id'],
	userId: string,
	relationship: string
): Promise<boolean> {
	try {
		const { status } = await API.post(
			`projects/${projectId}/permissions/user/${userId}/${relationship}`
		);
		if (status !== 200) {
			return false;
		}
		return true;
	} catch (error) {
		logger.error(error);
		return false;
	}
}

async function removePermissions(
	projectId: Project['id'],
	userId: string,
	relationship: string
): Promise<boolean> {
	try {
		const { status } = await API.delete(
			`projects/${projectId}/permissions/user/${userId}/${relationship}`
		);
		if (status !== 200) {
			return false;
		}
		return true;
	} catch (error) {
		logger.error(error);
		return false;
	}
}

async function updatePermissions(
	projectId: Project['id'],
	userId: string,
	oldRelationship: string,
	to: string
): Promise<boolean> {
	try {
		const { status } = await API.put(
			`projects/${projectId}/permissions/user/${userId}/${oldRelationship}?to=${to}`
		);
		if (status !== 200) {
			return false;
		}
		return true;
	} catch (error) {
		logger.error(error);
		return false;
	}
}

/**
 * Get the icon associated with an Asset
 */
const icons = new Map<string | AssetType, string | Component>([
	[AssetType.Document, 'file'],
	[AssetType.Model, 'share-2'],
	[AssetType.Dataset, DatasetIcon],
	[AssetType.Simulation, 'settings'],
	[AssetType.Code, 'code'],
	[AssetType.Workflow, 'git-merge'],
	['overview', 'layout']
]);

function getAssetIcon(type: AssetType | string | null): string | Component {
	if (type && icons.has(type)) {
		return icons.get(type) ?? 'circle';
	}
	return 'circle';
}

export {
	create,
	update,
	get,
	remove,
	getAll,
	addAsset,
	deleteAsset,
	getAssetIcon,
	setAccessibility,
	getPermissions,
	setPermissions,
	removePermissions,
	updatePermissions
};
