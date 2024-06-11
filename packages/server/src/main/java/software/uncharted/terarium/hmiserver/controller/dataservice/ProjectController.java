package software.uncharted.terarium.hmiserver.controller.dataservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectExport;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionGroup;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionRelationships;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionUser;
import software.uncharted.terarium.hmiserver.security.Roles;
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
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.TerariumAssetServices;
import software.uncharted.terarium.hmiserver.service.data.WorkflowService;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacGroup;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacObject;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacPermissionRelationship;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacProject;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacUser;

@RequestMapping("/projects")
@RestController
@Slf4j
@RequiredArgsConstructor
@Transactional
@Tags(@Tag(name = "Projects", description = "Project related operations"))
public class ProjectController {

	static final String WELCOME_MESSAGE = """
			<div>
				<h2>Hey there!</h2>
				<p>This is your project overview page. Use this space however you like. Not sure where to start? Here are some things you can try:</p>
				<br>
					<ul>
						<li><strong>Upload stuff:</strong> Upload documents, models, code or datasets with the green button in the bottom left corner.</li>
						<li><strong>Explore and add:</strong> Use the project selector in the top nav to switch to the Explorer where you can find documents, models and datasets that you can add to your project.</li>
						<li><strong>Build a model:</strong> Create a model that fits just what you need.</li>
						<li><strong>Create a workflow:</strong> Connect resources with operators so you can focus on the science and not the plumbing.</li>
					</ul>
				<br>
				<p>Feel free to erase this text and make it your own.</p>
			</div>
			""";
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

	// --------------------------------------------------------------------------
	// Basic Project Operations
	// --------------------------------------------------------------------------

