/**
 * Datasets
 */

import API from '@/api/api';
import { isEmpty, cloneDeep } from 'lodash';
import { logger } from '@/utils/logger';
import type { CsvAsset, CsvColumnStats, Dataset, PresignedURL } from '@/types/Types';
import { Ref } from 'vue';
import { AxiosResponse } from 'axios';
import { RunResults } from '@/types/SimulateConfig';

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

async function getClimateDataset(datasetId: string): Promise<Dataset | null> {
	const response = await API.get(`/climatedata/queries/fetch-esgf/${datasetId}`).catch((error) => {
		logger.error(`Error: climate data service was not able to retrieve the dataset ${datasetId} ${error}`);
	});
	return response?.data ?? null;
}

async function getClimateSubsetId(
	esgfId: string,
	parentDatasetId: string,
	envelope: string,
	options: {
		timestamps?: string;
		thinFactor?: string;
	}
): Promise<string | null> {
	const { timestamps, thinFactor } = options;
	const url = `/climatedata/queries/subset-esgf/${esgfId}?parent-dataset-id=${parentDatasetId}&envelope=${envelope}`;
	if (timestamps) url.concat(`&timestamps=${timestamps}`);
	if (thinFactor) url.concat(`&thin-factor=${thinFactor}`);

	let response = await API.get(url);

	// FIXME: Temporary polling solution
	if (response.status === 202) {
		return new Promise((resolve) => {
			const poller = setInterval(async () => {
				response = await API.get(url);
				if (response.status === 200) {
					clearInterval(poller);
					resolve(response?.data ?? null);
				}
			}, 30000);
		});
	}
	if (response.status === 200) {
		return response.data;
	}
	logger.error(`Climate-data service was not able to retrieve the subset of the dataset ${esgfId}`);
	return null;
}

async function getClimateDatasetPreview(esgfId: string): Promise<string | undefined> {
	const response = await API.get(`/climatedata/queries/preview-esgf/${esgfId}`).catch((error) => {
		logger.error(`Error: climate data service was not able to preview the dataset ${esgfId} ${error}`);
	});
	return response?.data ?? undefined;
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
//        so for now we are fetching by issueing multiple API calls
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
		logger.error(`Error: data-service was not able to retrieve the dataset's rawfile ${error}`);
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
 * @param userName
 * @param projectId
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
 * @param userName uploader of this dataset
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
	addToProject?: boolean
): Promise<Dataset | null> {
	if (addToProject === undefined) addToProject = true;
	try {
		const response: AxiosResponse<Dataset> = await API.post(
			`/simulations/${simulationId}/create-result-as-dataset/${projectId}?dataset-name=${datasetName}&add-to-project=${addToProject}`
		);
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

/**
 * This is a client side function to create a CsvAsset similar to the getCsv function from DatasetResource.java
 * The reason we reimplement this client side is because runResults already has all of the data we need, except
 * it's not in the correct format necessary to be used by the TeraDatasetDatatable component.
 * @param runResults
 * @param runId
 * @returns CsvAsset object with the data from the runResults
 */
const createCsvAssetFromRunResults = (runResults: RunResults, runId?: string): CsvAsset | null => {
	const runResult: RunResults = cloneDeep(runResults);
	const runIdList = runId ? [runId] : Object.keys(runResult);
	if (runIdList.length === 0) return null;

	const csvColHeaders = Object.keys(runResult[runIdList[0]][0]);
	let csvData: CsvAsset = {
		headers: csvColHeaders,
		csv: [csvColHeaders],
		rowCount: 0,
		stats: []
	};

	const csvColumns: { [key: string]: number[] } = {};

	runIdList.forEach((id) => {
		csvData = {
			...csvData,
			rowCount: csvData.rowCount + runResult[id].length
		};
		runResult[id].forEach((row) => {
			const rowValues = Object.values(row);
			csvData.csv.push(rowValues as any);

			csvColHeaders.forEach((header, index) => {
				if (!csvColumns[header]) {
					csvColumns[header] = [];
				}
				csvColumns[header].push(+rowValues[index]);
			});
		});
	});

	csvColHeaders.forEach((header) => {
		csvData.stats!.push(getCsvColumnStats(csvColumns[header]));
	});

	return csvData;
};

/**
 * This is a client side implementation of the getStats function from DatasetResource.java
 * NOTE: if performance ever becomes an issue, we can write a new endpoint to call getStats from the backend
 * @param csvColumn
 * @returns CsvColumnStats object
 */
const getCsvColumnStats = (csvColumn: number[]): CsvColumnStats => {
	const sortedCol = [...csvColumn].sort((a, b) => a - b);

	const minValue = sortedCol[0];
	const maxValue = sortedCol[sortedCol.length - 1];
	const mean = sortedCol.reduce((a, b) => a + b, 0) / sortedCol.length;
	const median = sortedCol[Math.floor(sortedCol.length / 2)];

	// Calculate standard deviation
	const squaredDifferences = sortedCol.map((value) => (value - mean) ** 2);
	const variance = squaredDifferences.reduce((a, b) => a + b, 0) / squaredDifferences.length;
	const sd = Math.sqrt(variance);

	// Set up bins
	const binCount = 10;
	const stepSize = (maxValue - minValue) / (binCount - 1);
	let bins: number[];

	if (stepSize === 0) {
		// Every value is the same, so just return a single bin
		bins = [sortedCol.length];
	} else {
		bins = Array(binCount).fill(0);
		// Fill bins
		sortedCol.forEach((value) => {
			const binIndex = Math.abs(Math.floor((value - minValue) / stepSize));
			bins[binIndex]++;
		});
	}

	return { bins, minValue, maxValue, mean, median, sd };
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
		const response = await downloadRawFile(dataset.id, dataset.fileNames[fileNameIndex], limit);
		return response;
	}
	return null;
}

export {
	getDataset,
	getClimateDataset,
	getClimateSubsetId,
	getClimateDatasetPreview,
	updateDataset,
	getBulkDatasets,
	downloadRawFile,
	getDownloadURL,
	createNewDatasetFromFile,
	createNewDatasetFromGithubFile,
	createDatasetFromSimulationResult,
	saveDataset,
	createCsvAssetFromRunResults,
	createDataset,
	getRawContent
};
