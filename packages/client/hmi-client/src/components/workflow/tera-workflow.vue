<template>
	<!-- add 'debug-mode' to debug this -->
	<tera-infinite-canvas
		v-if="!isWorkflowLoading"
		ref="canvasRef"
		@click="onCanvasClick()"
		@contextmenu="toggleContextMenu"
		@save-transform="saveTransform"
		@focus="() => {}"
		@blur="() => {}"
		@drop="onDrop"
		@dragover.prevent
		@dragenter.prevent
		:lastTransform="canvasTransform"
	>
		<!-- toolbar -->
		<template #foreground>
			<div class="toolbar glass">
				<tera-toggleable-input :model-value="wf.getName()" @update:model-value="updateWorkflowName" tag="h4" />
				<div class="button-group">
					<Button
						id="reset-zoom-btn"
						icon="pi pi-expand"
						label="Reset zoom"
						@click="resetZoom"
						size="small"
						outlined
						class="white-space-nowrap"
						severity="secondary"
					/>

					<Button
						id="add-component-btn"
						icon="pi pi-plus"
						label="Add component"
						@click="showAddComponentMenu"
						size="small"
						class="white-space-nowrap"
					/>
					<!--ContextMenu is used instead of TieredMenu for the submenus to appear on the left (not get cut off on the right)-->
					<ContextMenu ref="addComponentMenu" :model="contextMenuItems" style="white-space: nowrap; width: auto" />
				</div>
			</div>
			<div class="warning-banner" :class="{ visible: warningBanner && hasInvalidNodes }">
				A yellow header indicates that the node is stale due to upstream changes. Rerun to update.
				<a class="ml-auto mr-4" @click="dontShowAgain">Don't show this again</a
				><Button class="mr-2" icon="pi pi-times" size="small" text rounded @click="warningBanner = false" />
			</div>
		</template>
		<!-- data -->
		<template #data>
			<ContextMenu ref="contextMenu" :model="contextMenuItems" style="white-space: nowrap; width: auto" />

			<!-- Annotation test -->
			<tera-canvas-item
				v-for="annotation in wf.getAnnotations()"
				:key="annotation.id"
				:style="{ width: `300px`, top: `${annotation.y}px`, left: `${annotation.x}px` }"
				@dragging="(event) => updateAnnotationPosition(annotation, event)"
				@dragend="() => updateAnnotation(annotation)"
			>
				<tera-workflow-annotation
					:annotation="annotation"
					@remove-annotation="(event) => removeAnnotation(event)"
					@update-annotation="(event) => updateAnnotation(event)"
				/>
			</tera-canvas-item>

			<tera-canvas-item
				v-for="node in wf.getNodes()"
				:key="node.id"
				:style="{
					width: `${node.width}px`,
					top: `${node.y}px`,
					left: `${node.x}px`
				}"
				@dragging="(event) => updatePosition(node, event)"
				@dragend="
					isDragging = false;
					debounceSaveWorkflowPositions();
				"
			>
				<tera-operator
					ref="teraOperatorRefs"
					:node="node"
					:nodeMenu="outputPortMenu"
					@resize="resizeHandler"
					@port-selected="(port: WorkflowPort, direction: WorkflowDirection) => createNewEdge(node, port, direction)"
					@port-mouseover="onPortMouseover"
					@port-mouseleave="onPortMouseleave"
					@remove-operator="(event) => removeNode(event)"
					@duplicate-branch="duplicateBranch(node.id)"
					@remove-edges="removeEdges"
					@update-state="(event: any) => updateWorkflowNodeState(node, event)"
					@menu-selection="(operatorType: string, port: WorkflowPort) => onMenuSelection(operatorType, node, port)"
				>
					<template #body>
						<component
							:is="registry.getNode(node.operationType)"
							:node="node"
							@append-output="(port: any, newState: any) => appendOutput(node, port, newState)"
							@append-input-port="(event: any) => appendInput(node, event)"
							@update-state="(event: any) => updateWorkflowNodeState(node, event)"
							@open-drilldown="addOperatorToRoute(node.id)"
						/>
					</template>
				</tera-operator>
			</tera-canvas-item>
		</template>
		<!-- background -->
		<template #backgroundDefs>
			<marker id="circle" markerWidth="8" markerHeight="8" refX="5" refY="5">
				<circle cx="5" cy="5" r="3" style="fill: var(--text-color-secondary)" />
			</marker>
			<marker
				id="arrow"
				viewBox="0 0 16 16"
				refX="8"
				refY="8"
				orient="auto"
				markerWidth="16"
				markerHeight="16"
				markerUnits="userSpaceOnUse"
				xoverflow="visible"
			>
				<path d="M 0 0 L 8 8 L 0 16 z" style="fill: var(--text-color-secondary); fill-opacity: 1"></path>
			</marker>
			<marker
				id="smallArrow"
				viewBox="0 0 16 16"
				refX="8"
				refY="8"
				orient="auto"
				markerWidth="12"
				markerHeight="12"
				markerUnits="userSpaceOnUse"
				xoverflow="visible"
			>
				<path d="M 0 0 L 8 8 L 0 16 z" style="fill: var(--text-color-secondary)"></path>
			</marker>
		</template>
		<template #background>
			<path
				v-if="newEdge?.points"
				:d="drawPath(interpolatePointsForCurve(newEdge.points[0], newEdge.points[1]))"
				stroke="#667085"
				stroke-width="2"
				marker-start="url(#circle)"
				marker-end="url(#arrow)"
				fill="none"
			/>
			<path
				v-for="edge of wf.getEdges()"
				:key="edge.id"
				:d="drawPath(interpolatePointsForCurve(edge.points[0], edge.points[1]))"
				stroke="#667085"
				stroke-width="2"
				marker-start="url(#circle)"
				fill="none"
			/>
		</template>
	</tera-infinite-canvas>
	<tera-progress-spinner v-else :font-size="2" is-centered />
	<Teleport to="body">
		<component
			v-if="dialogIsOpened && currentActiveNode"
			:downstream-operators-nav="downstreamOperatorsNav"
			:is="registry.getDrilldown(currentActiveNode.operationType)"
			:node="currentActiveNode"
			:spawn-animation="drilldownSpawnAnimation"
			:upstream-operators-nav="upstreamOperatorsNav"
			@close="addOperatorToRoute(null)"
			@append-output="(port: any, newState: any) => appendOutput(currentActiveNode, port, newState)"
			@select-output="(event: any) => selectOutput(currentActiveNode, event)"
			@update-state="(event: any) => updateWorkflowNodeState(currentActiveNode, event)"
			@update-status="(status: OperatorStatus) => updateWorkflowNodeStatus(currentActiveNode, status)"
		/>
	</Teleport>
