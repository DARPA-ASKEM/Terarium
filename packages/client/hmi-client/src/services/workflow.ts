import { Component } from 'vue';
import { v4 as uuidv4 } from 'uuid';
import _ from 'lodash';
import API from '@/api/api';
import { logger } from '@/utils/logger';
import { EventEmitter } from '@/utils/emitter';
import type { Position } from '@/types/common';
import type {
	Operation,
	OperationData,
	Size,
	Workflow,
	WorkflowEdge,
	WorkflowNode,
	WorkflowPort,
	WorkflowOutput,
	WorkflowAnnotation
} from '@/types/workflow';
import {
	WorkflowPortStatus,
	OperatorStatus,
	WorkflowOperationTypes,
	WorkflowTransformations,
	Transform
} from '@/types/workflow';
import { useProjects } from '@/composables/project';
import useAuthStore from '@/stores/auth';
import dagre from 'dagre';

/**
 * A wrapper class around the workflow data struture to make it easier
 * to deal with CURD operations
 * */
export class WorkflowWrapper {
	private wf: Workflow;

	constructor(wf?: Workflow) {
		if (wf) {
			this.wf = _.cloneDeep(wf);
		} else {
			this.wf = emptyWorkflow();
		}
	}

	// This will replace the entire workflow, should only use for initial load
	// as there it will not propapate reactivity
	load(wf: Workflow) {
		this.wf = _.cloneDeep(wf);
	}

	dump() {
		return this.wf;
	}

	/**
	 * FIXME: Need to split workflow into different categories and sending the commands
	 * instead of the result. It is possible here to become de-synced: eg state-update-response
	 * comes in as we are about to change the output ports.
	 *
	 * delayUpdate is a used to indicate there are actions in progress, and an update from
	 * the DB can potentially overwrite what the user had already done that have yet to be flushed
	 * to the backend. In situation like this, we will update the version (so our subsequent updates are
	 * not rejected) and skip the rest. For example, the user may be dragging an operator on the
	 * canvas when the db upate comes in.
	 * */
	update(updatedWF: Workflow, delayUpdate: boolean) {
		if (updatedWF.id !== this.wf.id) {
			throw new Error(`Workflow failed, inconsistent ids updated=${updatedWF.id} self=${this.wf.id}`);
		}
		this.wf.name = updatedWF.name;
		this.wf.description = updatedWF.description;
		this.wf.annotations = updatedWF.annotations;

		const nodes = this.wf.nodes;
		const edges = this.wf.edges;
		const updatedNodeMap = new Map<string, WorkflowNode<any>>(updatedWF.nodes.map((n) => [n.id, n]));
		const updatedEdgeMap = new Map<string, WorkflowEdge>(updatedWF.edges.map((e) => [e.id, e]));

		if (delayUpdate) {
			for (let i = 0; i < nodes.length; i++) {
				const nodeId = nodes[i].id;
				const updated = updatedNodeMap.get(nodeId);
				if (updated) {
					if (!nodes[i].version || (updated.version as number) > (nodes[i].version as number)) {
						nodes[i].version = updated.version;
					}
				}
			}
			for (let i = 0; i < edges.length; i++) {
				const edgeId = edges[i].id;
				const updated = updatedEdgeMap.get(edgeId);
				if (updated) {
					if (!edges[i].version || (updated.version as number) > (edges[i].version as number)) {
						edges[i].version = updated.version;
					}
				}
			}
			return;
		}

		// Update and deletes
		for (let i = 0; i < nodes.length; i++) {
			const nodeId = nodes[i].id;
			const updated = updatedNodeMap.get(nodeId);
			if (updated) {
				if (!nodes[i].version || (updated.version as number) > (nodes[i].version as number)) {
					nodes[i].version = updated.version;
					nodes[i].isDeleted = updated.isDeleted;
					nodes[i].status = updated.status;
					nodes[i].x = updated.x;
					nodes[i].y = updated.y;
					nodes[i].width = updated.width;
					nodes[i].height = updated.height;
					nodes[i].active = updated.active;

					if (!_.isEqual(nodes[i].inputs, updated.inputs)) {
						nodes[i].inputs = updated.inputs;
					}
					if (!_.isEqual(nodes[i].outputs, updated.outputs)) {
						nodes[i].outputs = updated.outputs;
					}
					if (!_.isEqual(nodes[i].state, updated.state)) {
						nodes[i].state = updated.state;
					}
				}
				updatedNodeMap.delete(nodeId);
			}
		}
		for (let i = 0; i < edges.length; i++) {
			const edgeId = edges[i].id;
			const updated = updatedEdgeMap.get(edgeId);
			if (updated) {
				if (!edges[i].version || (updated.version as number) > (edges[i].version as number)) {
					edges[i] = Object.assign(edges[i], updated);
				}
				updatedEdgeMap.delete(edgeId);
			}
		}

		// New elements
		[...updatedNodeMap.values()].forEach((node) => this.wf.nodes.push(node));
		[...updatedEdgeMap.values()].forEach((edge) => this.wf.edges.push(edge));
	}

	getId() {
		return this.wf.id;
	}

	getName() {
		return this.wf.name;
	}

