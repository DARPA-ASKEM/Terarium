import { DataseriesConfig, RunType } from '@/types/SimulateConfig';
import type { CsvAsset, TimeSpan } from '@/types/Types';

export const getTimespan = (
	dataset?: CsvAsset,
	mapping?: { [key: string]: string }[]
): TimeSpan => {
	let start = 0;
	let end = 90;
	// If we have the min/max timestamp available from the csv asset use it
	if (dataset) {
		let tVar = 'timestamp';
		if (mapping) {
			// if there's a mapping for timestamp, then the model variable is guaranteed to be 'timestamp'
			const tMap = mapping.find((m) => m.modelVariable === 'timestamp');
			if (tMap) {
				tVar = tMap.datasetVariable;
			}
		}
		let tIndex = dataset.headers.indexOf(tVar);
		// if the timestamp column is not found, default to 0 as this is what is assumed to be the default
		// timestamp column in the pyciemss backend
		tIndex = tIndex === -1 ? 0 : tIndex;

		start = dataset.stats?.[tIndex].minValue!;
		end = dataset.stats?.[tIndex].maxValue!;
	}
	return { start, end };
};

export const getGraphDataFromDatasetCSV = (
	dataset: CsvAsset,
	columnVar: string,
	mapping?: { [key: string]: string }[],
	runType?: RunType
): DataseriesConfig | null => {
	// TA3 quirks, adjust variable names - FIXME
	const selectedVariableLookup =
		runType === RunType.Julia ? columnVar.slice(0, -3) : columnVar.slice(0, -6);
	const timeVariableLookup = 'timestamp';

	// Default
	let selectedVariable = selectedVariableLookup;
	let timeVariable = timeVariableLookup;

	// Map variable names to dataset column names
	if (mapping) {
		let result = mapping.find((m) => m.modelVariable === selectedVariable);
		if (result) {
			selectedVariable = result.datasetVariable;
		}

		result = mapping.find((m) => m.modelVariable === timeVariable);
		if (result) {
			timeVariable = result.datasetVariable;
		}
	}

	// Graph dataset index columns for x: timeVariable and y: selectedVariable
	let tIndex = -1;
	let sIndex = -1;
	for (let i = 0; i < dataset.headers.length; i++) {
		if (timeVariable.trim() === dataset.headers[i].trim()) tIndex = i;
		if (selectedVariable.trim() === dataset.headers[i].trim()) sIndex = i;
	}
	if (tIndex === -1 || sIndex === -1) {
		return null;
	}

	const graphData: DataseriesConfig = {
		// ignore the first row, it's the header
		data: dataset.csv.slice(1).map((datum: string[]) => ({
			x: +datum[tIndex],
			y: +datum[sIndex]
		})),
		label: `${columnVar} - dataset`,
		fill: false,
		borderColor: '#000000',
		borderDash: [3, 3]
	};

	return graphData;
};
