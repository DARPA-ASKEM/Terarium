import { IGraph } from '@graph-scaffolder/types';
import { AxiosError } from 'axios';
import { logger } from '@/utils/logger';
import API from '@/api/api';
import _ from 'lodash';

export interface PetriNet {
	S: State[]; // List of state names
	T: Transition[]; // List of transition names
	I: Input[]; // List of inputs
	O: Output[]; // List of outputs
}

interface State {
	sname: string;
	uid?: string | number;
}
interface Transition {
	tname: string;
	uid?: string | number;
}
interface Input {
	// Identifies the states connected by an edge going from state -> transition
	it: number; // Transition ID which is the target
	is: number; // State ID which is the source
}
interface Output {
	// Identifies the states connected by an edge going from transition -> state
	ot: number; // Transition ID which is the source
	os: number; // State ID which is the target
}

/*
Validates petrinet - check #2 must come before check #3 to avoid an infinite loop
Returns an string explaining the invalidity or a true boolean if it's valid
*/
export const petriNetValidator = (petrinet: PetriNet): string | true => {
	const { S, T, I, O } = petrinet;
	// console.log(petrinet);

	/* ----- 1. Requires at least one edge ----- */
	if (I.length < 1 && O.length < 1) return 'Invalid petri net: Requires at least one edge';

	/* ----- 2. Check that every node is at least either a source or a target ----- */
	const checkIfSourceOrTarget = (linkedIDs: number[], lastNodeID: number): boolean => {
		for (let id = 1; id < lastNodeID; id++) if (!linkedIDs.includes(id)) return false;
		return true;
	};
	const linkedTransitionIDs: number[] = [
		...new Set([...I.map((input) => input.it), ...O.map((output) => output.ot)])
	];
	const linkedStateIDs: number[] = [
		...new Set([...I.map((input) => input.is), ...O.map((output) => output.os)])
	];
	if (
		!checkIfSourceOrTarget(linkedTransitionIDs, T.length + 1) ||
		!checkIfSourceOrTarget(linkedStateIDs, S.length + 1)
	)
		return 'Invalid petri net: Every node should be at least either a source or a target';

	/* ----- 3. Make sure there aren't multiple petrinet bodies ----- */
	const statesSurroundingTransitions: number[][] = [];
	for (let id = 1; id < T.length + 1; id++) {
		// Save all the states where the current transition is a source or a target
		statesSurroundingTransitions.push([
			...I.filter((input) => input.it === id).map((input) => input.is),
			...O.filter((output) => output.ot === id).map((output) => output.os)
		]);
	}
	// console.log(statesSurroundingTransitions)

	const connectedStates: number[] = statesSurroundingTransitions[0];
	let potentialConnections: number[][] = [];
	// Merge all the arrays in statesSurroundingTransitions that have common values
	do {
		const statesToMerge: number[][] =
			potentialConnections.length > 0 ? potentialConnections : statesSurroundingTransitions;
		potentialConnections = [];

		for (let i = 0; i < statesToMerge.length; i++) {
			if (connectedStates.some((anyPlace) => statesToMerge[i].includes(anyPlace))) {
				connectedStates.push(...statesToMerge[i]);
			} else {
				potentialConnections.push(statesToMerge[i]);
			}
		}
		// console.log([...new Set(connectedStates)]);
		// console.log(potentialConnections);

		// If the potential connections from the last iteration are the exact same then there is more than one petrinet body
		if (statesToMerge.length === potentialConnections.length)
			return 'Invalid petri net: There are multiple petri net bodies';
	} while (potentialConnections.length > 0);

	return true; // All checks have been successfully passed
};

export interface NodeData {
	type: string;
	uid?: string | number;
	strataType: string;
}

export interface EdgeData {
	numEdges: number;
}
export enum NodeType {
	State = 'S',
	Transition = 'T'
}

/**
 * Convert a PetriNet graph representation to PetriNet ACset representation
 *
 * We don't necessarily index-parity in the conversion, that being
 *   X === parseIGraph2PetriNet(parsePetriNet2IGraph(X))
 *
 * Because the index positions could have changed, however it should have parity in terms of semantics.
 */