	getTransform() {
		return this.wf.transform;
	}

	getNodes() {
		return this.wf.nodes.filter((d) => d.isDeleted !== true);
	}

	getEdges() {
		return this.wf.edges.filter((d) => d.isDeleted !== true);
	}

	getAnnotations() {
		if (this.wf.annotations) {
			return Object.values(this.wf.annotations);
		}
		return [];
	}

	// @deprecated
	removeNode(id: string) {
		// Remove all the edges first
		const edgesToRemove = this.getEdges().filter((d) => d.source === id || d.target === id);
		const edgeIds = edgesToRemove.map((d) => d.id);
		edgeIds.forEach((edgeId) => {
			this.removeEdge(edgeId);
		});

		// Tombstone
		const node = this.wf.nodes.find((n) => n.id === id);
		if (node) {
			node.isDeleted = true;
		}
	}

	// @deprecated
	removeEdge(id: string) {
		const edgeToRemove = this.wf.edges.find((d) => d.id === id);
		if (!edgeToRemove) return;

		// Remove the data reference at the targetPort
		const targetNode = this.wf.nodes.find((d) => d.id === edgeToRemove.target);
		if (!targetNode) return;

		const targetPort = targetNode.inputs.find((d) => d.id === edgeToRemove.targetPortId);
		if (!targetPort) return;

		targetPort.value = null;
		targetPort.status = WorkflowPortStatus.NOT_CONNECTED;
		delete targetPort.label;

		// Resets the type to the original type (used when multiple types for a port are allowed)
		if (targetPort?.originalType) {
			targetPort.type = targetPort.originalType;
		}

		// Tombstone
		const edge = this.wf.edges.find((e) => e.id === id);
		if (edge) {
			edge.isDeleted = true;
		}

		// If there are no more references reset the connected status of the source node
		if (_.isEmpty(this.getEdges().filter((e) => e.source === edgeToRemove.source))) {
			const sourceNode = this.wf.nodes.find((d) => d.id === edgeToRemove.source);
			if (!sourceNode) return;
			const sourcePort = sourceNode.outputs.find((d) => d.id === edgeToRemove.sourcePortId);
			if (!sourcePort) return;
			sourcePort.status = WorkflowPortStatus.NOT_CONNECTED;
		}
	}

	addNode(op: Operation, pos: Position, options: { size?: OperatorNodeSize; state?: any }) {
		let currentUserName: string | undefined = '';
		try {
			currentUserName = useAuthStore().user?.username;
		} catch (err) {
			// do nothing
		}

		const nodeSize: Size = getOperatorNodeSize(options.size ?? OperatorNodeSize.medium);

		const node: WorkflowNode<any> = {
			id: uuidv4(),
			workflowId: this.wf.id,
			operationType: op.name,
			displayName: op.displayName,
			documentationUrl: op.documentationUrl,
			imageUrl: op.imageUrl,
			x: pos.x,
			y: pos.y,

			createdBy: currentUserName,
			createdAt: Date.now(),

			active: null,
			state: options.state ?? {},
			uniqueInputs: op.uniqueInputs ?? false,

			inputs: op.inputs.map((port) => ({
				id: uuidv4(),
				type: port.type,
				label: port.label,
				status: WorkflowPortStatus.NOT_CONNECTED,
				value: null,
				isOptional: port.isOptional ?? false
			})),

			outputs: op.outputs.map((port) => ({
				id: uuidv4(),
				type: port.type,
				label: port.label,
				status: WorkflowPortStatus.NOT_CONNECTED,
				value: null,
				isOptional: false,
				state: {}
			})),

			status: OperatorStatus.INVALID,
			width: nodeSize.width,
			height: nodeSize.height
		};
		if (op.initState && _.isEmpty(node.state)) {
			node.state = op.initState();
		}
		this.wf.nodes.push(node);
		return node;
	}

