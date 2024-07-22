import { isEmpty } from 'lodash';

const VEGALITE_SCHEMA = 'https://vega.github.io/schema/vega-lite/v5.json';

export const CATEGORICAL_SCHEME = ['#5F9E3E', '#4375B0', '#8F69B9', '#D67DBF', '#E18547', '#D2C446', '#84594D'];

export interface ForecastChartOptions {
	legend: boolean;
	translationMap?: Record<string, string>;
	colorscheme?: string[];

	title?: string;
	xAxisTitle: string;
	yAxisTitle: string;

	width: number;
	height: number;
}

export interface ForecastChartLayer {
	dataset: Record<string, any>[];
	variables: string[];
	timeField: string;
	groupField?: string;
}

export interface HistogramChartOptions {
	title: string;
	width: number;
	height: number;
	xAxisTitle: string;
	yAxisTitle: string;
	maxBins?: number;
	variables: { field: string; label?: string; width: number; color: string }[];
}

export const createHistogramChart = (dataset: Record<string, any>[], options: HistogramChartOptions) => {
	const maxBins = options.maxBins ?? 10;
	const axisColor = '#EEE';
	const labelColor = '#667085';
	const labelFontWeight = 'normal';
	const globalFont = 'Figtree';
	const titleObj = options.title
		? {
				text: options.title,
				anchor: 'start',
				subtitle: ' ',
				subtitlePadding: 4
			}
		: null;

	const barMinGapWidth = 4;
	const xDiff = 32; // Diff between inner chart content width and the outer box width
	const maxBarWidth = Math.max(...options.variables.map((v) => v.width));
	const reaminingXSpace = options.width - xDiff - maxBins * (maxBarWidth + barMinGapWidth);
	const xPadding = reaminingXSpace < 0 ? barMinGapWidth : reaminingXSpace / 2;

	const xaxis = {
		domainColor: axisColor,
		tickColor: { value: axisColor },
		labelColor: { value: labelColor },
		labelFontWeight,
		title: options.xAxisTitle,
		gridColor: '#EEE',
		gridOpacity: 1.0
	};
	const yaxis = structuredClone(xaxis);
	yaxis.title = options.yAxisTitle;

	const legendProperties = {
		title: null,
		padding: { value: 0 },
		strokeColor: null,
		orient: 'top',
		direction: 'horizontal',
		symbolStrokeWidth: 4,
		symbolSize: 200,
		labelFontSize: 12,
		labelOffset: 4
	};

	const createLayers = (opts) => {
		const colorScale = {
			domain: opts.variables.map((v) => v.label ?? v.field),
			range: opts.variables.map((v) => v.color)
		};
		return opts.variables.map((varOption) => ({
			mark: { type: 'bar', width: varOption.width },
			encoding: {
				x: { bin: { maxbins: maxBins }, field: varOption.field, axis: xaxis, scale: { padding: xPadding } },
				y: { aggregate: 'count', axis: yaxis },
				color: {
					legend: { ...legendProperties },
					type: 'nominal',
					datum: varOption.label ?? varOption.field,
					scale: colorScale
				}
			}
		}));
	};

	const spec = {
		$schema: 'https://vega.github.io/schema/vega-lite/v5.json',
		title: titleObj,
		width: options.width,
		height: options.height,
		autosize: { type: 'fit' },
		data: { values: dataset },
		layer: createLayers(options),
		config: {
			font: globalFont
		}
	};
	return spec;
};

/**
 * Generate Vegalite specs for simulation/forecast charts. The chart can contain:
 *  - sampling layer: multiple forecast runsk
 *  - statistics layer: statistical aggregate of the sampling layer
 *  - ground truth layer: any grounding data
 *
 * Data comes in as a list of multi-variate objects:
 *   [ { time: 1, var1: 0.2, var2: 0.5, var3: 0.1 }, ... ]
 *
 * This then transformed by the fold-transform to be something like:
 *   [
 *     { time: 1, var1: 0.2, var2: 0.5, var3: 0.1, var: 'var1', value: 0.2 },
 *     { time: 1, var1: 0.2, var2: 0.5, var3: 0.1, var: 'var2', value: 0.5 },
 *     { time: 1, var1: 0.2, var2: 0.5, var3: 0.1, var: 'var3', value: 0.1 },
 *     ...
 *   ]
 *
 * Then we use the new 'var' and 'value' columns to render timeseries
 * */
