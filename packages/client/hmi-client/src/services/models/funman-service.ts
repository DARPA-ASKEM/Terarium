import { logger } from '@/utils/logger';
import API from '@/api/api';
import type { FunmanInterval, FunmanPostQueriesRequest } from '@/types/Types';
import { ConstraintGroup, ConstraintType } from '@/components/workflow/ops/funman/funman-operation';
import { getBoundType } from '@/services/charts';

// Partially typing Funman response
interface FunmanBound {
	lb: number;
	ub: number;
}

interface FunmanParameterBound {
	lb: number;
	ub: number;
	point: number;
}

export interface FunmanBox {
	boxId: number;
	label: string;
	timestep: FunmanBound;
	isAtLatestTimestep: boolean;
	parameters: Record<string, FunmanParameterBound>;
}

export interface FunmanConstraintsResponse {
	soft: boolean;
	name: string;
	timepoints: FunmanInterval;
	additive_bounds: FunmanInterval;
	variables: string[];
	weights: number[];
	derivative: boolean;
}

export interface ProcessedFunmanResult {
	boxes: FunmanBox[];
	trajectories: any[];
	observableTrajectories: any[];
}

export interface RenderOptions {
	width: number;
	height: number;
	constraints?: any[];
	click?: Function;
}

export async function makeQueries(body: FunmanPostQueriesRequest, modelId: string, newModelConfigName: string) {
	try {
		const resp = await API.post('/funman/queries', body, {
			params: { 'model-id': modelId, 'new-model-config-name': newModelConfigName }
		});
		const output = resp.data;
		return output;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export async function cancelQueries(taskId: string) {
	try {
		const { data } = await API.delete(`/funman/queries/${taskId}`);
		return data;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

export function generateConstraintExpression(config: ConstraintGroup) {
	const { constraintType, interval, variables, timepoints } = config;
	let expression = '';
	for (let i = 0; i < variables.length; i++) {
		let expressionPart = `${variables[i]}(t)`;
		if (constraintType === ConstraintType.Increasing || constraintType === ConstraintType.Decreasing) {
			expressionPart = `d/dt ${expressionPart}`;
		}
		if (
			constraintType === ConstraintType.LessThan ||
			constraintType === ConstraintType.LessThanOrEqualTo ||
			constraintType === ConstraintType.Decreasing
		) {
			expressionPart += constraintType === ConstraintType.LessThan ? `<` : `\\leq`;
			expressionPart += constraintType === ConstraintType.Decreasing ? '0' : `${interval?.ub ?? 0}`;
		} else {
			expressionPart += constraintType === ConstraintType.GreaterThan ? `>` : `\\geq`;
			expressionPart += constraintType === ConstraintType.Increasing ? '0' : `${interval?.lb ?? 0}`;
		}
		// Adding the "for all in timepoints" in the same expression helps with text alignment
		expression += `${expressionPart} \\ \\forall \\ t \\in [${timepoints.lb}, ${timepoints.ub}] \\newline `;
	}
	return expression;
}

export const processFunman = (result: any) => {
	const stateIds: string[] = result.model.petrinet.model.states.map(({ id }) => id);
	const parameterIds: string[] = result.model.petrinet.semantics.ode.parameters.map(({ id }) => id);
	const observableIds: string[] = result.model.petrinet.semantics.ode.observables.map(({ id }) => id);
	const timepoints: number[] = result.request.structure_parameters[0].schedules[0].timepoints;

	function markBoxesEndingAtLatestTimestep(boxes: any[]) {
		if (boxes.length === 0) return [];
		let latestTimestep = 0;
		let maxKeysCount = 0;

		// The latest timestep is found in the box with the most keys
		boxes.forEach((box) => {
			const points = box?.points?.[0]?.values;
			if (!points) return;
			const keysCount = Object.keys(points).length;
			if (keysCount > maxKeysCount) {
				maxKeysCount = keysCount;
				latestTimestep = box.points[0].values.timestep;
			}
		});
		// Mark boxes with the latest timestep
		return boxes.map((box) => {
			const timestep = box?.points?.[0]?.values?.timestep;
			if (timestep) box.isAtLatestTimestep = timestep === latestTimestep;
			return box;
		});
	}
	const trueBoxes: any[] = markBoxesEndingAtLatestTimestep(result.parameter_space.true_boxes);
	const falseBoxes: any[] = markBoxesEndingAtLatestTimestep(result.parameter_space.false_boxes);
	const ambiguousBoxes: any[] = markBoxesEndingAtLatestTimestep(result.parameter_space.unknown_points);

	// "dataframes"
	const boxes: FunmanBox[] = [];
	const trajectories: any[] = [];
	const observableTrajectories: any[] = [];

	[...trueBoxes, ...falseBoxes, ...ambiguousBoxes].forEach((box, index) => {
		const points = box?.points?.[0]?.values;
		if (!points) return;

		boxes.push({
			boxId: index,
			label: box.label,
			timestep: box.bounds.timestep,
			isAtLatestTimestep: box.isAtLatestTimestep,
			parameters: Object.fromEntries(
				parameterIds.map((parameterId) => [parameterId, { ...box.bounds[parameterId], point: points[parameterId] }])
			)
		});

		// Get trajectories
		timepoints
			.slice(0, points.timestep + 1) // Only include timepoints up to the current timestep
			.forEach((timepoint) => {
				const trajectory: any = {
					boxId: index,
					legendItem: getBoundType(box.label),
					isAtLatestTimestep: box.isAtLatestTimestep,
					values: {},
					timepoint
				};
				stateIds.forEach((stateId) => {
					// Only push states that have a timestep key pair
					const key = `${stateId}_${timepoint}`;
					if (Object.prototype.hasOwnProperty.call(points, key)) {
						trajectory.values[stateId] = points[key];
					}
				});
				trajectories.push(trajectory);
			});

		timepoints.slice(0, points.timestep + 1).forEach((timepoint) => {
			const trajectory: any = {
				boxId: index,
				legendItem: getBoundType(box.label),
				isAtLatestTimestep: box.isAtLatestTimestep,
				values: {},
				timepoint
			};
			observableIds.forEach((observableId) => {
				// Only push states that have a timestep key pair
				const key = `${observableId}_${timepoint}`;
				if (Object.prototype.hasOwnProperty.call(points, key)) {
					trajectory.values[observableId] = points[key];
				}
			});
			observableTrajectories.push(trajectory);
		});
	});
	return { boxes, trajectories, observableTrajectories } as ProcessedFunmanResult;
};