</template>

<script setup lang="ts">
import { cloneDeep, isArray, intersection, debounce } from 'lodash';
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue';
import TeraInfiniteCanvas from '@/components/widgets/tera-infinite-canvas.vue';
import TeraCanvasItem from '@/components/widgets/tera-canvas-item.vue';
import type { Position } from '@/types/common';
import {
	Operation,
	Workflow,
	WorkflowEdge,
	WorkflowNode,
	WorkflowOutput,
	WorkflowPort,
	WorkflowDirection,
	WorkflowPortStatus,
	OperatorStatus,
	WorkflowAnnotation
} from '@/types/workflow';
// Operation imports
import TeraOperator from '@/components/operator/tera-operator.vue';
import TeraWorkflowAnnotation from '@/components/workflow/tera-workflow-annotation.vue';
import Button from 'primevue/button';
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import ContextMenu from 'primevue/contextmenu';
import * as workflowService from '@/services/workflow';
import { OperatorImport, OperatorNodeSize } from '@/services/workflow';
import * as d3 from 'd3';
import { AssetType, ClientEventType, EventType, ClientEvent } from '@/types/Types';
import { useDragEvent } from '@/services/drag-drop';
import { v4 as uuidv4 } from 'uuid';

import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';

import { useRouter, useRoute } from 'vue-router';
import { MenuItem } from 'primevue/menuitem';
import * as EventService from '@/services/event';
import { useProjects } from '@/composables/project';
// import useAuthStore from '@/stores/auth';
import { cloneNoteBookSession } from '@/services/notebook-session';
import * as SimulateCiemssOp from '@/components/workflow/ops/simulate-ciemss/mod';
import * as StratifyMiraOp from '@/components/workflow/ops/stratify-mira/mod';
import * as DatasetOp from '@/components/workflow/ops/dataset/mod';
import * as FunmanOp from '@/components/workflow/ops/funman/mod';
import * as SimulateEnsembleCiemssOp from '@/components/workflow/ops/simulate-ensemble-ciemss/mod';
import * as ModelOp from '@/components/workflow/ops/model/mod';
import * as ModelEditOp from '@/components/workflow/ops/model-edit/mod';
import * as ModelConfigOp from '@/components/workflow/ops/model-config/mod';
import * as CalibrateCiemssOp from '@/components/workflow/ops/calibrate-ciemss/mod';
import * as CalibrateEnsembleCiemssOp from '@/components/workflow/ops/calibrate-ensemble-ciemss/mod';
import * as DatasetTransformerOp from '@/components/workflow/ops/dataset-transformer/mod';
import * as OptimizeCiemssOp from '@/components/workflow/ops/optimize-ciemss/mod';
import * as DocumentOp from '@/components/workflow/ops/document/mod';
import * as ModelFromDocumentOp from '@/components/workflow/ops/model-from-equations/mod';
import * as ModelComparisonOp from '@/components/workflow/ops/model-comparison/mod';
import * as CompareDatasetsOp from '@/components/workflow/ops/compare-datasets/mod';
import * as InterventionPolicyOp from '@/components/workflow/ops/intervention-policy/mod';
import { subscribe, unsubscribe } from '@/services/ClientEventService';
import { activeProjectId } from '@/composables/activeProject';

