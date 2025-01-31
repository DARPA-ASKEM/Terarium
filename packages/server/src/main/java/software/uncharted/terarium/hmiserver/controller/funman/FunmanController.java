package software.uncharted.terarium.hmiserver.controller.funman;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationType;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.SimulationService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService.TaskMode;
import software.uncharted.terarium.hmiserver.service.tasks.ValidateModelConfigHandler;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RestController
@RequestMapping("/funman/queries")
@RequiredArgsConstructor
@Slf4j
public class FunmanController {

	private final ObjectMapper objectMapper;
	private final TaskService taskService;
	private final CurrentUserService currentUserService;
	private final ProjectService projectService;

	private final ValidateModelConfigHandler validateModelConfigHandler;
	private final SimulationService simulationService;
	private final Messages messages;

	@PostConstruct
	void init() {
		taskService.addResponseHandler(validateModelConfigHandler);
	}

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Dispatch a model configuration validation task")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dispatched successfully",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Simulation.class)
				)
			),
			@ApiResponse(responseCode = "400", description = "Invalid input or bad request", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue dispatching the request", content = @Content)
		}
	)
	public ResponseEntity<Simulation> createValidationRequest(
		@RequestBody final JsonNode input,
		@RequestParam(name = "model-id", required = true) final UUID modelId,
		@RequestParam(name = "new-model-config-name", required = true) final String newModelConfigName,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final TaskRequest taskRequest = new TaskRequest();
		taskRequest.setTimeoutMinutes(45);
		taskRequest.setType(TaskType.FUNMAN);
		taskRequest.setScript(ValidateModelConfigHandler.NAME);
		taskRequest.setUserId(currentUserService.get().getId());

		try {
			taskRequest.setInput(objectMapper.writeValueAsBytes(input));
		} catch (final Exception e) {
			log.error("Unable to serialize input", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.write"));
		}

		final Simulation sim = new Simulation();
		sim.setType(SimulationType.VALIDATION);
		sim.setStatus(ProgressState.RUNNING);
		sim.setDescription("funman model configuration validation");
		sim.setExecutionPayload(objectMapper.convertValue(input, JsonNode.class));

		// Create new simulation object to proxy the funman validation process
		final Simulation newSimulation;
		try {
			newSimulation = simulationService.createAsset(sim, projectId, permission);
		} catch (final Exception e) {
			log.error("An error occurred while trying to create a simulation asset.", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		final ValidateModelConfigHandler.Properties props = new ValidateModelConfigHandler.Properties();
		props.setProjectId(projectId);
		props.setModelId(modelId);
		props.setNewModelConfigName(newModelConfigName);
		props.setSimulationId(newSimulation.getId());
		taskRequest.setAdditionalProperties(props);

		try {
			taskService.runTask(TaskMode.ASYNC, taskRequest);
		} catch (final JsonProcessingException e) {
			log.error("Unable to serialize input", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.funman.json-processing"));
		} catch (final TimeoutException e) {
			log.warn("Timeout while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("task.funman.timeout"));
		} catch (final InterruptedException e) {
			log.warn("Interrupted while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, messages.get("task.funman.interrupted"));
		} catch (final ExecutionException e) {
			log.error("Error while waiting for task response", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("task.funman.execution-failure")
			);
		}

		return ResponseEntity.ok(newSimulation);
	}

	@DeleteMapping("/{task-id}")
	@Secured(Roles.USER)
	@Operation(summary = "Cancel a model configuration validation task")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dispatched cancellation successfully",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Void.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue dispatching the cancellation",
				content = @Content
			)
		}
	)
	public ResponseEntity<Void> cancelTask(
		@PathVariable("task-id") final UUID taskId,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		taskService.cancelTask(TaskType.FUNMAN, taskId);
		return ResponseEntity.ok().build();
	}
}
