// TODO: it might be best to move all these to getters and setters related to the model to services/model since these all seem to be split up at the moment
import _, { isEmpty } from 'lodash';
import { runDagreLayout } from '@/services/graph';
import { MiraModel } from '@/model-representation/mira/mira-common';
import { extractNestedStratas } from '@/model-representation/petrinet/mira-petri';
import { PetrinetRenderer } from '@/model-representation/petrinet/petrinet-renderer';
import type { Initial, Model, ModelParameter, State, RegNetVertex, Transition, Rate } from '@/types/Types';
import { getModelType } from '@/services/model';
import { AMRSchemaNames } from '@/types/common';
import { parseCurie } from '@/services/concept';
import { NestedPetrinetRenderer } from './petrinet/nested-petrinet-renderer';
import { isStratifiedModel, getContext, collapseTemplates } from './mira/mira';
import { extractTemplateMatrix } from './mira/mira-util';

export const getVariable = (miraModel: MiraModel, variableName: string) => {
	if (miraModel.initials[variableName]) {
		return {
			value: miraModel.initials[variableName].expression
		};
	}
	if (miraModel.parameters[variableName]) {
		return {
			value: miraModel.parameters[variableName].value
		};
	}
	const template = miraModel.templates.find((t) => t.name === variableName);
	if (template) {
		return {
			value: template.rate_law
		};
	}
	throw new Error(`${variableName} not found`);
};

export const getModelRenderer = (
	miraModel: MiraModel,
	graphElement: HTMLDivElement,
	useNestedRenderer: boolean
): PetrinetRenderer | NestedPetrinetRenderer => {
	const isStratified = isStratifiedModel(miraModel);
	// Debug start
	// console.group('mmt info');
	// console.log('# templates: ', miraModel.templates.length);
	// console.log('# parameters: ', Object.keys(miraModel.parameters).length);
	// console.log('stratified model: ', isStratified);
	// console.groupEnd();
	// Debug end

	if (useNestedRenderer && isStratified) {
		// FIXME: Testing, move to mira service
		const processedSet = new Set<string>();
		const conceptData: any = [];
		miraModel.templates.forEach((t) => {
			['subject', 'outcome', 'controller'].forEach((conceptKey) => {
				if (!t[conceptKey]) return;
				const conceptName = t[conceptKey].name;
				if (processedSet.has(conceptName)) return;
				conceptData.push({
					// FIXME: use reverse-lookup to get root concept
					base: _.first(conceptName.split('_')),
					...t[conceptKey].context
				});

				processedSet.add(conceptName);
			});
		});
		const dims = getContext(miraModel).keys;
		dims.unshift('base');

		const { matrixMap } = collapseTemplates(miraModel);
		const transitionMatrixMap = {};
		matrixMap.forEach((value, key) => {
			transitionMatrixMap[key] = extractTemplateMatrix(value).matrix;
		});

		const nestedMap = extractNestedStratas(conceptData, dims);
		return new NestedPetrinetRenderer({
			el: graphElement,
			useAStarRouting: false,
			useStableZoomPan: true,
			zoomModifier: 'ctrlKey',
			zoomRange: [0.1, 30],
			runLayout: runDagreLayout,
			dims,
			nestedMap,
			transitionMatrices: transitionMatrixMap
		});
	}

	return new PetrinetRenderer({
		el: graphElement,
		useAStarRouting: false,
		useStableZoomPan: true,
		zoomModifier: 'ctrlKey',
		runLayout: runDagreLayout,
		dragSelector: 'no-drag'
	});
};

/**
 * Returns the model parameters based on the model type.
 * @param {Model} model - The model object.
 * @returns {ModelParameter[]} - The model parameters.
 */
export function getParameters(model: Model): ModelParameter[] {
	const modelType = getModelType(model);
	switch (modelType) {
		case AMRSchemaNames.REGNET:
			return model.model?.parameters ?? [];
		case AMRSchemaNames.PETRINET:
		case AMRSchemaNames.STOCKFLOW:
		default:
			return model.semantics?.ode?.parameters ?? [];
	}
}

