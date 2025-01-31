/**
 * Datasets
 */

import API from '@/api/api';
import { isEmpty } from 'lodash';
import { logger } from '@/utils/logger';
import type { CsvAsset, Dataset, PresignedURL } from '@/types/Types';
import { Ref } from 'vue';
import { AxiosResponse } from 'axios';
import { FIFOCache } from '@/utils/FifoCache';
import { parseCsvAsset } from '@/utils/csv';
import { DataArray } from '@/services/models/simulation-service';

// Note: This is currently used in compare datasets operator
export const DATASET_VAR_NAME_PREFIX = 'data/';

/**
 * Get Dataset from the data service
 * @return Dataset|null - the dataset, or null if none returned by API
 */
async function getDataset(datasetId: string): Promise<Dataset | null> {
	const response = await API.get(`/datasets/${datasetId}`).catch((error) => {
		logger.error(`Error: data-service was not able to retrieve the dataset ${datasetId} ${error}`);
	});
	return response?.data ?? null;
}

/**
 * Update dataset from the dataservice
 * @return Dataset|null - the dataset, or null if none returned by API
 */
async function updateDataset(dataset: Dataset) {
	const response = await API.put(`/datasets/${dataset.id}`, dataset);
	return response?.data ?? null;
}

//
// Retrieve multiple datasets by their IDs
// FIXME: the backend does not support bulk fetch
//        so for now we are fetching by issuing multiple API calls
async function getBulkDatasets(datasetIDs: string[]) {
	const result: Dataset[] = [];
	const promiseList = [] as Promise<Dataset | null>[];
	datasetIDs.forEach((datasetId) => {
		promiseList.push(getDataset(datasetId));
	});
	const responsesRaw = await Promise.all(promiseList);
	responsesRaw.forEach((r) => {
		if (r) {
			result.push(r);
		}
	});
	return result;
}

/**
 * Get the raw (CSV) file content for a given dataset
 * @return Array<string>|null - the dataset raw content, or null if none returned by API
 */
async function downloadRawFile(datasetId: string, filename: string, limit: number = 100): Promise<CsvAsset | null> {
	const URL = `/datasets/${datasetId}/download-csv?filename=${filename}&limit=${limit}`;
	const response = await API.get(URL).catch((error) => {
		logger.error(`Error: data-service was not able to retrieve the dataset's raw file ${error}`);
	});
	return response?.data ?? null;
}

/**
 * Get the download URL for a given dataset asset
 * @param datasetId the dataset ID
 * @param filename the filename of the asset
 */
async function getDownloadURL(datasetId: string, filename: string): Promise<PresignedURL | null> {
	const response: AxiosResponse<PresignedURL> = await API.get(
		`/datasets/${datasetId}/download-url?filename=${filename}`
	);
	if (response.data && response.status === 200) {
		return response.data;
	}
	return null;
}

/**
 * Creates a new dataset in TDS from the dataset given (required name, url, description).
 * @param dataset the dataset with updated storage ID from tds
 */
async function createDataset(dataset: Dataset): Promise<Dataset | null> {
	const resp = await API.post('/datasets', dataset);
	if (resp && resp.status < 400 && resp.data) {
		return resp.data;
	}
	console.log(`Error creating new dataset ${resp?.status}`);
	return null;
}

/**
 * This is a helper function which creates a new dataset and adds a given CSV file to it. The data set will
 * share the same name as the file and can optionally have a description
 * @param repoOwnerAndName
 * @param path
 * @param userId
 * @param url the source url of the file
 */
async function createNewDatasetFromGithubFile(repoOwnerAndName: string, path: string, userId: string, url: string) {
	// Find the file name by removing the path portion
	const fileName: string | undefined = path.split('/').pop();

	if (!fileName) return null;

	// Remove the file extension from the name, if any
	const name: string = fileName?.replace(/\.[^/.]+$/, '');

	// Create a new dataset with the same name as the file, and post the metadata to TDS
	const dataset: Dataset = {
		name,
		datasetUrl: url,
		description: path,
		fileNames: [fileName],
		userId
	};

	const newDataset: Dataset | null = await createDataset(dataset);
	if (!newDataset || !newDataset.id) return null;

	const urlResponse = await API.put(
		`/datasets/${newDataset.id}/upload-csv-from-github?filename=${fileName}&path=${path}&repo-owner-and-name=${repoOwnerAndName}`,
		{
			timeout: 3600000
		}
	);

	if (!urlResponse || urlResponse.status >= 400) {
		return null;
	}

	return newDataset;
}

/**
 * This is a helper function which creates a new dataset and adds a given file to it. The data set will
 * share the same name as the file and can optionally have a description
 * @param progress reference to display in ui
 * @param file an arbitrary or csv file
 * @param userId
 * @param description description of the file. Optional. If not given description will be just the csv name
 */