	/**
	 * Create an edge between two nodes, and transfer the value from
	 * source-node's output port to target-node's input port
	 *
	 * The terms are used as depicted in the following schematics
	 *
	 *
	 *  ------------             ------------
	 *  | Source    |            | Target    |
	 *  |           |            |           |
	 *  |  [output port] ---> [input port]   |
	 *  |           |            |           |
	 *  ------------             -------------
	 *
	 *
	 * There are several special cases
	 *
	 * The target-input can accept multiple types, but this is more of an exception
	 * rather than the norm. This is resolved by checking one of the accepted type
	 * matches the provided type.
	 *
	 * The second case is when the source produce multiple types, a similar check, but
	 * on the reverse side is performed.
	 *
	 *
	 * Ambiguity arises when the source provide multiple types and the target accepts multiple
	 * types and there is no resolution. For example
	 * - source provides {A, B, C}
	 * - target accepts A or C
	 * We do not deal with this and throw an error/warning, and the edge creation will be cancelled.
	 *
	 * */
	addEdge(sourceId: string, sourcePortId: string, targetId: string, targetPortId: string, points: Position[]) {
		let currentUserName: string | undefined = '';
		try {
			currentUserName = useAuthStore().user?.username;
		} catch (err) {
			// do nothing
		}

		const sourceNode = this.wf.nodes.find((d) => d.id === sourceId);
		const targetNode = this.wf.nodes.find((d) => d.id === targetId);
		if (!sourceNode || !targetNode) return;

		const sourceOutputPort = sourceNode.outputs.find((d) => d.id === sourcePortId);
		const targetInputPort = targetNode.inputs.find((d) => d.id === targetPortId);
		if (!sourceOutputPort || !targetInputPort) return;

		// Check if edge already exist
		const existingEdge = this.getEdges().find(
			(d) =>
				d.source === sourceId &&
				d.sourcePortId === sourcePortId &&
				d.target === targetId &&
				d.targetPortId === targetPortId
		);
		if (existingEdge) return;

		// Check if type is compatible
		const outputTypes = sourceOutputPort.type.split('|').map((d) => d.trim());
		const allowedInputTypes = targetInputPort.type.split('|').map((d) => d.trim());
		const intersectionTypes = _.intersection(outputTypes, allowedInputTypes);

		// Not supported if there are more than one match
		if (intersectionTypes.length > 1) {
			console.error(`Ambiguous matching types [${outputTypes}] to [${allowedInputTypes}]`);
			return;
		}

		// Not supported if there is a mismatch
		if (intersectionTypes.length === 0 || targetInputPort.status === WorkflowPortStatus.CONNECTED) {
			return;
		}

		// check if the port value is unique, if so, we should not connect the incoming edge
		if (
			targetNode.inputs.some(
				(input) => targetNode.uniqueInputs && input.value?.[0] && input.value?.[0] === sourceOutputPort.value?.[0]
			)
		) {
			return;
		}

		// Transfer data value/reference
		targetInputPort.label = sourceOutputPort.label;
		if (outputTypes.length > 1) {
			const concreteType = intersectionTypes[0];
			if (sourceOutputPort.value) {
				targetInputPort.value = [sourceOutputPort.value[0][concreteType]];
			}
		} else {
			targetInputPort.value = sourceOutputPort.value;
		}

		// Transfer concrete type to the input type to match the output type
		// Saves the original type in case we want to revert when we unlink the edge
		if (allowedInputTypes.length > 1) {
			targetInputPort.originalType = targetInputPort.type;
			targetInputPort.type = sourceOutputPort.type;
		}

		const edge: WorkflowEdge = {
			id: uuidv4(),
			workflowId: this.wf.id,
			source: sourceId,
			sourcePortId,
			target: targetId,
			targetPortId,
			createdBy: currentUserName,
			createdAt: Date.now(),
			points: _.cloneDeep(points)
		};
		this.wf.edges.push(edge);
		sourceOutputPort.status = WorkflowPortStatus.CONNECTED;
		targetInputPort.status = WorkflowPortStatus.CONNECTED;
	}

	/**
	 * Including the node given by nodeId, copy/branch everything downstream,
	 *
	 * For example:
	 *    P - B - C - D
	 *    Q /
	 *
	 * if we branch at B, we will get
	 *
	 *    P - B  - C  - D
	 *      X
	 *    Q _ B' - C' - D'
	 *
	 * with { B', C', D' } new node entities
	 * */
	branchWorkflow(nodeId: string) {
		// 1. Find anchor point
		const anchor = this.getNodes().find((n) => n.id === nodeId);
		if (!anchor) return;

		// 2. Collect the subgraph that we want to copy
		const copyNodes: WorkflowNode<any>[] = [];
		const copyEdges: WorkflowEdge[] = [];
		const stack = [anchor.id]; // working list of nodeIds to crawl
		const processed: Set<string> = new Set();

		// basically depth-first-search
		while (stack.length > 0) {
			const id = stack.pop();
			const node = this.getNodes().find((n) => n.id === id);
			if (node) copyNodes.push(_.cloneDeep(node));
			processed.add(id as string);

			// Grab downstream edges
			const edges = this.getEdges().filter((e) => e.source === id);
			edges.forEach((edge) => {
				const newId = edge.target as string;
				if (!processed.has(newId)) {
					stack.push(edge.target as string);
				}
				copyEdges.push(_.cloneDeep(edge));
			});
		}

		// 3. Collect the upstream edges
		const targetIds = copyNodes.map((n) => n.id);
		const upstreamEdges = this.getEdges().filter((edge) => targetIds.includes(edge.target as string));
		const anchorUpstreamEdges = this.getEdges().filter((edge) => edge.target === anchor.id);
		upstreamEdges.forEach((edge) => {
			if (copyEdges.find((copyEdge) => copyEdge.id === edge.id)) return;
			copyEdges.push(_.cloneDeep(edge));
		});

		// 4. Reassign identifiers
		const registry: Map<string, string> = new Map();
		copyNodes.forEach((node) => {
			registry.set(node.id, uuidv4());
			node.inputs.forEach((port) => {
				registry.set(port.id, uuidv4());
			});
			node.outputs.forEach((port) => {
				registry.set(port.id, uuidv4());
			});
		});
		copyEdges.forEach((edge) => {
			registry.set(edge.id, uuidv4());
		});

		copyEdges.forEach((edge) => {
			// Don't replace anchor upstream edge sources, they are still valid
			if (anchorUpstreamEdges.map((e) => e.source).includes(edge.source) === false) {
				edge.source = registry.get(edge.source as string);
				edge.sourcePortId = registry.get(edge.sourcePortId as string);
			}
			edge.id = registry.get(edge.id) as string;
			edge.target = registry.get(edge.target as string);
			edge.targetPortId = registry.get(edge.targetPortId as string);
		});
		copyNodes.forEach((node) => {
			node.id = registry.get(node.id) as string;
			node.inputs.forEach((port) => {
				port.id = registry.get(port.id) as string;
			});
			node.outputs.forEach((port) => {
				port.id = registry.get(port.id) as string;
			});
			if (node.active) {
				node.active = registry.get(node.active) || null;
			}
		});

		// 5. Reposition new nodes so they don't exaclty overlap
		const offset = 75;
		copyNodes.forEach((n) => {
			n.y += offset;
		});
		copyEdges.forEach((edge) => {
			if (!edge.points || edge.points.length < 2) return;
			if (copyNodes.map((n) => n.id).includes(edge.source as string) === true) {
				edge.points[0].y += offset;
			}
			if (copyNodes.map((n) => n.id).includes(edge.target as string) === true) {
				edge.points[edge.points.length - 1].y += offset;
			}
		});

		// 6. Finally put everything back into the workflow
		this.wf.nodes.push(...copyNodes);
		this.wf.edges.push(...copyEdges);
	}