export const parseIGraph2PetriNet = (
	graph: IGraph<NodeData, EdgeData>,
	attributeForName?: string
) => {
	const result: PetriNet = {
		S: [],
		T: [],
		I: [],
		O: []
	};

	const name = attributeForName || 'label';

	const findNodeById = (id: string) => graph.nodes.find((n) => n.id === id);

	// States and transitions
	graph.nodes.forEach((node) => {
		if (node.data.type === NodeType.State) {
			result.S.push({ sname: node[name] });
		} else {
			result.T.push({ tname: node[name] });
		}
	});

	// Edges
	graph.edges.forEach((edge) => {
		const target = edge.target;
		const source = edge.source;

		// This means the edge from transition => state
		const targetInS = target.startsWith('s');

		if (targetInS) {
			// O
			let ot = -1;
			let os = -1;

			const otNode = findNodeById(target);
			if (otNode) {
				os = result.S.findIndex((d) => d.sname === otNode[name]);
			}
			const osNode = findNodeById(source);
			if (osNode) {
				ot = result.T.findIndex((d) => d.tname === osNode[name]);
			}

			// Julia index starts at 1
			ot++;
			os++;
			if (os > 0 && ot > 0 && edge.data) {
				for (let i = 0; i < edge.data.numEdges; i++) {
					result.O.push({ os, ot });
				}
			}
		} else {
			// I
			let it = -1;
			let is = -1;
			const itNode = findNodeById(target);
			if (itNode) {
				it = result.T.findIndex((d) => d.tname === itNode[name]);
			}

			const isNode = findNodeById(source);
			if (isNode) {
				is = result.S.findIndex((d) => d.sname === isNode[name]);
			}

			// Julia index starts at 1
			it++;
			is++;
			if (is > 0 && it > 0 && edge.data) {
				for (let i = 0; i < edge.data.numEdges; i++) {
					result.I.push({ is, it });
				}
			}
		}
	});
	return result;
};

/**
 * Given a petrinet model convert to an IGraph representation g
 * for the renderer
 * First add each node found in S and T, then add each edge found in I and O
 */
interface PetriSizeConfig {
	S: {
		width: number;
		height: number;
	};
	T: {
		width: number;
		height: number;
	};
}
const defaultSizeConfig: PetriSizeConfig = {
	S: { width: 40, height: 40 },
	T: { width: 40, height: 40 }
};
export const parsePetriNet2IGraph = (
	model: PetriNet,
	config: PetriSizeConfig = defaultSizeConfig
) => {
	const result: IGraph<NodeData, EdgeData> = {
		width: 500,
		height: 500,
		nodes: [],
		edges: []
	};

	// add each nodes in S
	for (let i = 0; i < model.S.length; i++) {
		const aNode = model.S[i];
		result.nodes.push({
			id: `s-${i + 1}`,
			label: aNode.sname,
			x: 0,
			y: 0,
			height: config.S.height,
			width: config.S.width,
			data: { type: NodeType.State, uid: aNode.uid },
			nodes: []
		});
	}

	// Add each node found in T
	for (let i = 0; i < model.T.length; i++) {
		const aTransition = model.T[i];
		result.nodes.push({
			id: `t-${i + 1}`,
			label: aTransition.tname,
			x: 0,
			y: 0,
			height: config.T.height,
			width: config.T.width,
			data: { type: NodeType.Transition, uid: aTransition.uid },
			nodes: []
		});
	} // end T

	// Edges found in I
	for (let i = 0; i < model.I.length; i++) {
		const iEdges = model.I[i];
		const sourceId = `s-${iEdges.is}`;
		const targetId = `t-${iEdges.it}`;

		// Collapse hyper edges
		const existingEdge = result.edges.find(
			(edge) => edge.source === sourceId && edge.target === targetId
		);
		if (existingEdge && existingEdge.data) {
			existingEdge.data.numEdges++;
			continue;
		}

		result.edges.push({
			source: sourceId,
			target: targetId,
			points: [],
			data: {
				numEdges: 1
			}
		});
	}
	// Edges found in O
	for (let i = 0; i < model.O.length; i++) {
		const oEdges = model.O[i];
		const sourceId = `t-${oEdges.ot}`;
		const targetId = `s-${oEdges.os}`;

		// Collapse hyper edges
		const existingEdge = result.edges.find(
			(edge) => edge.source === sourceId && edge.target === targetId
		);
		if (existingEdge && existingEdge.data) {
			existingEdge.data.numEdges++;
			continue;
		}

		result.edges.push({
			source: sourceId,
			target: targetId,
			points: [],
			data: {
				numEdges: 1
			}
		});
	}
	return result;
};

// Transform list of mathML strings to a petrinet ascet
export const mathmlToPetri = async (mathml: string[]) => {
	try {
		const resp = await API.post('/transforms/mathml-to-acset', mathml);

		if (resp && resp.status === 200 && resp.data) {
			return resp.data;
		}
		logger.error('mathmlToPetri: Server did not provide a correct response', { showToast: false });
	} catch (error: unknown) {
		if ((error as AxiosError).isAxiosError) {
			const axiosError = error as AxiosError;
			logger.error('mathmlToPetri Error: ', axiosError.response?.data || axiosError.message, {
				showToast: false
			});
		} else {
			logger.error(error, { showToast: false });
		}
	}
	return null;
};

