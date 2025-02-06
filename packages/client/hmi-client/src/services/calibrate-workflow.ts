import type { Dataset, CsvAsset } from '@/types/Types';
import { getModelConfigurationById, getObservables } from '@/services/model-configurations';
import { getCsvAsset } from '@/services/dataset';
import { getUnitsFromModelParts, getModelByModelConfigurationId, getTypesFromModelParts } from '@/services/model';

export interface CalibrateMap {
	modelVariable: string;
	datasetVariable: string;
}

export const isCalibrateMap = (obj: any): obj is CalibrateMap =>
	obj.modelVariable !== undefined && obj.datasetVariable !== undefined;

// Used in the setup of calibration node and drilldown.
// Takes a model-configuration id and grabs relevant objects.
export async function setupModelInput(modelConfigId: string | undefined) {
	if (!modelConfigId) return {};

	const [modelConfiguration, model] = await Promise.all([
		getModelConfigurationById(modelConfigId),
		getModelByModelConfigurationId(modelConfigId)
	]);

	if (!model) return { modelConfiguration };

	const modelPartUnits = getUnitsFromModelParts(model);
	const modelPartTypes = getTypesFromModelParts(model);
	const modelOptions: any[] = [...model.model.states, ...getObservables(modelConfiguration)];

	return {
		model,
		modelConfiguration,
		modelOptions,
		modelPartUnits,
		modelPartTypes
	};
}

export const setupCsvAsset = async (dataset: Dataset): Promise<CsvAsset | undefined> => {
	const filename = getFileName(dataset);
	// FIXME: We are setting the limit to -1 (i.e. no limit) on the number of rows returned.
	// This is a temporary fix since the datasets could be very large.
	const limit = -1;
	const csv = await getCsvAsset(dataset, filename, limit);
	return csv ?? undefined;
};

export const getFileName = (dataset: Dataset) =>
	// If our dataset includes a result.csv we will ensure to pick it.
	dataset.fileNames?.includes('result.csv') ? 'result.csv' : (dataset?.fileNames?.[0] ?? '');
