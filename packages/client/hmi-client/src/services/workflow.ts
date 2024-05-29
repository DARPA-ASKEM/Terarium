import { Component } from 'vue';
import { v4 as uuidv4 } from 'uuid';
import _ from 'lodash';
import API from '@/api/api';
import { logger } from '@/utils/logger';
import { EventEmitter } from '@/utils/emitter';
import type { Position } from '@/types/common';
import type {
	Operation,
	Size,
	Workflow,
	WorkflowEdge,
	WorkflowNode,
	WorkflowPort,
	WorkflowOutput
} from '@/types/workflow';
import { WorkflowPortStatus, OperatorStatus } from '@/types/workflow';
import { summarizeNotebook } from './beaker';

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

function getOperatorNodeSize(size: OperatorNodeSize): Size {
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

export const addNode = (
	wf: Workflow,
	op: Operation,
	pos: Position,
	options: { size?: OperatorNodeSize; state?: any }
) => {
	const nodeSize: Size = getOperatorNodeSize(options.size ?? OperatorNodeSize.medium);

	const node: WorkflowNode<any> = {
		id: uuidv4(),
		workflowId: wf.id,
		operationType: op.name,
		displayName: op.displayName,
		documentationUrl: op.documentationUrl,
		x: pos.x,
		y: pos.y,
		state: options.state ?? {},

		inputs: op.inputs.map((port) => ({
			id: uuidv4(),
			type: port.type,
			label: port.label,
			status: WorkflowPortStatus.NOT_CONNECTED,
			value: null,
			isOptional: port.isOptional ?? false,
			acceptMultiple: port.acceptMultiple
		})),

		outputs: op.outputs.map((port) => ({
			id: uuidv4(),
			type: port.type,
			label: port.label,
			status: WorkflowPortStatus.NOT_CONNECTED,
			value: null,
			isOptional: false,
			acceptMultiple: false,
			state: {}
		})),
		status: OperatorStatus.DEFAULT,

		width: nodeSize.width,
		height: nodeSize.height
	};
	if (op.initState && _.isEmpty(node.state)) {
		node.state = op.initState();
	}

	wf.nodes.push(node);
};

export const addEdge = (
	wf: Workflow,
	sourceId: string,
	sourcePortId: string,
	targetId: string,
	targetPortId: string,
	points: Position[]
) => {
	const sourceNode = wf.nodes.find((d) => d.id === sourceId);
	const targetNode = wf.nodes.find((d) => d.id === targetId);
	if (!sourceNode || !targetNode) return;

	const sourceOutputPort = sourceNode.outputs.find((d) => d.id === sourcePortId);
	const targetInputPort = targetNode.inputs.find((d) => d.id === targetPortId);
	if (!sourceOutputPort || !targetInputPort) return;

	// Check if edge already exist
	const existingEdge = wf.edges.find(
		(d) =>
			d.source === sourceId &&
			d.sourcePortId === sourcePortId &&
			d.target === targetId &&
			d.targetPortId === targetPortId
	);
	if (existingEdge) return;

	// Check if type is compatible
	const allowedTypes = targetInputPort.type.split('|');
	if (
		!allowedTypes.includes(sourceOutputPort.type) ||
		(!targetInputPort.acceptMultiple && targetInputPort.status === WorkflowPortStatus.CONNECTED)
	) {
		return;
	}

	// Transfer data value/reference
	if (targetInputPort.acceptMultiple && targetInputPort.value && sourceOutputPort.value) {
		targetInputPort.label = `${sourceOutputPort.label},${targetInputPort.label}`;
		targetInputPort.value = [...targetInputPort.value, ...sourceOutputPort.value];
	} else {
		targetInputPort.label = sourceOutputPort.label;
		targetInputPort.value = sourceOutputPort.value;
	}

	// Transfer concrete type to the input type to match the output type
	// Saves the original type in case we want to revert when we unlink the edge
	if (allowedTypes.length > 1) {
		targetInputPort.originalType = targetInputPort.type;
		targetInputPort.type = sourceOutputPort.type;
	}

	const edge: WorkflowEdge = {
		id: uuidv4(),
		workflowId: wf.id,
		source: sourceId,
		sourcePortId,
		target: targetId,
		targetPortId,
		points: _.cloneDeep(points)
	};

	wf.edges.push(edge);
	sourceOutputPort.status = WorkflowPortStatus.CONNECTED;
	targetInputPort.status = WorkflowPortStatus.CONNECTED;
};

export const removeEdge = (wf: Workflow, id: string) => {
	const edgeToRemove = wf.edges.find((d) => d.id === id);
	if (!edgeToRemove) return;

	// Remove the data reference at the targetPort
	const targetNode = wf.nodes.find((d) => d.id === edgeToRemove.target);
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

	// Edge re-assignment
	wf.edges = wf.edges.filter((edge) => edge.id !== id);

	// If there are no more references reset the connected status of the source node
	if (_.isEmpty(wf.edges.filter((e) => e.source === edgeToRemove.source))) {
		const sourceNode = wf.nodes.find((d) => d.id === edgeToRemove.source);
		if (!sourceNode) return;
		const sourcePort = sourceNode.outputs.find((d) => d.id === edgeToRemove.sourcePortId);
		if (!sourcePort) return;
		sourcePort.status = WorkflowPortStatus.NOT_CONNECTED;
	}
};

export const removeNode = (wf: Workflow, id: string) => {
	// Remove all the edges first
	const edgesToRemove = wf.edges.filter((d) => d.source === id || d.target === id);
	const edgeIds = edgesToRemove.map((d) => d.id);
	edgeIds.forEach((edgeId) => {
		removeEdge(wf, edgeId);
	});

	// Remove the node
	wf.nodes = wf.nodes.filter((node) => node.id !== id);
};

export const updateNodeState = (wf: Workflow, nodeId: string, state: any) => {
	const node = wf.nodes.find((d) => d.id === nodeId);
	if (!node) return;
	node.state = state;
};

export const updateNodeStatus = (wf: Workflow, nodeId: string, status: OperatorStatus) => {
	const node = wf.nodes.find((d) => d.id === nodeId);
	if (!node) return;
	node.status = status;
};

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
		const types = type.split('|');
		portLabel = types.map((t) => defaultPortLabels[t] ?? t).join(' or ');
	}

	if (isOptional) portLabel = portLabel.concat(' (optional)');

	return portLabel;
}

