package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.configuration.TaskRunnerConfiguration;
import software.uncharted.terarium.hmiserver.configuration.TaskRunnerConfiguration.RabbitConfig;
import software.uncharted.terarium.hmiserver.models.ClientEvent;
import software.uncharted.terarium.hmiserver.models.ClientEventType;
import software.uncharted.terarium.hmiserver.models.notification.NotificationEvent;
import software.uncharted.terarium.hmiserver.models.notification.NotificationGroup;
import software.uncharted.terarium.hmiserver.models.task.CompoundTask;
import software.uncharted.terarium.hmiserver.models.task.TaskFuture;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.models.task.TaskStatus;
import software.uncharted.terarium.hmiserver.service.ClientEventService;
import software.uncharted.terarium.hmiserver.service.notification.NotificationService;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

	public enum TaskMode {
		@JsonAlias("sync")
		SYNC("sync"),
		@JsonAlias("async")
		ASYNC("async");

		private final String value;

		TaskMode(final String value) {
			this.value = value;
		}

		public String toString() {
			return value;
		}
	}

	// This private subclass is to prevent people from setting the task id
	// themselves since many other resources are tied to the id and it is assumed
	// to be unique.
	@NoArgsConstructor
	@Data
	@EqualsAndHashCode(callSuper = true)
	private static class TaskRequestWithId extends TaskRequest {

		private UUID id;
		private String routingKey;

		TaskRequestWithId(final TaskRequest req, final String rk) {
			id = UUID.randomUUID();
			type = req.getType();
			script = req.getScript();
			input = req.getInput();
			userId = req.getUserId();
			projectId = req.getProjectId();
			timeoutMinutes = req.getTimeoutMinutes();
			additionalProperties = req.getAdditionalProperties();
			routingKey = rk;
			useCache = req.isUseCache();
		}

		public TaskResponse createResponse(final TaskStatus status, final String stdout, final String stderr) {
			return new TaskResponse()
				.setId(id)
				.setStatus(status)
				.setScript(getScript())
				.setUserId(userId)
				.setProjectId(projectId)
				.setAdditionalProperties(getAdditionalProperties())
				.setStdout(stdout)
				.setStderr(stderr)
				.setRoutingKey(getRoutingKey())
				.setUseCache(useCache)
				.setRequestSHA256(getSHA256());
		}
	}

	// This private subclass exists to prevent anything outside of this service from
	// mucking with the futures internal state.
	private static class CompletableTaskFuture extends TaskFuture {

		public CompletableTaskFuture(final UUID id) {
			this.id = id;
			this.future = new CompletableFuture<>();
		}

		public CompletableTaskFuture(final TaskRequestWithId req, final TaskResponse resp) {
			this.id = req.getId();

			// We are re-using a cached response, so lets create a TaskResponse from the
			// actual TaskRequest
			// and then copy over the response payload.
			final TaskResponse actualResp = req.createResponse(resp.getStatus(), resp.getStdout(), resp.getStderr());
			actualResp.setOutput(resp.getOutput());

			this.future = new CompletableFuture<>();
			this.future.complete(actualResp);
			this.latestResponse = actualResp;
		}

		public synchronized void complete(final TaskResponse resp) {
			this.latestResponse = resp;
			this.future.complete(resp);
		}
	}

	private final TaskRunnerConfiguration taskRunnerConfiguration;

	private static final String RESPONSE_CACHE_KEY = "task-service-response-cache";

	// TTL = Time to live, the maximum time a key will be in the cache before it is
	// evicted, regardless of activity.
	@Value("${terarium.taskrunner.response-cache-ttl-seconds:43200}") // 12 hours
	private long CACHE_TTL_SECONDS;

	// Max idle = The maximum time a key can be idle in the cache before it is
	// evicted.
	@Value("${terarium.taskrunner.response-cache-max-idle-seconds:7200}") // 2 hours
	private long CACHE_MAX_IDLE_SECONDS;

	private Map<String, RabbitAdmin> rabbitAdmins;
	private Map<String, RabbitAdmin> rabbitAdminsByConnection;
	private Map<String, URI> rabbitURIByKey;

	private final Map<String, CachingConnectionFactory> connectionFactories = new HashMap<>();
	private final Config config;
	private final ObjectMapper objectMapper;
	private final NotificationService notificationService;
	private final ClientEventService clientEventService;

	private final Map<String, DirectMessageListenerContainer> taskResponseConsumers = new HashMap<>();

	private final Map<String, TaskResponseHandler> responseHandlers = new ConcurrentHashMap<>();

	private final RedissonClient redissonClient;

	@Value("${terarium.taskrunner.deploymentRoutingKey:default}")
	private String deploymentRoutingKey;

	private final String instanceRoutingKey = UUID.randomUUID().toString();

	private RMapCache<String, TaskResponse> responseCache;
	private final Map<UUID, CompletableTaskFuture> futures = new ConcurrentHashMap<>();

	// The queue name that the taskrunner will consume on for requests.
	@Value("${terarium.taskrunner.request-queue}")
	private String TASK_RUNNER_REQUEST_QUEUE;

	// The exchange that the task responses are published to.
	@Value("${terarium.taskrunner.response-exchange}")
	private String TASK_RUNNER_RESPONSE_EXCHANGE;

	// The queue that the task responses are published to.
	@Value("${terarium.taskrunner.response-queue}")
	private String TASK_RUNNER_RESPONSE_QUEUE;

	@Value("${terarium.taskrunner.durable-queues}")
	private Boolean IS_DURABLE_QUEUES;

	// Once a single instance of the hmi-server has processed a task response, it
	// will publish to this exchange to broadcast the response to all other
	// instances. This will direct the message to all hmi-instances so that the
	// correct instance holding the sse can forward the response to the user.
	@Value("${terarium.taskrunner.response-broadcast-exchange}")
	private String TASK_RUNNER_RESPONSE_BROADCAST_EXCHANGE;

	// The exchange name to publish cancellations to.
	@Value("${terarium.taskrunner.cancellation-exchange}")
	private String TASK_RUNNER_CANCELLATION_EXCHANGE;

	private final Environment env;

	private boolean isRunningLocalProfile() {
		final String[] activeProfiles = env.getActiveProfiles();
		for (final String profile : activeProfiles) {
			if ("local".equals(profile)) {
				return true;
			}
		}
		return false;
	}

	@PostConstruct
	void init() {
		// use a distributed cache and lock so that these can be synchronized across
		// multiple instances of the hmi-server
		responseCache = redissonClient.getMapCache(RESPONSE_CACHE_KEY);

		if (isRunningLocalProfile()) {
			// sanity check for local development to clear the caches
			responseCache.clear();
		}

		// create the consumers
		initRabbitAdmins();
		initResponseConsumers();
	}

	private void declareAndBindTransientQueueWithRoutingKey(
		final TaskType requestType,
		final String exchangeName,
		final String queueName,
		final String routingKey
	) {
		RabbitAdmin rabbitAdmin = rabbitAdmins.get(requestType.toString());
		if (rabbitAdmin == null) {
			rabbitAdmin = rabbitAdmins.get("default");
		}

		// Declare a direct exchange
		final DirectExchange exchange = new DirectExchange(exchangeName, config.getDurableQueues(), false);
		rabbitAdmin.declareExchange(exchange);

		// Declare a queue
		final Queue queue = new Queue(queueName, config.getDurableQueues(), false, true);
		rabbitAdmin.declareQueue(queue);

		// Bind the queue to the exchange with a routing key
		final Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
		rabbitAdmin.declareBinding(binding);
	}

	private void declareQueue(final TaskType requestType, final String queueName) {
		RabbitAdmin rabbitAdmin = rabbitAdmins.get(requestType.toString());
		if (rabbitAdmin == null) {
			rabbitAdmin = rabbitAdmins.get("default");
		}
		final Queue queue = new Queue(queueName, config.getDurableQueues(), false, false);
		rabbitAdmin.declareQueue(queue);
	}

	private void deleteQueue(final TaskType requestType, final String queueName) {
		RabbitAdmin rabbitAdmin = rabbitAdmins.get(requestType.toString());
		if (rabbitAdmin == null) {
			rabbitAdmin = rabbitAdmins.get("default");
		}
		rabbitAdmin.deleteQueue(queueName);
	}

	private void convertAndSendToDefaultExchange(final TaskType requestType, final String queue, final String msg) {
		RabbitAdmin rabbitAdmin = rabbitAdmins.get(requestType.toString());
		if (rabbitAdmin == null) {
			rabbitAdmin = rabbitAdmins.get("default");
		}
		rabbitAdmin.getRabbitTemplate().convertAndSend(queue, msg);
	}

	private void convertAndSend(
		final TaskType requestType,
		final String exchange,
		final String routingKey,
		final String msg
	) {
		RabbitAdmin rabbitAdmin = rabbitAdmins.get(requestType.toString());
		if (rabbitAdmin == null) {
			rabbitAdmin = rabbitAdmins.get("default");
		}

		log.info(
			"Sending message to exchange: {} with routing key: {} to rabbit instance: {}",
			exchange,
			routingKey,
			requestType
		);

		rabbitAdmin.getRabbitTemplate().convertAndSend(exchange, routingKey, msg);
	}

	public void addResponseHandler(final TaskResponseHandler handler) {
		responseHandlers.put(handler.getName(), handler);
	}

	public void cancelTask(final TaskType taskType, final UUID taskId) {
		// send the cancellation to the task runner
		final String msg = "";
		convertAndSend(taskType, TASK_RUNNER_CANCELLATION_EXCHANGE, taskId.toString(), msg);
	}

	// This is an anonymous queue, every instance the hmi-server will receive a
	// message. Any operation that must occur on _every_ instance of the hmi-server
	// should be triggered here.
	@RabbitListener(
		bindings = @QueueBinding(
			value = @org.springframework.amqp.rabbit.annotation.Queue(
				autoDelete = "true",
				exclusive = "false",
				durable = "${terarium.taskrunner.durable-queues}"
			),
			exchange = @Exchange(
				value = "${terarium.taskrunner.response-broadcast-exchange}",
				durable = "${terarium.taskrunner.durable-queues}",
				autoDelete = "false",
				type = ExchangeTypes.DIRECT
			),
			key = "${terarium.taskrunner.deploymentRoutingKey}"
		),
		concurrency = "1"
	)
	private void onTaskResponseAllInstanceReceive(final Message message) {
		try {
			final TaskResponse resp = decodeMessage(message, TaskResponse.class);
			if (resp == null) {
				return;
			}

			log.info("Received response status {} for task {}", resp.getStatus(), resp.getId());

			if (
				resp.getStatus() == TaskStatus.SUCCESS ||
				resp.getStatus() == TaskStatus.CANCELLED ||
				resp.getStatus() == TaskStatus.FAILED
			) {
				final CompletableTaskFuture future = futures.remove(resp.getId());
				if (future != null) {
					log.info("Found promise for task id: {}", resp.getId());
					// complete the future
					log.info("Completing future for task id {} with status {}", resp.getId(), resp.getStatus());
					future.complete(resp);
				} else {
					log.info("Did not find promise for task id: {}", resp.getId());
				}
			} else {
				final CompletableTaskFuture future = futures.get(resp.getId());
				if (future != null) {
					log.info("Updating latest response on task id: {} future to {}", resp.getId(), resp.getStatus());
					future.setLatest(resp);
				}
			}
		} catch (final Exception e) {
			log.error("Error processing task response message", e);
		}
	}

	private void initRabbitAdmins() {
		try {
			rabbitAdmins = new HashMap<>();
			rabbitAdminsByConnection = new HashMap<>();
			rabbitURIByKey = new HashMap<>();
			for (final Map.Entry<String, RabbitConfig> entry : taskRunnerConfiguration.getRabbitmq().entrySet()) {
				final String key = entry.getKey();
				final RabbitConfig rabbitConfig = entry.getValue();

				final URI rabbitAddress = new URI(rabbitConfig.getAddresses());

				final CachingConnectionFactory connectionFactory;
				final RabbitAdmin rabbitAdmin;
				if (!connectionFactories.containsKey(rabbitAddress.toString())) {
					connectionFactory = new CachingConnectionFactory();
					connectionFactory.setUri(rabbitAddress);
					connectionFactory.setUsername(rabbitConfig.getUsername());
					connectionFactory.setPassword(rabbitConfig.getPassword());

					rabbitAdmin = new RabbitAdmin(connectionFactory);

					connectionFactories.put(rabbitAddress.toString(), connectionFactory);
					rabbitAdminsByConnection.put(rabbitAddress.toString(), rabbitAdmin);
				} else {
					connectionFactory = connectionFactories.get(rabbitAddress.toString());
					rabbitAdmin = rabbitAdminsByConnection.get(rabbitAddress.toString());
				}

				log.info("Creating taskrunner rabbit admin for type: {}", key);
				rabbitAdmins.put(key, rabbitAdmin);
				rabbitURIByKey.put(key, rabbitAddress);
			}
		} catch (final Exception e) {
			throw new RuntimeException("Error initializing rabbit admins", e);
		}
	}

	private void initResponseConsumers() {
		for (final Map.Entry<String, RabbitAdmin> entry : rabbitAdmins.entrySet()) {
			final String type = entry.getKey();
			final RabbitAdmin rabbitAdmin = entry.getValue();

			final URI rabbitAddress = rabbitURIByKey.get(type);

			// only create a consumer per unique rabbit address
			if (!taskResponseConsumers.containsKey(rabbitAddress.toString())) {
				// This is a shared queue, messages will round robin between every instance of
				// the hmi-server. Any operation that must occur once and only once should be
				// triggered here.

				// NOTE: when running local and hitting an external rabbitmq instance, we need a
				// unique queue to ensure the local server also gets it.
				final String queueName = !isRunningLocalProfile()
					? TASK_RUNNER_RESPONSE_QUEUE
					: TASK_RUNNER_RESPONSE_QUEUE + "-local-" + UUID.randomUUID();

				// Declare a direct exchange
				final DirectExchange exchange = new DirectExchange(TASK_RUNNER_RESPONSE_EXCHANGE, IS_DURABLE_QUEUES, false);
				rabbitAdmin.declareExchange(exchange);

				// Declare a queue
				final Queue queue = new Queue(queueName, IS_DURABLE_QUEUES, false, true);
				rabbitAdmin.declareQueue(queue);

				// Bind the queue to the exchange with a routing key
				final Binding binding = BindingBuilder.bind(queue).to(exchange).with(instanceRoutingKey);
				rabbitAdmin.declareBinding(binding);

				final DirectMessageListenerContainer container = new DirectMessageListenerContainer(
					rabbitAdmin.getRabbitTemplate().getConnectionFactory()
				);

				container.setPrefetchCount(1);
				container.setQueueNames(queueName);
				container.setMessageListener(message -> {
					onTaskResponseOneInstanceReceives(message);
				});
				container.start();

				log.info("Consumer on queue {} started for rabbit admin: {}", queueName, rabbitAddress);
				taskResponseConsumers.put(rabbitAddress.toString(), container);
			}
		}
	}

	private void onTaskResponseOneInstanceReceives(final Message message) {
		try {
			TaskResponse resp = decodeMessage(message, TaskResponse.class);
			if (resp == null) {
				return;
			}

			log.info("Received response status {} for task {}", resp.getStatus(), resp.getId());

			try {
				// execute the handler
				if (responseHandlers.containsKey(resp.getScript())) {
					// handle the response
					resp = responseHandlers.get(resp.getScript()).handle(resp);
				}
			} catch (final Exception e) {
				log.error("Error occured while executing response handler for task {}", resp.getId(), e);

				// if the handler fails processing a success, convert it to a failure
				resp.setStatus(TaskStatus.FAILED);
				resp.setOutput(e.getMessage().getBytes());
			}

			if (resp.getStatus() == TaskStatus.SUCCESS && resp.isUseCache()) {
				try {
					// add to the response cache
					log.info(
						"Writing SUCCESS response for task id {} to cache under SHA: {} for script {}",
						resp.getId(),
						resp.getRequestSHA256(),
						resp.getScript()
					);
					responseCache.put(
						resp.getRequestSHA256(),
						resp,
						CACHE_TTL_SECONDS,
						TimeUnit.SECONDS,
						CACHE_MAX_IDLE_SECONDS,
						TimeUnit.SECONDS
					);
				} catch (final Exception e) {
					log.error("Failed to write response to response cache {}", resp.getId(), e);
				}
			}

			try {
				// create the notification event
				final NotificationEvent event = new NotificationEvent();
				event.setData(resp);

				log.info("Creating notification event under group id: {}", resp.getId());

				notificationService.createNotificationEvent(resp.getId(), event);
			} catch (final Exception e) {
				log.error("Failed to persist notification event for for task {}", resp.getId());
			}

			try {
				// send the client event
				final ClientEventType clientEventType = TaskNotificationEventTypes.getTypeFor(resp.getScript());
				log.info("Sending client event with type {} for task {} ", clientEventType.toString(), resp.getId());

				final ClientEvent<TaskResponse> clientEvent = ClientEvent.<TaskResponse>builder()
					.notificationGroupId(resp.getId())
					.projectId(resp.getProjectId())
					.type(clientEventType)
					.data(resp)
					.build();
				clientEventService.sendToUser(clientEvent, resp.getUserId());
			} catch (final Exception e) {
				log.error("Failed to send client event for for task {}", resp.getId(), e);
			}

			// if the task failed, log to stdout / stderr
			if (resp.getStatus() == TaskStatus.FAILED) {
				if (resp.getStdout() != null && resp.getStdout().length() > 0) {
					log.error("Task {} failed, logging stdout", resp.getId());
					System.out.print(resp.getStdout());
				} else {
					log.error("Task {} failed, stdout is empty, nothing to log", resp.getId());
				}
				if (resp.getStderr() != null && resp.getStderr().length() > 0) {
					log.error("Task {} failed, logging stdout", resp.getId());
					System.out.print(resp.getStderr());
				} else {
					log.error("Task {} failed, stderr is empty, nothing to log", resp.getId());
				}
			}

			// once the handler has executed and the response cache is up to date, we now
			// will broadcast to all hmi-server instances to dispatch the clientside events
			broadcastTaskResponseToAllInstances(resp);
		} catch (final Exception e) {
			log.error("Error processing task response message", e);
		}
	}

	private void processCachedTaskResponse(final TaskRequestWithId req, TaskResponse resp) {
		try {
			log.info("Creating notification group under id: {}", req.getId());

			// create the notification group for the task
			final NotificationGroup group = new NotificationGroup();
			group.setId(req.getId()); // use the task id
			group.setType(TaskNotificationEventTypes.getTypeFor(req.getScript()).toString());
			group.setUserId(req.getUserId());
			group.setProjectId(req.getProjectId());

			notificationService.createNotificationGroup(group);
		} catch (final Exception e) {
			log.error("Failed to create notification group for id: {}", req.getId(), e);
		}

		try {
			// execute the handler
			if (responseHandlers.containsKey(resp.getScript())) {
				// handle the response
				resp = responseHandlers.get(resp.getScript()).handle(resp);
			}
		} catch (final Exception e) {
			log.error("Error occured while executing response handler for task {}", resp.getId(), e);

			// if the handler fails processing a success, convert it to a failure
			resp.setStatus(TaskStatus.FAILED);
			resp.setOutput(e.getMessage().getBytes());
		}

		try {
			log.info("Creating notification group under id: {}", req.getId());

			// create the notification group for the task
			final NotificationGroup group = new NotificationGroup();
			group.setId(req.getId()); // use the task id
			group.setType(TaskNotificationEventTypes.getTypeFor(req.getScript()).toString());
			group.setUserId(req.getUserId());
			group.setProjectId(req.getProjectId());

			notificationService.createNotificationGroup(group);
			// create the notification event
			final NotificationEvent event = new NotificationEvent();
			event.setData(resp);

			log.info("Creating notification event under group id: {}", resp.getId());

			notificationService.createNotificationEvent(resp.getId(), event);
		} catch (final Exception e) {
			log.error("Failed to persist notification event for for task {}", resp.getId());
		}

		try {
			// send the client event
			final ClientEventType clientEventType = TaskNotificationEventTypes.getTypeFor(resp.getScript());
			log.info("Sending client event with type {} for task {} ", clientEventType.toString(), resp.getId());

			final ClientEvent<TaskResponse> clientEvent = ClientEvent.<TaskResponse>builder()
				.notificationGroupId(resp.getId())
				.projectId(resp.getProjectId())
				.type(clientEventType)
				.data(resp)
				.build();
			clientEventService.sendToUser(clientEvent, resp.getUserId());
		} catch (final Exception e) {
			log.error("Failed to send client event for for task {}", resp.getId(), e);
		}
	}

	private void broadcastTaskResponseToAllInstances(final TaskResponse resp) {
		try {
			final String jsonStr = objectMapper.writeValueAsString(resp);

			log.info("Broadcasting task response for task id {} and status {}", resp.getId(), resp.getStatus());

			rabbitAdmins
				.get("default")
				.getRabbitTemplate()
				.convertAndSend(TASK_RUNNER_RESPONSE_BROADCAST_EXCHANGE, deploymentRoutingKey, jsonStr);
		} catch (final JsonProcessingException e) {
			log.error("Error serializing handler error response", e);
		}
	}

	private static <T> T decodeMessage(final Message message, final Class<T> clazz) {
		final ObjectMapper mapper = new ObjectMapper();

		try {
			return mapper.readValue(message.getBody(), clazz);
		} catch (final Exception e) {
			try {
				mapper.readValue(message.getBody(), JsonNode.class);
				log.error("Unable to parse message as {}.", clazz.getName(), e);
				return null;
			} catch (final Exception e1) {
				log.error("Error decoding message as either {} or {}", clazz.getName(), JsonNode.class.getName(), e1);
				return null;
			}
		}
	}

	public TaskFuture runTaskAsync(final TaskRequest r) throws JsonProcessingException {
		if (r.getType() == null) {
			throw new RuntimeException("TaskRequest must have a type set");
		}
		if (r.getScript().isEmpty()) {
			throw new RuntimeException("TaskRequest must have a script set");
		}

		final TaskRequestWithId req = new TaskRequestWithId(r, instanceRoutingKey);

		// create sha256 hash of the request
		final String hash = req.getSHA256();

		if (req.isUseCache()) {
			try {
				log.info(
					"Checking for cached response under SHA: {} for {} for script: {}",
					hash,
					req.getId(),
					req.getScript()
				);
				// check if there is an existing response for the hash
				final TaskResponse resp = responseCache.get(hash);

				if (resp != null) {
					// a task id already exits for the SHA256, this means the request has already
					// been dispatched.
					log.info("Task response found in cache for SHA: {}", hash);

					// create and return a completed task future
					final CompletableTaskFuture future = new CompletableTaskFuture(req, resp);

					// process the cached response as if it were a new response
					processCachedTaskResponse(req, future.getLatest());

					return future;
				}
			} catch (final Exception e) {
				log.warn(
					"Failed to check for cached response under SHA: {} for {} for script: {}, re-sending request",
					hash,
					req.getId(),
					req.getScript()
				);
				// remove the bad entry
				responseCache.remove(hash);
			}
		}

		// no cache entry for task, send a new one

		try {
			log.info("Creating notification group under id: {}", req.getId());

			// create the notification group for the task
			final NotificationGroup group = new NotificationGroup();
			group.setId(req.getId()); // use the task id
			group.setType(TaskNotificationEventTypes.getTypeFor(req.getScript()).toString());
			group.setUserId(req.getUserId());
			group.setProjectId(req.getProjectId());

			notificationService.createNotificationGroup(group);
		} catch (final Exception e) {
			log.error("Failed to create notification group for id: {}", req.getId(), e);
		}

		// now send request
		final String requestQueue = String.format("%s-%s", TASK_RUNNER_REQUEST_QUEUE, req.getType().toString());

		log.info("Readying task: {} with SHA: {} to send on queue: {}", req.getId(), hash, req.getType());

		// ensure the request queue exists
		declareQueue(req.getType(), requestQueue);

		// create the cancellation queue _BEFORE_ sending the request, because a
		// cancellation can be send before the request is consumed on the other end if
		// there is contention. We need this queue to exist to hold the message.
		final String queueName = req.getId().toString();
		final String cancellationRoutingKey = req.getId().toString();
		declareAndBindTransientQueueWithRoutingKey(
			req.getType(),
			TASK_RUNNER_CANCELLATION_EXCHANGE,
			queueName,
			cancellationRoutingKey
		);

		try {
			// send the request to the task runner
			log.info("Dispatching request for task id: {}", req.getId());
			final String jsonStr = objectMapper.writeValueAsString(req);
			convertAndSendToDefaultExchange(r.getType(), requestQueue, jsonStr);

			// publish the queued task response
			final TaskResponse queuedResponse = req.createResponse(TaskStatus.QUEUED, "", "");
			final String respJsonStr = objectMapper.writeValueAsString(queuedResponse);
			convertAndSend(r.getType(), TASK_RUNNER_RESPONSE_EXCHANGE, req.getRoutingKey(), respJsonStr);

			// create and return the future
			final CompletableTaskFuture future = new CompletableTaskFuture(req.getId());
			future.setLatest(queuedResponse);
			log.info("Adding future for task id: {} to the futures map", req.getId());
			futures.put(req.getId(), future);
			return future;
		} catch (final Exception e) {
			// ensure cancellation queue is removed on failure
			deleteQueue(req.getType(), queueName);
			throw e;
		}
	}

	public TaskResponse runTaskSync(final TaskRequest req)
		throws JsonProcessingException, TimeoutException, InterruptedException, ExecutionException {
		// send the request
		final TaskFuture future = runTaskAsync(req);

		try {
			// wait for the response
			log.info("Waiting for response for task id: {}", future.getId());
			final TaskResponse resp = future.getFinal(req.getTimeoutMinutes(), TimeUnit.MINUTES);
			if (resp.getStatus() == TaskStatus.CANCELLED) {
				throw new InterruptedException("Task was cancelled");
			}
			if (resp.getStatus() == TaskStatus.FAILED) {
				throw new RuntimeException("Task failed: " + new String(resp.getOutput()));
			}
			if (resp.getStatus() != TaskStatus.SUCCESS) {
				throw new RuntimeException("Task did not complete successfully");
			}
			log.info("Future completed for task: {}", future.getId());
			return resp;
		} catch (final TimeoutException e) {
			// remove the future
			futures.remove(future.getId());
			try {
				// if the task is still running, or hasn't started yet, lets cancel it
				cancelTask(req.getType(), future.getId());
			} catch (final Exception ee) {
				log.warn("Failed to cancel task: {}", future.getId(), ee);
			}
			throw new TimeoutException(
				"Task " + future.getId().toString() + " did not complete within " + req.getTimeoutMinutes() + " minutes"
			);
		}
	}

	public TaskResponse runTask(final TaskMode mode, final TaskRequest req)
		throws JsonProcessingException, TimeoutException, InterruptedException, ExecutionException {
		if (req instanceof CompoundTask) {
			return runTask(mode, (CompoundTask) req);
		}

		if (mode == TaskMode.SYNC) {
			return runTaskSync(req);
		} else if (mode == TaskMode.ASYNC) {
			return runTaskAsync(req).getLatest();
		} else {
			throw new IllegalArgumentException("Invalid task mode: " + mode);
		}
	}

	/**
	 * Runs a compound task, executing the primary task synchronously and the
	 * secondary tasks
	 * in the specified mode (synchronous or asynchronous).
	 *
	 * @param mode The mode in which to run the secondary tasks (SYNC or ASYNC).
	 * @param req  The compound task containing the primary and secondary tasks.
	 * @return The response of the primary task.
	 * @throws JsonProcessingException If there is an error processing JSON.
	 * @throws TimeoutException        If the task times out.
	 * @throws InterruptedException    If the task is interrupted.
	 * @throws ExecutionException      If there is an error during task execution.
	 */
	public TaskResponse runTask(final TaskMode mode, final CompoundTask req)
		throws JsonProcessingException, TimeoutException, InterruptedException, ExecutionException {
		final TaskResponse response = runTask(TaskMode.SYNC, req.getPrimaryTask());

		for (final TaskRequest secondaryTask : req.getSecondaryTasks()) {
			runTask(mode, secondaryTask);
		}
		return response;
	}
}
