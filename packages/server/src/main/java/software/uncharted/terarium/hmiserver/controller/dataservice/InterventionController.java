package software.uncharted.terarium.hmiserver.controller.dataservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
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
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.simulationservice.interventions.InterventionPolicy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.InterventionService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/interventions")
@RestController
@Slf4j
@RequiredArgsConstructor
public class InterventionController {

	final InterventionService interventionService;

	final ProjectAssetService projectAssetService;

	final ProjectService projectService;

	final CurrentUserService currentUserService;

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets intervention by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Intervention found.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = InterventionPolicy.class)
				)
			),
			@ApiResponse(responseCode = "204", description = "There was no intervention found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the intervention from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<InterventionPolicy> getIntervention(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<InterventionPolicy> intervention = interventionService.getAsset(id, permission);
		return intervention.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new intervention")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Intervention created.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = InterventionPolicy.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue creating the intervention",
				content = @Content
			)
		}
	)
	public ResponseEntity<InterventionPolicy> createIntervention(
		@RequestBody final InterventionPolicy item,
		@RequestParam(name = "skip-check", required = false, defaultValue = "false") final boolean skipCheck,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			if (skipCheck != true) {
				item.validateInterventionPolicy();
			}
		} catch (final Exception e) {
			final String error = "Failed to validate intervention";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
		try {
			final InterventionPolicy policy = interventionService.createAsset(item, projectId, permission);
			return ResponseEntity.status(HttpStatus.CREATED).body(policy);
		} catch (final IOException e) {
			final String error = "Unable to create intervention";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a intervention")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Intervention updated.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = InterventionPolicy.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Intervention could not be found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue updating the intervention",
				content = @Content
			)
		}
	)
	public ResponseEntity<InterventionPolicy> updateIntervention(
		@PathVariable("id") final UUID id,
		@RequestBody final InterventionPolicy intervention,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);
		try {
			intervention.setId(id);
			final Optional<InterventionPolicy> updated = interventionService.updateAsset(intervention, projectId, permission);
			return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final IOException e) {
			final String error = "Unable to update intervention";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		} catch (final IllegalArgumentException e) {
			final String error = "ID does not match Intervention object ID";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
		}
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Delete a intervention by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Delete intervention",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue deleting the intervention",
				content = @Content
			)
		}
	)
	public ResponseEntity<ResponseDeleted> deleteIntervention(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			interventionService.deleteAsset(id, projectId, permission);
			return ResponseEntity.ok(new ResponseDeleted("Intervention", id));
		} catch (final Exception e) {
			final String error = String.format("Failed to delete intervention %s", id);
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}
}