export const createForecastChart = (
	samplingLayer: ForecastChartLayer | null,
	statisticsLayer: ForecastChartLayer | null,
	groundTruthLayer: ForecastChartLayer | null,
	options: ForecastChartOptions
) => {
	const axisColor = '#EEE';
	const labelColor = '#667085';
	const labelFontWeight = 'normal';
	const globalFont = 'Figtree';
	const titleObj = options.title
		? {
				text: options.title,
				anchor: 'start',
				subtitle: ' ',
				subtitlePadding: 4
			}
		: null;

	const xaxis = {
		domainColor: axisColor,
		tickColor: { value: axisColor },
		labelColor: { value: labelColor },
		labelFontWeight,
		title: options.xAxisTitle,
		gridColor: '#EEE',
		gridOpacity: 1.0
	};
	const yaxis = structuredClone(xaxis);
	yaxis.title = options.yAxisTitle;

	const translationMap = options.translationMap;
	let labelExpr = '';
	if (translationMap) {
		Object.keys(translationMap).forEach((key) => {
			labelExpr += `datum.value === '${key}' ? '${translationMap[key]}' : `;
		});
		labelExpr += " 'other'";
	}

	const legendProperties = {
		title: null,
		padding: { value: 0 },
		strokeColor: null,
		orient: 'top',
		direction: 'horizontal',
		columns: Math.floor(options.width / 100),
		symbolStrokeWidth: 4,
		symbolSize: 200,
		labelFontSize: 12,
		labelOffset: 4
	};

	// Start building
	const spec: any = {
		$schema: VEGALITE_SCHEMA,
		title: titleObj,
		description: '',
		width: options.width,
		height: options.height,
		autosize: {
			type: 'fit'
		},
		config: {
			font: globalFont
		},

		// layers
		layer: [],

		// Make layers independent
		resolve: {
			legend: { color: 'independent' },
			scale: { color: 'independent' }
		}
	};

	// Helper function to capture common layer structure
	const newLayer = (layer: ForecastChartLayer, markType: string) => {
		const header = {
			mark: { type: markType },
			data: { values: layer.dataset },
			transform: [
				{
					fold: layer.variables,
					as: ['variableField', 'valueField']
				}
			]
		};
		const encoding = {
			x: { field: layer.timeField, type: 'quantitative', axis: xaxis },
			y: { field: 'valueField', type: 'quantitative', axis: yaxis },
			color: {
				field: 'variableField',
				type: 'nominal',
				scale: {
					domain: layer.variables,
					range: options.colorscheme || CATEGORICAL_SCHEME
				},
				legend: false
			}
		};

		return {
			...header,
			encoding
		} as any;
	};

	// Build sample layer
	if (samplingLayer && !isEmpty(samplingLayer.variables)) {
		const layerSpec = newLayer(samplingLayer, 'line');

		Object.assign(layerSpec.encoding, {
			detail: { field: samplingLayer.groupField, type: 'nominal' },
			strokeWidth: { value: 1 },
			opacity: { value: 0.1 }
		});

		spec.layer.push(layerSpec);
	}

	// Build statistical layer
	if (statisticsLayer && !isEmpty(statisticsLayer.variables)) {
		const layerSpec = newLayer(statisticsLayer, 'line');
		Object.assign(layerSpec.encoding, {
			opacity: { value: 1.0 },
			strokeWidth: { value: 2 }
		});

		if (options.legend === true) {
			layerSpec.encoding.color.legend = {
				...legendProperties
			};

			if (labelExpr.length > 0) {
				layerSpec.encoding.color.legend.labelExpr = labelExpr;
			}
		}
		spec.layer.push(layerSpec);
	}

	// Build ground truth layer
	if (groundTruthLayer && !isEmpty(groundTruthLayer.variables)) {
		const layerSpec = newLayer(groundTruthLayer, 'point');

		// FIXME: variables not aligned, set unique color for now
		layerSpec.encoding.color.scale.range = ['#333'];
		/* layerSpec.encoding.color.scale.range = options.colorscheme || CATEGORICAL_SCHEME; // This works until it doesn't */

		if (options.legend === true) {
			layerSpec.encoding.color.legend = {
				...legendProperties
			};

			if (labelExpr.length > 0) {
				layerSpec.encoding.color.legend.labelExpr = labelExpr;
			}
		}
		spec.layer.push(layerSpec);
	}

	// Build a transparent layer with fat lines as a better hover target for tooltips
	// Re-Build statistical layer
	if (statisticsLayer && !isEmpty(statisticsLayer.variables)) {
		const tooltipContent = statisticsLayer.variables?.map((d) => {
			const tip: any = {
				field: d,
				type: 'quantitative',
				format: '.4f'
			};

			if (options.translationMap && options.translationMap[d]) {
				tip.title = options.translationMap[d];
			}

			return tip;
		});

		const layerSpec = newLayer(statisticsLayer, 'line');
		Object.assign(layerSpec.encoding, {
			opacity: { value: 0 },
			strokeWidth: { value: 16 },
			tooltip: [{ field: statisticsLayer.timeField, type: 'quantitative' }, ...(tooltipContent || [])]
		});
		spec.layer.push(layerSpec);
	}

	return spec;
};

