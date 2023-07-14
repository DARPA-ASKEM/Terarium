import _, { cloneDeep } from 'lodash';
import API from '@/api/api';
import { IGraph } from '@graph-scaffolder/types';
import { PetriNetModel, Model, PetriNetTransition, TypingSemantics } from '@/types/Types';
import { PetriNet } from '@/petrinet/petrinet-service';

export interface NodeData {
	type: string;
	strataType?: string;
	expression?: string;
}

export interface EdgeData {
	numEdges: number;
}

// Used to derive equations
// AMR => ACSet => ODE => Equation => Latex
export const convertAMRToACSet = (amr: Model) => {
	const result: PetriNet = {
		S: [],
		T: [],
		I: [],
		O: []
	};

	const petrinetModel = amr.model as PetriNetModel;

	petrinetModel.states.forEach((s) => {
		result.S.push({ sname: s.id });
	});

	petrinetModel.transitions.forEach((t) => {
		result.T.push({ tname: t.id });
	});

	petrinetModel.transitions.forEach((transition) => {
		transition.input.forEach((input) => {
			result.I.push({
				is: result.S.findIndex((s) => s.sname === input) + 1,
				it: result.T.findIndex((t) => t.tname === transition.id) + 1
			});
		});
	});

	petrinetModel.transitions.forEach((transition) => {
		transition.output.forEach((output) => {
			result.O.push({
				os: result.S.findIndex((s) => s.sname === output) + 1,
				ot: result.T.findIndex((t) => t.tname === transition.id) + 1
			});
		});
	});

	return result;
};

export const convertToIGraph = (amr: Model) => {
	const result: IGraph<NodeData, EdgeData> = {
		width: 500,
		height: 500,
		amr: _.cloneDeep(amr),
		nodes: [],
		edges: []
	};

	const petrinetModel = amr.model as PetriNetModel;

	petrinetModel.states.forEach((state) => {
		// The structure of map is an array of arrays, where each inner array has 2 elements.
		// The first element is a state or transition id, the second element is the type id.
		// Find the inner array that matches the current state / transition that we are iterating on
		// Get the second element of that array, which is the id of its type
		const typeMap = amr.semantics?.typing?.map.find(
			(map) => map.length === 2 && state.id === map[0]
		);
		const strataType = typeMap?.[1] ?? '';
		result.nodes.push({
			id: state.id,
			label: state.name ?? state.id,
			type: 'state',
			x: 0,
			y: 0,
			width: 100,
			height: 100,
			data: { type: 'state', strataType },
			nodes: []
		});
	});

	petrinetModel.transitions.forEach((transition) => {
		// The structure of map is an array of arrays, where each inner array has 2 elements.
		// The first element is a state or transition id, the second element is the type id.
		// Find the inner array that matches the current state / transition that we are iterating on
		// Get the second element of that array, which is the id of its type
		const typeMap = amr.semantics?.typing?.map.find(
			(map) => map.length === 2 && transition.id === map[0]
		);

		const strataType = typeMap?.[1] ?? '';
		result.nodes.push({
			id: transition.id,
			label: transition.id,
			type: 'transition',
			x: 0,
			y: 0,
			width: 40,
			height: 40,
			data: { type: 'transition', strataType },
			nodes: []
		});
	});

	petrinetModel.transitions.forEach((transition) => {
		transition.input.forEach((input) => {
			const key = `${input}:${transition.id}`;

			// Collapse hyper edges
			const existingEdge = result.edges.find((edge) => edge.id === key);
			if (existingEdge && existingEdge.data) {
				existingEdge.data.numEdges++;
				return;
			}

			result.edges.push({
				id: key,
				source: input,
				target: transition.id,
				points: [],
				data: { numEdges: 1 }
			});
		});
	});

	petrinetModel.transitions.forEach((transition) => {
		transition.output.forEach((output) => {
			const key = `${transition.id}:${output}`;

			// Collapse hyper edges
			const existingEdge = result.edges.find((edge) => edge.id === key);
			if (existingEdge && existingEdge.data) {
				existingEdge.data.numEdges++;
				return;
			}

			result.edges.push({
				id: key,
				source: transition.id,
				target: output,
				points: [],
				data: { numEdges: 1 }
			});
		});
	});
	return result;
};

