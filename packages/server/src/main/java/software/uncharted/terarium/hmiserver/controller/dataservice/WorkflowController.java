package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.ClientEvent;
import software.uncharted.terarium.hmiserver.models.ClientEventType;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Contributor;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.InputPort;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.OutputPort;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.Workflow;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowAnnotation;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowEdge;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowNode;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowPositions;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.ClientEventService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectPermissionsService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.WorkflowService;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacProject;

@RequestMapping("/workflows")
@RestController
@Slf4j
@RequiredArgsConstructor
public class WorkflowController {

	final WorkflowService workflowService;

	final ProjectAssetService projectAssetService;

	final ProjectService projectService;

	final CurrentUserService currentUserService;

	final ClientEventService clientEventService;

	final ProjectPermissionsService projectPermissionsService;

	final ReBACService reBACService;

	final Messages messages;

	private void broadCastWorkflowChange(Workflow workflow, UUID projectId) {
		final String userId = currentUserService.get().getId();
		final ClientEvent<Workflow> event = ClientEvent.<Workflow>builder()
			.type(ClientEventType.WORKFLOW_UPDATE)
			.data(workflow)
			.userId(userId)
			.build();

		try {
			clientEventService.sendToAllUsers(event);
			// https://github.com/DARPA-ASKEM/terarium/issues/6008
			// final RebacProject rebacProject = new RebacProject(projectId, reBACService);
			// if (rebacProject.isPublic()) {
			// 	clientEventService.sendToAllUsers(event);
			// } else {
			// 	final List<String> userIds = projectPermissionsService
			// 		.getReaders(rebacProject)
			// 		.stream()
			// 		.map(Contributor::getUserId)
			// 		.toList();
			// 	clientEventService.sendToUsers(event, userIds);
			// }
		} catch (final Exception e) {
			log.error("Unable to notify users of update to workflow", e);
		}
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets workflow by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow found.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "There was no workflow found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the workflow from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<Workflow> getWorkflow(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		return workflow.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new workflow")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Workflow created.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> createWorkflow(
		@RequestBody final Workflow workflow,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(
				workflowService.createAsset(workflow, projectId, permission)
			);
		} catch (final IOException e) {
			final String error = "Unable to create workflow";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a workflow")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> updateWorkflow(
		@PathVariable("id") final UUID id,
		@RequestBody final Workflow workflow,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final String userId = currentUserService.get().getId();
		final Schema.Permission permission = projectService.checkPermissionCanWrite(userId, projectId);

		workflow.setId(id);
		final Optional<Workflow> updated;

		try {
			updated = workflowService.updateAsset(workflow, projectId, permission);
		} catch (final IOException e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}

		if (updated == null || updated.isEmpty()) {
			log.error("Updated workflow was empty");
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Delete a workflow by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Delete workflow",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class)
					)
				}
			),
			@ApiResponse(responseCode = "500", description = "There was an issue deleting the workflow", content = @Content)
		}
	)
	public ResponseEntity<ResponseDeleted> deleteWorkflow(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final ClientEvent<Void> event = ClientEvent.<Void>builder().type(ClientEventType.WORKFLOW_UPDATE).build();

		final RebacProject rebacProject = new RebacProject(projectId, reBACService);

		try {
			workflowService.deleteAsset(id, projectId, permission);
		} catch (final Exception e) {
			final String error = String.format("Failed to delete workflow %s", id);
			log.error(error, e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}

		try {
			clientEventService.sendToAllUsers(event);
			// https://github.com/DARPA-ASKEM/terarium/issues/6008
			// if (rebacProject.isPublic()) {
			// 	clientEventService.sendToAllUsers(event);
			// } else {
			// 	final List<String> userIds = projectPermissionsService
			// 		.getReaders(rebacProject)
			// 		.stream()
			// 		.map(Contributor::getUserId)
			// 		.toList();
			// 	clientEventService.sendToUsers(event, userIds);
			// }
		} catch (final Exception e) {
			log.error("Unable to notify users of deleted  workflow", e);
			// No response status exception here because the workflow was deleted successfully, and it's just the update that's failed.
		}

		return ResponseEntity.ok(new ResponseDeleted("Workflow", id));
	}

	@PostMapping("/{id}/node/{nodeId}/selected-output/{outputId}")
	@Secured(Roles.USER)
	@Operation(summary = "Select an operator output to use")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> selectOutput(
		@PathVariable("id") final UUID id,
		@PathVariable("nodeId") final UUID nodeId,
		@PathVariable("outputId") final UUID outputId,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.selectOutput(workflow.get(), nodeId, outputId);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
		broadCastWorkflowChange(updated.get(), projectId);

		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/update-position")
	@Secured(Roles.USER)
	@Operation(summary = "Update node and edge positions")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> updatePositions(
		@PathVariable("id") final UUID id,
		@RequestBody final WorkflowPositions payload,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.updatePositions(workflow.get(), payload);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.create-output")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/update-state")
	@Secured(Roles.USER)
	@Operation(summary = "Update operator states")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> updateNodeState(
		@PathVariable("id") final UUID id,
		@RequestBody final Map<UUID, JsonNode> payload,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.updateNodeState(workflow.get(), payload);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.create-output")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/update-status")
	@Secured(Roles.USER)
	@Operation(summary = "Update operator statuses")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> updateNodeStatus(
		@PathVariable("id") final UUID id,
		@RequestBody final Map<UUID, String> payload,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.updateNodeStatus(workflow.get(), payload);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.create-output")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@Data
	static class AppendOutputPayload {

		private OutputPort output;
		private JsonNode nodeState;
	}

	@PostMapping("/{id}/node/{nodeId}/input")
	@Secured(Roles.USER)
	@Operation(summary = "Append an input port to an operator node")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> appendInput(
		@PathVariable("id") final UUID id,
		@PathVariable("nodeId") final UUID nodeId,
		@RequestBody final InputPort payload,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.appendInput(workflow.get(), nodeId, payload);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.create-output")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/node/{nodeId}/output")
	@Secured(Roles.USER)
	@Operation(summary = "Append an output to an operator node")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> appendOutput(
		@PathVariable("id") final UUID id,
		@PathVariable("nodeId") final UUID nodeId,
		@RequestBody final AppendOutputPayload payload,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.appendOutput(workflow.get(), nodeId, payload.getOutput(), payload.getNodeState());
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.create-output")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/node")
	@Secured(Roles.USER)
	@Operation(summary = "Add a node to a workflow")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> addNode(
		@PathVariable("id") final UUID id,
		@RequestBody final WorkflowNode node,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.addNode(workflow.get(), node);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.add-node")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/remove-nodes")
	@Secured(Roles.USER)
	@Operation(summary = "Remove a node from a workflow")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> removeNodes(
		@PathVariable("id") final UUID id,
		@RequestBody final List<UUID> nodes,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.removeNodes(workflow.get(), nodes);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.remove-node")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/edge")
	@Secured(Roles.USER)
	@Operation(summary = "Add an edge to a workflow")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> addEdge(
		@PathVariable("id") final UUID id,
		@RequestBody final WorkflowEdge edge,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.addEdge(workflow.get(), edge);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.add-edge")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/remove-edges")
	@Secured(Roles.USER)
	@Operation(summary = "Remove an edge from a workflow")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> removeEdges(
		@PathVariable("id") final UUID id,
		@RequestBody final List<UUID> edges,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.removeEdges(workflow.get(), edges);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.remove-edge")
			);
		}

		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/annotation")
	@Secured(Roles.USER)
	@Operation(summary = "Add or update a workflow annotation")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> addOrUpdateAnnotation(
		@PathVariable("id") final UUID id,
		@RequestBody final WorkflowAnnotation annotation,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.addOrUpdateAnnotation(workflow.get(), annotation);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.annotation")
			);
		}
		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}/annotation/{annotationId}")
	@Secured(Roles.USER)
	@Operation(summary = "Remove a workflow annotation")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Workflow updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Workflow.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Workflow could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the workflow", content = @Content)
		}
	)
	public ResponseEntity<Workflow> removeAnnotation(
		@PathVariable("id") final UUID id,
		@PathVariable("annotationId") final UUID annotationId,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Workflow> workflow = workflowService.getAsset(id, permission);
		final Optional<Workflow> updated;
		if (workflow.isPresent() == false) {
			return ResponseEntity.notFound().build();
		}

		try {
			workflowService.removeAnnotation(workflow.get(), annotationId);
			updated = workflowService.updateAsset(workflow.get(), projectId, permission);
		} catch (final Exception e) {
			log.error("Unable to update workflow", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("workflow.update.annotation")
			);
		}
		broadCastWorkflowChange(updated.get(), projectId);
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}
}