/// /////////////////////////////////////////////////////////////////////////////
/// /////////////////////////////////////////////////////////////////////////////

export interface OptimizeChartOptions {
	variables?: string[];
	statisticalVariables?: string[];
	groundTruthVariables?: string[];

	legend: boolean;

	translationMap?: Record<string, string>;

	colorscheme?: string[];
	timeField: string;
	groupField: string;

	title?: string;
	xAxisTitle: string;
	yAxisTitle: string;

	width: number;
	height: number;
}

const binCount = 5;

export function formatSuccessChartData(
	riskResults: any,
	targetVariable: string,
	threshold: number,
	isMinimized: boolean
) {
	const targetState = `${targetVariable}_state`;
	const data = riskResults[targetState]?.qoi || [];
	if (isEmpty(data)) {
		return [];
	}

	const min = Math.min(...data);
	const max = Math.max(...data);
	const ranges: { range: string; count: number; tag: 'in' | 'out' }[] = [];

	// see whether ranges are in or out of the threshold
	const determineTag = (
		start: number,
		end: number,
		thresholdValue: number,
		isMinimizedValue: boolean
	): 'in' | 'out' => {
		if (isMinimizedValue) {
			return end < thresholdValue ? 'in' : 'out';
		}
		return start > thresholdValue ? 'in' : 'out';
	};
	if (min === max) {
		// case where there is only one value or all values are the same. we want to give the bar some thickness so that the user can see it.
		const start = min - 1;
		const end = min + 1;
		const count = data.length;

		ranges.push({
			range: `${start.toFixed(4)}-${end.toFixed(4)}`,
			count,
			tag: determineTag(start, end, threshold, isMinimized)
		});
	} else {
		const rangeSize = (max - min) / binCount;

		for (let i = 0; i < binCount; i++) {
			const start = min + i * rangeSize;
			const end = i === binCount - 1 ? max : start + rangeSize;
			// count the number of values in the range, some logic here to handle the last bin which is inclusive of the max value
			const count = data.filter((num) => num >= start && (i === binCount - 1 ? num <= end : num < end)).length;

			ranges.push({
				range: `${start.toFixed(4)}-${end.toFixed(4)}`,
				count,
				tag: determineTag(start, end, threshold, isMinimized)
			});
		}
	}

	return ranges;
}