	/**
	 * Sets the state and/or output for a given workflow node.
	 *
	 * @param {WorkflowNode<any>} node - The workflow node to set options for.
	 * @param {Object} options - The options to set for the node.
	 * @param {any} [options.state] - The state to set for the node.
	 * @param {any} [options.outputValue] - The output value to set for the node.
	 */
	updateNode(
		node: WorkflowNode<any>,
		options: {
			state: any;
			output?: Partial<WorkflowOutput<any>>;
		}
	) {
		// set state and statuses as invalid initially
		node.status = OperatorStatus.INVALID;
		node.state = Object.assign(node.state, options.state);
		const outputPort = node.outputs[0];
		if (!outputPort) return;
		outputPort.operatorStatus = node.status;

		// if there is an output set the output port and set statuses to success
		if (!_.isEmpty(options.output)) {
			node.active = outputPort.id;
			node.status = OperatorStatus.SUCCESS;
			outputPort.operatorStatus = node.status;
			Object.assign(outputPort, options.output);
			this.selectOutput(node, outputPort.id);
		}
	}

	/**
	 * Updates the operator's state using the data from a specified WorkflowOutput. If the operator's
	 * current state was not previously stored as a WorkflowOutput, this function first saves the current state
	 * as a new WorkflowOutput. It then replaces the operator's existing state with the data from the specified WorkflowOutput.
	 *
	 * @param operator - The operator whose state is to be updated.
	 * @param selectedWorkflowOutputId - The ID of the WorkflowOutput whose data will be used to update the operator's state.
	 * */
	selectOutput(operator: WorkflowNode<any>, selectedWorkflowOutputId: WorkflowOutput<any>['id']) {
		operator.outputs.forEach((output) => {
			output.isSelected = false;
			output.status = WorkflowPortStatus.NOT_CONNECTED;
		});

		// Update the Operator state with the selected one
		const selected = operator.outputs.find((output) => output.id === selectedWorkflowOutputId);
		if (!selected) {
			logger.warn(
				`Operator Output Id ${selectedWorkflowOutputId} does not exist within ${operator.displayName} Operator ${operator.id}.`
			);
			return;
		}

		selected.isSelected = true;
		operator.state = Object.assign(operator.state, _.cloneDeep(selected.state));
		operator.status = selected.operatorStatus ?? OperatorStatus.DEFAULT;
		operator.active = selected.id;

		// If this output is connected to input port(s), update the input port(s)
		const hasOutgoingEdges = this.getEdges().some((edge) => edge.source === operator.id);
		if (!hasOutgoingEdges) return;

		selected.status = WorkflowPortStatus.CONNECTED;

		const nodeMap = new Map<string, WorkflowNode<any>>(this.wf.nodes.map((node) => [node.id, node]));
		const nodeCache = new Map<string, WorkflowNode<any>[]>();
		nodeCache.set(operator.id, []);

		this.getEdges().forEach((edge) => {
			// Update the input port of the direct target node
			if (edge.source === operator.id) {
				const targetNode = this.wf.nodes.find((node) => node.id === edge.target);
				if (!targetNode) return;
				// Update the input port of the target node
				const targetPort = targetNode.inputs.find((port) => port.id === edge.targetPortId);
				if (!targetPort) return;
				edge.sourcePortId = selected.id; // Sync edge source port to selected output

				/**
				 * Handle compound type unwrangling, this is very similar to what
				 * we do in addEdge(...) but s already connected.
				 * */
				const selectedTypes = selected.type.split('|').map((d) => d.trim());
				const allowedInputTypes = targetPort.type.split('|').map((d) => d.trim());
				const intersectionTypes = _.intersection(selectedTypes, allowedInputTypes);

				// Sanity check: multiple matches found
				if (intersectionTypes.length > 1) {
					console.error(`Ambiguous matching types [${selectedTypes}] to [${allowedInputTypes}]`);
					return;
				}
				// Sanity check: no matches found
				if (intersectionTypes.length === 0) {
					return;
				}

				if (selectedTypes.length > 1) {
					const concreteType = intersectionTypes[0];
					if (selected.value) {
						targetPort.value = [selected.value[0][concreteType]];
					}
				} else {
					targetPort.value = selected.value; // mark
				}

				targetPort.label = selected.label;
			}

			// Collect node cache
			if (!edge.source || !edge.target) return;
			if (!nodeCache.has(edge.source)) nodeCache.set(edge.source, []);
			nodeCache.get(edge.source)?.push(nodeMap.get(edge.target) as WorkflowNode<any>);
		});

		cascadeInvalidateDownstream(operator, nodeCache);
	}

