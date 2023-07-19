import { v4 as uuidv4 } from 'uuid';
import API from '@/api/api';
import _ from 'lodash';
import {
	Operation,
	Position,
	Size,
	Workflow,
	WorkflowEdge,
	WorkflowNode,
	WorkflowPortStatus,
	WorkflowStatus
} from '@/types/workflow';

/**
 * Captures common actions performed on workflow nodes/edges. The functions here are
 * not optimized, on the account that we don't expect most workflow graphs to
 * exceed say ... 10-12 nodes with 30-40 edges.
 *
 * TODO:
 * - Should we update workflow node status on modification???
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

const defaultNodeSize: Size = { width: 180, height: 220 };
export const addNode = (
	wf: Workflow,
	op: Operation,
	pos: Position,
	options: { size?: Size; state?: any } = { size: defaultNodeSize, state: {} }
) => {
	const node: WorkflowNode = {
		id: uuidv4(),
		workflowId: wf.id,
		operationType: op.name,
		displayName: op.displayName,
		x: pos.x,
		y: pos.y,
		state: options.state,

		inputs: op.inputs.map((port) => ({
			id: uuidv4(),
			type: port.type,
			label: port.label,
			status: WorkflowPortStatus.NOT_CONNECTED,
			value: null,
			acceptMultiple: port.acceptMultiple
		})),
		outputs: [],
		/*
		outputs: op.outputs.map((port) => ({
			id: uuidv4(),
			type: port.type,
			label: port.label,
			status: WorkflowPortStatus.NOT_CONNECTED,
			value: null
		})),
	  */
		statusCode: WorkflowStatus.INVALID,

		width: options?.size?.width ?? defaultNodeSize.width,
		height: options?.size?.height ?? defaultNodeSize.height
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
	if (!sourceNode) return;
	if (!targetNode) return;

	const sourceOutputPort = sourceNode.outputs.find((d) => d.id === sourcePortId);
	const targetInputPort = targetNode.inputs.find((d) => d.id === targetPortId);

	if (!sourceOutputPort) return;
	if (!targetInputPort) return;

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
	if (sourceOutputPort.value === null) return;
	if (sourceOutputPort.type !== targetInputPort.type) return;
	if (!targetInputPort.acceptMultiple && targetInputPort.status === WorkflowPortStatus.CONNECTED) {
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

	// Remove the data refernece at the targetPort
	const targetNode = wf.nodes.find((d) => d.id === edgeToRemove.target);
	if (!targetNode) return;
	const targetPort = targetNode.inputs.find((d) => d.id === edgeToRemove.targetPortId);
	if (!targetPort) return;
	targetPort.value = null;

	// Edge re-assignment
	wf.edges = wf.edges.filter((edge) => edge.id !== id);

	// If there are no more references reset the connected status of the source node
	if (wf.edges.filter((e) => e.source === edgeToRemove.source).length === 0) {
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

// Get
export const getWorkflow = async (id: string) => {
	const response = await API.get(`/workflows/${id}`);
	return response?.data ?? null;
};

/// /////////////////////////////////////////////////////////////////////////////
// Events bus for workflow
/// /////////////////////////////////////////////////////////////////////////////
type EventCallback = (args: any) => void;
type EventName = string | symbol;
class EventEmitter {
	listeners: Map<EventName, Set<EventCallback>> = new Map();

	on(eventName: EventName, fn: EventCallback): void {
		if (!this.listeners.has(eventName)) {
			this.listeners.set(eventName, new Set());
		}
		this.listeners.get(eventName)?.add(fn);
	}

	once(eventName: EventName, fn: EventCallback): void {
		if (!this.listeners.has(eventName)) {
			this.listeners.set(eventName, new Set());
		}

		const onceWrapper = (args: any) => {
			fn(args);
			this.off(eventName, onceWrapper);
		};
		this.listeners.get(eventName)?.add(onceWrapper);
	}

	off(eventName: EventName, fn: EventCallback): void {
		const set = this.listeners.get(eventName);
		if (set) {
			set.delete(fn);
		}
	}

	removeAllEvents(eventName: EventName): void {
		if (this.listeners.has(eventName)) {
			this.listeners.delete(eventName);
		}
	}

	emit(eventName: EventName, args: any): boolean {
		const fns = this.listeners.get(eventName);
		if (!fns) return false;

		fns.forEach((f) => {
			f(args);
		});
		return true;
	}

	emitNodeStateChange(payload: { workflowId: string; nodeId: string; state: any }) {
		this.emit('node-state-change', payload);
	}
}

export const workflowEventBus = new EventEmitter();
