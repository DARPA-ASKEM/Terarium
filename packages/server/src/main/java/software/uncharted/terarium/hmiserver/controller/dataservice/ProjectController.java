package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.ClientEventType;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Contributor;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectExport;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionRelationships;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.ClientEventService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.TerariumAssetCloneService;
import software.uncharted.terarium.hmiserver.service.UserService;
import software.uncharted.terarium.hmiserver.service.data.ArtifactService;
import software.uncharted.terarium.hmiserver.service.data.CodeService;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ITerariumAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectPermissionsService;
import software.uncharted.terarium.hmiserver.service.data.ProjectSearchService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.TerariumAssetServices;
import software.uncharted.terarium.hmiserver.service.data.WorkflowService;
import software.uncharted.terarium.hmiserver.service.notification.NotificationGroupInstance;
import software.uncharted.terarium.hmiserver.service.notification.NotificationService;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacGroup;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacPermissionRelationship;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacProject;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacUser;

@RequestMapping("/projects")
@RestController
@Slf4j
@RequiredArgsConstructor
@Tags(@Tag(name = "Projects", description = "Project related operations"))
public class ProjectController {

	static final String WELCOME_MESSAGE = "";
	final Messages messages;
	final ArtifactService artifactService;
	final ModelService modelService;
	final CodeService codeService;
	final CurrentUserService currentUserService;
	final DatasetService datasetService;
	final DocumentAssetService documentAssetService;
	final ProjectAssetService projectAssetService;
	final ProjectService projectService;
	final ReBACService reBACService;
	final TerariumAssetServices terariumAssetServices;
	final TerariumAssetCloneService cloneService;
	final UserService userService;
	final WorkflowService workflowService;
	final ObjectMapper objectMapper;
	final ProjectPermissionsService projectPermissionsService;
	final ProjectSearchService projectSearchService;

	final ClientEventService clientEventService;
	final NotificationService notificationService;
	private ExecutorService executor;

	@Data
	private static class Properties {

		private final UUID projectId;
	}

	@Value("${terarium.extractionService.poolSize:10}")
	private int POOL_SIZE;

	@PostConstruct
	void init() {
		executor = Executors.newFixedThreadPool(POOL_SIZE);
	}

	// --------------------------------------------------------------------------
	// Basic Project Operations
	// --------------------------------------------------------------------------

