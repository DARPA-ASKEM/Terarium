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

// Used in the setup of calibration node and drill down
// Takes a model config Id and grabs relevant objects
export const setupModelInput = async (modelConfigId: string | undefined) => {
	if (modelConfigId) {
		const [modelConfiguration, model] = await Promise.all([
			getModelConfigurationById(modelConfigId),
			getModelByModelConfigurationId(modelConfigId)
		]);

		const modelPartUnits = !model ? {} : getUnitsFromModelParts(model);
		const modelPartTypes = !model ? {} : getTypesFromModelParts(model);

		const modelOptions: any[] = model?.model.states;

		getObservables(modelConfiguration).forEach((o) => {
			modelOptions.push(o);
		});

		return {
			model,
			modelConfiguration,
			modelOptions,
			modelPartUnits,
			modelPartTypes
		};
	}
	return {};
};

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
