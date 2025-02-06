import API from '@/api/api';
import type { TaskResponse } from '@/types/Types';
import { logger } from '@/utils/logger';

/**
 * Fetches model card data from the server and wait for task to finish.
 * @param {string} modelId - The model ID.
 * @param {string} documentId - The document ID.
 */
export async function modelCard(modelId: string, documentId?: string): Promise<void> {
	try {
		await API.post<TaskResponse>('/gollm/model-card', null, {
			params: {
				'model-id': modelId,
				'document-id': documentId
			}
		});
	} catch (err) {
		logger.error(err);
	}
}

/**
 * Fetches dataset card data from the server and wait for task to finish.
 * @param {string} datasetId - The model ID.
 * @param {string} documentId - The document ID.
 */
export async function datasetCard(datasetId: string, documentId?: string): Promise<TaskResponse> {
	const { data } = await API.get<TaskResponse>('/gollm/enrich-dataset', {
		params: {
			'dataset-id': datasetId,
			'document-id': documentId
		}
	});
	return data;
}

export async function interventionPolicyFromDocument(
	documentId: string,
	modelId: string,
	workflowId?: string,
	nodeId?: string
): Promise<TaskResponse> {
	const { data } = await API.get<TaskResponse>('/gollm/interventions-from-document', {
		params: {
			'model-id': modelId,
			'document-id': documentId,
			'workflow-id': workflowId,
			'node-id': nodeId
		}
	});
	return data;
}

export async function interventionPolicyFromDataset(
	datasetId: string,
	modelId: string,
	workflowId?: string,
	nodeId?: string
) {
	const { data } = await API.get<TaskResponse>('/gollm/interventions-from-dataset', {
		params: {
			'model-id': modelId,
			'dataset-id': datasetId,
			'workflow-id': workflowId,
			'node-id': nodeId
		}
	});
	return data;
}

export async function enrichModelMetadata(
	modelId: string,
	documentId: string,
	overwrite: boolean
): Promise<TaskResponse> {
	const { data } = await API.get<TaskResponse>('/gollm/enrich-model-metadata', {
		params: {
			'model-id': modelId,
			'document-id': documentId,
			overwrite
		}
	});
	return data;
}

export async function configureModelFromDocument(
	documentId: string,
	modelId: string,
	workflowId?: string,
	nodeId?: string
): Promise<TaskResponse> {
	const { data } = await API.get<TaskResponse>('/gollm/configure-model', {
		params: {
			'model-id': modelId,
			'document-id': documentId,
			'workflow-id': workflowId,
			'node-id': nodeId
		}
	});
	return data;
}

export async function equationsFromImage(base64ImageStr: string): Promise<TaskResponse> {
	const params: Record<string, any> = { mode: 'SYNC' };
	const { data } = await API.post<TaskResponse>('/gollm/equations-from-image', { base64ImageStr }, { params });
	return data;
}

export async function configureModelFromDataset(
	modelId: string,
	datasetId: string,
	matrixStr: string,
	workflowId?: string,
	nodeId?: string
): Promise<TaskResponse> {
	const { data } = await API.post<TaskResponse>(
		'/gollm/configure-from-dataset',
		{ matrixStr },
		{
			params: {
				'model-id': modelId,
				'dataset-id': datasetId,
				'workflow-id': workflowId,
				'node-id': nodeId
			}
		}
	);
	return data;
}

export async function compareModels(
	modelIds: string[],
	goal?: string,
	workflowId?: string,
	nodeId?: string
): Promise<TaskResponse> {
	const { data } = await API.get<TaskResponse>('/gollm/compare-models', {
		params: {
			'model-ids': modelIds.join(','),
			goal,
			'workflow-id': workflowId,
			'node-id': nodeId
		}
	});
	return data;
}

export async function cancelTask(taskId: string): Promise<void> {
	try {
		await API.put<TaskResponse>(`/gollm/${taskId}`);
	} catch (err) {
		logger.error(`An issue occurred while cancelling task with id: ${taskId}. ${err}`);
	}
}