/**
 * Returns the model parameter with the specified ID.
 * @param {Model} model - The model object.
 * @param {string} parameterId - The ID of the parameter.
 * @returns {ModelParameter | null} - The model parameter or null if not found.
 */
export function getParameter(model: Model, parameterId: string): ModelParameter | undefined {
	const modelType = getModelType(model);
	switch (modelType) {
		case AMRSchemaNames.REGNET:
			return model.model?.parameters.find((p) => p.id === parameterId);
		case AMRSchemaNames.PETRINET:
		case AMRSchemaNames.STOCKFLOW:
		default:
			return model.semantics?.ode?.parameters?.find((p) => p.id === parameterId);
	}
}

export function updateModelPartProperty(modelPart: any, key: string, value: any) {
	if (key === 'unitExpression') {
		if (isEmpty(value)) {
			console.warn(`Invalid value setting ${modelPart}[${key}]`);
			return;
		}

		if (!modelPart.units) modelPart.units = { expression: '', expression_mathml: '' };
		modelPart.units.expression = value;
		modelPart.units.expression_mathml = `<ci>${value}</ci>`;
	} else if (key === 'concept') {
		if (!modelPart.grounding?.identifiers) modelPart.grounding = { identifiers: {}, modifiers: {} };
		modelPart.grounding.identifiers = parseCurie(value);
	} else {
		modelPart[key] = value;
	}
}

export function updateParameter(model: Model, id: string, key: string, value: any) {
	const parameters = getParameters(model);
	const parameter = parameters.find((p: ModelParameter) => p.id === id);
	if (!parameter) return;
	updateModelPartProperty(parameter, key, value);

	// FIXME: (For stockflow) Sometimes auxiliaries can share the same ids as parameters so for now both are be updated in that case
	const auxiliaries = model.model?.auxiliaries ?? [];
	const auxiliary = auxiliaries.find((a) => a.id === id);
	if (!auxiliary) return;
	updateModelPartProperty(auxiliary, key, value);
}

export function updateState(model: Model, id: string, key: string, value: any) {
	const states = getStates(model);
	const state = states.find((i: any) => i.id === id);
	if (!state) return;
	updateModelPartProperty(state, key, value);
}

export function updateObservable(model: Model, id: string, key: string, value: any) {
	const observables = model?.semantics?.ode?.observables ?? [];
	const observable = observables.find((o) => o.id === id);
	if (!observable) return;
	updateModelPartProperty(observable, key, value);
}

export function updateTransition(model: Model, id: string, key: string, value: any) {
	const transitions: Transition[] = model?.model?.transitions ?? [];
	const transition = transitions.find((t) => t.id === id);
	if (!transition) return;
	if (transition.properties && key === 'name') {
		transition.properties.name = value;
	}
	updateModelPartProperty(transition, key, value);
}

export function updateTime(model: Model, key: string, value: any) {
	const time: State = model?.semantics?.ode?.time;
	updateModelPartProperty(time, key, value);
}

// Gets states, vertices, stocks (no stock type yet)
export function getStates(model: Model): (State & RegNetVertex)[] {
	const modelType = getModelType(model);
	switch (modelType) {
		case AMRSchemaNames.REGNET:
			return model.model?.vertices ?? [];
		case AMRSchemaNames.PETRINET:
			return model.model?.states ?? [];
		case AMRSchemaNames.STOCKFLOW:
			return model.model?.stocks ?? [];
		default:
			return [];
	}
}

/**
 * Retrieves the metadata for a specific initial in the model.
 * @param {Model} model - The model object.
 * @param {string} target - The target of the initial.
 * @returns {any} - The metadata for the specified initial or undefined if not found.
 */
export function getInitialMetadata(model: Model, target: string) {
	return model.metadata?.initials?.[target];
}

export function getInitialName(model: Model, target: string): string {
	return model.model.states.find((s) => s.id === target)?.name ?? '';
}

export function getInitialDescription(model: Model, target: string): string {
	return model.model.states.find((s) => s.id === target)?.description ?? '';
}

export function getInitialUnits(model: Model, target: string): string {
	return model.model.states.find((s) => s.id === target)?.units?.expression ?? '';
}

/**
 * Returns the model initials based on the model type.
 * @param {Model} model - The model object.
 * @returns {Initial[]} - The model initials.
 */
