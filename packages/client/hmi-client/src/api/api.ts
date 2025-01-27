import { logger } from '@/utils/logger';
import axios, { AxiosError, AxiosHeaders } from 'axios';
import { EventSource } from 'extended-eventsource';
import { ServerError } from '@/types/ServerError';
import { Ref, ref } from 'vue';
import { activeProjectId } from '@/composables/activeProject';
import useAuthStore from '../stores/auth';

export class FatalError extends Error {}

function getProjectIdFromUrl(): string | null {
	const url = new URL(window.location.href);
	const match = url.pathname.match(/\/projects\/([a-z,0-9,-]+)\//);
	return match ? match[1] : null;
}

function getProjectId(): string | null {
	// ActiveProjectId is often not available when the API is called from a global context or immediately after pages are hard refreshed, so we need to check the URL for the project id
	return activeProjectId.value ?? localStorage.getItem('activeProjectId') ?? getProjectIdFromUrl() ?? null;
}

const API = axios.create({
	baseURL: '/api',
	headers: new AxiosHeaders()
});

// Hook in bearer tokens
// See
// - https://medium.com/swlh/handling-access-and-refresh-tokens-using-axios-interceptors-3970b601a5da
API.interceptors.request.use(
	(config) => {
		const auth = useAuthStore();
		config.headers.setAuthorization(`Bearer ${auth.getToken()}`);
		const projectId = getProjectId();
		console.log('projectId', projectId);
		if (projectId) {
			if (config.params) {
				config.params['project-id'] = projectId;
			} else {
				config.params = { 'project-id': projectId };
			}
		}
		return config;
	},
	(error) => {
		logger.error(error, { showToast: false, toastTitle: `Authentication Error` });
	}
);

API.interceptors.response.use(
	(response) => response,
	(error: AxiosError) => {
		if (error.status === 401) {
			// redirect to login
			const auth = useAuthStore();
			auth.login(window.location.href);
		} else {
			let message = error.message;
			let title = `${error.response?.statusText} (${error.response?.status})`;
			if (error?.response?.data) {
				const responseError: ServerError = error.response.data as ServerError;

				// check to see if the 'message' property is set. If it is, set message to that value.
				// If not, check the 'trace' property and extract the error from that.
				// It will be the substring between the first set of quotations marks.
				if (responseError.message) {
					message = `${responseError.message}`;
				} else if (responseError.trace) {
					// extract the substring between the first set of quotation marks and use that
					const start = responseError.trace.indexOf('"');
					const end = responseError.trace.indexOf('"', start + 1);
					message = responseError.trace.substring(start + 1, end);
				}

				message = message ?? 'An Error occurred';
				title = `${responseError.error} (${responseError.status})`;
			}

			logger.error(message, {
				showToast: true,
				toastTitle: title
			});
		}
		return null;
	}
);

// eslint-disable-next-line no-promise-executor-return
const timeout = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export enum PollerState {
	Done = 'done',
	Failed = 'failed',
	ExceedThreshold = 'ExceedThreshold',
	Cancelled = 'Cancelled'
}

export interface PollResponse<T> {
	error: any;
	progress?: any;
	data: T | null;
	cancelled?: boolean;
}

type PollerCallback<T> = (...args: any[]) => Promise<PollResponse<T>>;
type ProgressCallback = (progressData: any, current: number, max: number) => void;

export interface PollerResult<T> {
	state: PollerState;
	data: T | null;
	error?: string;
}

export class Poller<T> {
	pollingInterval = 3000;

	pollingThreshold = 150;

	keepGoing = false;

	poll: PollerCallback<T> | null;

	progressAction: ProgressCallback | null;

	numPolls = 0;

	constructor() {
		this.poll = null;
		this.progressAction = null;
	}

	setInterval(v: number) {
		this.pollingInterval = v;
		return this;
	}

	setThreshold(v: number) {
		this.pollingThreshold = v;
		return this;
	}

	setPollAction(f: PollerCallback<T>) {
		this.poll = f;
		return this;
	}

	setProgressAction(f: ProgressCallback) {
		this.progressAction = f;
		return this;
	}

	// Start polling, there are 5 foreseeable exit conditions
	// 1. Done, we have the result
	// 2. Failed, request returned with 4xx or 5xx status
	// 3. Failed, any unexpected errors
	// 4. ExceedThreshold, took longer than allotted time
	// 5. Cancelled, the user has cancelled this
	async start(): Promise<PollerResult<T>> {
		this.keepGoing = true;
		this.numPolls = 0;

		let response: PollResponse<T> | null = null;

		if (!this.poll) throw new Error('Poll callback undefined');

		while (this.numPolls < this.pollingThreshold) {
			this.numPolls++;
			if (!this.keepGoing) {
				return {
					state: PollerState.Cancelled,
					data: null
				};
			}

			try {
				// eslint-disable-next-line no-await-in-loop
				response = (await this.poll()) as PollResponse<T>;
				const { error, progress, data, cancelled } = response;

				if (error) {
					return {
						state: PollerState.Failed,
						data: null,
						error
					};
				}

				if (cancelled) {
					return {
						state: PollerState.Cancelled,
						data: null
					};
				}

				if (data) {
					return {
						state: PollerState.Done,
						data
					};
				}

				// We are still in progress
				if (this.progressAction) {
					this.progressAction(progress, this.numPolls, this.pollingThreshold);
				}
			} catch (error) {
				return {
					state: PollerState.Failed,
					data: null
				};
			}

			// eslint-disable-next-line no-await-in-loop
			await timeout(this.pollingInterval);
		}

		return {
			state: PollerState.ExceedThreshold,
			data: null
		};
	}

	// Not really a fluent API, but convienent
	stop() {
		this.keepGoing = false;
	}
}

/**
 * Interface defining handlers for Server-Sent Events (SSE).
 */
export interface TaskEventHandlers {
	/**
	 * Handler for incoming SSE data.
	 * @param {any} data The received data.
	 */
	ondata: (data: any, closeConnection: () => void) => void;
	/**
	 * Handler for SSE connection open event.
	 * @param {Response} response The response object.
	 */
	onopen?: (response: Response) => void;
	/**
	 * Handler for SSE connection error.
	 * @param {Error} error The error object.
	 */
	onerror?: (error: Error) => void;
	/**
	 * Handler for SSE connection close event.
	 */
	onclose?: () => void;
}

/**
 * Class representing a Server-Sent Events (SSE) handler.
 */
export class TaskHandler {
	private url: string;

	private handlers: TaskEventHandlers;

	private eventSource: EventSource | null;

	public isRunning: Ref<boolean>;

	/**
	 * Create a TaskHandler.
	 * @param {string} url - The URL to connect to.
	 * @param {TaskHandlers} handlers - The handlers for the SSE events.
	 */
	constructor(url: string, handlers: TaskEventHandlers) {
		this.url = url;
		this.handlers = handlers;
		this.isRunning = ref(false);
		this.eventSource = null;
	}

	/**
	 * Close the SSE connection.
	 */
	public closeConnection() {
		if (this.eventSource) this.eventSource.close();
		if (this.handlers.onclose) this.handlers.onclose();
		this.isRunning.value = false;
		logger.info('Connection closed', { showToast: false });
	}

	/**
	 * Start the task.
	 */
	async start(): Promise<void> {
		this.isRunning.value = true;
		const handlers = this.handlers;
		const authStore = useAuthStore();

		try {
			this.eventSource = new EventSource(API.defaults.baseURL + this.url, {
				headers: {
					Authorization: `Bearer ${authStore.getToken()}`
				},
				retry: 3000
			});
			this.eventSource.onmessage = (message: MessageEvent) => {
				try {
					const data = message?.data;
					const parsedData = JSON.parse(data);
					const closeConnection = this.closeConnection.bind(this);
					this.handlers.ondata(parsedData, closeConnection);
				} catch (error) {
					logger.error(error, { showToast: false });
					this.closeConnection();
				}
			};
			this.eventSource.onerror = (error: any) => {
				if (this.handlers.onerror) this.handlers.onerror(error);
				if (error instanceof FatalError) {
					// closes the connection on fatal error otherwise it will keep retrying
					throw error;
				}
			};
			this.eventSource.onopen = async (response: any) => {
				logger.info('Connection opened', { showToast: false });
				if (handlers.onopen) handlers.onopen(response);
			};
		} catch (error: unknown) {
			logger.error(error, { showToast: false });
			this.closeConnection();
		}
	}
}

export default API;
