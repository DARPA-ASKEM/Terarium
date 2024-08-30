package software.uncharted.terarium.hmiserver.controller.dataservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import software.uncharted.terarium.hmiserver.models.dataservice.ChartAnnotation;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.ChartAnnotationService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/chart-annotations")
@RestController
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ChartAnnotationController {

	final ChartAnnotationService chartAnnotationService;

	final ProjectAssetService projectAssetService;

	final ProjectService projectService;

	final CurrentUserService currentUserService;

	private static class SearchRequestBody {

		public UUID nodeId;
	}

	@PostMapping("/search")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a list of chart annotations by provided node ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "chart annotations found.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ChartAnnotation.class)
				)
			),
			@ApiResponse(responseCode = "204", description = "There was no chart annotation found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the chart annotations from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<List<ChartAnnotation>> getChartAnnotationsByNodeId(
		@RequestParam(name = "project-id", required = false) final UUID projectId,
		@RequestBody final SearchRequestBody body
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final List<ChartAnnotation> chartAnnotations = chartAnnotationService.getAnnotationsByNodeId(
			body.nodeId,
			permission
		);

		return ResponseEntity.ok(chartAnnotations);
	}

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new annotation")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Annotation created.",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ChartAnnotation.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue creating the chart annotation",
				content = @Content
			)
		}
	)
	public ResponseEntity<ChartAnnotation> createChartAnnotation(
		@RequestBody final ChartAnnotation item,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(
				chartAnnotationService.createAsset(item, projectId, permission)
			);
		} catch (final IOException e) {
			final String error = "Unable to create chart annotation";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Delete a chart annotation by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Delete chart annotation",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue deleting the chart annotation",
				content = @Content
			)
		}
	)
	public ResponseEntity<ResponseDeleted> deleteChartAnnotation(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			chartAnnotationService.deleteAsset(id, projectId, permission);
			return ResponseEntity.ok(new ResponseDeleted("ChartAnnotation", id));
		} catch (final Exception e) {
			final String error = String.format("Failed to delete chart annotation %s", id);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}
}