export function getInitials(model: Model): Initial[] {
	const modelType = getModelType(model);
	switch (modelType) {
		case AMRSchemaNames.REGNET:
			return model.model?.vertices ?? [];
		case AMRSchemaNames.PETRINET:
		case AMRSchemaNames.STOCKFLOW:
		default:
			return model.semantics?.ode?.initials ?? [];
	}
}

export function isModelMissingMetadata(model: Model): boolean {
	const parameters: ModelParameter[] = getParameters(model);
	const initials: Initial[] = getInitials(model);

	const initialsCheck = initials.some((i) => {
		const initialMetadata = getInitialMetadata(model, i.target);
		return !initialMetadata?.name || !initialMetadata?.description || !initialMetadata?.units;
	});

	const parametersCheck = parameters.some((p) => !p.name || !p.description || !p.units?.expression);
	return initialsCheck || parametersCheck;
}

/**
 * Sanity check Petrinet AMR, returns a list of discovered faults
 * - Check various arrays match up in lengths
 * - Check states make sense
 * - Check transitions make sense
 * */
export function checkPetrinetAMR(amr: Model) {
	function isASCII(str: string) {
		// eslint-disable-next-line
		return /^[\x00-\x7F]*$/.test(str);
	}

	const results: { type: string; content: string }[] = [];
	const model = amr.model;
	const ode = amr.semantics?.ode;

	const numStates = model.states.length;
	const numTransitions = model.transitions.length;
	const numInitials = ode?.initials?.length || 0;
	const numRates = ode?.rates?.length || 0;

	if (numStates === 0) {
		results.push({ type: 'warn', content: 'zero states' });
	}

	if (numTransitions === 0) {
		results.push({ type: 'warn', content: 'zero transitions' });
	}

	if (numStates !== numInitials) {
		results.push({ type: 'error', content: 'states need to match initials' });
	}
	if (numRates !== numTransitions) {
		results.push({ type: 'error', content: 'transitions need to match rates' });
	}

	// Build cache
	const initialMap: Map<string, Initial> = new Map();
	const rateMap: Map<string, Rate> = new Map();

	ode?.initials?.forEach((initial) => {
		initialMap.set(initial.target, initial);
	});
	ode?.rates?.forEach((rate) => {
		rateMap.set(rate.target, rate);
	});

	// Check state
	const stateSet = new Set<string>();
	const initialSet = new Set<string>();
	model.states.forEach((state) => {
		const initial = initialMap.get(state.id);
		if (!initial) {
			results.push({ type: 'error', content: `${state.id} has no initial` });
		}
		if (_.isEmpty(initial?.expression)) {
			results.push({ type: 'warn', content: `${state.id} has no initial.expression` });
		}
		if (!isASCII(initial?.expression as string)) {
			results.push({ type: 'warn', content: `${state.id} has non-ascii expression` });
		}
		if (stateSet.has(state.id)) {
			results.push({ type: 'error', content: `state (${state.id}) has duplicate` });
		}
		if (initialSet.has(initial?.target as string)) {
			results.push({ type: 'error', content: `initial (${initial?.target}) has duplicate` });
		}
		stateSet.add(state.id);
		initialSet.add(initial?.target as string);
	});

	// Check transitions
	const transitionSet = new Set<string>();
	const rateSet = new Set<string>();
	model.transitions.forEach((transition) => {
		const rate = rateMap.get(transition.id);
		if (!rate) {
			results.push({ type: 'error', content: `${transition.id} has no rate` });
		}
		if (_.isEmpty(rate?.expression)) {
			results.push({ type: 'warn', content: `${transition.id} has no rate.expression` });
		}
		if (!isASCII(rate?.expression as string)) {
			results.push({ type: 'warn', content: `${transition.id} has non-ascii expression` });
		}
		if (transitionSet.has(transition.id)) {
			results.push({ type: 'error', content: `transition (${transition.id}) has duplicate` });
		}
		if (rateSet.has(rate?.target as string)) {
			results.push({ type: 'error', content: `rate (${rate?.target}) has duplicate` });
		}
		transitionSet.add(transition.id);
		rateSet.add(rate?.target as string);
	});

	return results;
}