const WORKFLOW_SAVE_INTERVAL = 4000;

// const currentUserId = useAuthStore().user?.id;

const registry = new workflowService.WorkflowRegistry();
registry.registerOp(SimulateCiemssOp);
registry.registerOp(StratifyMiraOp);
registry.registerOp(SimulateEnsembleCiemssOp);
registry.registerOp(DatasetOp);
registry.registerOp(FunmanOp);
registry.registerOp(ModelOp);
registry.registerOp(ModelEditOp);
registry.registerOp(CalibrateEnsembleCiemssOp);
registry.registerOp(ModelConfigOp);
registry.registerOp(CalibrateCiemssOp);
registry.registerOp(DatasetTransformerOp);
registry.registerOp(OptimizeCiemssOp);
registry.registerOp(DocumentOp);
registry.registerOp(ModelFromDocumentOp);
registry.registerOp(ModelComparisonOp);
registry.registerOp(InterventionPolicyOp);
registry.registerOp(CompareDatasetsOp);

// Will probably be used later to save the workflow in the project
const props = defineProps<{
	assetId: string;
}>();

const outputPortMenu = ref(workflowService.getNodeMenu(registry.operationMap));
const upstreamOperatorsNav = ref<MenuItem[]>([]);
const downstreamOperatorsNav = ref<MenuItem[]>([]);
const drilldownSpawnAnimation = ref<'left' | 'right' | 'scale'>('scale');

const route = useRoute();
const router = useRouter();

const warningBanner = ref(true);

const newNodePosition = { x: 0, y: 0 };
let canvasTransform = { x: 0, y: 0, k: 1 };
let currentPortPosition: Position = { x: 0, y: 0 };
let isMouseOverPort: boolean = false;
let saveTimer: any = null;
let isDragging = false;

let startTime: number = 0;

const isWorkflowLoading = ref(false);

const currentActiveNode = ref<WorkflowNode<any> | null>(null);
const newEdge = ref<WorkflowEdge | undefined>();
const dialogIsOpened = ref(false);

const wf = ref<workflowService.WorkflowWrapper>(new workflowService.WorkflowWrapper());
const contextMenu = ref();

const currentProjectId = ref<string | null>(null);

const teraOperatorRefs = ref();
const canvasRef = ref();

async function updateWorkflowName(newName: string) {
	const workflowClone = cloneDeep(wf.value.dump());
	workflowClone.name = newName;
	await workflowService.saveWorkflow(workflowClone);
	await useProjects().refresh();
	wf.value.load(await workflowService.getWorkflow(props.assetId));
}

// eslint-disable-next-line
const _updateWorkflow = (event: ClientEvent<any>) => {
	if (event.data.id !== wf.value.getId()) {
		return;
	}

	// const delayUpdate = isDragging || event.userId === currentUserId;
	wf.value.update(event.data as Workflow);
};

const nodeStateMap: Map<string, any> = new Map();
const updateWorkflowHandler = debounce(_updateWorkflow, 250);
const saveNodeStateHandler = debounce(async () => {
	const updatedWorkflow = await workflowService.updateState(wf.value.getId(), nodeStateMap);
	nodeStateMap.clear();

	(updatedWorkflow as Workflow).nodes.forEach((node) => {
		if (node.isDeleted === false) {
			wf.value.updateNodeState(node.id, node.state);
		}
	});
}, 250);

const nodePositionSet: Set<string> = new Set();
const edgePositionSet: Set<string> = new Set();
const debounceSaveWorkflowPositions = debounce(() => {
	saveWorkflowPositions();
}, 100);

async function appendInput(
	node: WorkflowNode<any>,
	port: {
		label: string;
		type: string;
		isOptional?: boolean;
	}
) {
	const inputPort: WorkflowPort = {
		id: uuidv4(),
		type: port.type,
		label: port.label,
		isOptional: port.isOptional || false,
		value: null,
		status: WorkflowPortStatus.NOT_CONNECTED
	};

	const updatedWorkflow = await workflowService.appendInput(wf.value.getId(), node.id, inputPort);
	wf.value.update(updatedWorkflow);
}

/**
 * The operator creates a new output, this will mark the
 * output as selected, and revert the selection status of
 * existing outputs
 * */
async function appendOutput(
	node: WorkflowNode<any> | null,
	port: {
		type: string;
		label?: string;
		value: any;
		state?: any;
		isSelected?: boolean;
	},
	newState: any = null
) {
	if (!node) return;

	const uuid = uuidv4();
	const outputPort: WorkflowOutput<any> = {
		id: uuid,
		type: port.type,
		label: port.label,
		value: isArray(port.value) ? port.value : [port.value],
		isOptional: false,
		status: WorkflowPortStatus.NOT_CONNECTED,
		state: port.state,
		isSelected: true,
		timestamp: new Date(),
		// We assume that if we can produce an output, the status is okay
		operatorStatus: OperatorStatus.SUCCESS
	};

	const updatedWorkflow = await workflowService.appendOutput(wf.value.getId(), node.id, outputPort, newState);
	wf.value.update(updatedWorkflow);

	// We want to try to wait here, because we replace default dummy outputs we might
	// try to do id-lookup to a non-existing element
	nextTick().then(() => {
		relinkEdges(null);
		debounceSaveWorkflowPositions();
	});
}