export function createOptimizeChart(
	riskResults: any,
	targetVariable: string,
	threshold: number,
	isMinimized: boolean
): any {
	const data = formatSuccessChartData(riskResults, targetVariable, threshold, isMinimized);

	return {
		$schema: VEGALITE_SCHEMA,
		width: 400,
		height: 400,
		data: {
			values: data
		},
		transform: [
			{
				calculate: 'split(datum.range, "-")[0]',
				as: 'start'
			},
			{
				calculate: 'split(datum.range, "-")[1]',
				as: 'end'
			}
		],
		layer: [
			{
				mark: {
					type: 'bar',
					stroke: 'black',
					tooltip: true,
					interpolate: 'linear'
				},
				encoding: {
					y: {
						field: 'start',
						type: 'quantitative',
						title: `${isMinimized ? 'Max' : 'Min'} value of ${targetVariable} at all timepoints`
					},
					y2: { field: 'end' },
					x: {
						aggregate: 'sum',
						field: 'count',
						type: 'quantitative',
						title: 'Number of samples'
					},
					color: {
						field: 'tag',
						type: 'nominal',
						scale: {
							domain: ['failing', 'passing'],
							range: ['#FFAB00', '#1B8073']
						}
					}
				}
			},
			{
				mark: { type: 'rule', strokeDash: [4, 4], color: 'black' },
				encoding: {
					y: { datum: +threshold }
				}
			},
			{
				mark: {
					type: 'text',
					align: 'left',
					text: `Threshold = ${+threshold}`,
					baseline: 'line-bottom'
				},
				encoding: {
					y: { datum: +threshold }
				}
			}
		],
		config: {
			legend: { title: null, orient: 'top', direction: 'horizontal' }
		}
	};
}

function createInterventionChartMarkers(data: { name: string; value: number; time: number }[]) {
	const markers = data.map((ele) => ({
		data: { values: data },
		mark: { type: 'rule', strokeDash: [4, 4], color: 'black' },
		encoding: {
			x: { datum: ele.time }
		}
	}));

	const labelsSpec = {
		data: { values: data },
		mark: {
			type: 'text',
			align: 'left',
			angle: 90,
			dx: 5,
			dy: -10
		},
		encoding: {
			x: { field: 'time', type: 'quantitative' },
			y: { field: 'value', type: 'quantitative' },
			text: { field: 'name', type: 'nominal' }
		}
	};

	return [...markers, labelsSpec];
}

function createStatisticLayer(
	data: Record<string, any>[],
	xaxis,
	yaxis,
	options: OptimizeChartOptions,
	isPreStatistic: boolean
) {
	const statisticalVariables = options.statisticalVariables?.map((d) => d);
	const tooltipContent = statisticalVariables?.map((d) => ({
		field: d,
		type: 'quantitative',
		format: '.4f'
	}));

	const layerSpec: any = {
		mark: { type: 'line' },
		data: { values: data },
		transform: [
			{
				fold: statisticalVariables,
				as: ['stat_variable', 'stat_value']
			}
		],
		encoding: {
			x: { field: options.timeField, type: 'quantitative', axis: xaxis },
			y: { field: 'stat_value', type: 'quantitative', axis: yaxis },
			color: {
				field: 'stat_variable',
				type: 'nominal',
				scale: {
					domain: statisticalVariables,
					range: [isPreStatistic ? '#AAB3C6' : '#1B8073']
				},
				legend: options.legend
					? {
							title: null,
							orient: 'top',
							direction: 'horizontal',
							labelExpr: isPreStatistic
								? 'datum.value ? "Before Optimization" : ""'
								: 'datum.value ? "After Optimization" : ""'
						}
					: null
			},
			opacity: { value: 1.0 },
			strokeWidth: { value: 3.5 },
			tooltip: [{ field: options.timeField, type: 'quantitative' }, ...(tooltipContent || [])]
		}
	};

	return layerSpec;
}

