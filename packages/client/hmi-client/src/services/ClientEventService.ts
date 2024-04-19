import { EventSource } from 'extended-eventsource';
import type { ClientEvent, ExtractionStatusUpdate } from '@/types/Types';
import { ClientEventType } from '@/types/Types';
import useAuthStore from '@/stores/auth';
import getConfiguration from '@/services/ConfigService';

/**
 * A map of event types to message handlers
 */
const subscribers = new Map<ClientEventType, ((data: ClientEvent<any>) => void)[]>();

/**
 * The last time a heartbeat was received
 */
let lastHeartbeat = new Date().valueOf();

/**
 * The initial backoff time in milliseconds for resubscribing to the SSE endpoint
 * in the event of a retriable error
 */
let backoffMs = 1000;

/**
 * Whether we are currently reconnecting to the SSE endpoint
 */
let reconnecting = false;

/**
 * An error that can be retried
 */
class RetriableError extends Error {}

/**
 * Connects to the SSE endpoint and adds a message handler to pass on the messages to the subscribers
 */
export async function init(): Promise<void> {
	const authStore = useAuthStore();

	const eventSource = new EventSource('/api/client-event', {
		headers: {
			Authorization: `Bearer ${authStore.token}`
		},
		retry: 3000
	});
	eventSource.onmessage = (message: MessageEvent) => {
		// Parse the data as a ClientEvent and pass it on to the subscribers
		const data = JSON.parse(message.data) as ClientEvent<any>;
		if (data.type === ClientEventType.Heartbeat) {
			lastHeartbeat = new Date().valueOf();
			return;
		}
		const handlers = subscribers.get(data.type);
		if (handlers) {
			handlers.forEach((handler) => handler(data));
		}
	};
	eventSource.onopen = async (response: any) => {
		if (response.status === 401) {
			// redirect to the login page
			authStore.keycloak?.login({
				redirectUri: window.location.href
			});
		} else if (response.status >= 500) {
			throw new RetriableError('Internal server error');
		} else {
			// Reset the backoff time as we've made a connection successfully
			backoffMs = 1000;
		}
	};
	eventSource.onerror = (error: any) => {
		// If we get a retriable error, double the backoff time up to a maximum of 60 seconds
		if (error instanceof RetriableError) {
			backoffMs *= 2;
			return Math.min(backoffMs, 60000);
		}
		throw error; // fatal
	};
}

/**
 * Periodically checks if we have received a heartbeat within the configured interval
 * and reconnects if not
 */
setInterval(async () => {
	if (!reconnecting) {
		reconnecting = true;
		const config = await getConfiguration();
		const heartbeatIntervalMillis = config?.sseHeartbeatIntervalMillis ?? 10000;
		if (new Date().valueOf() - lastHeartbeat > heartbeatIntervalMillis) {
			await init();
		}
		reconnecting = false;
	}
}, 1000);

/**
 * Subscribes to a specific event type
 * @param eventType       The event type to subscribe to
 * @param messageHandler  The message handler
 */
export async function subscribe(
	eventType: ClientEventType,
	messageHandler: (data: ClientEvent<any>) => void
): Promise<void> {
	if (!subscribers.has(eventType)) {
		subscribers.set(eventType, []);
	}
	subscribers.get(eventType)?.push(messageHandler);
}

/**
 * Unsubscribes from a specific event type
 * @param eventType
 * @param messageHandler
 */
export async function unsubscribe(
	eventType: ClientEventType,
	messageHandler: (data: ClientEvent<any>) => void
): Promise<void> {
	const handlers = subscribers.get(eventType);
	if (handlers) {
		const index = handlers.indexOf(messageHandler);
		if (index > -1) {
			handlers.splice(index, 1);
		}
	}
}

/**
 * Handle messages received from the extraction client-event
 */
export const extractionStatusUpdateHandler = async (event: ClientEvent<ExtractionStatusUpdate>) => {
	const { data } = event;
	if (data.error) {
		console.error(`[${data.t}]: ${data.error}`);
		await unsubscribe(ClientEventType.Extraction, extractionStatusUpdateHandler);
		return;
	}

	console.debug(`[${data.t}]: ${data.message}`);
	if (data.t >= 1.0) {
		await unsubscribe(ClientEventType.Extraction, extractionStatusUpdateHandler);
	}
};
