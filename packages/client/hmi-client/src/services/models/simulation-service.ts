import { csvParse, autoType } from 'd3';
import { logger } from '@/utils/logger';
import API from '@/api/api';
import {
	CalibrationRequestCiemss,
	ClientEvent,
	ClientEventType,
	EnsembleCalibrationCiemssRequest,
	EnsembleSimulationCiemssRequest,
	EventType,
	OptimizeRequestCiemss,
	ProgressState,
	Simulation,
	SimulationRequest,
	CsvAsset,
	SimulationType,
	TerariumAsset
} from '@/types/Types';
import { RunResults } from '@/types/SimulateConfig';
import * as EventService from '@/services/event';
import { useProjects } from '@/composables/project';
import { subscribe, unsubscribe } from '@/services/ClientEventService';
import { FIFOCache } from '@/utils/FifoCache';
import { AxiosResponse } from 'axios';

export type DataArray = Record<string, any>[];

export enum CiemssMethodOptions {
	dopri5 = 'dopri5',
	euler = 'euler'
}

export async function cancelCiemssJob(runId: String) {
	try {
		const resp = await API.get(`simulation-request/ciemss/cancel/${runId}`);
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeForecastJobCiemss(simulationParam: SimulationRequest, metadata?: any) {
	try {
		const resp = await API.post('simulation-request/ciemss/forecast', {
			payload: simulationParam,
			metadata
		});
		EventService.create(
			EventType.TransformPrompt,
			useProjects().activeProject.value?.id,
			JSON.stringify({
				type: 'ciemss',
				params: simulationParam
			})
		);
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function getRunResult(runId: string, filename: string) {
	try {
		const resp = await API.get(`simulations/${runId}/result`, {
			params: { filename }
		});
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

const dataArrayCache = new FIFOCache<Promise<string>>(100);
export async function getRunResultCSV(
	runId: string,
	filename: string,
	renameFn?: (s: string) => string
): Promise<DataArray> {
	try {
		const cacheKey = `${runId}:${filename}`;

		let promise = dataArrayCache.get(cacheKey);
		if (!promise) {
			promise = API.get(`simulations/${runId}/result`, {
				params: { filename }
			}).then((res) => res.data);

			dataArrayCache.set(cacheKey, promise);
		}

		// If a rename function is defined, loop over the first row
		let dataStr = await promise;
		if (renameFn) {
			const lines = dataStr.split(/\n/);
			const line0 = lines[0].split(/,/).map(renameFn).join(',');
			lines[0] = line0;
			dataStr = lines.join('\n');
		}
		const output = csvParse(dataStr, autoType);
		return output;
	} catch (err) {
		logger.error(err);
		return [];
	}
}

// Return the presigned download URL of the calibration output blob
// This is only applicable to CIEMSS functionalities
export async function getCalibrateBlobURL(runId: string) {
	const resp = await API.get(`simulations/${runId}/download-url`, {
		params: { filename: 'parameters.dill' }
	});
	return resp.data.url;
}

// @deprecated - The notion of RunResult is a outdated with introduction of Vegalite charts
// that use a more barebone setup closer to the raw data
export async function getRunResultCiemss(runId: string, filename = 'result.csv') {
	const resultCsv = await getRunResult(runId, filename);
	const csvData = csvParse(resultCsv);

	const output = {
		parsedRawData: csvData,
		runResults: {} as RunResults,
		runConfigs: {} as { [paramKey: string]: number[] }
	};
	const { parsedRawData, runResults, runConfigs } = output;

	const sampleList = new Array(Number(parsedRawData[parsedRawData.length - 1].sample_id) + 1)
		.fill('0')
		.map((_x, i) => i.toString());

	// initialize runResults ds
	for (let i = 0; i < sampleList.length; i++) {
		runResults[i.toString()] = [];
	}

	// populate runResults
	parsedRawData.forEach((inputRow) => {
		const outputRowRunResults = { timestamp: inputRow.timepoint_id };
		Object.keys(inputRow).forEach((key) => {
			if (key.endsWith('_observable_state')) {
				const newKey = key.replace(/_observable_state$/, '');
				outputRowRunResults[newKey] = inputRow[key];
				if (!runConfigs[newKey]) {
					runConfigs[newKey] = [];
				}
				runConfigs[newKey].push(Number(inputRow[key]));
				return;
			}
			if (key.endsWith('_state')) {
				const newKey = key.replace(/_state$/, '');
				outputRowRunResults[newKey] = inputRow[key];
				if (!runConfigs[newKey]) {
					runConfigs[newKey] = [];
				}
				runConfigs[newKey].push(Number(inputRow[key]));
				return;
			}
			if (key.startsWith('persistent_') && key.endsWith('_param')) {
				const newKey = key.replace(/_param$/, '').replace(/^persistent_/, '');
				outputRowRunResults[newKey] = inputRow[key];
				if (!runConfigs[newKey]) {
					runConfigs[newKey] = [];
				}
				runConfigs[newKey].push(Number(inputRow[key]));
			}
		});
		runResults[inputRow.sample_id as string].push(outputRowRunResults as any);
	});

	Object.keys(runConfigs).forEach((key) => {
		runConfigs[key] = runConfigs[key].sort();
	});

	return output;
}

export async function getSimulation(id: Simulation['id']): Promise<Simulation | null> {
	try {
		const response = await API.get(`/simulations/${id}`);
		return response.data;
	} catch (error) {
		logger.error(error);
		return null;
	}
}

export async function makeCalibrateJobCiemss(calibrationParams: CalibrationRequestCiemss, metadata?: any) {
	try {
		const resp = await API.post('simulation-request/ciemss/calibrate', {
			payload: calibrationParams,
			metadata
		});
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeOptimizeJobCiemss(optimizeParams: OptimizeRequestCiemss, metadata?: any) {
	try {
		const resp = await API.post('simulation-request/ciemss/optimize', {
			payload: optimizeParams,
			metadata
		});
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeEnsembleCiemssSimulation(params: EnsembleSimulationCiemssRequest, metadata?: any) {
	try {
		const resp = await API.post('simulation-request/ciemss/ensemble-simulate', {
			payload: params,
			metadata
		});
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeEnsembleCiemssCalibration(params: EnsembleCalibrationCiemssRequest, metadata?: any) {
	try {
		const resp = await API.post('simulation-request/ciemss/ensemble-calibrate', {
			payload: params,
			metadata
		});
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function createSimulationAssets(
	simulationId: string,
	simulationType: SimulationType,
	newName: string | null,
	assetId?: string
) {
	try {
		const response: AxiosResponse<TerariumAsset[]> = await API.post(
			`/simulations/${simulationId}/create-assets-from-simulation`,
			{
				name: newName,
				simulationType,
				assetId
			}
		);
		const output = response.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function subscribeToUpdateMessages(
	simulationIds: string[],
	eventType: ClientEventType,
	messageHandler: (data: ClientEvent<any>) => void
) {
	await API.get(`/simulations/subscribe?simulation-ids=${simulationIds}`);
	await subscribe(eventType, messageHandler);
}

export async function unsubscribeToUpdateMessages(
	simulationIds: string[],
	eventType: ClientEventType,
	messageHandler: (data: ClientEvent<any>) => void
) {
	await API.get(`/simulations/unsubscribe?simulation-ids=${simulationIds}`);
	await unsubscribe(eventType, messageHandler);
}

export async function pollAction(id: string) {
	const simResponse: Simulation | null = await getSimulation(id);
	if (!simResponse) {
		console.error(`Error occured with simulation ${id}`);
		return { data: null, progress: null, error: `Failed running simulation ${id}` };
	}

	if ([ProgressState.Queued, ProgressState.Running].includes(simResponse.status)) {
		// TODO: untangle progress
		return { data: null, progress: simResponse, error: null };
	}

	if ([ProgressState.Error, ProgressState.Failed].includes(simResponse.status)) {
		const errorMessage: string = simResponse.statusMessage || `Failed running simulation ${id}`;
		return { data: null, progress: null, error: errorMessage };
	}

	if (simResponse.status === ProgressState.Cancelled) {
		return { data: null, progress: null, error: null, cancelled: true };
	}
	return { data: simResponse, progress: null, error: null };
}

// FIXME: PyCIEMSS renames state and parameters, should consolidate upstream in pyciemss-service
export const parsePyCiemssMap = (obj: Record<string, any>) => {
	const keys = Object.keys(obj);
	const result: Record<string, string> = {};

	keys.forEach((k) => {
		if (k.endsWith('_observable_state')) {
			const newKey = k.replace(/_observable_state$/, '');
			result[newKey] = k;
			return;
		}

		if (k.endsWith('_state')) {
			const newKey = k.replace(/_state$/, '');
			result[newKey] = k;
			return;
		}

		if (k.startsWith('persistent_') && k.endsWith('_param')) {
			const newKey = k.replace(/_param$/, '').replace(/^persistent_/, '');
			result[newKey] = k;
			return;
		}
		result[k] = k;
	});

	return result;
};

/**
 * FIXME: This overlaps somewhat with services/dataset#createCsvAssetFromRunResults,
 *
 * This is a simpler version
 * - without dealing with a list/set of runResults, which is now depreated.
 * - no column stats, which are not used
 * */
export const convertToCsvAsset = (data: Record<string, any>[], keys: string[]) => {
	const csvData: CsvAsset = {
		headers: keys,
		csv: [],
		rowCount: data.length
	};
	data.forEach((datum) => {
		const row: any[] = [];
		keys.forEach((k) => {
			row.push(datum[k] || '');
		});
		csvData.csv.push(row);
	});
	return csvData;
};
