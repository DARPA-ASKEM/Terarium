package software.uncharted.terarium.hmiserver.controller.funman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.funman.FunmanPostQueriesRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.proxies.funman.FunmanProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService.TaskMode;
import software.uncharted.terarium.hmiserver.service.tasks.ValidateModelConfigHandler;


import java.util.UUID;

@RestController
@RequestMapping("/funman/queries")
@RequiredArgsConstructor
@Slf4j
public class FunmanController {

	private final ObjectMapper objectMapper;
	private final TaskService taskService;
	private final CurrentUserService currentUserService;

	private final ValidateModelConfigHandler validateModelConfigHandler;

	@PostConstruct
	void init() {
		taskService.addResponseHandler(validateModelConfigHandler);
	}


	@PostMapping("/test")
	@Secured(Roles.USER)
	@Operation(summary = "Dispatch a model configuration validation task")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Dispatched successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema =
												@io.swagger.v3.oas.annotations.media.Schema(
														implementation = TaskResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description = "The provided document text is too long",
						content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "There was an issue dispatching the request",
						content = @Content)
			})
	public ResponseEntity<TaskResponse> createValidationRequest(@RequestBody final JsonNode input) {

		try {
			final TaskRequest taskRequest = new TaskRequest();
			taskRequest.setType(TaskType.FUNMAN);
			taskRequest.setScript(ValidateModelConfigHandler.NAME);
			taskRequest.setUserId(currentUserService.get().getId());
			taskRequest.setInput(objectMapper.writeValueAsBytes(input));

			// TODO: create Simulation for tracking
			final UUID uuid = UUID.randomUUID();
			final ValidateModelConfigHandler.Properties props = new ValidateModelConfigHandler.Properties();
			props.setSimulationId(uuid);
			taskRequest.setAdditionalProperties(props);

			return ResponseEntity.ok().body(taskService.runTask(TaskMode.ASYNC, taskRequest));
		} catch (final ResponseStatusException e) {
			throw e;
		} catch (final Exception e) {
			final String error = "Unable to dispatch task request";
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	// Depreacated
	private final FunmanProxy funmanProxy;

	@GetMapping("/{queryId}/halt")
	@Secured(Roles.USER)
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Query halted"),
			})
	public ResponseEntity<JsonNode> halt(@PathVariable final String queryId) {
		try {
			final ResponseEntity<JsonNode> response = funmanProxy.halt(queryId);
			return ResponseEntity.ok(response.getBody());
		} catch (final FeignException e) {
			final String error = "Error halting";
			final int status = e.status() >= 400 ? e.status() : 500;
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.valueOf(status), error);
		}
	}

	@GetMapping("/{queryId}")
	@Secured(Roles.USER)
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Query found"),
			})
	public ResponseEntity<JsonNode> getQueries(@PathVariable final String queryId) {
		try {
			final ResponseEntity<JsonNode> response = funmanProxy.getQueries(queryId);
			return ResponseEntity.ok(response.getBody());
		} catch (final FeignException e) {
			final String error = "Error getting query";
			final int status = e.status() >= 400 ? e.status() : 500;
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.valueOf(status), error);
		}
	}

	@PostMapping
	@Secured(Roles.USER)
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Query posted"),
			})
	public ResponseEntity<JsonNode> postQueries(@RequestBody final FunmanPostQueriesRequest requestBody) {
		try {
			final ResponseEntity<JsonNode> response = funmanProxy.postQueries(requestBody);
			return ResponseEntity.ok(response.getBody());
		} catch (final FeignException e) {
			final String error = "Error posting query";
			final int status = e.status() >= 400 ? e.status() : 500;
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.valueOf(status), error);
		}
	}
}