function updateWorkflowNodeState(node: WorkflowNode<any> | null, state: any) {
	if (!node) return;
	if (nodeStateMap.has(node.id)) {
		nodeStateMap.set(node.id, Object.assign(nodeStateMap.get(node.id), state));
	} else {
		nodeStateMap.set(node.id, state);
	}

	// FIXME: in some places we do consecutive update-state events programmatically, this cause
	// an issue if we delay updates because they may not be independent. For now we will immediately
	// update the client-copy.
	wf.value.updateNodeState(node.id, nodeStateMap.get(node.id));

	saveNodeStateHandler();
}

async function updateWorkflowNodeStatus(node: WorkflowNode<any> | null, status: OperatorStatus) {
	if (!node) return;

	const payload: Map<string, OperatorStatus> = new Map([[node.id, status]]);

	const updatedWorkflow = await workflowService.updateStatus(wf.value.getId(), payload);
	wf.value.update(updatedWorkflow);
}

async function selectOutput(node: WorkflowNode<any> | null, selectedOutputId: string) {
	const updatedWorkflow = await workflowService.selectOutput(wf.value.getId(), node!.id, selectedOutputId);
	wf.value.update(updatedWorkflow);
}

// Route is mutated then watcher is triggered to open or close the drilldown
function addOperatorToRoute(
	nodeId: string | null,
	animation: 'left' | 'right' | 'scale' = 'scale' // drilldownSpawnAnimation is set here, left/right animations are for drilldown navigation
) {
	drilldownSpawnAnimation.value = animation;
	if (nodeId !== null) {
		router.push({ query: { operator: nodeId } });
	} else {
		router.push({ query: {} });
	}
}

const openDrilldown = (node: WorkflowNode<any>) => {
	currentActiveNode.value = node;
	startTime = Date.now();
	dialogIsOpened.value = true;
};

const closeDrilldown = async () => {
	dialogIsOpened.value = false;
	const timeSpent: number = Date.now() - startTime;
	await EventService.create(
		EventType.OperatorDrilldownTiming,
		useProjects().activeProject.value?.id,
		JSON.stringify({
			node: currentActiveNode.value?.displayName,
			timeSpent
		})
	);
};

const removeNode = async (nodeId: string) => {
	const updatedWorkflow = await workflowService.removeNodes(wf.value.getId(), [nodeId]);
	wf.value.update(updatedWorkflow);
};

const duplicateBranch = async (nodeId: string) => {
	wf.value.branchWorkflow(nodeId);

	cloneNoteBookSessions();

	const updatedWorkflow = await workflowService.saveWorkflow(wf.value.dump(), currentProjectId.value ?? undefined);
	wf.value.update(updatedWorkflow);
};

// We need to clone data-transform sessions, unlike other operators that are
// append-only, data-transform updates so we need to create distinct copies.
const cloneNoteBookSessions = async () => {
	const sessionIdSet = new Set<string>();

	const operationList = [DatasetTransformerOp.operation.name];

	for (let i = 0; i < wf.value.getNodes().length; i++) {
		const node = wf.value.getNodes()[i];
		if (operationList.includes(node.operationType)) {
			const state = node.state;
			const sessionId = state.notebookSessionId as string;
			if (!sessionId) continue;
			if (!sessionIdSet.has(sessionId)) {
				sessionIdSet.add(sessionId);
			} else {
				// eslint-disable-next-line
				const session = await cloneNoteBookSession(sessionId);
				state.notebookSessionId = session.id;
				sessionIdSet.add(session.id);
			}
		}
	}
};

const addOperatorToWorkflow: Function =
	(operator: OperatorImport, nodeSize: OperatorNodeSize = OperatorNodeSize.medium) =>
	async () => {
		const node = workflowService.newOperator(wf.value.getId(), operator.operation, newNodePosition, {
			size: nodeSize
		});
		const updatedWorkflow = await workflowService.addNode(wf.value.getId(), node);
		wf.value.update(updatedWorkflow);

		return node;
	};