	updateNodeState(nodeId: string, state: any) {
		const node = this.getNodes().find((d) => d.id === nodeId);
		if (!node) return;
		node.state = state;
	}

	updateNodeStatus(nodeId: string, status: OperatorStatus) {
		const node = this.getNodes().find((d) => d.id === nodeId);
		if (!node) return;
		node.status = status;
	}

	// Get neighbor nodes for drilldown navigation
	getNeighborNodes = (id: string) => {
		const cache = new Map(this.getNodes().map((node) => [node.id, node]));
		const inputEdges = this.getEdges().filter((e) => e.target === id);
		const outputEdges = this.getEdges().filter((e) => e.source === id);
		return {
			upstreamNodes: inputEdges.map((e) => e.source && cache.get(e.source)).filter(Boolean) as WorkflowNode<any>[],
			downstreamNodes: outputEdges.map((e) => e.target && cache.get(e.target)).filter(Boolean) as WorkflowNode<any>[]
		};
	};

	runDagreLayout() {
		const g = new dagre.graphlib.Graph({ compound: true });
		g.setGraph({});
		g.setDefaultEdgeLabel(() => ({}));
		g.graph().rankDir = 'LR';
		g.graph().nodesep = 120;
		g.graph().ranksep = 120;
		this.getNodes().forEach((node) => {
			g.setNode(node.id, {
				label: node.displayName,
				width: node.width,
				height: node.height
			});
		});

		this.getEdges().forEach((edge) => {
			g.setEdge(edge.source, edge.target);
		});

		dagre.layout(g);

		this.getNodes().forEach((node) => {
			const n = g.node(node.id);
			if (!n) return;
			node.x = n.x;
			node.y = n.y;
		});
	}

	setWorkflowName(name: string) {
		this.wf.name = name;
	}

	setWorkflowScenario(scenario: any) {
		this.wf.scenario = scenario;
	}
}

/**
 * Captures common actions performed on workflow nodes/edges. The functions here are
 * not optimized, on the account that we don't expect most workflow graphs to
 * exceed say ... 10-12 nodes with 30-40 edges.
 *
 */
export const emptyWorkflow = (name: string = 'test', description: string = '') => {
	const workflow: Workflow = {
		id: uuidv4(),
		name,
		description,

		transform: { x: 0, y: 0, k: 1 },
		nodes: [],
		edges: []
	};
	return workflow;
};

export enum OperatorNodeSize {
	small,
	medium,
	large,
	xlarge
}

export function getOperatorNodeSize(size: OperatorNodeSize): Size {
	switch (size) {
		case OperatorNodeSize.small:
			return { width: 140, height: 220 };
		case OperatorNodeSize.large:
			return { width: 300, height: 220 };
		case OperatorNodeSize.xlarge:
			return { width: 420, height: 220 };
		case OperatorNodeSize.medium:
		default:
			return { width: 180, height: 220 };
	}
}

export const isAssetOperator = (operationType: string) =>
	(
		[
			WorkflowOperationTypes.MODEL,
			WorkflowOperationTypes.DATASET,
			WorkflowOperationTypes.DOCUMENT,
			WorkflowOperationTypes.CODE
		] as string[]
	).includes(operationType);

export const iconToOperatorMap = new Map<string, string>([
	[WorkflowOperationTypes.DOCUMENT, 'pi pi-file'],
	[WorkflowOperationTypes.MODEL, 'pi pi-share-alt'],
	[WorkflowOperationTypes.DATASET, 'pi pi-table'],
	[WorkflowOperationTypes.SIMULATE_CIEMSS, 'pi pi-chart-line'],
	[WorkflowOperationTypes.CALIBRATION_CIEMSS, 'pi pi-chart-line'],
	[WorkflowOperationTypes.MODEL_CONFIG, 'pi pi-cog'],
	[WorkflowOperationTypes.STRATIFY_MIRA, 'pi pi-share-alt'],
	[WorkflowOperationTypes.SIMULATE_ENSEMBLE_CIEMSS, 'pi pi-chart-line'],
	[WorkflowOperationTypes.CALIBRATE_ENSEMBLE_CIEMSS, 'pi pi-chart-line'],
	[WorkflowOperationTypes.DATASET_TRANSFORMER, 'pi pi-table'],
	[WorkflowOperationTypes.SUBSET_DATA, 'pi pi-table'],
	[WorkflowOperationTypes.FUNMAN, 'pi pi-cog'],
	[WorkflowOperationTypes.CODE, 'pi pi-code'],
	[WorkflowOperationTypes.MODEL_COMPARISON, 'pi pi-share-alt'],
	[WorkflowOperationTypes.OPTIMIZE_CIEMSS, 'pi pi-chart-line'],
	[WorkflowOperationTypes.MODEL_EDIT, 'pi pi-share-alt'],
	[WorkflowOperationTypes.MODEL_FROM_EQUATIONS, 'pi pi-share-alt'],
	[WorkflowOperationTypes.REGRIDDING, 'pi pi-table'],
	[WorkflowOperationTypes.INTERVENTION_POLICY, 'pi pi-cog']
]);