	@GetMapping
	@Secured(Roles.USER)
	@Operation(summary = "Gets all projects (which are visible to this user)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Projects found.", content = @Content(array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class)))),
			@ApiResponse(responseCode = "204", description = "There are no errors, but also no projects for this user", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue with rebac permissions", content = @Content),
			@ApiResponse(responseCode = "503", description = "There was an issue communicating with back-end services", content = @Content)
	})
	public ResponseEntity<List<Project>> getProjects(
			@RequestParam(name = "include-inactive", defaultValue = "false") final Boolean includeInactive) {
		final RebacUser rebacUser = new RebacUser(currentUserService.get().getId(), reBACService);

		List<UUID> projectIds = null;
		try {
			projectIds = rebacUser.lookupProjects();
		} catch (final Exception e) {
			log.error("Error retrieving projects from spicedb", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
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
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		if (projects.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		projects.forEach(project -> {
			final List<AssetType> assetTypes = Arrays.asList(
					AssetType.DATASET, AssetType.MODEL, AssetType.DOCUMENT, AssetType.WORKFLOW);

			final RebacProject rebacProject = new RebacProject(project.getId(), reBACService);
			final Schema.Permission permission = projectService.checkPermissionCanRead(
					currentUserService.get().getId(), project.getId());

			// Set the user permission for the project. If we are unable to get the user
			// permission, we remove the project.
			try {
				project.setUserPermission(rebacUser.getPermissionFor(rebacProject));
			} catch (final Exception e) {
				log.error(
						"Failed to get user permissions from spicedb for project {}... Removing Project from list.",
						project.getId(),
						e);
				projects.remove(project);
				return;
			}

			// Set the public status for the project. If we are unable to get the public
			// status, we default to private.
			try {
				project.setPublicProject(rebacProject.isPublic());
			} catch (final Exception e) {
				log.error(
						"Failed to get project {} public status from spicedb... Defaulting to private.",
						project.getId(),
						e);
				project.setPublicProject(false);
			}

			// Set the contributors for the project. If we are unable to get the
			// contributors, we default to an empty
			// list.
			List<Contributor> contributors = null;
			try {
				contributors = getContributors(rebacProject);
			} catch (final Exception e) {
				log.error("Failed to get project contributors from spicedb for project {}", project.getId(), e);
			}

			// Set the metadata for the project. If we are unable to get the metadata, we
			// default to empty values.
			try {
				final List<ProjectAsset> assets = projectAssetService.findActiveAssetsForProject(project.getId(),
						assetTypes, permission);

				final Map<String, String> metadata = new HashMap<>();

				final Map<AssetType, Integer> counts = new EnumMap<>(AssetType.class);
				for (final ProjectAsset asset : assets) {
					counts.put(asset.getAssetType(), counts.getOrDefault(asset.getAssetType(), 0) + 1);
				}

				metadata.put("contributor-count", Integer.toString(contributors == null ? 0 : contributors.size()));
				metadata.put(
						"datasets-count",
						counts.getOrDefault(AssetType.DATASET, 0).toString());
				metadata.put(
						"document-count",
						counts.getOrDefault(AssetType.DOCUMENT, 0).toString());
				metadata.put(
						"models-count", counts.getOrDefault(AssetType.MODEL, 0).toString());
				metadata.put(
						"workflows-count",
						counts.getOrDefault(AssetType.WORKFLOW, 0).toString());

				project.setMetadata(metadata);
			} catch (final Exception e) {
				log.error(
						"Failed to get project assets from postgres db for project {}. Setting Default Metadata.",
						project.getId(),
						e);
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
	 * Capture the subset of RebacPermissionRelationships for a given Project.
	 *
	 * @param rebacProject the Project to collect RebacPermissionRelationships of.
	 * @return List of Users and Groups who have edit capability of the rebacProject
	 */
	private List<Contributor> getContributors(final RebacProject rebacProject) throws Exception {
		final Map<String, Contributor> contributorMap = new HashMap<>();

		final List<RebacPermissionRelationship> permissionRelationships = rebacProject.getPermissionRelationships();
		for (final RebacPermissionRelationship permissionRelationship : permissionRelationships) {
			final Schema.Relationship relationship = permissionRelationship.getRelationship();
			// Ensure the relationship is capable of editing the project
			if (relationship.equals(Schema.Relationship.CREATOR)
					|| relationship.equals(Schema.Relationship.ADMIN)
					|| relationship.equals(Schema.Relationship.WRITER)) {
				if (permissionRelationship.getSubjectType().equals(Schema.Type.USER)) {
					final PermissionUser user = reBACService.getUser(permissionRelationship.getSubjectId());
					final String name = user.getFirstName() + " " + user.getLastName();
					if (!contributorMap.containsKey(name)) {
						contributorMap.put(name, new Contributor(name, relationship));
					}
				} else if (permissionRelationship.getSubjectType().equals(Schema.Type.GROUP)) {
					final PermissionGroup group = reBACService.getGroup(permissionRelationship.getSubjectId());
					if (!contributorMap.containsKey(group.getName())) {
						contributorMap.put(group.getName(), new Contributor(group.getName(), relationship));
					}
				}
			}
		}

		return new ArrayList<>(contributorMap.values());
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
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Project found.", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class))
			}),
			@ApiResponse(responseCode = "403", description = "User does not have permission to view this project", content = @Content),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue with rebac permissions", content = @Content),
			@ApiResponse(responseCode = "503", description = "Error communicating with back-end services", content = @Content)
	})
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
			final List<Contributor> contributors = getContributors(rebacProject);
			for (final Contributor contributor : contributors) {
				authors.add(contributor.name);
			}

			project.get().setPublicProject(rebacProject.isPublic());
			project.get().setUserPermission(rebacUser.getPermissionFor(rebacProject));
			project.get().setAuthors(authors);
		} catch (final Exception e) {
			log.error("Failed to get project permissions from spicedb", e);
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, messages.get("projects.unable-to-get-permissions"));
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
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Project marked for deletion", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class))
			}),
			@ApiResponse(responseCode = "403", description = "The current user does not have delete privileges to this project", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions or deleting the project", content = @Content)
	})
	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<ResponseDeleted> deleteProject(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanAdministrate(currentUserService.get().getId(), id);

		final boolean deleted = projectService.delete(id);
		if (deleted)
			return ResponseEntity.ok(new ResponseDeleted("project", id));

		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("projects.unable-to-delete"));
	}

	@Operation(summary = "Creates a new project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Project created", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class)),
			}),
			@ApiResponse(responseCode = "400", description = "The provided information is not valid to create a project", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was a rebac issue when creating the project", content = @Content),
			@ApiResponse(responseCode = "503", description = "There was an issue communicating with the data store or rebac service", content = @Content)
	})
	@PostMapping
	@Secured(Roles.USER)
	public ResponseEntity<Project> createProject(
			@RequestParam("name") final String name, @RequestParam("description") final String description) {

		if (name == null || name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("projects.name-required"));
		}

		final String userId = currentUserService.get().getId();

		Project project = (Project) new Project()
				.setOverviewContent(WELCOME_MESSAGE.getBytes())
				.setUserId(userId)
				.setName(name)
				.setDescription(description);

		try {
			project = projectService.createProject(project);
		} catch (final Exception e) {
			log.error("Error creating project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		try {
			final RebacProject rebacProject = new RebacProject(project.getId(), reBACService);
			final RebacGroup rebacAskemAdminGroup = new RebacGroup(ReBACService.ASKEM_ADMIN_GROUP_ID, reBACService);
			final RebacUser rebacUser = new RebacUser(userId, reBACService);

			rebacUser.createCreatorRelationship(rebacProject);
			rebacAskemAdminGroup.createWriterRelationship(rebacProject);
		} catch (final Exception e) {
			log.error("Error setting user's permissions for project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		} catch (final RelationshipAlreadyExistsException e) {
			log.error("Error the user is already the creator of this project", e);
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, messages.get("rebac.relationship-already-exists"));
		}

		return ResponseEntity.status(HttpStatus.CREATED).body(project);
	}

	@Operation(summary = "Updates a project by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Project marked for deletion", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class))
			}),
			@ApiResponse(responseCode = "403", description = "The current user does not have update privileges to this project", content = @Content),
			@ApiResponse(responseCode = "404", description = "Project could not be found", content = @Content),
			@ApiResponse(responseCode = "503", description = "An error occurred when trying to communicate with either the postgres or spicedb"
					+ " databases", content = @Content)
	})
	@PutMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Project> updateProject(
			@PathVariable("id") final UUID id, @RequestBody final Project project) {
		projectService.checkPermissionCanWrite(currentUserService.get().getId(), id);

		project.setId(id);
		final Optional<Project> updatedProject;
		try {
			updatedProject = projectService.updateProject(project);
		} catch (final Exception e) {
			log.error("Error updating project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		if (!updatedProject.isPresent()) {
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, messages.get("projects.unable-to-update"));
		}

		return ResponseEntity.ok(updatedProject.get());
	}

	@Operation(summary = "Export a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The project export", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProjectExport.class))
			}),
			@ApiResponse(responseCode = "403", description = "The current user does not have read privileges to this project", content = @Content),
			@ApiResponse(responseCode = "404", description = "Project could not be found", content = @Content),
			@ApiResponse(responseCode = "503", description = "An error occurred when trying to communicate with either the postgres or spicedb"
					+ " databases", content = @Content)
	})
	@GetMapping("/export/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<ProjectExport> exportProject(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanRead(currentUserService.get().getId(), id);
		try {
			return ResponseEntity.ok(cloneService.exportProject(id));
		} catch (final Exception e) {
			log.error("Error exporting project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}
	}

	@Operation(summary = "Import a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "The project export", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Project.class))
			}),
			@ApiResponse(responseCode = "400", description = "An error occurred when trying to parse the import file", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was a rebac issue when creating the project", content = @Content),
			@ApiResponse(responseCode = "503", description = "An error occurred when trying to communicate with either the postgres or spicedb"
					+ " databases", content = @Content)
	})
	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	public ResponseEntity<Project> importProject(@RequestPart("file") final MultipartFile input) {

		ProjectExport projectExport;
		try {
			projectExport = objectMapper.readValue(input.getInputStream(), ProjectExport.class);
		} catch (final Exception e) {
			log.error("Error parsing project export", e);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("projects.import-parse-failure"));
		}

		final String userId = currentUserService.get().getId();
		final String userName = userService.getById(userId).getName();

		Project project;
		try {
			project = cloneService.importProject(userId, userName, projectExport);
		} catch (final Exception e) {
			log.error("Error importing project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		try {
			project = projectService.createProject(project);
		} catch (final Exception e) {
			log.error("Error creating project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		try {
			final RebacProject rebacProject = new RebacProject(project.getId(), reBACService);
			final RebacGroup rebacAskemAdminGroup = new RebacGroup(ReBACService.ASKEM_ADMIN_GROUP_ID, reBACService);
			final RebacUser rebacUser = new RebacUser(userId, reBACService);

			rebacUser.createCreatorRelationship(rebacProject);
			rebacAskemAdminGroup.createWriterRelationship(rebacProject);
		} catch (final Exception e) {
			log.error("Error setting user's permissions for project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		} catch (final RelationshipAlreadyExistsException e) {
			log.error("Error the user is already the creator of this project", e);
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, messages.get("rebac.relationship-already-exists"));
		}

		return ResponseEntity.status(HttpStatus.CREATED).body(project);
	}

	// --------------------------------------------------------------------------
	// Project Assets
	// --------------------------------------------------------------------------

	@Operation(summary = "Creates an asset inside of a given project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Asset Created", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProjectAsset.class))
			}),
			@ApiResponse(responseCode = "403", description = "The current user does not have write privileges to this project", content = @Content),
			@ApiResponse(responseCode = "409", description = "Asset already exists in this project", content = @Content),
			@ApiResponse(responseCode = "500", description = "Error finding project", content = @Content),
			@ApiResponse(responseCode = "503", description = "An error occurred when trying to communicate with either the postgres or spicedb"
					+ " databases", content = @Content)
	})
	@PostMapping("/{id}/assets/{asset-type}/{asset-id}")
	@Secured(Roles.USER)
	public ResponseEntity<ProjectAsset> createAsset(
			@PathVariable("id") final UUID projectId,
			@PathVariable("asset-type") final String assetTypeName,
			@PathVariable("asset-id") final UUID assetId) {

		final AssetType assetType = AssetType.getAssetType(assetTypeName, objectMapper);
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		final Optional<Project> project;
		try {
			project = projectService.getProject(projectId);
			if (!project.isPresent()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("projects.not-found"));
			}
		} catch (final Exception e) {
			log.error("Error communicating with project service", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		final ITerariumAssetService<? extends TerariumAsset> terariumAssetService = terariumAssetServices
				.getServiceByType(assetType);

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
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		final List<ProjectAsset> projectAssets = new ArrayList<>();
		for (final TerariumAsset asset : assets) {
			final Optional<ProjectAsset> projectAsset = projectAssetService.createProjectAsset(project.get(), assetType,
					asset, permission);

			if (projectAsset.isEmpty()) {
				throw new ResponseStatusException(
						HttpStatus.INTERNAL_SERVER_ERROR, messages.get("asset.unable-to-create"));
			}

			projectAssets.add(projectAsset.get());
		}

		// return the first project asset, it is always the original asset that we
		// wanted to add
		return ResponseEntity.ok(projectAssets.get(0));
	}

	@Operation(summary = "Deletes an asset inside of a given project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Asset Deleted", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class))
			}),
			@ApiResponse(responseCode = "204", description = "The asset was not deleted and no errors occurred", content = @Content),
			@ApiResponse(responseCode = "403", description = "The current user does not have write privileges to this project", content = @Content),
			@ApiResponse(responseCode = "500", description = "Error deleting asset", content = @Content),
			@ApiResponse(responseCode = "503", description = "An error occurred when trying to communicate with either the postgres or spicedb"
					+ " databases", content = @Content)
	})
	@DeleteMapping("/{id}/assets/{asset-type}/{asset-id}")
	@Secured(Roles.USER)
	public ResponseEntity<ResponseDeleted> deleteAsset(
			@PathVariable("id") final UUID projectId,
			@PathVariable("asset-type") final String assetTypeName,
			@PathVariable("asset-id") final UUID assetId) {

		final AssetType assetType = AssetType.getAssetType(assetTypeName, objectMapper);

		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		final boolean deleted = projectAssetService.deleteByAssetId(projectId, assetType, assetId, permission);
		if (deleted) {
			return ResponseEntity.ok(new ResponseDeleted("ProjectAsset " + assetTypeName, assetId));
		}

		return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
	}

	@GetMapping("/{id}/permissions")
	@Secured(Roles.USER)
	@Operation(summary = "Gets the permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions found", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "403", description = "The current user does not have read privileges to this project", content = @Content),
			@ApiResponse(responseCode = "503", description = "An error occurred when trying to communicate with spicedb database", content = @Content)
	})
	public ResponseEntity<PermissionRelationships> getProjectPermissions(@PathVariable("id") final UUID id) {
		projectService.checkPermissionCanRead(currentUserService.get().getId(), id);

		final RebacProject rebacProject = new RebacProject(id, reBACService);

		final PermissionRelationships permissions = new PermissionRelationships();
		try {
			for (final RebacPermissionRelationship permissionRelationship : rebacProject.getPermissionRelationships()) {
				if (permissionRelationship.getSubjectType().equals(Schema.Type.USER)) {
					permissions.addUser(
							reBACService.getUser(permissionRelationship.getSubjectId()),
							permissionRelationship.getRelationship());
				} else if (permissionRelationship.getSubjectType().equals(Schema.Type.GROUP)) {
					permissions.addGroup(
							reBACService.getGroup(permissionRelationship.getSubjectId()),
							permissionRelationship.getRelationship());
				}
			}
		} catch (final Exception e) {
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}

		return ResponseEntity.ok(permissions);
	}

	// --------------------------------------------------------------------------
	// Project Permissions
	// --------------------------------------------------------------------------

	@PostMapping("/{id}/permissions/group/{group-id}/{relationship}")
	@Secured({ Roles.USER, Roles.SERVICE })
	@Operation(summary = "Sets a group's permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions set", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	public ResponseEntity<JsonNode> setProjectGroupPermissions(
			@PathVariable("id") final UUID projectId,
			@PathVariable("group-id") final String groupId,
			@PathVariable("relationship") final String relationship) {
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacGroup who = new RebacGroup(groupId, reBACService);
			return setProjectPermissions(what, who, relationship);
		} catch (final Exception e) {
			log.error("Error setting project group permission relationships", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@PutMapping("/{id}/permissions/group/{groupId}/{oldRelationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Updates a group's permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions updated", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	public ResponseEntity<JsonNode> updateProjectGroupPermissions(
			@PathVariable("id") final UUID projectId,
			@PathVariable("groupId") final String groupId,
			@PathVariable("oldRelationship") final String oldRelationship,
			@RequestParam("to") final String newRelationship) {
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacGroup who = new RebacGroup(groupId, reBACService);
			return updateProjectPermissions(what, who, oldRelationship, newRelationship);
		} catch (final Exception e) {
			log.error("Error deleting project user permission relationships", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@DeleteMapping("/{id}/permissions/group/{group-id}/{relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a group's permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions deleted", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	public ResponseEntity<JsonNode> removeProjectGroupPermissions(
			@PathVariable("id") final UUID projectId,
			@PathVariable("group-id") final String groupId,
			@PathVariable("relationship") final String relationship) {
		if (relationship.equalsIgnoreCase(Schema.Relationship.CREATOR.toString())) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacGroup who = new RebacGroup(groupId, reBACService);
			return removeProjectPermissions(what, who, relationship);
		} catch (final Exception e) {
			log.error("Error deleting project group permission relationships", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@Operation(summary = "Toggle a project public, or restricted, by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Project visibility has been updated", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UUID.class))
			}),
			@ApiResponse(responseCode = "304", description = "The current user does not have privileges to modify this project.", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	@PutMapping("/set-public/{id}/{isPublic}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> makeProjectPublic(
			@PathVariable("id") final UUID id, @PathVariable("isPublic") final boolean isPublic) {
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), id);

			// Getting the project permissions
			final RebacProject project = new RebacProject(id, reBACService);
			// Getting the user permissions
			final RebacUser user = new RebacUser(currentUserService.get().getId(), reBACService);
			// Getting the Public group permissions
			final RebacGroup who = new RebacGroup(ReBACService.PUBLIC_GROUP_ID, reBACService);
			// Setting the relationship to be of a reader
			final String relationship = Schema.Relationship.READER.toString();

			if (isPublic) {
				// Set the Public Group permissions to READ the Project
				return setProjectPermissions(project, who, relationship);
			} else {
				// Remove the Public Group permissions to READ the Project
				return removeProjectPermissions(project, who, relationship);
			}
		} catch (final Exception e) {
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	@PostMapping("/{id}/permissions/user/{user-id}/{relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Sets a user's permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions set", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	public ResponseEntity<JsonNode> setProjectUserPermissions(
			@PathVariable("id") final UUID projectId,
			@PathVariable("user-id") final String userId,
			@PathVariable("relationship") final String relationship) {
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacUser who = new RebacUser(userId, reBACService);
			return setProjectPermissions(what, who, relationship);
		} catch (final Exception e) {
			log.error("Error setting project user permission relationships", e);
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, "Error setting project user permission relationships");
		}
	}

	@PutMapping("/{id}/permissions/user/{user-id}/{old-relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Updates a user's permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions updated", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "404", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	public ResponseEntity<JsonNode> updateProjectUserPermissions(
			@PathVariable("id") final UUID projectId,
			@PathVariable("user-id") final String userId,
			@PathVariable("old-relationship") final String oldRelationship,
			@RequestParam("to") final String newRelationship) {
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacUser who = new RebacUser(userId, reBACService);
			return updateProjectPermissions(what, who, oldRelationship, newRelationship);
		} catch (final Exception e) {
			log.error("Error deleting project user permission relationships", e);
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting project user permission relationships");
		}
	}

	@DeleteMapping("/{id}/permissions/user/{user-id}/{relationship}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a user's permissions for a project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Permissions deleted", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PermissionRelationships.class))),
			@ApiResponse(responseCode = "304", description = "Permission not modified", content = @Content),
			@ApiResponse(responseCode = "403", description = "Project not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "An error occurred verifying permissions", content = @Content)
	})
	public ResponseEntity<JsonNode> removeProjectUserPermissions(
			@PathVariable("id") final UUID projectId,
			@PathVariable("user-id") final String userId,
			@PathVariable("relationship") final String relationship) {
		try {
			projectService.checkPermissionCanAdministrate(
					currentUserService.get().getId(), projectId);

			final RebacProject what = new RebacProject(projectId, reBACService);
			final RebacUser who = new RebacUser(userId, reBACService);
			return removeProjectPermissions(what, who, relationship);
		} catch (final Exception e) {
			log.error("Error deleting project user permission relationships", e);
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting project user permission relationships");
		}
	}

	private static ResponseEntity<JsonNode> setProjectPermissions(
			final RebacProject what, final RebacObject who, final String relationship) throws Exception {
		try {
			what.setPermissionRelationships(who, relationship);
			return ResponseEntity.ok().build();
		} catch (final RelationshipAlreadyExistsException e) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
	}

	private static ResponseEntity<JsonNode> updateProjectPermissions(
			final RebacProject what, final RebacObject who, final String oldRelationship, final String newRelationship)
			throws Exception {
		try {
			what.removePermissionRelationships(who, oldRelationship);
			what.setPermissionRelationships(who, newRelationship);
			return ResponseEntity.ok().build();
		} catch (final RelationshipAlreadyExistsException e) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
	}

	private static ResponseEntity<JsonNode> removeProjectPermissions(
			final RebacProject what, final RebacObject who, final String relationship) throws Exception {
		try {
			what.removePermissionRelationships(who, relationship);
			return ResponseEntity.ok().build();
		} catch (final RelationshipAlreadyExistsException e) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
	}

	/** A Contributor is a User or Group that is capable of editing a Project. */
	private class Contributor {
		String name;
		Schema.Relationship permission;

		Contributor(final String name, final Schema.Relationship permission) {
			this.name = name;
			this.permission = permission;
		}
	}
}
