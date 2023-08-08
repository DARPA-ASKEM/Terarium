import { csvParse } from 'd3';
import { logger } from '@/utils/logger';
import API from '@/api/api';
import {
	Simulation,
	SimulationRequest,
	CalibrationRequestJulia,
	CalibrationRequestCiemss,
	EventType,
	EnsembleSimulationCiemssRequest,
	EnsembleCalibrationCiemssRequest
} from '@/types/Types';
import { RunResults } from '@/types/SimulateConfig';
import * as EventService from '@/services/event';
import useResourcesStore from '@/stores/resources';
import { ProgressState, WorkflowNode } from '@/types/workflow';
import { cloneDeep, isEqual } from 'lodash';

export async function makeForecastJob(simulationParam: SimulationRequest) {
	try {
		const resp = await API.post('simulation-request/forecast', simulationParam);
		EventService.create(
			EventType.TransformPrompt,
			useResourcesStore().activeProject?.id,
			JSON.stringify({
				type: 'julia',
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

export async function makeForecastJobCiemss(simulationParam: SimulationRequest) {
	try {
		const resp = await API.post('simulation-request/ciemss/forecast/', simulationParam);
		EventService.create(
			EventType.TransformPrompt,
			useResourcesStore().activeProject?.id,
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

// TODO: Add typing to julia's output: https://github.com/DARPA-ASKEM/Terarium/issues/1655
export async function getRunResultJulia(runId: string, filename = 'result.json') {
	try {
		const resp = await API.get(`simulations/${runId}/result`, {
			params: { filename }
		});
		const output = resp.data;
		const columnNames = (output[0].colindex.names as string[]).join(',');
		let csvData: string = columnNames as string;
		for (let j = 0; j < output[0].columns[0].length; j++) {
			csvData += '\n';
			for (let i = 0; i < output[0].columns.length; i++) {
				csvData += `${output[0].columns[i][j]},`;
			}
		}
		return csvData;
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
			const keyArr = key.split('_');
			const keySuffix = keyArr.pop();
			const keyName = keyArr.join('_');

			if (keySuffix === 'param') {
				outputRowRunResults[keyName] = inputRow[key];
				if (!runConfigs[keyName]) {
					runConfigs[keyName] = [];
				}
				runConfigs[keyName].push(Number(inputRow[key]));
			} else if (keySuffix === 'sol') {
				outputRowRunResults[keyName] = inputRow[key];
			} else if (keySuffix === 'obs') {
				outputRowRunResults[keyName] = inputRow[key];
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

export async function makeCalibrateJobJulia(calibrationParams: CalibrationRequestJulia) {
	try {
		EventService.create(
			EventType.RunCalibrate,
			useResourcesStore().activeProject?.id,
			JSON.stringify(calibrationParams)
		);
		const resp = await API.post('simulation-request/calibrate', calibrationParams);
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeCalibrateJobCiemss(calibrationParams: CalibrationRequestCiemss) {
	try {
		const resp = await API.post('simulation-request/ciemss/calibrate', calibrationParams);
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeEnsembleCiemssSimulation(params: EnsembleSimulationCiemssRequest) {
	try {
		const resp = await API.post('simulation-request/ciemss/ensemble-simulate', params);
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function makeEnsembleCiemssCalibration(params: EnsembleCalibrationCiemssRequest) {
	try {
		const resp = await API.post('simulation-request/ciemss/ensemble-calibrate', params);
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

// add a simulation in progress if it does not exist
const addSimulationInProgress = (node: WorkflowNode, runIds: string[]) => {
	const state = cloneDeep(node.state);

	if (!state.simulationsInProgress) {
		state.simulationsInProgress = [];
	}
	runIds.forEach((runId) => {
		if (!state.simulationsInProgress.includes(runId)) {
			state.simulationsInProgress.push(runId);
		}
	});

	return state;
};

// delete a simulation in progress if it exists
const deleteSimulationInProgress = (node: WorkflowNode, runIds: string[]) => {
	const state = cloneDeep(node.state);

	if (state.simulationsInProgress) {
		runIds.forEach((runId) => {
			const index = state.simulationsInProgress.indexOf(runId);
			if (index !== -1) {
				state.simulationsInProgress.splice(index, 1);
			}
		});
	}

	return state;
};

// This function returns a string array of run ids.
export const querySimulationInProgress = (node: WorkflowNode): string[] => {
	const state = node.state;
	if (state.simulationsInProgress && state.simulationsInProgress.length > 0) {
		// return all run ids on the node
		return state.simulationsInProgress;
	}

	// return an empty array if no run ids are present
	return [];
};

export async function simulationPollAction(
	simulationIds: string[],
	node: WorkflowNode,
	progress,
	emitFn: (event: 'append-output-port' | 'update-state', ...args: any[]) => void
) {
	const requestList: Promise<Simulation | null>[] = [];
	simulationIds.forEach((id) => {
		requestList.push(getSimulation(id));
	});
	const response = await Promise.all(requestList);
	if (response.every((simulation) => simulation!.status === ProgressState.COMPLETE)) {
		const newState = deleteSimulationInProgress(node, simulationIds);
		// only update state if it is different from the current one
		if (!isEqual(node.state, newState)) {
			emitFn('update-state', newState);
		}
		return {
			data: response,
			progress: null,
			error: null
		};
	}
	if (
		response.find(
			(simulation) =>
				simulation?.status === ProgressState.QUEUED || simulation?.status === ProgressState.RUNNING
		)
	) {
		const newState = addSimulationInProgress(node, simulationIds);
		// only update state if it is different from the current one
		if (!isEqual(node.state, newState)) {
			emitFn('update-state', newState);
		}
		progress.value = {
			status: ProgressState.RUNNING,
			value: 0
		};
	}

	return {
		data: null,
		progress: null,
		error: null
	};
}