async function onMenuSelection(operatorType: string, menuNode: WorkflowNode<any>, port: WorkflowPort) {
	const name = operatorType;
	const operation = registry.getOperation(operatorType);
	const node = registry.getNode(operatorType);
	const drilldown = registry.getDrilldown(operatorType);

	const width = workflowService.getOperatorNodeSize(OperatorNodeSize.medium).width;
	newNodePosition.x = menuNode.x + 80 + width;
	newNodePosition.y = menuNode.y;

	if (name && operation && node && drilldown) {
		const newNode: WorkflowNode<any> = await addOperatorToWorkflow({ name, operation, node, drilldown })();

		// The split('|') is for complex types - [modelId|modelConfigId] or [datasetId|simulationId]
		const portTypes = port.type.split('|');
		const inputPorts: WorkflowPort[] = [];
		newNode.inputs.forEach((input) => {
			if (intersection(input.type.split('|'), portTypes).length) {
				inputPorts.push(input);
			}
		});

		const edgePayload: WorkflowEdge = {
			id: uuidv4(),
			workflowId: wf.value.getId(),
			source: menuNode.id,
			sourcePortId: port.id,
			target: newNode.id,
			targetPortId: inputPorts[0].id,
			points: [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]
		};

		const updatedWorkflow = await workflowService.addEdge(wf.value.getId(), edgePayload);
		wf.value.update(updatedWorkflow);

		// Force edges to re-evaluate
		await nextTick();
		relinkEdges(null);
	}
}

// Menu categories and list items are in order of appearance for separators to work
const contextMenuItems: MenuItem[] = [
	{
		label: 'Modeling',
		items: [
			{
				label: ModelFromDocumentOp.operation.displayName,
				command: addOperatorToWorkflow(ModelFromDocumentOp)
			},
			{
				label: ModelEditOp.operation.displayName,
				command: addOperatorToWorkflow(ModelEditOp)
			},
			{
				label: StratifyMiraOp.operation.displayName,
				command: addOperatorToWorkflow(StratifyMiraOp)
			},
			{
				label: ModelComparisonOp.operation.displayName,
				command: addOperatorToWorkflow(ModelComparisonOp)
			}
		]
	},
	{
		label: 'Config & Intervention',
		items: [
			{
				label: ModelConfigOp.operation.displayName,
				command: addOperatorToWorkflow(ModelConfigOp)
			},
			{
				label: FunmanOp.operation.displayName,
				command: addOperatorToWorkflow(FunmanOp)
			},
			{
				label: InterventionPolicyOp.operation.displayName,
				command: addOperatorToWorkflow(InterventionPolicyOp)
			}
		]
	},
	{
		label: 'Simulation',
		items: [
			{
				label: SimulateCiemssOp.operation.displayName,
				command: addOperatorToWorkflow(SimulateCiemssOp)
			},
			{
				label: CalibrateCiemssOp.operation.displayName,
				command: addOperatorToWorkflow(CalibrateCiemssOp)
			},
			{
				label: OptimizeCiemssOp.operation.displayName,
				command: addOperatorToWorkflow(OptimizeCiemssOp)
			},
			{
				label: SimulateEnsembleCiemssOp.operation.displayName,
				command: addOperatorToWorkflow(SimulateEnsembleCiemssOp)
			},
			{
				label: CalibrateEnsembleCiemssOp.operation.displayName,
				command: addOperatorToWorkflow(CalibrateEnsembleCiemssOp)
			}
		]
	},
	{
		label: 'Data',
		items: [
			{
				label: DatasetTransformerOp.operation.displayName,
				command: addOperatorToWorkflow(DatasetTransformerOp)
			},
			{
				label: CompareDatasetsOp.operation.displayName,
				command: addOperatorToWorkflow(CompareDatasetsOp)
			}
		]
	},
	{
		label: 'Text block',
		command: () => {
			addAnnotationToWorkflow();
		}
	}
];
const addComponentMenu = ref();
const showAddComponentMenu = () => {
	const el = document.querySelector('#add-component-btn');
	const coords = el?.getBoundingClientRect();

	// Places new operators roughly in the centre
	if (canvasRef.value) {
		const box = (canvasRef.value.$el as HTMLDivElement).getBoundingClientRect();
		newNodePosition.x = (Math.random() * 50 + 0.5 * box.width - canvasTransform.x) / canvasTransform.k;
		newNodePosition.y = (Math.random() * 50 + 0.5 * box.height - canvasTransform.y) / canvasTransform.k;
	}

	if (coords) {
		const event = new PointerEvent('click', {
			clientX: coords.x + coords.width,
			clientY: coords.y + coords.height
		});
		addComponentMenu.value.toggle(event);
	}
};

const { getDragData } = useDragEvent();

async function onDrop(event: DragEvent) {
	const { assetId, assetType } = getDragData('initAssetNode') as {
		assetId: string;
		assetType: AssetType;
	};

	if (assetId && assetType) {
		updateNewNodePosition(event);

		let operation: Operation;
		let state: any = null;

		switch (assetType) {
			case AssetType.Model:
				operation = ModelOp.operation;
				state = { modelId: assetId };
				break;
			case AssetType.Dataset:
				operation = DatasetOp.operation;
				state = { datasetId: assetId };
				break;
			case AssetType.Document:
				operation = DocumentOp.operation;
				state = { documentId: assetId };
				break;
			default:
				return;
		}
		const operator = workflowService.newOperator(wf.value.getId(), operation, newNodePosition, { state });
		const updatedWorkflow = await workflowService.addNode(wf.value.getId(), operator);
		wf.value.update(updatedWorkflow);
	}
}