function createSampleLayer(
	data: Record<string, any>[],
	xaxis,
	yaxis,
	options: OptimizeChartOptions,
	isPreSample: boolean
) {
	const sampleVariables = options.variables?.map((d) => d);

	return {
		mark: { type: 'line' },
		data: { values: data },
		transform: [
			{
				fold: sampleVariables,
				as: ['sample_variable', 'sample_value']
			}
		],
		encoding: {
			x: { field: options.timeField, type: 'quantitative', axis: xaxis },
			y: { field: 'sample_value', type: 'quantitative', axis: yaxis },
			color: {
				field: 'sample_variable',
				type: 'nominal',
				scale: {
					domain: sampleVariables,
					range: [isPreSample ? '#AAB3C6' : '#1B8073']
				},
				legend: false // No legend for sampling-layer, too noisy
			},
			detail: { field: options.groupField, type: 'nominal' },
			strokeWidth: { value: 1 },
			opacity: { value: 0.1 }
		}
	};
}
export const createOptimizeForecastChart = (
	preSampleRunData: Record<string, any>[],
	preStatisticData: Record<string, any>[],
	postSampleRunData: Record<string, any>[],
	postStatisticData: Record<string, any>[],
	interventionsData: { name: string; value: number; time: number }[],
	options: OptimizeChartOptions
) => {
	const axisColor = '#EEE';
	const labelColor = '#667085';
	const labelFontWeight = 'normal'; // Adjust font weight here
	const titleObj = options.title
		? {
				text: options.title,
				anchor: 'start',
				subtitle: ' ',
				subtitlePadding: 4
			}
		: null;

	const xaxis = {
		domainColor: axisColor,
		tickColor: { value: axisColor },
		labelColor: { value: labelColor },
		labelFontWeight,
		title: options.xAxisTitle,
		gridColor: '#EEE',
		gridOpacity: 1.0
	};
	const yaxis = structuredClone(xaxis);
	yaxis.title = options.yAxisTitle;

	const spec: any = {
		$schema: VEGALITE_SCHEMA,
		title: titleObj,
		description: '',
		width: options.width,
		height: options.height,
		autosize: {
			type: 'fit'
		},

		// layers
		layer: [],

		// Make layers independent
		resolve: {
			legend: { color: 'independent' },
			scale: { color: 'independent' }
		}
	};

	// Build pre sample layer
	if (preSampleRunData && preSampleRunData.length > 0) {
		spec.layer.push(createSampleLayer(preSampleRunData, xaxis, yaxis, options, true));
	}

	// Build post sample layer
	if (postSampleRunData && postSampleRunData.length > 0) {
		spec.layer.push(createSampleLayer(postSampleRunData, xaxis, yaxis, options, false));
	}

	// Build pre statistical layer
	if (preStatisticData && preStatisticData.length > 0) {
		spec.layer.push(createStatisticLayer(preStatisticData, xaxis, yaxis, options, true));
	}

	if (postStatisticData && postStatisticData.length > 0) {
		spec.layer.push(createStatisticLayer(postStatisticData, xaxis, yaxis, options, false));
	}

	if (interventionsData && interventionsData.length > 0) {
		createInterventionChartMarkers(interventionsData).forEach((marker) => {
			spec.layer.push(marker);
		});
	}

	return spec;
};

export const createInterventionChart = (interventionsData: { name: string; value: number; time: number }[]) => {
	const spec: any = {
		$schema: VEGALITE_SCHEMA,
		width: 400,
		autosize: {
			type: 'fit'
		},
		layer: []
	};
	if (interventionsData && interventionsData.length > 0) {
		// markers
		createInterventionChartMarkers(interventionsData).forEach((marker) => {
			spec.layer.push(marker);
		});
		// chart
		spec.layer.push({
			data: { values: interventionsData },
			mark: 'point',
			encoding: {
				x: { field: 'time', type: 'quantitative' },
				y: { field: 'value', type: 'quantitative' }
			}
		});
	}
	return spec;
};
