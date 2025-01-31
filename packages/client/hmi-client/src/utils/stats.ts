import { meanBy, isEmpty } from 'lodash';
import { logger } from '@/utils/logger';

export type DataArray = Record<string, any>[];

export const mean = (numberList: number[]) =>
	numberList.reduce((acc: number, val: number) => acc + val, 0) / numberList.length;

// Lifted from https://stackoverflow.com/questions/7343890/standard-deviation-javascript
export const stddev = (numberList: number[], usePopulation = false) => {
	const avg = mean(numberList);
	return Math.sqrt(
		numberList
			.reduce((acc: number[], val: number) => acc.concat((val - avg) ** 2), [])
			.reduce((acc, val) => acc + val, 0) /
			(numberList.length - (usePopulation ? 0 : 1))
	);
};

// Get the mean absolute error between two maps.
// Note this will only compare values that share the same key.
export const mae = (arr1: DataArray, arr2: DataArray, keyField: string, valueField: string) => {
	const map1: Map<number, number> = new Map();
	const map2: Map<number, number> = new Map();

	// Remap for convenience
	arr1.forEach((ele) => {
		const timestamp = ele[keyField];
		const value = ele[valueField];
		map1.set(timestamp, value);
	});

	arr2.forEach((ele) => {
		const timestamp = ele[keyField];
		const value = ele[valueField];
		map2.set(timestamp, value);
	});

	const sharedKey = [...map1.keys()].filter((key) => map2.has(key));
	const error = meanBy(sharedKey, (key) => {
		const firstValue = map1.get(key) as number;
		const secondValue = map2.get(key) as number;
		return Math.abs(firstValue - secondValue);
	});
	return error;
};

export function quantile(numberList: number[], q: number) {
	if (numberList.length === 0) return 0;

	const pos = (numberList.length - 1) * q;
	const lowerIndex = Math.floor(pos);
	const upperIndex = Math.ceil(pos);

	if (lowerIndex === upperIndex) {
		return numberList[lowerIndex];
	}

	// Linear interpolation between two closest ranks
	const lowerValue = numberList[lowerIndex];
	const upperValue = numberList[upperIndex];

	return lowerValue + (upperValue - lowerValue) * (pos - lowerIndex);
}

// Weighted Interval Score (WIS)
// These are js ports of python functions specified in this issue: https://github.com/orgs/DARPA-ASKEM/projects/3/views/8?pane=issue&itemId=93848937&issue=DARPA-ASKEM%7Cterarium%7C6067
// The original python code: https://github.com/adrian-lison/interval-scoring/blob/master/scoring.py

const DEFAULT_ALPHA_QS = [
	0.01, 0.025, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95,
	0.975, 0.99
];

// Compute the estimated quantiles from a time-series dataset with many samples.
export function computeQuantile(
	summaryResult: Record<string, any>,
	variableName: string,
	alphas: number[] = DEFAULT_ALPHA_QS
) {
	const values = summaryResult.map((row) => row[variableName]);
	const quantileValueMap: Record<number, number> = {};
	alphas.forEach((q) => {
		quantileValueMap[q] = quantile(values, q);
	});
	return quantileValueMap;
}

// Compute interval scores (1) for an array of observations and predicted intervals.
// Either a dictionary with the respective (alpha/2) and (1-(alpha/2)) quantiles via q_dict needs to be
// specified or the quantiles need to be specified via q_left and q_right.
// This is a dumbed down version of the original function as it's just handling the case where the left and right quantiles are not specified, etc.
export function getIntervalScore(
	groundTruthObservations: Record<number, number>,
	variableObservations: Record<number, number>,
	alpha: number,
	percent: boolean,
	checkConsistency: boolean
) {
	const leftQuantile = variableObservations[alpha / 2];
	const rightQuantile = variableObservations[1 - alpha / 2];

	if (checkConsistency && leftQuantile > rightQuantile) {
		logger.error('Left quantile must be smaller than right quantile. Datasets are not ideal for WIS calculation.');
		return { sharpness: [], calibration: [], total: [] };
	}

	// FIXME: The python code expects sharpness to be a list but for now it makes sense for it to be a number
	let sharpness = rightQuantile - leftQuantile;

	let calibration = Object.values(groundTruthObservations).map((obs) => {
		const leftClip = Math.max(0, leftQuantile - obs);
		const rightClip = Math.max(0, obs - rightQuantile);
		return ((leftClip + rightClip) * 2) / alpha;
	});

	if (percent) {
		const groundTruthObservationsValues = Object.values(groundTruthObservations);
		sharpness /= Math.abs(groundTruthObservationsValues[0]);
		calibration = calibration.map((cal, index) => cal / Math.abs(groundTruthObservationsValues[index]));
	}

	const total = calibration.map((cal) => cal + sharpness);

	return {
		total,
		sharpness: [sharpness], // Make sharpness a list for consistency with the original python code (and so it works at all lol)
		calibration
	};
}

// Compute weighted interval scores for an array of observations and a number of different predicted intervals.
// This function implements the WIS-score (2). A dictionary with the respective (alpha/2)
// and (1-(alpha/2)) quantiles for all alpha levels given in `alphas` needs to be specified.
export function getWeightedIntervalScore(
	groundTruthObservations: Record<number, number>,
	variableObservations: Record<number, number>,
	percent: boolean = true,
	alphas: number[] = [0.02, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9],
	weights: number[] = [],
	checkConsistency: boolean = true
) {
	if (isEmpty(weights)) {
		weights = alphas.map((alpha) => alpha / 2);
	}

	// Calculate interval scores for each alpha and weight
	const intervalScores = alphas.map((alpha, index) => {
		const intervalScore = getIntervalScore(
			groundTruthObservations,
			variableObservations,
			alpha,
			percent,
			checkConsistency
		);

		// Weigh scores
		const { total, sharpness, calibration } = intervalScore;
		const weight = weights[index];
		return [total.map((d) => d * weight), sharpness.map((d) => d * weight), calibration.map((d) => d * weight)];
	});

	// This is what zip does in python
	const [totalScores, sharpnessScores, calibrationScores] = intervalScores[0].map((_, colIndex) =>
		intervalScores.map((row) => row[colIndex])
	);

	// Sum the scores across all alphas and normalize by the sum of the weights
	const weightSum = weights.reduce((sum, val) => sum + val, 0);

	// Sum column-wise (equivalent to np.sum(np.vstack(interval_scores[i]), axis=0))
	function columnWiseSum(array: number[][]) {
		return array[0].map((_, colIndex) => array.reduce((sum, row) => sum + row[colIndex], 0));
	}

	const total = columnWiseSum(totalScores).map((d) => d / weightSum);
	const sharpness = columnWiseSum(sharpnessScores).map((d) => d / weightSum);
	const calibration = columnWiseSum(calibrationScores).map((d) => d / weightSum);

	return { total, sharpness, calibration };
}