/**
 * API hooks: Handles reading and writing back to the store
 * */

// Create
export const createWorkflow = async (workflow: Workflow) => {
	const response = await API.post('/workflows', workflow);
	return response?.data ?? null;
};

// Update
export const updateWorkflow = async (workflow: Workflow) => {
	const id = workflow.id;
	const response = await API.put(`/workflows/${id}`, workflow);
	return response?.data ?? null;
};

// Get workflow
// Note that projectId is optional as projectId is assigned by the axios API interceptor if value is available from activeProjectId. If the method is call from place where activeProjectId is not available, projectId should be passed as an argument as all endpoints requires projectId as a parameter.
export const getWorkflow = async (id: string, projectId?: string) => {
	const response = await API.get(`/workflows/${id}`, { params: { 'project-id': projectId } });
	return response?.data ?? null;
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
 * Updates the operator's state using the data from a specified WorkflowOutput. If the operator's
 * current state was not previously stored as a WorkflowOutput, this function first saves the current state
 * as a new WorkflowOutput. It then replaces the operator's existing state with the data from the specified WorkflowOutput.
 *
 * @param operator - The operator whose state is to be updated.
 * @param selectedWorkflowOutputId - The ID of the WorkflowOutput whose data will be used to update the operator's state.
 */

export function selectOutput(
	wf: Workflow,
	operator: WorkflowNode<any>,
	selectedWorkflowOutputId: WorkflowOutput<any>['id']
) {
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
	const hasOutgoingEdges = wf.edges.some((edge) => edge.source === operator.id);
	if (!hasOutgoingEdges) return;

	selected.status = WorkflowPortStatus.CONNECTED;

	const nodeMap = new Map<WorkflowNode<any>['id'], WorkflowNode<any>>(
		wf.nodes.map((node) => [node.id, node])
	);
	const nodeCache = new Map<WorkflowOutput<any>['id'], WorkflowNode<any>[]>();
	nodeCache.set(operator.id, []);

	wf.edges.forEach((edge) => {
		// Update the input port of the direct target node
		if (edge.source === operator.id) {
			const targetNode = wf.nodes.find((node) => node.id === edge.target);
			if (!targetNode) return;
			// Update the input port of the target node
			const targetPort = targetNode.inputs.find((port) => port.id === edge.targetPortId);
			if (!targetPort) return;
			edge.sourcePortId = selected.id; // Sync edge source port to selected output
			targetPort.label = selected.label;
			targetPort.value = selected.value;
		}

		// Collect node cache
		if (!edge.source || !edge.target) return;
		if (!nodeCache.has(edge.source)) nodeCache.set(edge.source, []);
		nodeCache.get(edge.source)?.push(nodeMap.get(edge.target) as WorkflowNode<any>);
	});

	cascadeInvalidateDownstream(operator, nodeCache);
}

export function getActiveOutputSummary(node: WorkflowNode<any>) {
	const output = node.outputs.find((o) => o.id === node.active);
	return output?.summary;
}

export function updateOutputPort(node: WorkflowNode<any>, updatedOutputPort: WorkflowOutput<any>) {
	let outputPort = node.outputs.find((port) => port.id === updatedOutputPort.id);
	if (!outputPort) return;
	outputPort = Object.assign(outputPort, updatedOutputPort);
}

// Keep track of the summary generation requests to prevent multiple requests for the same workflow output
const summaryGenerationRequestIds = new Set<string>();

export async function generateSummary(node: WorkflowNode<any>, outputPort: WorkflowOutput<any>) {
	if (!outputPort?.notebook || !node || summaryGenerationRequestIds.has(outputPort.id)) return null;
	try {
		summaryGenerationRequestIds.add(outputPort.id);
		// TODO: generate notebook here for corresponding outputPort
		const result = await summarizeNotebook(outputPort.notebook);
		return result;
	} catch {
		return { title: outputPort.label, summary: 'Generating AI summary has failed.' };
	} finally {
		summaryGenerationRequestIds.delete(outputPort.id);
	}
}

// Check if the current-state matches that of the output-state.
// Note operatorState subsumes the keys of the outputState
export const isOperatorStateInSync = (
	operatorState: Record<string, any>,
	outputState: Record<string, any>
) => {
	const hasKey = Object.prototype.hasOwnProperty;

	const keys = Object.keys(outputState);
	for (let i = 0; i < keys.length; i++) {
		const key = keys[i];
		if (!hasKey.call(operatorState, key)) return false;
		if (!_.isEqual(operatorState[key], outputState[key])) return false;
	}
	return true;
};

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
export const branchWorkflow = (wf: Workflow, nodeId: string) => {
	// 1. Find anchor point
	const anchor = wf.nodes.find((n) => n.id === nodeId);
	if (!anchor) return;

	// 2. Collect the subgraph that we want to copy
	const copyNodes: WorkflowNode<any>[] = [];
	const copyEdges: WorkflowEdge[] = [];
	const stack = [anchor.id]; // working list of nodeIds to crawl
	const processed: Set<string> = new Set();

	// basically depth-first-search
	while (stack.length > 0) {
		const id = stack.pop();
		const node = wf.nodes.find((n) => n.id === id);
		if (node) copyNodes.push(_.cloneDeep(node));
		processed.add(id as string);

		// Grab downstream edges
		const edges = wf.edges.filter((e) => e.source === id);
		edges.forEach((edge) => {
			const newId = edge.target as string;
			if (!processed.has(newId)) {
				stack.push(edge.target as string);
			}
			copyEdges.push(_.cloneDeep(edge));
		});
	}

	// 3. Collect the upstream edges of the anchor
	const upstreamEdges = wf.edges.filter((edge) => edge.target === anchor.id);
	upstreamEdges.forEach((edge) => {
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
		// Don't replace upstream edge sources, they are still valid
		if (upstreamEdges.map((e) => e.source).includes(edge.source) === false) {
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
			node.active = registry.get(node.active);
		}
	});

	// 5. Reposition new nodes so they don't exaclty overlap
	const offset = 75;
	copyNodes.forEach((n) => {
		n.y += offset;
	});
	copyEdges.forEach((edge) => {
		if (!edge.points || edge.points.length < 2) return;
		if (upstreamEdges.map((e) => e.source).includes(edge.source) === false) {
			edge.points[0].y += offset;
		}
		edge.points[edge.points.length - 1].y += offset;
	});

	// 6. Finally put everything back into the workflow
	wf.nodes.push(...copyNodes);
	wf.edges.push(...copyEdges);
};
