import { useProjects } from '@/composables/project';
import {
	CloneProjectStatusUpdate,
	ExtractionStatusUpdate,
	ModelEnrichmentStatusUpdate,
	NotificationItem,
	NotificationItemStatus
} from '@/types/common';
import { ProjectPages } from '@/types/Project';
import {
	AssetType,
	ClientEvent,
	ClientEventType,
	ProgressState,
	SimulationEngine,
	SimulationNotificationData,
	StatusUpdate,
	TaskResponse,
	TaskStatus
} from '@/types/Types';
import { logger } from '@/utils/logger';
import { snakeToCapitalSentence } from '@/utils/text';
import { Ref } from 'vue';
import { getModel } from '@/services/model';
import { getDocumentAsset } from './document-assets';
import { getWorkflow } from './workflow';

type NotificationEventData = TaskResponse | StatusUpdate<unknown>;
/**
 * We use ProgressState to represent the status of the notification for a task/job.
 * Since the task status from the task runners response (TaskResponse) is represented by TaskStatus, we need to map TaskStatus to ProgressState.
 */
const taskStatusToProgressState = {
	[TaskStatus.Queued]: ProgressState.Queued,
	[TaskStatus.Running]: ProgressState.Running,
	[TaskStatus.Success]: ProgressState.Complete,
	[TaskStatus.Failed]: ProgressState.Failed,
	[TaskStatus.Cancelled]: ProgressState.Cancelled,
	[TaskStatus.Cancelling]: ProgressState.Cancelling
};
const getStatusFromTaskResponse = (data: TaskResponse) => taskStatusToProgressState[data.status];

const isTaskResponse = (data: any): data is TaskResponse =>
	data.id !== undefined && data.script !== undefined && data.status;

const toastTitle = {
	[ClientEventType.ExtractionPdf]: {
		success: 'PDF Extraction Completed',
		error: 'PDF Extraction Error'
	}
};

const logStatusMessage = (eventType: ClientEventType, status: ProgressState, msg: string, error: string) => {
	if (![ProgressState.Complete, ProgressState.Failed, ProgressState.Cancelled].includes(status)) return;

	if (status === ProgressState.Complete)
		logger.success(msg, {
			showToast: true,
			toastTitle: toastTitle[eventType]?.success ?? `${snakeToCapitalSentence(eventType)} Completed`
		});
	if (status === ProgressState.Failed)
		logger.error(error, {
			showToast: true,
			toastTitle: toastTitle[eventType]?.error ?? `${snakeToCapitalSentence(eventType)} Failed`
		});
	if (status === ProgressState.Cancelled)
		logger.info(msg, {
			showToast: true,
			toastTitle: toastTitle[eventType]?.cancelled ?? `${snakeToCapitalSentence(eventType)} Cancelled`
		});
};

const updateStatusFromTaskResponse = (event: ClientEvent<TaskResponse>): NotificationItemStatus => {
	const status = getStatusFromTaskResponse(event.data);
	const error = status === ProgressState.Failed ? event.data.stderr : '';
	const statusMsg = {
		[ProgressState.Running]: 'in progress...',
		[ProgressState.Cancelling]: 'is cancelling...',
		[ProgressState.Cancelled]: 'was cancelled.',
		[ProgressState.Complete]: 'completed.'
	};
	const msg = `${snakeToCapitalSentence(event.type)} ${statusMsg[status] ?? ''}`;
	return { status, msg, error };
};

const buildNotificationItemStatus = <T extends NotificationEventData>(
	event: ClientEvent<T>
): NotificationItemStatus => {
	if (isTaskResponse(event.data)) return updateStatusFromTaskResponse(event as ClientEvent<TaskResponse>);
	return {
		status: event.data.state,
		msg: event.data.message,
		progress: event.data.progress,
		error: event.data.error
	};
};

