/**
 * Documents Asset
 */

import API from '@/api/api';
import type { Document, DocumentAsset } from '@/types/Types';
import { logger } from '@/utils/logger';
import { Ref } from 'vue';

/**
 * Get all documents
 * @return Array<DocumentAsset>|null - the list of all document assets, or null if none returned by API
 */
async function getAll(): Promise<DocumentAsset[] | null> {
	const response = await API.get('/document-asset').catch((error) => {
		logger.error(`Error: ${error}`);
	});
	return response?.data ?? null;
}

/**
 * Get DocumentAsset from the data service
 * @return DocumentAsset|null - the dataset, or null if none returned by API
 */
async function getDocumentAsset(documentId: string): Promise<DocumentAsset | null> {
	const response = await API.get(`/document-asset/${documentId}`).catch((error) => {
		logger.error(
			`Error: data-service was not able to retreive the document asset ${documentId} ${error}`
		);
	});
	return response?.data ?? null;
}

/**
 * This is a helper function which uploads an arbitrary document to TDS and creates a new document asset from it.
 * @param file the file to upload
 * @param userName owner of this project
 * @param projectId the project ID
 * @param description? description of the file. Optional. If not given description will be just the file name
 * @param progress? reference to display in ui
 */
async function uploadDocumentAssetToProject(
	file: File,
	userId: string,
	description?: string,
	progress?: Ref<number>
): Promise<DocumentAsset | null> {
	// Create a new document with the same name as the file, and post the metadata to TDS
	const documentAsset: DocumentAsset = {
		name: file.name,
		description: description || file.name,
		fileNames: [file.name],
		userId
	};

	const newDocumentAsset: DocumentAsset | null = await createNewDocumentAsset(documentAsset);
	if (!newDocumentAsset || !newDocumentAsset.id) return null;

	const successfulUpload = await addFileToDocumentAsset(newDocumentAsset.id, file, progress);
	if (!successfulUpload) return null;

	return newDocumentAsset;
}

async function createNewDocumentFromGithubFile(
	repoOwnerAndName: string,
	path: string,
	userId: string,
	description?: string
) {
	// Find the file name by removing the path portion
	const fileName: string | undefined = path.split('/').pop();

	if (!fileName) return null;

	// Create a new document with the same name as the file, and post the metadata to TDS
	const documentAsset: DocumentAsset = {
		name: fileName,
		description: description || fileName,
		fileNames: [fileName],
		userId
	};

	const newDocument: DocumentAsset | null = await createNewDocumentAsset(documentAsset);
	if (!newDocument || !newDocument.id) return null;

	const urlResponse = await API.put(
		`/document-asset/${newDocument.id}/upload-document-from-github?filename=${fileName}&path=${path}&repo-owner-and-name=${repoOwnerAndName}`,
		{
			timeout: 3600000
		}
	);

	if (!urlResponse || urlResponse.status >= 400) {
		return null;
	}

	return newDocument;
}

/**
 * Creates a new document asset in TDS and returns the new document asset object id
 * @param document the document asset to create
 */
async function createNewDocumentAsset(documentAsset: DocumentAsset): Promise<DocumentAsset | null> {
	const response = await API.post('/document-asset', documentAsset);
	if (!response || response.status >= 400) return null;
	return response.data;
}

/**
 * Adds a file to a document in TDS
 * @param documentId the documentId to add the file to
 * @param file the file to upload
 */
async function addFileToDocumentAsset(
	documentId: string,
	file: File,
	progress?: Ref<number>
): Promise<boolean> {
	const formData = new FormData();
	formData.append('file', file);

	const response = await API.put(`/document-asset/${documentId}/upload-document`, formData, {
		params: {
			filename: file.name
		},
		headers: {
			'Content-Type': 'multipart/form-data'
		},
		onUploadProgress(progressEvent) {
			if (progress) {
				progress.value = Math.min(
					90,
					Math.round((progressEvent.loaded * 100) / (progressEvent?.total ?? 100))
				);
			}
		},
		timeout: 3600000
	});

	return response && response.status < 400;
}

async function downloadDocumentAsset(documentId: string, fileName: string): Promise<any> {
	try {
		const response = await API.get(
			`document-asset/${documentId}/download-document?filename=${fileName}`,
			{ responseType: 'arraybuffer' }
		);
		const blob = new Blob([response?.data], { type: 'application/pdf' });
		const pdfLink = window.URL.createObjectURL(blob);
		return pdfLink ?? null;
	} catch (error) {
		logger.error(`Error: Unable to download pdf for document asset ${documentId}: ${error}`);
		return null;
	}
}

async function getDocumentFileAsText(documentId: string, fileName: string): Promise<string | null> {
	const response = await API.get(
		`/document-asset/${documentId}/download-document-as-text?filename=${fileName}`,
		{}
	);

	if (!response) {
		return null;
	}

	return response.data;
}

async function getEquationFromImageUrl(
	documentId: string,
	filename: string
): Promise<string | null> {
	const response = await API.get(
		`/document-asset/${documentId}/image-to-equation?filename=${filename}`,
		{}
	);

	if (!response) {
		return null;
	}

	return response.data;
}

async function getBulkDocumentAssets(docIDs: string[]) {
	const result: DocumentAsset[] = [];
	const promiseList = [] as Promise<DocumentAsset | null>[];
	docIDs.forEach((docId) => {
		promiseList.push(getDocumentAsset(docId));
	});
	const responsesRaw = await Promise.all(promiseList);
	responsesRaw.forEach((r) => {
		if (r) {
			result.push(r);
		}
	});
	return result;
}

async function createDocumentFromXDD(
	document: Document,
	projectId: string
): Promise<DocumentAsset | null> {
	if (!document || !projectId) return null;
	const response = await API.post<DocumentAsset>(`/document-asset/create-document-from-xdd`, {
		document,
		projectId
	});
	// TODO : subscribe to EXTRACTION messages
	return response.data ?? null;
}
export {
	createDocumentFromXDD,
	createNewDocumentAsset,
	createNewDocumentFromGithubFile,
	downloadDocumentAsset,
	getAll,
	getBulkDocumentAssets,
	getDocumentAsset,
	getDocumentFileAsText,
	getEquationFromImageUrl,
	uploadDocumentAssetToProject
};