function toggleContextMenu(event: MouseEvent) {
	contextMenu.value.show(event);
	updateNewNodePosition(event);
}

function updateNewNodePosition(event: MouseEvent) {
	newNodePosition.x = (event.offsetX - canvasTransform.x) / canvasTransform.k;
	newNodePosition.y = (event.offsetY - canvasTransform.y) / canvasTransform.k;
}

function saveTransform(newTransform: { k: number; x: number; y: number }) {
	canvasTransform = newTransform;

	const t = wf.value.getTransform();
	t.x = newTransform.x;
	t.y = newTransform.y;
	t.k = newTransform.k;
}

const isCreatingNewEdge = computed(() => newEdge.value && newEdge.value.points && newEdge.value.points.length === 2);

async function createNewEdge(node: WorkflowNode<any>, port: WorkflowPort, direction: WorkflowDirection) {
	if (!isCreatingNewEdge.value) {
		newEdge.value = {
			id: 'new edge',
			workflowId: '0',
			points: [
				{ x: currentPortPosition.x, y: currentPortPosition.y },
				{ x: currentPortPosition.x, y: currentPortPosition.y }
			],
			source: direction === WorkflowDirection.FROM_OUTPUT ? node.id : undefined,
			sourcePortId: direction === WorkflowDirection.FROM_OUTPUT ? port.id : undefined,
			target: direction === WorkflowDirection.FROM_OUTPUT ? undefined : node.id,
			targetPortId: direction === WorkflowDirection.FROM_OUTPUT ? undefined : port.id,
			direction
		};
	} else {
		const edgePayload: WorkflowEdge = {
			id: uuidv4(),
			workflowId: wf.value.getId(),
			source: newEdge.value!.source ?? node.id,
			sourcePortId: newEdge.value!.sourcePortId ?? port.id,
			target: newEdge.value!.target ?? node.id,
			targetPortId: newEdge.value!.targetPortId ?? port.id,
			points: newEdge.value!.points
		};
		if (edgePayload.source === edgePayload.target) {
			cancelNewEdge();
			return;
		}

		const updatedWorkflow = await workflowService.addEdge(wf.value.getId(), edgePayload);
		wf.value.update(updatedWorkflow);
		cancelNewEdge();
	}
}

async function removeEdges(portId: string) {
	const edges = wf.value
		.getEdges()
		.filter(({ targetPortId, sourcePortId }) => targetPortId === portId || sourcePortId === portId);

	const updatedWorkflow = await workflowService.removeEdges(
		wf.value.getId(),
		edges.map((e) => e.id)
	);
	wf.value.update(updatedWorkflow);
}

function onCanvasClick() {
	if (isCreatingNewEdge.value) {
		cancelNewEdge();
	}
}

function cancelNewEdge() {
	newEdge.value = undefined;
}

function onPortMouseover(position: Position) {
	currentPortPosition = position;
	isMouseOverPort = true;
}

function onPortMouseleave() {
	isMouseOverPort = false;
}

function resizeHandler(node: WorkflowNode<any>) {
	relinkEdges(node);
	debounceSaveWorkflowPositions();
}

/*
 * Relink edges that have become detached
 *
 * [output-port](edge source => edge target)[input-port]
 */
function relinkEdges(node: WorkflowNode<any> | null) {
	const allEdges = wf.value.getEdges();
	const candidateEdges = node ? allEdges.filter((e) => e.source === node.id || e.target === node.id) : allEdges;

	// Note id can start with numerals, so we need [id=...]
	const getPortElement = (id: string) => d3.select(`[id='${id}']`).select('.port').node() as HTMLElement;

	// Cache
	const nodeMap = new Map<string, WorkflowNode<any>>(wf.value.getNodes().map((n) => [n.id, n]));

	for (let i = 0; i < candidateEdges.length; i++) {
		const edge = candidateEdges[i];
		const sourceNode = nodeMap.get(edge.source as string);
		const sourcePortElem = getPortElement(edge.sourcePortId as string);
		const targetNode = nodeMap.get(edge.target as string);
		const targetPortElem = getPortElement(edge.targetPortId as string);

		if (!sourcePortElem || !targetPortElem) continue;

		edge.points[0].x = sourceNode!.x + sourceNode!.width + sourcePortElem.offsetWidth * 0.5;
		edge.points[0].y = sourceNode!.y + sourcePortElem.offsetTop + sourcePortElem.offsetHeight * 0.5;
		edge.points[1].x = targetNode!.x + targetPortElem.offsetWidth * 0.5;
		edge.points[1].y = targetNode!.y + targetPortElem.offsetTop + targetPortElem.offsetHeight * 0.5;
		edgePositionSet.add(edge.id);
	}
}

