/**
 * Project
 */

import API from '@/api/api';
import { IProject, ProjectAssets } from '@/types/Project';
import { logger } from '@/utils/logger';
import DatasetIcon from '@/assets/svg/icons/dataset.svg?component';
import { Component } from 'vue';
import * as EventService from '@/services/event';
import { ExternalPublication, EventType, Project, AssetType } from '@/types/Types';

/**
 * Create a project
 * @param name Project['name']
 * @param [description] Project['description']
 * @param [username] Project['username']
 * @return Project|null - the appropriate project, or null if none returned by API
 */
async function create(
	name: Project['name'],
	description: Project['description'] = '',
	username: Project['username'] = ''
): Promise<Project | null> {
	try {
		const project: Project = {
			name,
			description,
			username,
			active: true,
			assets: {} as Project['assets']
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

async function update(project: IProject): Promise<IProject | null> {
	try {
		const { id, name, description, active, username } = project;
		const response = await API.put(`/projects/${id}`, { id, name, description, active, username });
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
 * @param projectId {IProject["id"]} - the id of the project to be removed
 * @return boolean - if the removal was succesful
 */
async function remove(projectId: IProject['id']): Promise<boolean> {
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
 * Get project assets for a given project per id
 * @param projectId projet id to get assets for
 * @param types optional list of types. If none are given we assume you want it all
 * @return ProjectAssets|null - the appropriate project, or null if none returned by API
 */
async function getAssets(projectId: string, types?: string[]): Promise<ProjectAssets | null> {
	try {
		let url = `/projects/${projectId}/assets`;
		if (types) {
			types.forEach((type, indx) => {
				// add URL with format: ...?types=A&types=B&types=C
				url += `${indx === 0 ? '?' : '&'}types=${type}`;
			});
		} else {
			Object.values(AssetType).forEach((type, indx) => {
				if (type !== AssetType.Documents) url += `${indx === 0 ? '?' : '&'}types=${type}`;
			});
		}
		const response = await API.get(url);
		const { status, data } = response;
		if (status !== 200) return null;
		data.documents = [
			{
				id: '586e8b37-b79b-4ef3-ac3d-f240c0b8c8de',
				name: 'SIDARTHE - Lack of practical identifiability may hamper reliable predictions in COVID-19 epidemic models',
				username: 'Adam Smith',
				description: 'string',
				timestamp: '2023-09-27T13:54:50',
				file_names: ['paper.pdf'],
				metadata: {},
				document_url:
					'https://github.com/DARPA-ASKEM/knowledge-middleware/blob/main/tests/scenarios/sidarthe/paper.pdf',
				source: 'Science Advances',
				text: 'INTRODUCTION\nThe pandemic caused by severe acute respiratory syndrome\ncoronavirus-2 is challenging humanity in an unprecedented way\n(1), with the disease, which in a few months has spread around the\nworld, affecting large parts of the population (2, 3) and often requir-\ning hospitalization or even intensive care (4, 5). Mitigating the impact\nof coronavirus disease 2019 (COVID-19) urges synergistic efforts to\nunderstand, predict, and control the many, often elusive, facets of\n...',
				grounding: {
					identifiers: {},
					context: {}
				},
				assets: [
					{
						file_name: 'figure1.png',
						asset_type: 'image',
						metadata: {
							type: 'figures',
							description: 'Figure showing the spread rate',
							dimensions: '1200x800'
						}
					},
					{
						file_name: 'equation1.tex',
						asset_type: 'equation',
						metadata: {
							type: 'equations',
							content: '\\frac{dI}{dt} = \\beta SI'
						}
					}
				]
			}
		];
		return data ?? null;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

/**
 * Get projects publication assets for a given project per id
 * @param projectId projet id to get assets for
 * @return ExternalPublication[] the documents assets for the project
 */
async function getPublicationAssets(projectId: string): Promise<ExternalPublication[]> {
	try {
		const url = `/projects/${projectId}/assets?types=${AssetType.Publications}`;
		const response = await API.get(url);
		const { status, data } = response;
		if (status === 200) {
			return data?.[AssetType.Publications] ?? ([] as ExternalPublication[]);
		}
	} catch (error) {
		logger.error(error);
	}
	return [] as ExternalPublication[];
}

/**
 * Add project asset
 * @projectId string - represents the project id wherein the asset will be added
 * @assetType string - represents the type of asset to be added, e.g., 'documents'
 * @assetId string - represents the id of the asset to be added. This will be the internal id of some asset stored in one of the data service collections
 * @return any|null - some result if success, or null if none returned by API
 */
async function addAsset(projectId: string, assetsType: string, assetId: string) {
	// FIXME: handle cases where assets is already added to the project
	const url = `/projects/${projectId}/assets/${assetsType}/${assetId}`;
	const response = await API.post(url);

	EventService.create(
		EventType.AddResourcesToProject,
		projectId,
		JSON.stringify({
			assetsType,
			assetId
		})
	);
	return response?.data ?? null;
}

/**
 * Delete a project asset
 * @projectId IProject["id"] - represents the project id wherein the asset will be added
 * @assetType AssetType - represents the type of asset to be added, e.g., 'documents'
 * @assetId string | number - represents the id of the asset to be added. This will be the internal id of some asset stored in one of the data service collections
 * @return boolean
 */
async function deleteAsset(
	projectId: IProject['id'],
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
 * @param projectId - string
 * @param containingAssetsInformation - boolean - Add the assets information during the same call
 * @return Project|null - the appropriate project, or null if none returned by API
 */
async function get(
	projectId: string,
	containingAssetsInformation: boolean = false
): Promise<IProject | null> {
	try {
		const { status, data } = await API.get(`/projects/${projectId}`);
		if (status !== 200) return null;
		const project = data as IProject;

		if (project && containingAssetsInformation) {
			const assets = await getAssets(projectId);
			if (assets) {
				project.assets = assets;
			}
		}

		return project ?? null;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

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
	getAssets,
	getAssetIcon,
	getPublicationAssets
};
