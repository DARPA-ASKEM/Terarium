import type { Position } from '@/types/common';

export const WorkflowOperationTypes = Object.freeze({
	CALIBRATION_CIEMSS: 'CalibrationOperationCiemss',
	DATASET: 'Dataset',
	MODEL: 'ModelOperation',
	SIMULATE_CIEMSS: 'SimulateCiemssOperation',
	STRATIFY_MIRA: 'StratifyMira',
	SIMULATE_ENSEMBLE_CIEMSS: 'SimulateEnsembleCiemms',
	CALIBRATE_ENSEMBLE_CIEMSS: 'CalibrateEnsembleCiemms',
	DATASET_TRANSFORMER: 'DatasetTransformer',
	SUBSET_DATA: 'SubsetData',
	FUNMAN: 'Funman',
	CODE: 'Code',
	MODEL_COMPARISON: 'ModelComparison',
	MODEL_CONFIG: 'ModelConfiguration',
	OPTIMIZE_CIEMSS: 'OptimizeCiemss',
	MODEL_EDIT: 'ModelEdit',
	DOCUMENT: 'Document',
	MODEL_FROM_EQUATIONS: 'ModelFromEquations',
	REGRIDDING: 'Regridding',
	INTERVENTION_POLICY: 'InterventionPolicy',
	COMPARE_DATASETS: 'CompareDatasets'
});

export enum OperatorStatus {
	DEFAULT = 'default',
	IN_PROGRESS = 'in progress',
	SUCCESS = 'success',
	INVALID = 'invalid',
	ERROR = 'error'
}

export enum WorkflowPortStatus {
	CONNECTED = 'connected',
	NOT_CONNECTED = 'not connected'
}

// Defines the type of data an operation can consume and output
export interface OperationData {
	type: string;
	label?: string;
	isOptional?: boolean;
}

// Defines a function: eg: model, simulate, calibrate
export interface Operation {
	name: string;
	description: string;
	displayName: string; // Human-readable name for each node.
	documentationUrl?: string;
	imageUrl?: string;

	// The operation is self-runnable, that is, given just the inputs we can derive the outputs
	isRunnable: boolean;

	initState?: Function;

	action?: Function;
	validation?: Function;

	inputs: OperationData[];
	outputs: OperationData[];
	uniqueInputs?: boolean;
}

// Defines the data-exchange between WorkflowNode
// In most cases the value here will be an assetId
export interface WorkflowPort {
	id: string;
	type: string;
	originalType?: string;
	status: WorkflowPortStatus;
	label?: string;
	value?: any[] | null;
	isOptional: boolean;
}

// Operator Output needs more information than a standard operator port.
export interface WorkflowOutput<S> extends WorkflowPort {
	isSelected?: boolean;
	operatorStatus?: OperatorStatus;
	state?: Partial<S>;
	timestamp?: Date;
}

// Common state properties for all operators
export interface BaseState {
	annotation?: string; // @deprecated
	summaryId?: string;
}

export interface WorkflowAnnotation {
	id: string;
	type: string;
	x: number;
	y: number;
	textSize: number;
	content: string;
}

// Node definition in the workflow
// This is the graphical operation of the operation defined in operationType
export interface WorkflowNode<S> {
	// Information
	id: string;
	workflowId: string;
	isDeleted?: boolean;
	version?: number;
	createdBy?: string;
	createdAt?: number;

	displayName: string;
	operationType: string;
	documentationUrl?: string;
	imageUrl?: string;

	// Position on canvas
	x: number;
	y: number;
	width: number;
	height: number;

	// Current operator state
	state: S; // Internal state. For example chosen model, display color ... etc
	active: WorkflowOutput<S>['id'] | null;

	// I/O
	inputs: WorkflowPort[];
	outputs: WorkflowOutput<S>[];

	// Behaviour
	status: OperatorStatus;

	uniqueInputs?: boolean;
}

export interface WorkflowEdge {
	id: string;
	workflowId: string;
	isDeleted?: boolean;
	version?: number;
	createdBy?: string;
	createdAt?: number;

	points: Position[];
	source?: WorkflowNode<any>['id'];
	sourcePortId?: string;

	target?: WorkflowNode<any>['id'];
	targetPortId?: string;

	// is this edge being started from an input or output?
	// not persisted; only used during edge creation
	direction?: WorkflowDirection;
}

export enum WorkflowDirection {
	FROM_INPUT,
	FROM_OUTPUT
}

export interface Transform {
	x: number;
	y: number;
	k: number;
}
export interface Workflow {
	id: string;
	name: string;
	description: string;

	// zoom x-y translate and zoom
	transform: Transform;
	nodes: WorkflowNode<any>[];
	edges: WorkflowEdge[];

	annotations?: { [key: string]: WorkflowAnnotation };
	scenario?: any;
}

export interface Size {
	width: number;
	height: number;
}

export interface WorkflowTransformations {
	workflows: { [key: string]: Transform };
}

export interface AssetBlock<T> {
	name: string;
	includeInProcess: boolean;
	isCollapsed?: false;
	asset: T;
}
