package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.models.Id;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.Assets;
import software.uncharted.terarium.hmiserver.models.dataservice.Project;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionRelationships;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ProjectProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/projects")
@RestController
@Slf4j
public class ProjectController {

	@Autowired
	ReBACService reBACService;

	@Autowired
	CurrentUserService currentUserService;


	@Autowired
	private ProjectProxy proxy;

	@GetMapping
	@Secured(Roles.USER)
	public ResponseEntity<List<Project>> getProjects(
		@RequestParam(name = "include_inactive", defaultValue = "false") final Boolean includeInactive
	) {
		List<Project> projects = proxy.getProjects(includeInactive).getBody();
		if (projects == null) {
			return ResponseEntity.noContent().build();
		}
		RebacUser rebacUser = new RebacUser(currentUserService.getToken().getSubject(), reBACService);
		// Remove non-active (soft-deleted) projects
		projects = projects
			.stream()
			.filter(Project::getActive)
			.filter(project -> {
				try {
					return rebacUser.canRead(new RebacProject(project.getProjectID(), reBACService));
				} catch (Exception e) {
					log.error("Error getting user's permissions for project", e);
					return false;
				}
			})
			.toList();

		projects.forEach(project -> {
			try {
				List<AssetType> assetTypes = Arrays.asList(AssetType.datasets, AssetType.models, AssetType.publications);

				RebacProject rebacProject = new RebacProject(project.getProjectID(), reBACService);
				project.setPublicProject(rebacProject.isPublic());
				project.setUserPermission(rebacUser.getPermissionFor(rebacProject));

				Assets assets = proxy.getAssets(project.getProjectID(), assetTypes).getBody();
				Map<String, String> metadata = new HashMap<>();
				metadata.put("datasets-count", assets.getDatasets() == null ? "0" : String.valueOf(assets.getDatasets().size()));
				metadata.put("extractions-count", assets.getExtractions() == null ? "0" : String.valueOf(assets.getExtractions().size()));
				metadata.put("models-count", assets.getModels() == null ? "0" : String.valueOf(assets.getModels().size()));
				metadata.put("publications-count", assets.getPublications() == null ? "0" : String.valueOf(assets.getPublications().size()));
				metadata.put("workflows-count", assets.getWorkflows() == null ? "0" : String.valueOf(assets.getWorkflows().size()));
				metadata.put("artifacts-count", assets.getArtifacts() == null ? "0" : String.valueOf(assets.getArtifacts().size()));
				project.setMetadata(metadata);
			} catch (Exception e) {
				log.error("Cannot get Datasets, Models, and Publications assets from data-service for project_id {}", project.getProjectID(), e);
			}
		});


		return ResponseEntity.ok(projects);
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Project> getProject(
		@PathVariable("id") final String id
	) {
		try {
			RebacUser rebacUser = new RebacUser(currentUserService.getToken().getSubject(), reBACService);
			RebacProject rebacProject = new RebacProject(id, reBACService);
			if (rebacUser.canRead(rebacProject)) {
				Project project = proxy.getProject(id).getBody();
				project.setPublicProject(rebacProject.isPublic());
				project.setUserPermission(rebacUser.getPermissionFor(rebacProject));
				return ResponseEntity.ok(project);
			}
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			log.error("Error getting project", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@GetMapping("/{id}/permissions")
	@Secured(Roles.USER)
	public ResponseEntity<PermissionRelationships> getProjectPermissions(
		@PathVariable("id") final String id
	) {
		try {
			RebacProject rebacProject = new RebacProject(id, reBACService);
			if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canRead(rebacProject)) {
				PermissionRelationships permissions = new PermissionRelationships();
				for (RebacPermissionRelationship permissionRelationship : rebacProject.getPermissionRelationships()) {
					if (permissionRelationship.getSubjectType().equals(Schema.Type.USER)) {
						permissions.addUser(reBACService.getUser(permissionRelationship.getSubjectId()), permissionRelationship.getRelationship());
					} else if (permissionRelationship.getSubjectType().equals(Schema.Type.GROUP)) {
						permissions.addGroup(reBACService.getGroup(permissionRelationship.getSubjectId()), permissionRelationship.getRelationship());
					}
				}

				return ResponseEntity.ok(permissions);
			}
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			log.error("Error getting project permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}


	@PostMapping("/{projectId}/permissions/group/{groupId}/{relationship}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> setProjectGroupPermissions(
		@PathVariable("projectId") final String projectId,
		@PathVariable("groupId") final String groupId,
		@PathVariable("relationship") final String relationship
	) {
		try {
			RebacProject what = new RebacProject(projectId, reBACService);
			RebacGroup who = new RebacGroup(groupId, reBACService);
			return setProjectPermissions(what, who, relationship);
		} catch (Exception e) {
			log.error("Error setting project group permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@PutMapping("/{projectId}/permissions/group/{groupId}/{oldRelationship}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> updateProjectGroupPermissions(
		@PathVariable("projectId") final String projectId,
		@PathVariable("groupId") final String groupId,
		@PathVariable("oldRelationship") final String oldRelationship,
		@RequestParam("to") final String newRelationship
	) {
		try {
			RebacProject what = new RebacProject(projectId, reBACService);
			RebacGroup who = new RebacGroup(groupId, reBACService);
			return updateProjectPermissions(what, who, oldRelationship, newRelationship);
		} catch (Exception e) {
			log.error("Error deleting project user permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@DeleteMapping("/{projectId}/permissions/group/{groupId}/{relationship}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> removeProjectGroupPermissions(
		@PathVariable("projectId") final String projectId,
		@PathVariable("groupId") final String groupId,
		@PathVariable("relationship") final String relationship
	) {
		if (relationship.equals(Schema.Relationship.CREATOR)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
		try {
			RebacProject what = new RebacProject(projectId, reBACService);
			RebacGroup who = new RebacGroup(groupId, reBACService);
			return removeProjectPermissions(what, who, relationship);
		} catch (Exception e) {
			log.error("Error deleting project group permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@PostMapping("/{projectId}/permissions/user/{userId}/{relationship}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> setProjectUserPermissions(
		@PathVariable("projectId") final String projectId,
		@PathVariable("userId") final String userId,
		@PathVariable("relationship") final String relationship
	) {
		try {
			RebacProject what = new RebacProject(projectId, reBACService);
			RebacUser who = new RebacUser(userId, reBACService);
			return setProjectPermissions(what, who, relationship);
		} catch (Exception e) {
			log.error("Error setting project user permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@PutMapping("/{projectId}/permissions/user/{userId}/{oldRelationship}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> updateProjectUserPermissions(
		@PathVariable("projectId") final String projectId,
		@PathVariable("userId") final String userId,
		@PathVariable("oldRelationship") final String oldRelationship,
		@RequestParam("to") final String newRelationship
	) {
		try {
			RebacProject what = new RebacProject(projectId, reBACService);
			RebacUser who = new RebacUser(userId, reBACService);
			return updateProjectPermissions(what, who, oldRelationship, newRelationship);
		} catch (Exception e) {
			log.error("Error deleting project user permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@DeleteMapping("/{projectId}/permissions/user/{userId}/{relationship}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> removeProjectUserPermissions(
		@PathVariable("projectId") final String projectId,
		@PathVariable("userId") final String userId,
		@PathVariable("relationship") final String relationship
	) {
		try {
			RebacProject what = new RebacProject(projectId, reBACService);
			RebacUser who = new RebacUser(userId, reBACService);
			return removeProjectPermissions(what, who, relationship);
		} catch (Exception e) {
			log.error("Error deleting project user permission relationships", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	private ResponseEntity<JsonNode> setProjectPermissions(RebacProject what, RebacObject who, String relationship) throws Exception {
		if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canAdministrate(what)) {
			try {
				what.setPermissionRelationships(who, relationship);
				return ResponseEntity.ok().build();
			} catch (RelationshipAlreadyExistsException e) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
			}
		}
		return ResponseEntity.notFound().build();
	}

	private ResponseEntity<JsonNode> updateProjectPermissions(RebacProject what, RebacObject who, String oldRelationship, String newRelationship) throws Exception {
		if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canAdministrate(what)) {
			try {
				what.removePermissionRelationships(who, oldRelationship);
				what.setPermissionRelationships(who, newRelationship);
				return ResponseEntity.ok().build();
			} catch (RelationshipAlreadyExistsException e) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
			}
		}
		return ResponseEntity.notFound().build();
	}

	private ResponseEntity<JsonNode> removeProjectPermissions(RebacProject what, RebacObject who, String relationship) throws Exception {
		if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canAdministrate(what)) {
			try {
				what.removePermissionRelationships(who, relationship);
				return ResponseEntity.ok().build();
			} catch (RelationshipAlreadyExistsException e) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
			}
		}
		return ResponseEntity.notFound().build();
	}


	@PostMapping
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> createProject(
		@RequestBody final Project project
	) throws JsonProcessingException {
		ResponseEntity<JsonNode> res = proxy.createProject(project);
		if (res != null) {

			ObjectMapper mapper = new ObjectMapper();
			Id id = mapper.treeToValue(res.getBody(), Id.class);
			String location = res.getHeaders().get("Location").get(0);
			String server = res.getHeaders().get("Server").get(0);

			try {
				new RebacUser(currentUserService.getToken().getSubject(), reBACService).createCreatorRelationship(new RebacProject(Integer.toString(id.getId()), reBACService));
			} catch (Exception e) {
				log.error("Error setting user's permissions for project", e);
				// TODO: Rollback potential?
			} catch (RelationshipAlreadyExistsException e) {
				log.error("Error the user is already the creator of this project", e);
				// TODO: Rollback potential?
			}
			return ResponseEntity.status(HttpStatus.CREATED).header("Location", location).header("Server", server).body(res.getBody());
		} else {
			log.error("createProject proxy returned a null");
			return ResponseEntity.internalServerError().build();
		}
	}

	@PutMapping("/{project_id}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> updateProject(
		@PathVariable("project_id") final String id,
		@RequestBody final Project project
	) {
		try {
			if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canWrite(new RebacProject(id, reBACService))) {
				return ResponseEntity.ok(proxy.updateProject(id, project).getBody());
			}
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		} catch (Exception e) {
			log.error("Error updating project", e);
			return ResponseEntity.internalServerError().build();
		}

	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> deleteProject(
		@PathVariable("id") final String id
	) {

		try {
			if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canAdministrate(new RebacProject(id, reBACService))) {
				return ResponseEntity.ok(proxy.deleteProject(id).getBody());
			}
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		} catch (Exception e) {
			log.error("Error deleting project", e);
			return ResponseEntity.internalServerError().build();
		}

	}

	@GetMapping("/{project_id}/assets")
	@Secured(Roles.USER)
	public ResponseEntity<Assets> getAssets(
		@PathVariable("project_id") final String projectId,
		@RequestParam("types") final List<AssetType> types
	) {
		try {
			if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canRead(new RebacProject(projectId, reBACService))) {
				return ResponseEntity.ok(proxy.getAssets(projectId, types).getBody());
			}
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			log.error("Error getting project assets", e);
			return ResponseEntity.internalServerError().build();
		}


	}

	@PostMapping("/{project_id}/assets/{resource_type}/{resource_id}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> createAsset(
		@PathVariable("project_id") final String projectId,
		@PathVariable("resource_type") final AssetType type,
		@PathVariable("resource_id") final String resourceId
	) {


		try {
			if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canWrite(new RebacProject(projectId, reBACService))) {
				return ResponseEntity.ok(proxy.createAsset(projectId, type, resourceId).getBody());
			}
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		} catch (Exception e) {
			log.error("Error creating project assets", e);
			return ResponseEntity.internalServerError().build();
		}

	}

	@DeleteMapping("/{project_id}/assets/{resource_type}/{resource_id}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> deleteAsset(
		@PathVariable("project_id") final String projectId,
		@PathVariable("resource_type") final AssetType type,
		@PathVariable("resource_id") final String resourceId
	) {

		try {
			if (new RebacUser(currentUserService.getToken().getSubject(), reBACService).canWrite(new RebacProject(projectId, reBACService))) {
				return ResponseEntity.ok(proxy.deleteAsset(projectId, type, resourceId).getBody());
			}
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		} catch (Exception e) {
			log.error("Error deleting project assets", e);
			return ResponseEntity.internalServerError().build();
		}

	}
}
