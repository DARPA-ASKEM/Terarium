import API from '@/api/api';
import type { Summary } from '@/types/Types';

type SummarizationMode = 'ASYNC' | 'SYNC';
export const createLLMSummary = async (prompt: string, mode: SummarizationMode = 'ASYNC') => {
	const response = API.post(`/gollm/generate-summary?mode=${mode}`, prompt);
	return response;
};

export const createSummary = async (summary: Summary) => {
	const response = await API.put('/summary', summary);
	return response.data;
};

export const updateSummary = async (summary: Summary) => {
	const response = await API.post('/summary', summary);
	return response.data;
};

export const getSummaries = async (ids: string[]) => {
	const response = await API.post('/summary/search', ids);
	return response.data;
};