// Get port label for frontend
const defaultPortLabels = {
	modelId: 'Model',
	modelConfigId: 'Model configuration',
	datasetId: 'Dataset',
	simulationId: 'Simulation',
	codeAssetId: 'Code asset'
};

export function getPortLabel({ label, type, isOptional }: WorkflowPort) {
	let portLabel = type; // Initialize to port type (fallback)

	// Assign to name of port value
	if (label) portLabel = label;
	// Assign to default label using port type
	else if (defaultPortLabels[type]) {
		portLabel = defaultPortLabels[type];
	}
	// Create name if there are multiple types
	else if (type.includes('|')) {
		const types = type.split('|').map((d) => d.trim());
		portLabel = types.map((t) => defaultPortLabels[t] ?? t).join(' or ');
	}

	if (isOptional) portLabel = portLabel.concat(' (optional)');

	return portLabel;
}

export function getOutputLabel(outputs: WorkflowOutput<any>[], id: string) {
	const selectedOutput = outputs.find((output) => output.id === id);
	if (!selectedOutput) return '';

	// multiple output types, choose first name to use as label arbitrarily
	if (selectedOutput.type.includes('|')) {
		const outputType = selectedOutput.type.split('|');
		return useProjects().getAssetName(selectedOutput.value?.[0]?.[outputType?.[0]]) || selectedOutput.label;
	}

	// default use single output type
	return useProjects().getAssetName(selectedOutput.value?.[0]) || selectedOutput.label;
}

// Checker for resource-operators (e.g. model, dataset) that automatically create an output
// without needing to "run" the operator because we can drag them onto the canvas
export function canPropagateResource(outputs: WorkflowOutput<any>[]) {
	return _.isEmpty(outputs) || (outputs.length === 1 && !outputs[0].value);
}

/**
 * API hooks: Handles reading and writing back to the store
 * */

// Create
export const createWorkflow = async (workflow: Workflow) => {
	const response = await API.post('/workflows', workflow);
	return response?.data ?? null;
};

// Update/save
export const saveWorkflow = async (workflow: Workflow, projectId?: string) => {
	const id = workflow.id;
	const response = await API.put(`/workflows/${id}`, workflow, { params: { 'project-id': projectId } });
	return response?.data ?? null;
};

// Get workflow
// Note that projectId is optional as projectId is assigned by the axios API
// interceptor if value is available from activeProjectId.
// If the method is call from place where activeProjectId is not available,
// projectId should be passed as an argument as all endpoints requires
// projectId as a parameter.
export const getWorkflow = async (id: string, projectId?: string) => {
	const response = await API.get(`/workflows/${id}`, { params: { 'project-id': projectId } });
	return response?.data ?? null;
};

// FIXME: These follow functions overlap with WorkflowWrapper, will need to be sorted out. Jan 2025
export const newOperator = (
	workflowId: string,
	op: Operation,
	pos: Position,
	options: { size?: OperatorNodeSize; state?: any }
) => {
	let currentUserName: string | undefined = '';
	try {
		currentUserName = useAuthStore().user?.username;
	} catch (err) {
		// do nothing
	}
	const nodeSize: Size = getOperatorNodeSize(options.size ?? OperatorNodeSize.medium);

	const operator: WorkflowNode<any> = {
		id: uuidv4(),
		workflowId,
		operationType: op.name,
		displayName: op.displayName,
		documentationUrl: op.documentationUrl,
		imageUrl: op.imageUrl,
		x: pos.x,
		y: pos.y,

		createdBy: currentUserName,
		createdAt: Date.now(),

		active: null,
		state: options.state ?? {},
		uniqueInputs: op.uniqueInputs ?? false,

		inputs: op.inputs.map((port) => ({
			id: uuidv4(),
			type: port.type,
			label: port.label,
			status: WorkflowPortStatus.NOT_CONNECTED,
			value: null,
			isOptional: port.isOptional ?? false
		})),

		outputs: op.outputs.map((port) => ({
			id: uuidv4(),
			type: port.type,
			label: port.label,
			status: WorkflowPortStatus.NOT_CONNECTED,
			value: null,
			isOptional: false,
			state: {}
		})),

		status: OperatorStatus.INVALID,
		width: nodeSize.width,
		height: nodeSize.height
	};
	if (op.initState && _.isEmpty(operator.state)) {
		operator.state = op.initState();
	}
	return operator;
};