	@GetMapping
	@Secured(Roles.USER)
	@Operation(summary = "Gets all projects (which are visible to this user)")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Projects found.",
				content = @Content(
					array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class))
				)
			),
			@ApiResponse(
				responseCode = "204",
				description = "There are no errors, but also no projects for this user",
				content = @Content
			),
			@ApiResponse(responseCode = "500", description = "There was an issue with rebac permissions", content = @Content),
			@ApiResponse(
				responseCode = "503",
				description = "There was an issue communicating with back-end services",
				content = @Content
			)
		}
	)
	public ResponseEntity<List<Project>> getProjects(
		@RequestParam(name = "include-inactive", defaultValue = "false") final Boolean includeInactive
	) {
		final RebacUser rebacUser = new RebacUser(currentUserService.get().getId(), reBACService);

		List<UUID> projectIds = null;
		try {
			projectIds = rebacUser.lookupProjects();
		} catch (final Exception e) {
			log.error("Error retrieving projects from spicedb", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}

		if (projectIds == null || projectIds.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		// Get projects from the project repository associated with the list of ids.
		// Filter the list of projects to only include active projects.
		final List<Project> projects;
		try {
			projects = includeInactive
				? projectService.getProjects(projectIds)
				: projectService.getActiveProjects(projectIds);
		} catch (final Exception e) {
			log.error("Error retrieving projects from postgres db", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		if (projects.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		projects.forEach(project -> {
			final RebacProject rebacProject = new RebacProject(project.getId(), reBACService);

			// Set the user permission for the project. If we are unable to get the user
			// permission, we remove the project.
			try {
				project.setUserPermission(rebacUser.getPermissionFor(rebacProject));
			} catch (final Exception e) {
				log.error(
					"Failed to get user permissions from spicedb for project {}... Removing Project from list.",
					project.getId(),
					e
				);
				projects.remove(project);
				return;
			}

			// Set the public status for the project. If we are unable to get the public
			// status, we default to private.
			try {
				project.setPublicProject(rebacProject.isPublic());
			} catch (final Exception e) {
				log.error("Failed to get project {} public status from spicedb... Defaulting to private.", project.getId(), e);
				project.setPublicProject(false);
			}

			// Set the contributors for the project. If we are unable to get the
			// contributors, we default to an empty
			// list.
			List<Contributor> contributors = null;
			try {
				contributors = projectPermissionsService.getContributors(rebacProject);
				project
					.getMetadata()
					.put("contributor-count", Integer.toString(contributors == null ? 0 : contributors.size()));
			} catch (final Exception e) {
				log.error("Failed to get project contributors from spicedb for project {}", project.getId(), e);
			}

			// Set the author name for the project. If we are unable to get the author name,
			// we don't set a value.
			try {
				if (project.getUserId() != null) {
					final String authorName = userService.getById(project.getUserId()).getName();
					if (authorName != null) {
						project.setUserName(authorName);
					}
				}
			} catch (final Exception e) {
				log.error("Failed to get project author name from postgres db for project {}", project.getId(), e);
			}
		});

		return ResponseEntity.ok(projects);
	}

	/**
	 * Gets the project by id
	 *
	 * @param id the UUID for a project
	 * @return The project wrapped in a response entity, a 404 if missing or a 500
	 *         if there is a rebac permissions
	 *         issue.
	 */
	@Operation(summary = "Gets a project by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Project found.",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "User does not have permission to view this project",
				content = @Content
			),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue with rebac permissions", content = @Content),
			@ApiResponse(responseCode = "503", description = "Error communicating with back-end services", content = @Content)
		}
	)
	@GetMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Project> getProject(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanRead(currentUserService.get().getId(), id);
		final RebacUser rebacUser = new RebacUser(currentUserService.get().getId(), reBACService);
		final RebacProject rebacProject = new RebacProject(id, reBACService);

		final Optional<Project> project = projectService.getProject(id);

		if (!project.isPresent()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("projects.not-found"));
		}

		try {
			final List<String> authors = new ArrayList<>();
			final List<Contributor> contributors = projectPermissionsService.getContributors(rebacProject);
			for (final Contributor contributor : contributors) {
				authors.add(contributor.getName());
			}

			project.get().setPublicProject(rebacProject.isPublic());
			project.get().setUserPermission(rebacUser.getPermissionFor(rebacProject));
			project.get().setAuthors(authors);
		} catch (final Exception e) {
			log.error("Failed to get project permissions from spicedb", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("projects.unable-to-get-permissions")
			);
		}

		if (project.get().getUserId() != null) {
			final String authorName = userService.getById(project.get().getUserId()).getName();
			if (authorName != null) {
				project.get().setUserName(authorName);
			}
		}

		return ResponseEntity.ok(project.get());
	}

	@Operation(summary = "Soft deletes project by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Project marked for deletion",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have delete privileges to this project",
				content = @Content
			),
			@ApiResponse(
				responseCode = "500",
				description = "An error occurred verifying permissions or deleting the project",
				content = @Content
			)
		}
	)
	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<ResponseDeleted> deleteProject(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), id);

		try {
			final boolean deleted = projectService.delete(id);
			if (deleted) {
				return ResponseEntity.ok(new ResponseDeleted("project", id));
			}
		} catch (final Exception e) {
			log.error("Error deleting project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		log.error("Failed to delete project");
		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("projects.unable-to-delete"));
	}

	@Operation(summary = "Creates a new project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Project created",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "400",
				description = "The provided information is not valid to create a project",
				content = @Content
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was a rebac issue when creating the project",
				content = @Content
			),
			@ApiResponse(
				responseCode = "503",
				description = "There was an issue communicating with the data store or rebac service",
				content = @Content
			)
		}
	)
	@PostMapping
	@Secured(Roles.USER)
	public ResponseEntity<Project> createProject(
		@RequestParam("name") final String name,
		@RequestParam("description") final String description,
		@RequestParam(name = "thumbnail", defaultValue = "default") final String thumbnail
	) {
		if (name == null || name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("projects.name-required"));
		}

		final String userId = currentUserService.get().getId();

		Project project = (Project) new Project()
			.setOverviewContent(WELCOME_MESSAGE.getBytes())
			.setThumbnail(thumbnail)
			.setUserId(userId)
			.setName(name)
			.setDescription(description);

		try {
			project = projectService.createProject(project);
		} catch (final Exception e) {
			log.error("Error creating project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		try {
			final RebacProject rebacProject = new RebacProject(project.getId(), reBACService);
			final RebacGroup rebacAskemAdminGroup = new RebacGroup(ReBACService.ASKEM_ADMIN_GROUP_ID, reBACService);
			final RebacUser rebacUser = new RebacUser(userId, reBACService);

			rebacUser.createCreatorRelationship(rebacProject);
			rebacAskemAdminGroup.createWriterRelationship(rebacProject);
		} catch (final Exception e) {
			log.error("Error setting user's permissions for project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		} catch (final RelationshipAlreadyExistsException e) {
			log.error("Error the user is already the creator of this project", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("rebac.relationship-already-exists")
			);
		}

		return ResponseEntity.status(HttpStatus.CREATED).body(project);
	}

	@Operation(summary = "Updates a project by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Project marked for deletion",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have update privileges to this project",
				content = @Content
			),
			@ApiResponse(responseCode = "404", description = "Project could not be found", content = @Content),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with either the postgres or spicedb" + " databases",
				content = @Content
			)
		}
	)
	@PutMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Project> updateProject(@PathVariable("id") final UUID id, @RequestBody final Project project) {
		projectService.checkPermissionCanWrite(currentUserService.get().getId(), id);

		final Optional<Project> originalProject = projectService.getProject(id);
		if (originalProject.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("projects.not-found"));
		}

		project.setId(id);
		final Optional<Project> updatedProject;
		try {
			updatedProject = projectService.updateProject(project);
		} catch (final Exception e) {
			log.error("Error updating project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		if (!updatedProject.isPresent()) {
			log.error("Updated Project is NOT present");
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("projects.unable-to-update"));
		}

		if (originalProject.get().getPublicAsset() != updatedProject.get().getPublicAsset()) {
			try {
				projectAssetService.togglePublicForAssets(
					terariumAssetServices,
					id,
					updatedProject.get().getPublicAsset(),
					Schema.Permission.WRITE
				);
			} catch (final Exception e) {
				log.error("Error updating project", e);
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
			}
		}

		return ResponseEntity.ok(updatedProject.get());
	}

	@Operation(summary = "Copy a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "The project copy",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have read privileges to this project",
				content = @Content
			),
			@ApiResponse(responseCode = "404", description = "Project could not be found", content = @Content),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with either the postgres or spicedb" + " databases",
				content = @Content
			)
		}
	)
	@PostMapping("/clone/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Project> copyProject(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanRead(currentUserService.get().getId(), id);
		// time the progress takes to reach each subsequent half.
		final Double HALFTIME_SECONDS = 1.0;

		Future<Project> project;
		Project clonedProject;

		final String userId = currentUserService.get().getId();
		final String userName = userService.getById(userId).getName();

		final NotificationGroupInstance<Properties> notificationInterface = new NotificationGroupInstance<Properties>(
			clientEventService,
			notificationService,
			ClientEventType.CLONE_PROJECT,
			null,
			new Properties(id),
			HALFTIME_SECONDS
		);

		try {
			notificationInterface.sendMessage("Cloning the Project...");

			project = executor.submit(() -> {
				log.info("Staring Cloning Process...");
				final ProjectExport export = cloneService.exportProject(id);
				export.getProject().setName("Copy of " + export.getProject().getName());
				log.info("Cloning...");
				final Project cloneProject = cloneService.importProject(userId, userName, export);
				log.info("Cloned...");
				return cloneProject;
			});
			clonedProject = project.get();
		} catch (final ExecutionException e) {
			log.error("Execution Exception exporting project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		} catch (final CancellationException e) {
			log.error("Cancelled exporting project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("generic.io-error.write"));
		} catch (final InterruptedException e) {
			log.error("Interrupted exporting project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		} catch (final Exception e) {
			log.error("Error exporting project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		try {
			final RebacProject rebacProject = new RebacProject(clonedProject.getId(), reBACService);
			final RebacGroup rebacAskemAdminGroup = new RebacGroup(ReBACService.ASKEM_ADMIN_GROUP_ID, reBACService);
			final RebacUser rebacUser = new RebacUser(userId, reBACService);

			rebacUser.createCreatorRelationship(rebacProject);
			rebacAskemAdminGroup.createWriterRelationship(rebacProject);
		} catch (final Exception e) {
			log.error("Error setting user's permissions for project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		} catch (final RelationshipAlreadyExistsException e) {
			log.error("Error the user is already the creator of this project", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("rebac.relationship-already-exists")
			);
		}
		notificationInterface.sendFinalMessage("Cloning complete");
		return ResponseEntity.status(HttpStatus.CREATED).body(clonedProject);
	}

	@Operation(summary = "Export a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "The project export",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProjectExport.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have read privileges to this project",
				content = @Content
			),
			@ApiResponse(responseCode = "404", description = "Project could not be found", content = @Content),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with either the postgres or spicedb" + " databases",
				content = @Content
			)
		}
	)
	@GetMapping("/export/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<byte[]> exportProject(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanRead(currentUserService.get().getId(), id);
		try {
			final ProjectExport export = cloneService.exportProject(id);

			final HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.parseMediaType("application/zip"));
			final String filename = "project-" + id + ".zip";
			headers.setContentDispositionFormData(filename, filename);
			headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

			return new ResponseEntity<>(export.getAsZipFile(), headers, HttpStatus.OK);
		} catch (final Exception e) {
			log.error("Error exporting project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}
	}

	@Operation(summary = "Import a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "The project export",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "400",
				description = "An error occurred when trying to parse the import file",
				content = @Content
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was a rebac issue when creating the project",
				content = @Content
			),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with either the postgres or spicedb" + " databases",
				content = @Content
			)
		}
	)
	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	public ResponseEntity<Project> importProject(@RequestPart("file") final MultipartFile input) {
		if (input.getContentType() != null && !input.getContentType().equals("application/zip")) {
			return ResponseEntity.badRequest().build();
		}

		final ProjectExport projectExport = new ProjectExport();
		try {
			projectExport.loadFromZipFile(input.getInputStream());
		} catch (final Exception e) {
			log.error("Error parsing project", e);
			return ResponseEntity.badRequest().build();
		}

		final String userId = currentUserService.get().getId();
		final String userName = userService.getById(userId).getName();

		Project project;
		try {
			log.info("Importing project");
			project = cloneService.importProject(userId, userName, projectExport);
			log.info("Project imported");
		} catch (final Exception e) {
			log.error("Error importing project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		try {
			log.info("Setting project permissions");
			final RebacProject rebacProject = new RebacProject(project.getId(), reBACService);
			final RebacGroup rebacAskemAdminGroup = new RebacGroup(ReBACService.ASKEM_ADMIN_GROUP_ID, reBACService);
			final RebacUser rebacUser = new RebacUser(userId, reBACService);

			rebacUser.createCreatorRelationship(rebacProject);
			rebacAskemAdminGroup.createWriterRelationship(rebacProject);
			log.info("Project permissions set");
		} catch (final Exception e) {
			log.error("Error setting user's permissions for project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		} catch (final RelationshipAlreadyExistsException e) {
			log.error("Error the user is already the creator of this project", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("rebac.relationship-already-exists")
			);
		}

		log.info("Returning project");
		return ResponseEntity.status(HttpStatus.CREATED).body(project);
	}

	// --------------------------------------------------------------------------
	// Project Assets
	// --------------------------------------------------------------------------

	@Operation(summary = "Creates an asset inside of a given project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Asset Created",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProjectAsset.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have write privileges to this project",
				content = @Content
			),
			@ApiResponse(responseCode = "409", description = "Asset already exists in this project", content = @Content),
			@ApiResponse(responseCode = "500", description = "Error finding project", content = @Content),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with either the postgres or spicedb" + " databases",
				content = @Content
			)
		}
	)
	@PostMapping("/{id}/assets/{asset-type}/{asset-id}")
	@Secured(Roles.USER)
	public ResponseEntity<ProjectAsset> createAsset(
		@PathVariable("id") final UUID projectId,
		@PathVariable("asset-type") final String assetTypeName,
		@PathVariable("asset-id") final UUID assetId
	) {
		final AssetType assetType = AssetType.getAssetType(assetTypeName, objectMapper);
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Project> project;
		try {
			project = projectService.getProject(projectId);
			if (!project.isPresent()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("projects.not-found"));
			}
		} catch (final Exception e) {
			log.error("Error communicating with project service", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		final ITerariumAssetService<? extends TerariumAsset> terariumAssetService = terariumAssetServices.getServiceByType(
			assetType
		);

		// check if the asset is already associated with a project, if it is, we should
		// clone it and create a new asset

		final UUID owningProjectId = projectAssetService.getProjectIdForAsset(assetId, permission);
		final List<TerariumAsset> assets;

		try {
			if (owningProjectId != null) {
				// if the asset is already under another project, we need to clone it and its
				// dependencies
				assets = cloneService.cloneAndPersistAsset(owningProjectId, assetId);
			} else {
				// TODO: we should probably check asset dependencies and make sure they are part
				// of the project, and if not clone them
				final Optional<? extends TerariumAsset> asset = terariumAssetService.getAsset(assetId, permission);
				if (asset.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("asset.not-found"));
				}
				assets = List.of(asset.get());
			}
		} catch (final IOException e) {
			log.error("IO exception when trying to create asset", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		final List<ProjectAsset> projectAssets = new ArrayList<>();
		for (final TerariumAsset asset : assets) {
			final Optional<ProjectAsset> projectAsset = projectAssetService.createProjectAsset(
				project.get(),
				assetType,
				asset,
				permission
			);

			if (projectAsset.isEmpty()) {
				log.error("Project Asset is empty");
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("asset.unable-to-create"));
			}

			projectAssets.add(projectAsset.get());
		}

		// return the first project asset, it is always the original asset that we
		// wanted to add
		return ResponseEntity.ok(projectAssets.get(0));
	}

	@Operation(summary = "Deletes an asset inside of a given project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Asset Deleted",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "204",
				description = "The asset was not deleted and no errors occurred",
				content = @Content
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have write privileges to this project",
				content = @Content
			),
			@ApiResponse(responseCode = "500", description = "Error deleting asset", content = @Content),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with either the postgres or spicedb" + " databases",
				content = @Content
			)
		}
	)
	@DeleteMapping("/{id}/assets/{asset-type}/{asset-id}")
	@Secured(Roles.USER)
	public ResponseEntity<ResponseDeleted> deleteAsset(
		@PathVariable("id") final UUID projectId,
		@PathVariable("asset-type") final String assetTypeName,
		@PathVariable("asset-id") final UUID assetId
	) {
		final AssetType assetType = AssetType.getAssetType(assetTypeName, objectMapper);

		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final boolean deleted = projectAssetService.deleteByAssetId(projectId, assetType, assetId, permission);
		if (deleted) {
			return ResponseEntity.ok(new ResponseDeleted("ProjectAsset " + assetTypeName, assetId));
		}

		return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
	}

	@GetMapping("/{id}/permissions")
	@Secured(Roles.USER)
	@Operation(summary = "Gets the permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions found",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have read privileges to this project",
				content = @Content
			),
			@ApiResponse(
				responseCode = "503",
				description = "An error occurred when trying to communicate with spicedb database",
				content = @Content
			)
		}
	)
	public ResponseEntity<PermissionRelationships> getProjectPermissions(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanRead(currentUserService.get().getId(), id);

		final RebacProject rebacProject = new RebacProject(id, reBACService);

		final PermissionRelationships permissions = new PermissionRelationships();
		try {
			for (final RebacPermissionRelationship permissionRelationship : rebacProject.getPermissionRelationships()) {
				if (permissionRelationship.getSubjectType().equals(Schema.Type.USER)) {
					permissions.addUser(
						reBACService.getUser(permissionRelationship.getSubjectId()),
						permissionRelationship.getRelationship()
					);
				} else if (permissionRelationship.getSubjectType().equals(Schema.Type.GROUP)) {
					permissions.addGroup(
						reBACService.getGroup(permissionRelationship.getSubjectId()),
						permissionRelationship.getRelationship()
					);
				}
			}
		} catch (final Exception e) {
			log.error("Failed to get permissions for project", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}

		return ResponseEntity.ok(permissions);
	}

	// --------------------------------------------------------------------------
	// Project Permissions
	// --------------------------------------------------------------------------

	@PostMapping("/{id}/permissions/group/{group-id}/{relationship}")
	@Secured({ Roles.USER, Roles.SERVICE })
	@Operation(summary = "Sets a group's permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions set",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	public ResponseEntity<JsonNode> setProjectGroupPermissions(
		@PathVariable("id") final UUID projectId,
		@PathVariable("group-id") final String groupId,
		@PathVariable("relationship") final String relationship
	) {
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacGroup who = new RebacGroup(groupId, reBACService);
			projectPermissionsService.setProjectPermissions(what, who, relationship);
			return ResponseEntity.ok().build();
		} catch (final Exception e) {
			log.error("Error setting project group permission relationships", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@PutMapping("/{id}/permissions/group/{groupId}/{oldRelationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Updates a group's permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions updated",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	public ResponseEntity<JsonNode> updateProjectGroupPermissions(
		@PathVariable("id") final UUID projectId,
		@PathVariable("groupId") final String groupId,
		@PathVariable("oldRelationship") final String oldRelationship,
		@RequestParam("to") final String newRelationship
	) {
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacGroup who = new RebacGroup(groupId, reBACService);
			return projectPermissionsService.updateProjectPermissions(what, who, oldRelationship, newRelationship);
		} catch (final Exception e) {
			log.error("Error deleting project user permission relationships", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@DeleteMapping("/{id}/permissions/group/{group-id}/{relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a group's permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions deleted",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	public ResponseEntity<JsonNode> removeProjectGroupPermissions(
		@PathVariable("id") final UUID projectId,
		@PathVariable("group-id") final String groupId,
		@PathVariable("relationship") final String relationship
	) {
		if (relationship.equalsIgnoreCase(Schema.Relationship.CREATOR.toString())) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacGroup who = new RebacGroup(groupId, reBACService);
			projectPermissionsService.removeProjectPermissions(what, who, relationship);
			return ResponseEntity.ok().build();
		} catch (final Exception e) {
			log.error("Error deleting project group permission relationships", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@Operation(summary = "Toggle a project public, or restricted, by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Project visibility has been updated",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class)
					)
				}
			),
			@ApiResponse(
				responseCode = "403",
				description = "The current user does not have privileges to modify this project.",
				content = @Content
			),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	@PutMapping("/set-public/{id}/{isPublic}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> makeProjectPublic(
		@PathVariable("id") final UUID id,
		@PathVariable("isPublic") final boolean isPublic
	) {
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), id);

			// Getting the project permissions
			final RebacProject project = new RebacProject(id, reBACService);
			// Getting the user permissions
			final RebacUser user = new RebacUser(currentUserService.get().getId(), reBACService);
			// Getting the Public group permissions
			final RebacGroup who = new RebacGroup(ReBACService.PUBLIC_GROUP_ID, reBACService);
			// Setting the relationship to be of a reader
			final String relationship = Schema.Relationship.READER.toString();

			final Optional<Project> p = projectService.getProject(id);
			if (p.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("projects.not-found"));
			}

			// Update the project and child assets
			p.get().setPublicAsset(isPublic);
			projectAssetService.togglePublicForAssets(terariumAssetServices, id, isPublic, Schema.Permission.WRITE);
			projectService.updateProject(p.get());

			if (isPublic) {
				// Set the Public Group permissions to READ the Project
				projectPermissionsService.setProjectPermissions(project, who, relationship);
			} else {
				// Remove the Public Group permissions to READ the Project
				projectPermissionsService.removeProjectPermissions(project, who, relationship);
			}
			return ResponseEntity.ok().build();
		} catch (final ResponseStatusException rethrow) {
			throw rethrow;
		} catch (final Exception e) {
			log.error("Unexpected error, failed to set project public permissions", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@PostMapping("/{id}/permissions/user/{user-id}/{relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Sets a user's permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions set",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	public ResponseEntity<JsonNode> setProjectUserPermissions(
		@PathVariable("id") final UUID projectId,
		@PathVariable("user-id") final String userId,
		@PathVariable("relationship") final String relationship
	) {
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacUser who = new RebacUser(userId, reBACService);
			projectSearchService.addProjectPermission(projectId, userId);
			projectPermissionsService.setProjectPermissions(what, who, relationship);
			return ResponseEntity.ok().build();
		} catch (final Exception e) {
			log.error("Error setting project user permission relationships", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Error setting project user permission relationships"
			);
		}
	}

	@PutMapping("/{id}/permissions/user/{user-id}/{old-relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Updates a user's permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions updated",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	public ResponseEntity<JsonNode> updateProjectUserPermissions(
		@PathVariable("id") final UUID projectId,
		@PathVariable("user-id") final String userId,
		@PathVariable("old-relationship") final String oldRelationship,
		@RequestParam("to") final String newRelationship
	) {
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacUser who = new RebacUser(userId, reBACService);

			// no need to update the search perms since we don't reduce permissions below a
			// read.

			return projectPermissionsService.updateProjectPermissions(what, who, oldRelationship, newRelationship);
		} catch (final Exception e) {
			log.error("Error deleting project user permission relationships", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Error deleting project user permission relationships"
			);
		}
	}

	@DeleteMapping("/{id}/permissions/user/{user-id}/{relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a user's permissions for a project")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Permissions deleted",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class)
				)
			),
			@ApiResponse(responseCode = "304", description = "Permission not modified", content = @Content),
			@ApiResponse(responseCode = "403", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
		}
	)
	public ResponseEntity<JsonNode> removeProjectUserPermissions(
		@PathVariable("id") final UUID projectId,
		@PathVariable("user-id") final String userId,
		@PathVariable("relationship") final String relationship
	) {
		try {
			projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacUser who = new RebacUser(userId, reBACService);

			projectSearchService.removeProjectPermission(projectId, userId);

			projectPermissionsService.removeProjectPermissions(what, who, relationship);

			return ResponseEntity.ok().build();
		} catch (final Exception e) {
			log.error("Error deleting project user permission relationships", e);
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Error deleting project user permission relationships"
			);
		}
	}
}
