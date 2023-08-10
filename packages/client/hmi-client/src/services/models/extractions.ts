import API, { Poller, PollerState, PollResponse } from '@/api/api';
import { AxiosError, AxiosResponse } from 'axios';
import { Artifact, Model } from '@/types/Types';
import { logger } from '@/utils/logger';
import { CodeArtifactExtractionMetaData } from '@/types/Code';

/**
 * Fetch information from the extraction service via the Poller utility
 * @param id
 * @return {Promise<PollerResult>}
 */
export async function fetchExtraction(id: string) {
	const pollerResult: PollResponse<any> = { data: null, progress: null, error: null };
	const poller = new Poller<object>()
		.setPollAction(async () => {
			const response = await API.get(`/extract/status/${id}`);

			// Finished
			if (response?.status === 200 && response?.data?.status === 'finished') {
				pollerResult.data = response.data.result;
				return pollerResult;
			}

			// Failed
			if (response?.status === 200 && response?.data?.status === 'failed') {
				pollerResult.error = true;
				return pollerResult;
			}

			// Queued
			return pollerResult;
		})
		.setThreshold(30);
	return poller.start();
}

/**
 * Transform a list of LaTeX strings to an AMR
 * @param latex string[] - list of LaTeX strings representing a model
 * @param framework [string] - the framework to use for the extraction, default to 'petrinet'
 * @return {Promise<Model | null>}
 */
const latexToAMR = async (latex: string[], framework = 'petrinet'): Promise<Model | null> => {
	try {
		const response: AxiosResponse<Model> = await API.post(
			`/extract/latex-to-amr/${framework}`,
			latex
		);
		if (response && response?.status === 200 && response?.data) {
			return response.data;
		}
		logger.error(`LaTeX to AMR request failed`, { toastTitle: 'Error - SKEMA Unified' });
	} catch (error: unknown) {
		logger.error(error, { showToast: false, toastTitle: 'Error - SKEMA Unified' });
	}
	return null;
};

/**
 * Transform a MathML list of strings to an AMR
 * @param mathml string[] - list of MathML strings representing a model
 * @param framework [string] - the framework to use for the extraction, default to 'petrinet'
 * @return {Promise<Model | null>}
 */
const mathmlToAMR = async (mathml: string[], framework = 'petrinet'): Promise<Model | null> => {
	try {
		const response = await API.post(`/extract/mathml-to-amr?framework=${framework}`, mathml);
		if (response && response?.status === 200) {
			const { id, status } = response.data;
			if (status === 'queued') {
				const result = await fetchExtraction(id);
				if (result?.state === PollerState.Done) {
					return result.data as Model;
				}
			}
			if (status === 'finished') {
				return response.data.result as Model;
			}
		}
		logger.error(`MathML to AMR request failed`, { toastTitle: 'Error - extraction-service' });
	} catch (error: unknown) {
		if ((error as AxiosError).isAxiosError) {
			const axiosError = error as AxiosError;
			logger.error('[extraction-service]', axiosError.response?.data || axiosError.message, {
				showToast: false,
				toastTitle: 'Error - extraction-service'
			});
		} else {
			logger.error(error, { showToast: false, toastTitle: 'Error - extraction-service' });
		}
	}
	return null;
};

/**
 * Transform a source code to an AMR
 * @param artifactId Artifact.id - the artifact id to extract
 * @param name [Model.name] - the name to give to the extracted model
 * @param description [Model.description] - the description to give to the extracted model
 * @return {Promise<Model | null>}
 */
const codeToAMR = async (
	artifactId: Artifact['id'],
	name: Model['name'],
	description: Model['description'],
	metadata?: CodeArtifactExtractionMetaData | null
): Promise<Model | null> =>
	API.post(`/extract/code-to-amr`, { artifactId, name, description, metadata });

/**
 * Given a dataset, enrich its metadata
 * Returns a runId used to poll for result
 */
export const profileDataset = async (datasetId: string, artifactId: string | null = null) => {
	let response: any = null;
	if (artifactId) {
		response = await API.post(`/extract/profile-dataset/${datasetId}?artifact_id=${artifactId}`);
	} else {
		response = await API.post(`/extract/profile-dataset/${datasetId}`);
	}
	console.log('data profile response', response.data);
	return response.data.id;
};

const extractTextFromPDFArtifact = async (artifactId: string): Promise<string | null> => {
	try {
		const response = await API.post(`/extract/pdf-to-text?artifact_id=${artifactId}`);
		if (response?.status === 200 && response?.data?.id) return response.data.id;
		logger.error('pdf text extraction request failed', {
			showToast: false,
			toastTitle: 'Error - pdfExtractions'
		});
	} catch (error: unknown) {
		if ((error as AxiosError).isAxiosError) {
			const axiosError = error as AxiosError;
			logger.error('[pdfExtractions]', axiosError.response?.data || axiosError.message, {
				showToast: false,
				toastTitle: 'Error - pdf text extraction'
			});
		} else {
			logger.error(error, { showToast: false, toastTitle: 'Error - pdf text extraction' });
		}
	}

	return null;
};

const pdfExtractions = async (
	artifactId: string,
	pdfName?: string,
	description?: string
): Promise<string | null> => {
	// I've purposefully excluded the MIT and SKEMA options here, so they're always
	// defaulted to true.

	let url = `/extract/pdf-extractions?artifact_id=${artifactId}`;
	if (pdfName) {
		url += `&pdf_name=${pdfName}`;
	}
	if (description) {
		url += `&description=${description}`;
	}

	try {
		const response = await API.post(url);
		if (response?.status === 200 && response?.data?.id) return response.data.id;
		logger.error('pdf Extractions request failed', {
			showToast: false,
			toastTitle: 'Error - pdfExtractions'
		});
	} catch (error: unknown) {
		if ((error as AxiosError).isAxiosError) {
			const axiosError = error as AxiosError;
			logger.error('[pdfExtractions]', axiosError.response?.data || axiosError.message, {
				showToast: false,
				toastTitle: 'Error - pdfExtractions'
			});
		} else {
			logger.error(error, { showToast: false, toastTitle: 'Error - pdfExtractions' });
		}
	}

	return null;
};

export const extractPDF = async (artifact: Artifact) => {
	if (artifact.id) {
		const resp: string | null = await extractTextFromPDFArtifact(artifact.id);
		if (resp) {
			const pollResult = await fetchExtraction(resp);
			if (pollResult?.state === PollerState.Done) {
				await pdfExtractions(artifact.id); // we don't care now. fire and forget.
			}
		}
	}
};

export { mathmlToAMR, latexToAMR, codeToAMR };