async function createNewDatasetFromFile(
	progress: Ref<number>,
	file: File,
	userId: string,
	description?: string
): Promise<Dataset | null> {
	const fileType = file.name.endsWith('.csv') ? 'csv' : 'file';
	// Remove the file extension from the name, if any
	const name = file.name.replace(/\.[^/.]+$/, '');

	// Create a new dataset with the same name as the file, and post the metadata to TDS
	const dataset: Dataset = {
		name,
		description: description || file.name,
		fileNames: [file.name],
		userId
	};

	const newDataset: Dataset | null = await createDataset(dataset);
	if (!newDataset || !newDataset.id) return null;

	const formData = new FormData();
	formData.append('file', file);

	const urlResponse = await API.put(`/datasets/${newDataset.id}/upload-${fileType}`, formData, {
		params: {
			filename: file.name
		},
		headers: {
			'Content-Type': 'multipart/form-data'
		},
		onUploadProgress(progressEvent) {
			progress.value = Math.min(90, Math.round((progressEvent.loaded * 100) / (progressEvent?.total ?? 100)));
		},
		timeout: 3600000
	});

	if (!urlResponse || urlResponse.status >= 400) {
		return null;
	}

	return newDataset;
}

async function createDatasetFromSimulationResult(
	projectId: string,
	simulationId: string,
	datasetName: string | null,
	addToProject: boolean = true,
	modelConfigurationId?: string,
	interventionPolicyId?: string
): Promise<Dataset | null> {
	try {
		let URL = `/simulations/${simulationId}/create-result-as-dataset/${projectId}?dataset-name=${datasetName}&add-to-project=${addToProject}`;
		if (modelConfigurationId) URL += `&model-configuration-id=${modelConfigurationId}`;
		if (interventionPolicyId) URL += `&intervention-policy-id=${interventionPolicyId}`;
		const response: AxiosResponse<Dataset> = await API.post(URL);
		return response.data as Dataset;
	} catch (error) {
		logger.error(`/simulations/{id}/create-result-as-dataset/{projectId} not responding:  ${error}`, {
			toastTitle: 'TDS - Simulation'
		});
		return null;
	}
}

const saveDataset = async (projectId: string, simulationId: string | undefined, datasetName: string | null) => {
	if (!simulationId) return false;
	const response = await createDatasetFromSimulationResult(projectId, simulationId, datasetName);
	return response !== null;
};

async function getRawContent(
	dataset: Dataset,
	limit: number = 100,
	fileNameIndex: number = 0
): Promise<CsvAsset | null> {
	// If it's an ESGF dataset or a NetCDF file, we don't want to download the raw content
	if (!dataset?.id || dataset.esgfId || dataset.metadata?.format === 'netcdf') return null;
	// We are assuming here there is only a single csv file.
	if (
		dataset.fileNames &&
		!isEmpty(dataset.fileNames) &&
		!isEmpty(dataset.fileNames[fileNameIndex]) &&
		dataset.fileNames[fileNameIndex].endsWith('.csv')
	) {
		return downloadRawFile(dataset.id, dataset.fileNames[fileNameIndex], limit);
	}
	return null;
}

async function getCsvAsset(dataset: Dataset, filename: string, limit: number = -1): Promise<CsvAsset | null> {
	// If it's an ESGF dataset or a NetCDF file, we don't want to download the raw content
	if (!dataset?.id || dataset.esgfId || dataset.metadata?.format === 'netcdf') return null;
	return (await downloadRawFile(dataset.id as string, filename, limit)) as CsvAsset;
}

const datasetResultCSVCache = new FIFOCache<Promise<CsvAsset | null>>(100);
async function getDatasetResultCSV(dataset: Dataset, filename: string, renameFn?: (s: string) => string) {
	const cacheKey = `${dataset.id}:${filename}`;
	let promise = datasetResultCSVCache.get(cacheKey);
	if (!promise) {
		promise = getCsvAsset(dataset, filename);
		datasetResultCSVCache.set(cacheKey, promise);
	}
	const result = await promise;
	if (!result) return [];
	// we should not modify the original result since it may have been cached and persisted in memory
	const csvAsset = { ...result };
	if (renameFn) {
		csvAsset.headers = csvAsset.headers.map((header) => header.trim()).map(renameFn);
	}
	const output = parseCsvAsset(csvAsset);
	return output as DataArray;
}

/**
 * Merge multiple datasets into a single dataset
 * For example, if you have two datasets with the following data:
 * dataset1 = [{a: 1, b: 2}, {a: 3, b: 4}]
 * dataset2 = [{c: 5, d: 6}, {c: 7, d: 8}]
 * The merged dataset will be:
 * [{a: 1, b: 2, c: 5, d: 6}, {a: 3, b: 4, c: 7, d: 8}]
 * @param results The datasets to merge
 * @returns The merged dataset
 */
const mergeResults = (...results: DataArray[]) => {
	const maxLength = Math.max(...results.map((result) => result.length));
	const result: DataArray = [];
	for (let i = 0; i < maxLength; i++) {
		const row: Record<string, number> = {};
		results.forEach((dataset) => Object.assign(row, dataset[i]));
		result.push(row);
	}
	return result;
};

export {
	getDataset,
	updateDataset,
	getBulkDatasets,
	downloadRawFile,
	getDownloadURL,
	createNewDatasetFromFile,
	createNewDatasetFromGithubFile,
	createDatasetFromSimulationResult,
	saveDataset,
	createDataset,
	getRawContent,
	getCsvAsset,
	getDatasetResultCSV,
	mergeResults
};
