import API from '@/api/api';
import { AxiosResponse } from 'axios';
import { extractionStatusUpdateHandler, subscribe } from '@/services/ClientEventService';
import { type DocumentAsset, type Model, ClientEventType } from '@/types/Types';
import { logger } from '@/utils/logger';
import { Workflow, WorkflowNode } from '@/types/workflow';

/**
 * Clean up a list of equations
 * @param equations string[] - list of LaTeX or mathml strings representing a model
 * @return {Promise<string[]>}
 */
export const getCleanedEquations = async (
	equations: string[]
): Promise<{ cleanedEquations: string[]; wasCleaned: boolean } | null> => {
	try {
		const response = await API.post<{ cleanedEquations: string[]; wasCleaned: boolean }>(
			`/knowledge/clean-equations`,
			equations
		);
		return response.data ?? null;
	} catch (error: unknown) {
		logger.error(error, { showToast: false });
	}
	return null;
};

/**
 * Define the request type
 * @param equations string[] - list of LaTeX or mathml strings representing a model
 * @param modelId string= - the model id to use for the extraction
 * @param documentId string= - the document id source of the equations
 */
export interface EquationsToAMRRequest {
	equations: string[];
	modelId?: Model['id'];
	documentId?: DocumentAsset['id'];
	workflowId?: Workflow['id'];
	nodeId?: WorkflowNode<any>['id'];
	extractionService?: 'mira' | 'skema';
}

/**
 * Transform a list of LaTeX or mathml strings to an AMR
 * @param request EquationsToAMRRequest
 * @return {Promise<any>}
 */
export const equationsToAMR = async (request: EquationsToAMRRequest): Promise<string | null> => {
	try {
		const response: AxiosResponse<string> = await API.post(`/knowledge/equations-to-model`, request);
		return response.data;
	} catch (error: unknown) {
		logger.error(error, { showToast: false });
	}
	return null;
};

/**
 * Extract text and artifacts from a PDF document
 * @param documentId string - the document id to extract text
 */
export const extractPDF = async (documentId: DocumentAsset['id']) => {
	if (documentId) {
		const response = await API.post(`/knowledge/pdf-extractions?document-id=${documentId}`);
		if (response?.status === 202) {
			await subscribe(ClientEventType.Extraction, extractionStatusUpdateHandler);
		}
	}
};