export const selectOutput = async (id: string, nodeId: string, outputId: string, projectId?: string) => {
	console.log('>> workflowService.selectOutput');
	const response = await API.post(`/workflows/${id}/node/${nodeId}/selected-output/${outputId}`, {
		params: { 'project-id': projectId }
	});
	return response.data ?? null;
};

export const appendOutput = async (id: string, nodeId: string, output: WorkflowOutput<any>, nodeState: any) => {
	console.log('>> workflowService.appendOutput');
	const response = await API.post(`/workflows/${id}/node/${nodeId}/output`, { output, nodeState });
	return response.data ?? null;
};

export const appendInput = async (id: string, nodeId: string, input: WorkflowPort) => {
	console.log('>> workflowService.appendInput');
	const response = await API.post(`/workflows/${id}/node/${nodeId}/input`, input);
	return response.data ?? null;
};

export const addNode = async (id: string, node: WorkflowNode<any>) => {
	console.log('>> workflowService.addNode');
	const response = await API.post(`/workflows/${id}/node`, node);
	return response.data ?? null;
};

export const addEdge = async (id: string, edge: WorkflowEdge) => {
	console.log('>> workflowService.addEdge', edge.source, edge.target);
	const response = await API.post(`/workflows/${id}/edge`, edge);
	return response.data ?? null;
};

export const addOrUpdateAnnotation = async (id: string, annotation: WorkflowAnnotation) => {
	console.log('>> workflowService.addOrUpdateAnnotation');
	const response = await API.post(`/workflows/${id}/annotation`, annotation);
	return response.data ?? null;
};

export const removeAnnotation = async (id: string, annotationId: string) => {
	console.log('>> workflowService.removeAnnotation');
	const response = await API.delete(`/workflows/${id}/annotation/${annotationId}`);
	return response.data ?? null;
};

/// /////////////////////////////////////////////////////////////////////////////
// Bulk API actions
/// /////////////////////////////////////////////////////////////////////////////
export const removeNodes = async (id: string, nodeIds: string[]) => {
	console.log('>> workflowService.removeNode', nodeIds);
	const response = await API.post(`/workflows/${id}/remove-nodes`, nodeIds);
	return response.data ?? null;
};

export const removeEdges = async (id: string, edgeIds: string[]) => {
	console.log('>> workflowService.removeEdge', edgeIds);
	const response = await API.post(`/workflows/${id}/remove-edges`, edgeIds);
	return response.data ?? null;
};

export const updateState = async (id: string, stateMap: Map<string, any>) => {
	console.log('>> workflowService.updateState');
	const response = await API.post(`/workflows/${id}/update-state`, Object.fromEntries(stateMap));
	return response.data ?? null;
};

export const updateStatus = async (id: string, statusMap: Map<string, OperatorStatus>) => {
	console.log('>> workflowService.updateStatus');
	const response = await API.post(`/workflows/${id}/update-status`, Object.fromEntries(statusMap));
	return response.data ?? null;
};

/// /////////////////////////////////////////////////////////////////////////////
// Events bus for workflow
/// /////////////////////////////////////////////////////////////////////////////
class WorkflowEventEmitter extends EventEmitter {
	emitNodeStateChange(payload: { workflowId: string; nodeId: string; state: any }) {
		this.emit('node-state-change', payload);
	}

	emitNodeRefresh(payload: { workflowId: string; nodeId: string }) {
		this.emit('node-refresh', payload);
	}
}

export const workflowEventBus = new WorkflowEventEmitter();

/// /////////////////////////////////////////////////////////////////////////////
// Workflow component registry, this is used to
// dynamically determine which component should be rendered
/// /////////////////////////////////////////////////////////////////////////////
export interface OperatorImport {
	name: string;
	operation: Operation;
	node: Component;
	drilldown: Component;
}
export class WorkflowRegistry {
	operationMap: Map<string, Operation>;

	nodeMap: Map<string, Component>;

	drilldownMap: Map<string, Component>;

	constructor() {
		this.operationMap = new Map();
		this.nodeMap = new Map();
		this.drilldownMap = new Map();
	}

	set(name: string, operation: Operation, node: Component, drilldown: Component) {
		this.operationMap.set(name, operation);
		this.nodeMap.set(name, node);
		this.drilldownMap.set(name, drilldown);
	}

	// shortcut
	registerOp(op: OperatorImport) {
		this.set(op.name, op.operation, op.node, op.drilldown);
	}

	getOperation(name: string) {
		return this.operationMap.get(name);
	}

	getNode(name: string) {
		return this.nodeMap.get(name);
	}

	getDrilldown(name: string) {
		return this.drilldownMap.get(name);
	}

	remove(name: string) {
		this.nodeMap.delete(name);
		this.drilldownMap.delete(name);
	}
}

export function cascadeInvalidateDownstream(
	sourceNode: WorkflowNode<any>,
	nodeCache: Map<WorkflowOutput<any>['id'], WorkflowNode<any>[]>
) {
	const downstreamNodes = nodeCache.get(sourceNode.id);
	downstreamNodes?.forEach((node) => {
		node.status = OperatorStatus.INVALID;
		cascadeInvalidateDownstream(node, nodeCache); // Recurse
	});
}