// Creates notification event handlers for each type of client events that manipulates given notification items ref
export const createNotificationEventHandlers = (notificationItems: Ref<NotificationItem[]>) => {
	const handlers = {} as Record<ClientEventType, (event: ClientEvent<any>) => void>;

	const registerHandler = <T extends NotificationEventData>(
		eventType: ClientEventType,
		onCreateNewNotificationItem: (event: ClientEvent<T>, createdItem: NotificationItem) => void = () => {}
	) => {
		handlers[eventType] = (event: ClientEvent<T>) => {
			if (!event.data) return;
			const existingItem = notificationItems.value.find(
				(item) => item.notificationGroupId === event.notificationGroupId
			);
			const lastUpdated = new Date(event.createdAtMs).getTime();
			if (!existingItem) {
				const newItem: NotificationItem = {
					notificationGroupId: event.notificationGroupId ?? '',
					projectId: event.projectId,
					type: event.type,
					typeDisplayName: snakeToCapitalSentence(event.type),
					lastUpdated,
					acknowledged: false,
					supportCancel: false,
					context: '',
					sourceName: '',
					assetId: '',
					pageType: ProjectPages.OVERVIEW, // Default to overview page
					...buildNotificationItemStatus(event)
				};
				notificationItems.value.push(newItem);
				onCreateNewNotificationItem(event, newItem);
				return;
			}
			// Update existing item status
			Object.assign(existingItem, {
				lastUpdated,
				...buildNotificationItemStatus(event)
			});
		};
	};

	// Register handlers for each client event type
	registerHandler<CloneProjectStatusUpdate>(ClientEventType.CloneProject, (event, created) => {
		created.assetId = event.data.data.projectId;
		created.typeDisplayName = 'Cloned Project';
		useProjects()
			.get(created.assetId)
			.then((project) => {
				created.sourceName = project?.name ?? 'Source Project';
			});
	});
	registerHandler<ExtractionStatusUpdate>(ClientEventType.ExtractionPdf, (event, created) => {
		created.assetId = event.data.data.documentId;
		created.pageType = AssetType.Document;
		created.typeDisplayName = 'PDF Extraction';
		getDocumentAsset(created.assetId, created.projectId).then((document) =>
			Object.assign(created, { sourceName: document?.name || '' })
		);
	});
	registerHandler<TaskResponse>(ClientEventType.TaskGollmConfigureModelFromDocument, (event, created) => {
		created.supportCancel = true;
		created.sourceName = 'Model Configuration from Document';
		created.assetId = event.data.additionalProperties.workflowId as string;
		created.pageType = AssetType.Workflow;
		created.nodeId = event.data.additionalProperties.nodeId as string;
		getWorkflow(created.assetId, created.projectId).then((workflow) =>
			Object.assign(created, { context: workflow?.name || '' })
		);
	});
	registerHandler<TaskResponse>(ClientEventType.TaskGollmConfigureModelFromDataset, (event, created) => {
		created.supportCancel = true;
		created.sourceName = 'Model Configuration from Dataset';
		created.assetId = event.data.additionalProperties.workflowId as string;
		created.pageType = AssetType.Workflow;
		created.nodeId = event.data.additionalProperties.nodeId as string;
		getWorkflow(created.assetId, created.projectId).then((workflow) =>
			Object.assign(created, { context: workflow?.name || '' })
		);
	});
	registerHandler<TaskResponse>(ClientEventType.TaskGollmCompareModel, (event, created) => {
		created.supportCancel = true;
		created.sourceName = 'Compare models';
		created.assetId = event.data.additionalProperties.workflowId as string;
		created.pageType = AssetType.Workflow;
		created.nodeId = event.data.additionalProperties.nodeId as string;
		getWorkflow(created.assetId, created.projectId).then((workflow) =>
			Object.assign(created, { context: workflow?.name || '' })
		);
	});
	registerHandler<TaskResponse>(ClientEventType.TaskGollmInterventionsFromDocument, (event, created) => {
		created.supportCancel = true;
		created.sourceName = 'Intervention Policies from Document';
		created.assetId = event.data.additionalProperties.workflowId as string;
		created.pageType = AssetType.Workflow;
		created.nodeId = event.data.additionalProperties.nodeId as string;
		getWorkflow(created.assetId, created.projectId).then((workflow) =>
			Object.assign(created, { context: workflow?.name || '' })
		);
	});
	registerHandler<TaskResponse>(ClientEventType.TaskGollmInterventionsFromDataset, (event, created) => {
		created.supportCancel = true;
		created.sourceName = 'Intervention Policies from Dataset';
		created.assetId = event.data.additionalProperties.workflowId as string;
		created.pageType = AssetType.Workflow;
		created.nodeId = event.data.additionalProperties.nodeId as string;
		getWorkflow(created.assetId, created.projectId).then((workflow) =>
			Object.assign(created, { context: workflow?.name || '' })
		);
	});
	registerHandler<StatusUpdate<SimulationNotificationData>>(
		ClientEventType.SimulationNotification,
		(event, created) => {
			created.supportCancel = event.data.data.simulationEngine === SimulationEngine.Ciemss;
			created.sourceName = event.data.data.metadata?.nodeName || '';
			created.assetId = event.data.data.metadata?.workflowId || '';
			created.pageType = AssetType.Workflow;
			created.nodeId = event.data.data.metadata?.nodeId || '';
			created.context = event.data.data.metadata?.workflowName || '';
			created.typeDisplayName = `${snakeToCapitalSentence(event.data.data.simulationType)} (${event.data.data.simulationEngine.toLowerCase()})`;
		}
	);
	registerHandler<ModelEnrichmentStatusUpdate>(ClientEventType.KnowledgeEnrichmentModel, (event, created) => {
		created.sourceName = 'Model Enrichment';
		// Check if the event data contains a workflowId and nodeId
		if (event.data.data?.workflowId && event.data.data?.nodeId) {
			created.assetId = event.data.data.workflowId as string;
			created.pageType = AssetType.Workflow;
			created.nodeId = event.data.data.nodeId as string;
			getWorkflow(created.assetId, created.projectId).then((workflow) =>
				Object.assign(created, { context: workflow?.name || '' })
			);
		}

		// We display the model page from where the enrichment model was triggered
		else {
			created.assetId = event.data.data.modelId as string;
			created.pageType = AssetType.Model;
			created.typeDisplayName = 'Model Enrichment';
			getModel(created.assetId, created.projectId).then((model) =>
				Object.assign(created, { sourceName: model?.name || '' })
			);
		}
	});

	const getHandler = (eventType: ClientEventType) => handlers[eventType] ?? (() => {});

	return {
		get: getHandler,
		getSupportedEventTypes: () => Object.keys(handlers) as ClientEventType[]
	};
};

/**
 * Creates notification event logger for the provided visible notification items.
 * @param visibleNotificationItems
 * @returns
 */
export const createNotificationEventLogger = (visibleNotificationItems: Ref<NotificationItem[]>) => {
	const handleLogging = <T extends NotificationEventData>(event: ClientEvent<T>) => {
		if (!event.notificationGroupId) return;
		const notificationItem = visibleNotificationItems.value.find(
			(item) => item.notificationGroupId === event.notificationGroupId
		);
		if (!notificationItem) return;
		const status = buildNotificationItemStatus(event);
		logStatusMessage(event.type, status.status, status.msg, status.error);
	};
	return handleLogging;
};
