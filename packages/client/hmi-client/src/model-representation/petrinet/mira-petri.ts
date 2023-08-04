import _ from 'lodash';
import { Model, PetriNetTransition } from '@/types/Types';

export const getStates = (amr: Model) => {
	const model = amr.model;
	const lookup = new Map();
	const matrixData: object[] = [];

	const dupe: Set<string> = new Set();
	const uniqueStates: any[] = []; // FIXME: grounding typing incorrect

	for (let i = 0; i < model.states.length; i++) {
		const state = model.states[i];
		const grounding = state.grounding;

		const obj: any = {};
		obj._id = state.id;

		if (grounding && grounding.modifiers) {
			const modifierKeys = Object.keys(grounding.modifiers);
			let str = state.id;
			modifierKeys.forEach((key) => {
				str = str.replace(`_${grounding.modifiers[key]}`, '');
				obj[key] = grounding.modifiers[key];
			});
			obj._base = str;

			lookup.set(state.id, str);
			if (!dupe.has(str)) {
				uniqueStates.push({
					id: str,
					name: str,
					grounding: { identifiers: grounding.identifiers }
				});
			}
			dupe.add(str);
		} else {
			obj._base = state.id;
			lookup.set(state.id, state.id);
			if (!dupe.has(state.id)) {
				uniqueStates.push({
					id: state.id,
					name: state.id,
					grounding: { identifiers: grounding.identifiers }
				});
			}
			dupe.add(state.id);
		}
		matrixData.push(obj);
	}
	return { uniqueStates, lookup, matrixData };
};

export const getTransitions = (amr: Model, lookup: Map<string, string>) => {
	const model = amr.model;
	const uniqueTransitions: Partial<PetriNetTransition>[] = [];
	const matrixData: object[] = [];

	// Cache state-modifiers for faster fetch
	const stateModifierMap = new Map();
	model.states.forEach((state) => {
		stateModifierMap.set(state.id, state.grounding.modifiers);
	});

	const isSameTransition = (t1: Partial<PetriNetTransition>, t2: Partial<PetriNetTransition>) =>
		_.isEqual(t1.input, t2.input) && _.isEqual(t1.output, t2.output);

	let c = 0;

	// Rebuild transitions by contracting the connecting states and fnding the
	// unique set at the end
	for (let i = 0; i < model.transitions.length; i++) {
		const obj: any = {};
		const transition = model.transitions[i];
		const input = transition.input.map((d) => lookup.get(d));
		const output = transition.output.map((d) => lookup.get(d));
		const newTransition = { id: '', input, output };

		// Build matrixData array
		obj._id = transition.id;
		transition.input.forEach((sid) => {
			const modifiers = stateModifierMap.get(sid);
			if (modifiers) {
				Object.keys(modifiers).forEach((k) => {
					obj[k] = modifiers[k];
				});
			}
		});

		const existingTransition = uniqueTransitions.find((d) => isSameTransition(d, newTransition));

		if (!existingTransition) {
			const newId = `T${c++}`;
			newTransition.id = newId;
			uniqueTransitions.push(_.cloneDeep(newTransition));
			obj._base = newId;
		} else {
			obj._base = existingTransition.id;
		}
		matrixData.push(obj);
	}
	return { uniqueTransitions, matrixData };
};

export const runExtraction = (matrixData: any[], stratas: string[]) => {
	if (stratas.length === 0) {
		return {};
	}
	let result: any = _.groupBy(matrixData, stratas[0]);
	const nextStratas = _.clone(stratas);
	nextStratas.shift();

	Object.keys(result).forEach((key) => {
		if (key === 'undefined') {
			// No result, skip and start on the next
			result = runExtraction(matrixData, nextStratas);
		} else {
			// Go down to the next depth
			result[key] = runExtraction(result[key], nextStratas);
		}
	});
	return result;
};

/**
 * Given an MIRA AMR, extract and compute a presentation-layer data format
 */
export const getAMRPresentationData = (amr: Model) => {
	const statesData = getStates(amr);
	const transitionsData = getTransitions(amr, statesData.lookup);

	const compactModel = {
		states: statesData.uniqueStates,
		transitions: transitionsData.uniqueTransitions
	};

	return {
		compactModel,
		stateMatrixData: statesData.matrixData,
		transitionMatrixData: transitionsData.matrixData
	};
};