function resetZoom() {
	let scaleFactor = 1;
	let translateX = 1;
	let translateY = 1;

	// Approximate the bounding box of the workflow
	const xExtent = d3.extent(wf.value.getNodes().map((n) => n.x)) as [number, number];
	const yExtent = d3.extent(wf.value.getNodes().map((n) => n.y)) as [number, number];

	const xMin = xExtent[0] - 20;
	const xMax = xExtent[1] + 200;
	const yMin = yExtent[0] - 20;
	const yMax = yExtent[1] + 300;

	// Find fitted scale
	const workflowCenterX = 0.5 * (xMax - xMin);
	const workflowCenterY = 0.5 * (yMax - yMin);

	const canvasW = (canvasRef.value.$el as HTMLElement).clientWidth;
	const canvasH = (canvasRef.value.$el as HTMLElement).clientHeight;

	scaleFactor = Math.min(canvasW / (xMax - xMin), canvasH / (yMax - yMin));

	// Calculate the translation to center the graph
	translateX = canvasW / 2 - scaleFactor * workflowCenterX;
	translateY = canvasH / 2 - scaleFactor * workflowCenterY;

	// Call exposed function to set zoom
	canvasRef.value.setZoom(translateX, translateY, scaleFactor);
}

let prevX = 0;
let prevY = 0;

function mouseUpdate(event: MouseEvent) {
	if (isCreatingNewEdge.value) {
		const pointIndex = newEdge.value?.direction === WorkflowDirection.FROM_OUTPUT ? 1 : 0;
		if (isMouseOverPort) {
			newEdge.value!.points[pointIndex].x = currentPortPosition.x;
			newEdge.value!.points[pointIndex].y = currentPortPosition.y;
		} else {
			const dx = event.x - prevX;
			const dy = event.y - prevY;
			newEdge.value!.points[pointIndex].x += dx / canvasTransform.k;
			newEdge.value!.points[pointIndex].y += dy / canvasTransform.k;
		}
	}
	prevX = event.x;
	prevY = event.y;
}

function updateEdgePositions(node: WorkflowNode<any>, { x, y }) {
	wf.value.getEdges().forEach((edge) => {
		if (edge.source === node.id) {
			edge.points[0].x += x / canvasTransform.k;
			edge.points[0].y += y / canvasTransform.k;
			edgePositionSet.add(edge.id);
		}
		if (edge.target === node.id) {
			edge.points[edge.points.length - 1].x += x / canvasTransform.k;
			edge.points[edge.points.length - 1].y += y / canvasTransform.k;
			edgePositionSet.add(edge.id);
		}
	});
}

const saveWorkflowPositions = async () => {
	const nodes = new Map(
		wf.value
			.getNodes()
			.filter((n) => nodePositionSet.has(n.id))
			.map((n) => [n.id, { x: n.x, y: n.y }])
	);

	const edges = new Map(
		wf.value
			.getEdges()
			.filter((e) => edgePositionSet.has(e.id))
			.map((e) => {
				const start = e.points[0];
				const end = e.points[1];
				return [
					e.id,
					[
						{ x: start.x, y: start.y },
						{ x: end.x, y: end.y }
					]
				];
			})
	);

	nodePositionSet.clear();
	edgePositionSet.clear();
	await workflowService.updatePositions(wf.value.getId(), nodes, edges);
};

const updatePosition = (node: WorkflowNode<any>, { x, y }) => {
	const teraNode = teraOperatorRefs.value.find((operatorNode) => operatorNode.id === node.id);
	if (teraNode.isEditing ?? false) {
		return;
	}
	isDragging = true;
	node.x += x / canvasTransform.k;
	node.y += y / canvasTransform.k;
	nodePositionSet.add(node.id);
	updateEdgePositions(node, { x, y });
};

const addAnnotationToWorkflow = async () => {
	const updatedWorkflow = await workflowService.addOrUpdateAnnotation(wf.value.getId(), {
		id: uuidv4(),
		x: newNodePosition.x,
		y: newNodePosition.y,
		content: 'Double click to edit...',
		type: '',
		textSize: 12
	});
	wf.value.update(updatedWorkflow);
};

const updateAnnotationPosition = (annotation: WorkflowAnnotation, event: any) => {
	annotation.x += event.x / canvasTransform.k;
	annotation.y += event.y / canvasTransform.k;
};

const updateAnnotation = async (annotation: WorkflowAnnotation) => {
	const updatedWorkflow = await workflowService.addOrUpdateAnnotation(wf.value.getId(), annotation);
	wf.value.update(updatedWorkflow);
};

const removeAnnotation = async (annotationId: string) => {
	const updatedWorkflow = await workflowService.removeAnnotation(wf.value.getId(), annotationId);
	wf.value.update(updatedWorkflow);
};