const DUMMY_VALUE = -999;
export const convertToAMRModel = (g: IGraph<NodeData, EdgeData>) => g.amr;

export const newAMR = () => {
	const amr: Model = {
		id: '',
		name: 'new model',
		description: '',
		schema:
			'https://raw.githubusercontent.com/DARPA-ASKEM/Model-Representations/petrinet_v0.5/petrinet/petrinet_schema.json',
		schema_name: 'petrinet',
		model_version: '0.1',
		model: {
			states: [],
			transitions: []
		},
		semantics: {
			ode: {
				rates: [],
				initials: [],
				parameters: []
			}
		}
	};
	return amr;
};

export const addState = (amr: Model, id: string, name: string) => {
	if (amr.model.states((s) => s.id === id)) {
		return;
	}
	console.log(`adding state ${id}`);
	amr.model.states.push({
		id,
		name,
		description: ''
	});
	amr.semantics?.ode.initials?.push({
		target: id,
		expression: `${id}init`,
		expression_mathml: `<ci>${id}init</ci>`
	});
	amr.semantics?.ode.parameters?.push({
		id: `${id}init`,
		name: '',
		description: '',
		value: DUMMY_VALUE
	});
};

export const addTransition = (amr: Model, id: string, name: string, value?: number) => {
	if (amr.model.transitions.find((t) => t.id === id)) {
		return;
	}
	amr.model.transitions.push({
		id,
		input: [],
		output: [],
		properties: {
			name,
			description: ''
		}
	});
	amr.semantics?.ode.rates?.push({
		target: id,
		expression: `${id}Param`,
		expression_mathml: `<ci>${id}Param</ci>`
	});
	amr.semantics?.ode.parameters?.push({
		id: `${id}Param`,
		name: '',
		description: '',
		value: value ?? DUMMY_VALUE
	});
};

export const removeState = (amr: Model, id: string) => {
	const model = amr.model as PetriNetModel;

	// Remove from AMR topology
	model.states = model.states.filter((d) => d.id !== id);
	model.transitions.forEach((t) => {
		_.remove(t.input, (d) => d === id);
		_.remove(t.output, (d) => d === id);
	});

	// Remove from semantics
	if (amr.semantics?.ode) {
		const ode = amr.semantics.ode;
		if (ode.initials) {
			_.remove(ode.initials, (d) => d.target === id);
		}
	}
};

export const removeTransition = (amr: Model, id: string) => {
	const model = amr.model as PetriNetModel;

	// Remove from AMR topology
	model.transitions = model.transitions.filter((d) => d.id !== id);

	// Remove from semantics
	if (amr.semantics?.ode) {
		const ode = amr.semantics.ode;
		if (ode.rates) {
			_.remove(ode.rates, (d) => d.target === id);
		}
	}
};

// Update a transition's expression and expression_mathml fields based on
// mass-kinetics
export const updateRateExpression = (
	amr: Model,
	transition: PetriNetTransition,
	parameterId?: string
) => {
	const rate = amr.semantics?.ode.rates.find((d) => d.target === transition.id);
	if (!rate) return;

	const param = amr.semantics?.ode?.parameters?.find(
		(d) => d.id === (parameterId ?? `${transition.id}Param`)
	);
	if (!param) return;

	const inputStr = transition.input.map((d) => `${d}`);
	// eslint-disable-next-line
	const expression = inputStr.join('*') + '*' + param.id;
	// eslint-disable-next-line
	const expressionMathml =
		`<apply><times/>${inputStr.map((d) => `<ci>${d}</ci>`).join('')}<ci>${param.id}</ci>` +
		`</apply>`;

	rate.expression = expression;
	rate.expression_mathml = expressionMathml;
};