// Transfrom a petrinet into latex
export const petriToLatex = async (petri: PetriNet): Promise<string | null> => {
	try {
		const payloadPetri = {
			S: petri.S.map((s) => ({ sname: s.sname })),
			T: petri.T.map((t) => ({ tname: t.tname })),
			I: petri.I,
			O: petri.O
		};

		const resp = await API.post('/transforms/acset-to-latex', payloadPetri);

		if (resp && resp.status === 200 && resp.data && typeof resp.data === 'string') {
			return resp.data;
		}

		logger.error('petriToLatex: Server did not provide a correct response', { showToast: false });
	} catch (error: unknown) {
		if ((error as AxiosError).isAxiosError) {
			const axiosError = error as AxiosError;
			logger.error('petriToLatex Error:', axiosError.response?.data || axiosError.message, {
				showToast: false
			});
		} else {
			logger.error(error, { showToast: false });
		}
	}
	return null;
};

/**
 * @description: clone graphOne X times where X is the length of nodes in graphTwo.
 * 				Example, SIR + QNQ leads to two distinct SIR graphs. One labelled for Q and one labelled for NQ
 * @param: graphOne, IGraph, a graph you want to clone
 * @param: graphTwo -> IGraph, a graph you want to clone
 * @return IGraph
 * @todo: https://github.com/DARPA-ASKEM/Terarium/issues/868
 */

function cloneFirstGraph(
	graphOne: IGraph<NodeData, EdgeData>,
	graphTwo: IGraph<NodeData, EdgeData>
) {
	const resultGraph: IGraph<NodeData, EdgeData> = { nodes: [], edges: [] };
	for (let i = 0; i < graphTwo.nodes.length; i++) {
		if (graphTwo.nodes[i].data.type === NodeType.State) {
			const tempGraph = _.cloneDeep(graphOne);
			tempGraph.nodes.forEach((node) => {
				node.id = `${node.id},${graphTwo.nodes[i].id}`;
				node.label = `${node.label},${graphTwo.nodes[i].label}`;
				resultGraph.nodes.push(node);
			});
			tempGraph.edges.forEach((edge) => {
				edge.source = `${edge.source},${graphTwo.nodes[i].id}`;
				edge.target = `${edge.target},${graphTwo.nodes[i].id}`;
				resultGraph.edges.push(edge);
			});
		}
	}
	return resultGraph;
}

/**
 * @description: Take 2 PetriNets and stratify them without any typing or ontology
 * @return IGraph of result for easy graphing
 * @todo: https://github.com/DARPA-ASKEM/Terarium/issues/868
 */

export function blindStratification(petrinetOne: PetriNet, petrinetTwo: PetriNet) {
	const graphOne = parsePetriNet2IGraph(petrinetOne);
	const graphTwo = parsePetriNet2IGraph(petrinetTwo);
	const resultGraph: IGraph<NodeData, EdgeData> = cloneFirstGraph(graphOne, graphTwo);
	// Add graphTwo's shape to connect everything
	for (let i = 0; i < graphTwo.edges.length; i++) {
		for (let j = 0; j < graphOne.nodes.length; j++) {
			if (graphOne.nodes[j].data.type === NodeType.State) {
				// Find the edges source that type as transition.
				const graphTwoSourceNode = graphTwo.nodes.find(
					(node) => node.id === graphTwo.edges[i].source && node.data.type === NodeType.Transition
				);
				if (graphTwoSourceNode) {
					// Create the (S,transition) nodes (Example: S,unquarantine)
					const newTransitionId = `${graphOne.nodes[j].id},${graphTwoSourceNode.id}`;
					const newTransitionLabel = `${graphOne.nodes[j].label},${graphTwoSourceNode.label}`;
					resultGraph.nodes.push({
						id: newTransitionId,
						label: newTransitionLabel,
						data: { type: NodeType.Transition },
						height: graphOne.nodes[j].height,
						width: graphOne.nodes[j].width,
						x: graphOne.nodes[j].x,
						y: graphOne.nodes[j].y,
						nodes: []
					});
				}
				// Create the edges for (S,transition)
				const newSource = `${graphOne.nodes[j].id},${graphTwo.edges[i].source}`;
				const newTarget = `${graphOne.nodes[j].id},${graphTwo.edges[i].target}`;
				resultGraph.edges.push({ source: newSource, target: newTarget, points: [] });
			}
		}
	}
	return resultGraph;
}