///
// Operator
///

/**
 * Update the output of a node referenced by the output id
 * @param node
 * @param updatedOutput
 */
export function updateOutput(node: WorkflowNode<any>, updatedOutput: WorkflowOutput<any>) {
	const foundOutput = node.outputs.find((output) => output.id === updatedOutput.id);
	if (foundOutput) {
		Object.assign(foundOutput, updatedOutput);
	}
}

// Check if the current-state matches that of the output-state.
//
// Note operatorState subsumes the keys of the outputState, thus if
// all of outputState[x] matches operatorState[x] it is considered
// to be in sync.
export const isOperatorStateInSync = (
	operatorState: Record<string, any>,
	outputState: Record<string, any>
): boolean => {
	const hasKey = Object.prototype.hasOwnProperty;

	// Use cloneDeep to get rid of any proxies
	const operatorStateRaw = _.cloneDeep(operatorState);
	const outputStateRaw = _.cloneDeep(outputState);

	const keys = Object.keys(outputStateRaw);
	for (let i = 0; i < keys.length; i++) {
		const key = keys[i];
		if (!hasKey.call(operatorStateRaw, key)) return false;
		if (!_.isEqual(operatorStateRaw[key], outputStateRaw[key])) return false;
	}
	return true;
};
export const isWorkflowNodeDirty = (node: WorkflowNode<any>): boolean => {
	// There is no output
	if (!node.active) return true;

	// Main check
	const outputState = node.outputs.filter((d) => d.id === node.active)[0].state;
	if (outputState) {
		return !isOperatorStateInSync(node.state, outputState);
	}
	return false;
};

export interface OperatorMenuItem {
	type: string;
	displayName: string;
}

function assetToOperation(operationMap: Map<string, Operation>) {
	const result = new Map<string, OperatorMenuItem[]>();
	operationMap.forEach((operation, key) => {
		const inputList: Array<OperationData> = operation.inputs ?? [];
		inputList.forEach((input) => {
			input.type.split('|').forEach((subType) => {
				if (!result.has(subType)) {
					result.set(subType, []);
				}
				result.get(subType)?.push({
					type: key,
					displayName: operation.displayName
				});
			});
		});
	});
	return result;
}

function operationToAsset(operationMap: Map<string, Operation>) {
	const result = new Map<string, string[]>();

	operationMap.forEach((operation, key) => {
		result.set(key, []);

		const outputList: OperationData[] = operation.outputs ?? [];
		outputList.forEach((output) => {
			output.type.split('|').forEach((subType) => {
				result.get(key)?.push(subType);
			});
		});
	});
	return result;
}

/* We want to get mapping of { operation => [operation] } */
export function getNodeMenu(operationMap: Map<string, Operation>) {
	const menuOptions = new Map<string, OperatorMenuItem[]>();

	const inputMap = assetToOperation(operationMap);
	const outputMap = operationToAsset(operationMap);

	// Going from
	//   outputMap(Operator => assetId[]) => inputMap(assetId => Operator[]) ;
	//
	// For example
	//   Calibrate => [datasetId, modelConfig] => [Validate, Simulate, DataTransform...]
	outputMap.forEach((assetTypes, operationKey) => {
		const check = new Set<String>();
		const menuItems: OperatorMenuItem[] = [];

		assetTypes.forEach((assetType) => {
			const availableInputOperations = inputMap.get(assetType) ?? [];

			availableInputOperations.forEach((item) => {
				if (!check.has(item.type)) {
					check.add(item.type);
					menuItems.push(item);
				}
			});
		});
		menuOptions.set(operationKey, menuItems);
	});

	return menuOptions;
}

export function getLocalStorageTransform(id: string): Transform | null {
	const terariumWorkflowTransforms = localStorage.getItem('terariumWorkflowTransforms');
	if (!terariumWorkflowTransforms) {
		return null;
	}

	const workflowTransformations: WorkflowTransformations = JSON.parse(terariumWorkflowTransforms);
	if (!workflowTransformations.workflows[id]) {
		return null;
	}
	return workflowTransformations.workflows[id];
}

export function setLocalStorageTransform(id: string, canvasTransform: { x: number; y: number; k: number }) {
	const terariumWorkflowTransforms = localStorage.getItem('terariumWorkflowTransforms');
	if (!terariumWorkflowTransforms) {
		const transformation: WorkflowTransformations = { workflows: { [id]: canvasTransform } };
		localStorage.setItem('terariumWorkflowTransforms', JSON.stringify(transformation));
		return;
	}
	const workflowTransformations = JSON.parse(terariumWorkflowTransforms);
	workflowTransformations.workflows[id] = canvasTransform;
	localStorage.setItem('terariumWorkflowTransforms', JSON.stringify(workflowTransformations));
}

// FIXME: move this into WorkflowWrapper, confusing with appendInput which is an API call
export function appendInputPort(node: WorkflowNode<any>, port: { type: string; label?: string }) {
	node.inputs.push({
		id: uuidv4(),
		type: port.type,
		label: port.label,
		isOptional: false,
		status: WorkflowPortStatus.NOT_CONNECTED
	});
}