export const addEdge = (amr: Model, sourceId: string, targetId: string) => {
	const model = amr.model as PetriNetModel;
	const state = model.states.find((d) => d.id === sourceId);
	if (state) {
		// if source is a state then the target is a transition
		const transition = model.transitions.find((d) => d.id === targetId);
		if (transition) {
			transition.input.push(sourceId);
			updateRateExpression(amr, transition);
		}
	} else {
		// if source is a transition then the target is a state
		const transition = model.transitions.find((d) => d.id === sourceId);
		if (transition) {
			transition.output.push(targetId);
			updateRateExpression(amr, transition);
		}
	}
};

export const removeEdge = (amr: Model, sourceId: string, targetId: string) => {
	const model = amr.model as PetriNetModel;
	const state = model.states.find((d) => d.id === sourceId);
	if (state) {
		const transition = model.transitions.find((d) => d.id === targetId);
		if (!transition) return;

		let c = 0;
		transition.input = transition.input.filter((id) => {
			if (c === 0 && id === sourceId) {
				c++;
				return false;
			}
			return true;
		});
		updateRateExpression(amr, transition);
	} else {
		const transition = model.transitions.find((d) => d.id === sourceId);
		if (!transition) return;

		let c = 0;
		transition.output = transition.output.filter((id) => {
			if (c === 0 && id === targetId) {
				c++;
				return false;
			}
			return true;
		});
		updateRateExpression(amr, transition);
	}
};

export const updateState = (amr: Model, id: string, newId: string, newName: string) => {
	const model = amr.model as PetriNetModel;
	const state = model.states.find((d) => d.id === id);
	if (!state) return;

	state.id = newId;
	state.name = newName;

	const initial = amr.semantics?.ode.initials?.find((d) => d.target === id);
	if (!initial) return;
	initial.target = newId;

	model.transitions.forEach((transition) => {
		for (let i = 0; i < transition.input.length; i++) {
			if (transition.input[i] === id) transition.input[i] = newId;
		}
		for (let i = 0; i < transition.output.length; i++) {
			if (transition.output[i] === id) transition.output[i] = newId;
		}
	});

	model.transitions.forEach((t) => {
		updateRateExpression(amr, t);
	});
};

export const updateTransitione = (amr: Model, id: string, newId: string, newName: string) => {
	const model = amr.model as PetriNetModel;
	const transition = model.transitions.find((d) => d.id === id);
	if (!transition) return;
	transition.id = newId;
	transition.properties.name = newName;

	const rate = amr.semantics?.ode.rates?.find((d) => d.target === id);
	if (!rate) return;
	rate.target = newId;

	model.transitions.forEach((t) => {
		updateRateExpression(amr, t);
	});
};

// Replace typing semantics
export const addTyping = (amr: Model, typing: TypingSemantics) => {
	if (amr.semantics) {
		amr.semantics.typing = typing;
	}
};

// Add a reflexive transition loop to the state
// This is a special type of addTransition that creates a self loop
const DEFAULT_REFLEXIVE_PARAM_VALUE = 1.0;
export const addReflexives = (amr: Model, stateId: string, reflexiveId: string) => {
	addTransition(amr, reflexiveId, reflexiveId, DEFAULT_REFLEXIVE_PARAM_VALUE);
	const transition = (amr.model as PetriNetModel).transitions.find((t) => t.id === reflexiveId);
	if (transition) {
		transition.input = [stateId];
		transition.output = [stateId];
	}
};

export const mergeMetadata = (amr: Model, amrOld: Model) => {
	console.log(amr, amrOld);
};

function modifyModelforStratification(amr: Model) {
	const amrCopy = cloneDeep(amr);
	if (amrCopy.semantics?.typing) {
		const { name, description, schema, semantics } = amrCopy;
		const typeSystem = {
			name,
			description,
			schema,
			model_version: amrCopy.model_version,
			model: semantics?.typing?.system
		};
		amrCopy.semantics.typing.system = typeSystem;
	}
	return amrCopy;
}

export const stratify = async (baseAMR: Model, fluxAMR: Model) => {
	const baseModel = modifyModelforStratification(baseAMR);
	const fluxModel = modifyModelforStratification(fluxAMR);
	console.log({
		baseModel,
		fluxModel
	});
	const response = await API.post('/modeling-request/stratify', {
		baseModel,
		fluxModel
	});
	return response.data as Model;
};