function interpolatePointsForCurve(a: Position, b: Position): Position[] {
	const controlXOffset = 50;
	return [a, { x: a.x + controlXOffset, y: a.y }, { x: b.x - controlXOffset, y: b.y }, b];
}

const pathFn = d3
	.line<{ x: number; y: number }>()
	.x((d) => d.x)
	.y((d) => d.y)
	.curve(d3.curveBasis);

// Get around typescript complaints
const drawPath = (v: any) => pathFn(v) as string;

const handleDrilldown = () => {
	const operatorId = route.query?.operator?.toString();
	if (operatorId) {
		const operator = wf.value.getNodes().find((n) => n.id === operatorId);
		if (!operator) return;
		// Prepare drilldown navigation menus
		const { upstreamNodes, downstreamNodes } = wf.value.getNeighborNodes(operatorId);
		upstreamOperatorsNav.value = [
			{
				label: 'Upstream operators',
				items: upstreamNodes.map((upstreamNode) => ({
					label: workflowService.isAssetOperator(upstreamNode.operationType)
						? upstreamNode.outputs[0].label // Asset name
						: upstreamNode.displayName, // Operator name
					icon: workflowService.iconToOperatorMap.get(upstreamNode.operationType) ?? 'pi pi-cog',
					command: () => addOperatorToRoute(upstreamNode.id, 'right')
				}))
			}
		];
		downstreamOperatorsNav.value = [
			{
				label: 'Downstream operators',
				items: downstreamNodes.map((downstreamNode) => ({
					label: workflowService.isAssetOperator(downstreamNode.operationType)
						? downstreamNode.outputs[0].label // Asset name
						: downstreamNode.displayName, // Operator name
					icon: workflowService.iconToOperatorMap.get(downstreamNode.operationType) ?? 'pi pi-cog',
					command: () => addOperatorToRoute(downstreamNode.id, 'left')
				}))
			}
		];
		// Open drilldown
		openDrilldown(operator);
	} else {
		closeDrilldown();
	}
};

const hasInvalidNodes = computed(() => wf.value.getNodes().some((node) => node.status === OperatorStatus.INVALID));

const dontShowAgain = () => {
	localStorage.setItem('warningBannerDismissed', 'true');
	warningBanner.value = false;
};

watch(
	() => props.assetId,
	async (newId, oldId) => {
		// Save previous workflow, if applicable
		if (newId !== oldId && oldId) {
			saveWorkflowPositions();
			workflowService.setLocalStorageTransform(wf.value.getId(), canvasTransform);
		}

		const workflowId = props.assetId;
		if (!workflowId) return;
		isWorkflowLoading.value = true;

		const transform = workflowService.getLocalStorageTransform(workflowId);
		if (transform) {
			canvasTransform = transform;
		}
		wf.value.load(await workflowService.getWorkflow(workflowId));
		isWorkflowLoading.value = false;

		handleDrilldown();
	},
	{ immediate: true }
);

watch(
	() => [route.query],
	() => {
		if (isWorkflowLoading.value) return;
		handleDrilldown();
	},
	{ deep: true }
);

onMounted(() => {
	// check if the user has dismissed the warning banner
	if (localStorage.getItem('warningBannerDismissed') === 'true') {
		warningBanner.value = false;
	}

	document.addEventListener('mousemove', mouseUpdate);

	saveTimer = setInterval(async () => {
		workflowService.setLocalStorageTransform(wf.value.getId(), canvasTransform);
	}, WORKFLOW_SAVE_INTERVAL);

	subscribe(ClientEventType.WorkflowUpdate, updateWorkflowHandler);
	currentProjectId.value = activeProjectId.value;
});

onUnmounted(() => {
	saveWorkflowPositions();
	if (saveTimer) {
		clearInterval(saveTimer);
	}
	unsubscribe(ClientEventType.WorkflowUpdate, updateWorkflowHandler);

	if (canvasTransform) {
		workflowService.setLocalStorageTransform(wf.value.getId(), canvasTransform);
	}
	document.removeEventListener('mousemove', mouseUpdate);
	workflowService.saveWorkflow(wf.value.dump());
});
</script>

<style scoped>
.toolbar {
	align-items: center;
	background-color: var(--surface-transparent);
	border-bottom: 1px solid var(--surface-border-light);
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	padding: var(--gap-1) var(--gap-4);
	z-index: 900;
}

.glass {
	backdrop-filter: blur(10px);
}

.button-group {
	align-items: center;
	display: flex;
	flex-direction: row;
	gap: var(--gap-3);
}

.rename-workflow {
	align-items: center;
	display: flex;
	flex-wrap: nowrap;
}

.warning-banner {
	width: 100%;
	font-size: var(--font-caption);
	border-bottom: 1px solid var(--surface-border-light);
	display: flex;
	align-items: center;
	background-color: var(--surface-warning);
	height: 0;
	overflow: hidden;
	padding-left: 1rem;
	transition: height 0.15s ease-out;
	&.visible {
		height: 2rem;
	}
}
</style>
