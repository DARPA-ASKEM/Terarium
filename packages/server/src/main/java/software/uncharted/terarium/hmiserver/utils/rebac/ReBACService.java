package software.uncharted.terarium.hmiserver.utils.rebac;

import static software.uncharted.terarium.hmiserver.utils.rebac.httputil.HttpUtil.composeResourceUrl;
import static software.uncharted.terarium.hmiserver.utils.rebac.httputil.HttpUtil.doDeleteJSON;
import static software.uncharted.terarium.hmiserver.utils.rebac.httputil.HttpUtil.doPostJSON;

import com.authzed.api.v1.Core;
import com.authzed.api.v1.PermissionService.Consistency;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionGroup;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionRole;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionUser;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacPermissionRelationship;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReBACService {

	Keycloak keycloak;

	private final Config config;

	private final SchemaManager schemaManager = new SchemaManager();

	@Value("${terarium.keycloak.realm}")
	String REALM_NAME;

	@Value("${spicedb.shared-key}")
	String SPICEDB_PRESHARED_KEY;

	@Value("${spicedb.target}")
	String SPICEDB_TARGET;

	@Value("${spicedb.launchmode}")
	String SPICEDB_LAUNCHMODE;

	@Value("${terarium.keycloak.api-service-name}")
	String API_SERVICE_USER_NAME = "api-service";

	private BearerToken spiceDbBearerToken;
	private ManagedChannel channel;

	public static final String PUBLIC_GROUP_NAME = "Public";
	public static String PUBLIC_GROUP_ID;
	public static final String ASKEM_ADMIN_GROUP_NAME = "ASKEM Admins";
	public static String ASKEM_ADMIN_GROUP_ID;

	public static String API_SERVICE_USER_ID;

	volatile String CURRENT_ZED_TOKEN;

	private String getKeycloakBearerToken() {
		return "Bearer " + keycloak.tokenManager().getAccessTokenString();
	}

	@PostConstruct
	void startup() throws Exception {

		keycloak = KeycloakBuilder.builder()
				.serverUrl(config.getKeycloak().getUrl())
				.realm(config.getKeycloak().getAdminRealm())
				.clientId(config.getKeycloak().getAdminClientId())
				.grantType(OAuth2Constants.PASSWORD)
				.username(config.getKeycloak().getAdminUsername())
				.password(config.getKeycloak().getAdminPassword())
				.build();

		spiceDbBearerToken = new BearerToken(SPICEDB_PRESHARED_KEY);
		if (SPICEDB_LAUNCHMODE.equals("TEST")) {
			channel = InProcessChannelBuilder.forName("TestSpiceDB").build();
			return;
		} else {
			final ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(SPICEDB_TARGET);
			if (SPICEDB_LAUNCHMODE.equals("TLS")) {
				builder.useTransportSecurity();
			} else {
				builder.usePlaintext();
			}
			channel = builder.build();
		}

		log.info("Init ReBAC");
		if (!schemaManager.doesSchemaExist(channel, spiceDbBearerToken)) {
			schemaManager.createSchema(channel, spiceDbBearerToken, Schema.schema);

			PUBLIC_GROUP_ID = getGroupId(PUBLIC_GROUP_NAME);
			if (PUBLIC_GROUP_ID == null) {
				PUBLIC_GROUP_ID = createGroup(PUBLIC_GROUP_NAME).getId();
			}

			ASKEM_ADMIN_GROUP_ID = getGroupId(ASKEM_ADMIN_GROUP_NAME);
			if (ASKEM_ADMIN_GROUP_ID == null) {
				ASKEM_ADMIN_GROUP_ID = createGroup(ASKEM_ADMIN_GROUP_NAME).getId();
			}

			final UsersResource usersResource = keycloak.realm(REALM_NAME).users();
			final List<UserRepresentation> users = usersResource.list();
			for (final UserRepresentation userRepresentation : users) {
				if (userRepresentation.getEmail() == null
						|| userRepresentation.getEmail().isBlank()) {
					continue;
				}
				final UserResource userResource = usersResource.get(userRepresentation.getId());
				final String userId = userRepresentation.getId();
				final SchemaObject user = new SchemaObject(Schema.Type.USER, userId);
				final SchemaObject publicGroup = new SchemaObject(Schema.Type.GROUP, PUBLIC_GROUP_ID);
				final SchemaObject adminGroup = new SchemaObject(Schema.Type.GROUP, ASKEM_ADMIN_GROUP_ID);

				for (final RoleRepresentation roleRepresentation :
						userResource.roles().getAll().getRealmMappings()) {
					if (roleRepresentation.getDescription().isBlank()) {
						switch (roleRepresentation.getName()) {
							case "user":
								try {
									createRelationship(user, publicGroup, Schema.Relationship.MEMBER);
								} catch (final RelationshipAlreadyExistsException e) {
									log.error("Failed to add user {} to Public Group", userId, e);
								}
								break;
							case "admin":
								try {
									createRelationship(user, publicGroup, Schema.Relationship.ADMIN);
								} catch (final RelationshipAlreadyExistsException e) {
									log.error("Failed to add admin {} to Public Group", userId, e);
								}
								try {
									createRelationship(user, adminGroup, Schema.Relationship.ADMIN);
								} catch (final RelationshipAlreadyExistsException e) {
									log.error("Failed to add admin {} to Admin Group", userId, e);
								}
								break;
						}
					}
				}
			}
		} else {
			PUBLIC_GROUP_ID = getGroupId(PUBLIC_GROUP_NAME);
			ASKEM_ADMIN_GROUP_ID = getGroupId(ASKEM_ADMIN_GROUP_NAME);

			// Ensure ASKEM_ADMIN_GROUP can write to all Projects
			final SchemaObject askemAdminGroup = new SchemaObject(Schema.Type.GROUP, ASKEM_ADMIN_GROUP_ID);
			final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
			final List<UUID> projectIds = rebac.lookupResources(Schema.Type.PROJECT, getCurrentConsistency());
			for (final UUID projectId : projectIds) {
				final SchemaObject project = new SchemaObject(Schema.Type.PROJECT, projectId.toString());
				try {
					createRelationship(askemAdminGroup, project, Schema.Relationship.WRITER);
				} catch (final RelationshipAlreadyExistsException ignore) {
				}
			}
		}
		API_SERVICE_USER_ID = getUserId(API_SERVICE_USER_NAME);
	}

	private String getUserId(final String name) {
		final List<UserRepresentation> users =
				keycloak.realm(REALM_NAME).users().search(name);
		for (final UserRepresentation user : users) {
			if (user.getUsername().equals(API_SERVICE_USER_NAME)) {
				return user.getId();
			}
		}
		throw new RuntimeException("Api service user account does not exist");
	}

	private String getGroupId(final String name) {
		final List<GroupRepresentation> groups =
				keycloak.realm(REALM_NAME).groups().groups(name, true, 0, Integer.MAX_VALUE, true);
		for (final GroupRepresentation group : groups) {
			if (group.getPath().equals("/" + name)) {
				return group.getId();
			}
		}
		return null;
	}

	private Response createGroupMaybeParent(final String parentId, final GroupRepresentation group) {
		if (parentId == null) {
			return keycloak.realm(REALM_NAME).groups().add(group);
		} else {
			final GroupResource parentGroup =
					keycloak.realm(REALM_NAME).groups().group(parentId);
			return parentGroup.subGroup(group);
		}
	}

	private PermissionGroup createGroup(final String parentId, final String name) {
		final GroupRepresentation groupRepresentation = new GroupRepresentation();
		groupRepresentation.setName(name);

		final Response response = createGroupMaybeParent(parentId, groupRepresentation);
		switch (response.getStatus()) {
			case 201:
				return new PermissionGroup(CreatedResponseUtil.getCreatedId(response), name);
			case 409:
				log.error("Conflicting Name");
				return null;
			default:
				log.error("Other Error: " + response.getStatus());
				return null;
		}
	}

	public PermissionGroup createGroup(final String name) {
		return this.createGroup(null, name);
	}

	public PermissionUser getUser(final String id) {
		final UsersResource usersResource = keycloak.realm(REALM_NAME).users();
		final UserResource userResource = usersResource.get(id);
		final UserRepresentation userRepresentation = userResource.toRepresentation();
		return new PermissionUser(
				userRepresentation.getId(),
				userRepresentation.getFirstName(),
				userRepresentation.getLastName(),
				userRepresentation.getEmail());
	}

	public List<PermissionUser> getUsers() {
		final List<PermissionUser> response = new ArrayList<>();
		final UsersResource usersResource = keycloak.realm(REALM_NAME).users();
		final Integer maxUsers = usersResource.count();
		final List<UserRepresentation> users = usersResource.list(0, maxUsers + 1);
		for (final UserRepresentation userRepresentation : users) {
			if (userRepresentation.getEmail() == null
					|| userRepresentation.getEmail().isBlank()) {
				continue;
			}
			final UserResource userResource = usersResource.get(userRepresentation.getId());

			final List<PermissionRole> roles = new ArrayList<>();
			for (final RoleRepresentation roleRepresentation :
					userResource.roles().getAll().getRealmMappings()) {
				if (roleRepresentation.getDescription().isBlank()) {
					final PermissionRole role = new PermissionRole(
							roleRepresentation.getId(), roleRepresentation.getName()
							// no users are acquired (to avoid circular references etc)
							);
					roles.add(role);
				}
			}

			final PermissionUser user = new PermissionUser(
					userRepresentation.getId(),
					userRepresentation.getFirstName(),
					userRepresentation.getLastName(),
					userRepresentation.getEmail(),
					roles);
			response.add(user);
		}
		return response;
	}

	public List<PermissionRole> getRoles() {
		final List<PermissionRole> response = new ArrayList<>();

		final RolesResource rolesResource = keycloak.realm(REALM_NAME).roles();
		for (final RoleRepresentation roleRepresentation : rolesResource.list()) {
			if (roleRepresentation.getDescription().isBlank()) {
				final RoleResource roleResource = rolesResource.get(roleRepresentation.getName());
				final List<PermissionUser> users = new ArrayList<>();
				for (final UserRepresentation userRepresentation : roleResource.getRoleUserMembers()) {
					if (userRepresentation.getEmail() != null) {
						final PermissionUser user = new PermissionUser(
								userRepresentation.getId(),
								userRepresentation.getFirstName(),
								userRepresentation.getLastName(),
								userRepresentation.getEmail()
								// no roles are acquired (to avoid circular references etc)
								);
						users.add(user);
					}
				}

				final PermissionRole role =
						new PermissionRole(roleRepresentation.getId(), roleRepresentation.getName(), users);
				response.add(role);
			}
		}

		return response;
	}

	public List<PermissionGroup> getGroups() {
		final List<PermissionGroup> response = new ArrayList<>();

		final List<GroupRepresentation> groups =
				keycloak.realm(REALM_NAME).groups().groups();
		for (final GroupRepresentation groupRepresentation : groups) {
			final PermissionGroup group =
					new PermissionGroup(groupRepresentation.getId(), groupRepresentation.getName());
			response.add(group);
		}

		return response;
	}

	public PermissionGroup getGroup(final String id) {
		final GroupResource groupResource = keycloak.realm(REALM_NAME).groups().group(id);
		final GroupRepresentation groupRepresentation = groupResource.toRepresentation();
		final PermissionGroup permissionGroup =
				new PermissionGroup(groupRepresentation.getId(), groupRepresentation.getName());

		return permissionGroup;
	}

	/**
	 * Determines if user `who` has `permission` on resource `what`
	 *
	 * @param who User requesting access
	 * @param permission Granted permission
	 * @param what Resource being questioned
	 * @return true if resource grants permission for user, otherwise false
	 * @throws Exception some sort of ReBAC error, most likely SpiceDB is unavailable
	 */
	public boolean can(final SchemaObject who, final Schema.Permission permission, final SchemaObject what)
			throws Exception {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		if (SPICEDB_LAUNCHMODE.equals("TEST")) {
			return true;
		}
		return rebac.checkPermission(who, permission, what, getCurrentConsistency());
	}

	public boolean isMemberOf(final SchemaObject who, final SchemaObject what) throws Exception {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.checkPermission(who, Schema.Permission.MEMBERSHIP, what, getCurrentConsistency());
	}

	public boolean isCreator(final SchemaObject who, final SchemaObject what) throws Exception {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.hasRelationship(who, Schema.Relationship.CREATOR, what, getCurrentConsistency());
	}

	public void createRelationship(
			final SchemaObject who, final SchemaObject what, final Schema.Relationship relationship)
			throws Exception, RelationshipAlreadyExistsException {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		CURRENT_ZED_TOKEN = rebac.createRelationship(who, relationship, what);
	}

	public void removeRelationship(
			final SchemaObject who, final SchemaObject what, final Schema.Relationship relationship)
			throws Exception, RelationshipAlreadyExistsException {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		CURRENT_ZED_TOKEN = rebac.removeRelationship(who, relationship, what);
	}

	private Consistency getCurrentConsistency() {
		if (CURRENT_ZED_TOKEN == null) {
			return Consistency.newBuilder().setFullyConsistent(true).build();
		}
		final Core.ZedToken zedToken =
				Core.ZedToken.newBuilder().setToken(CURRENT_ZED_TOKEN).build();
		return Consistency.newBuilder().setAtLeastAsFresh(zedToken).build();
	}

	public List<RebacPermissionRelationship> getRelationships(final SchemaObject what) throws Exception {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.getRelationship(what, getCurrentConsistency());
	}

	public ResponseEntity<Void> deleteRoleFromUser(final String roleName, final String userId) {
		final UsersResource usersResource = keycloak.realm(REALM_NAME).users();
		final UserResource userResource = usersResource.get(userId);
		try {
			// test to see if user was found
			userResource.toRepresentation();
		} catch (final Exception ignore) {
			log.error("There is no user with id {}", userId);
			return ResponseEntity.notFound().build();
		}

		RoleRepresentation roleToRemove = null;
		final RolesResource rolesResource = keycloak.realm(REALM_NAME).roles();
		for (final RoleRepresentation roleRepresentation : rolesResource.list()) {
			// RoleResource roleResource = rolesResource.get(roleRepresentation.getName());
			if (roleRepresentation.getName().equals(roleName)) {
				roleToRemove = roleRepresentation;
			}
		}

		if (roleToRemove == null) {
			log.error("There is no role {}", roleName);
			return ResponseEntity.notFound().build();
		}

		final String resourceUrl = composeResourceUrl(
				config.getKeycloak().getUrl() + "/admin/", REALM_NAME, "users/" + userId + "/role-mappings/realm");

		final List<RoleRepresentation> roles = new ArrayList<>();
		roles.add(roleToRemove);

		try {
			doDeleteJSON(resourceUrl, getKeycloakBearerToken(), roles);
			return ResponseEntity.ok().build();
		} catch (final Exception e) {
			log.error(e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}

	public ResponseEntity<Void> addRoleToUser(final String roleName, final String userId) {
		final UsersResource usersResource = keycloak.realm(REALM_NAME).users();
		final UserResource userResource = usersResource.get(userId);
		try {
			// test to see if user was found
			userResource.toRepresentation();
		} catch (final Exception ignore) {
			log.error("There is no user with id {}", userId);
			return ResponseEntity.notFound().build();
		}

		RoleRepresentation roleToAdd = null;
		final RolesResource rolesResource = keycloak.realm(REALM_NAME).roles();
		for (final RoleRepresentation roleRepresentation : rolesResource.list()) {
			final RoleResource roleResource = rolesResource.get(roleRepresentation.getName());
			if (roleRepresentation.getName().equals(roleName)) {
				roleToAdd = roleRepresentation;
				for (final UserRepresentation user : roleResource.getRoleUserMembers()) {
					if (user.getId().equals(userId)) {
						log.debug("Add Role To User: already belongs");
						return ResponseEntity.status(HttpStatusCode.valueOf(304))
								.build();
					}
				}
			}
		}

		if (roleToAdd == null) {
			log.debug("there is no role by that name");
			return ResponseEntity.notFound().build();
		}

		final String resourceUrl = composeResourceUrl(
				config.getKeycloak().getUrl() + "/admin/", REALM_NAME, "users/" + userId + "/role-mappings/realm");

		final List<RoleRepresentation> roles = new ArrayList<>();
		roles.add(roleToAdd);

		try {
			doPostJSON(resourceUrl, getKeycloakBearerToken(), roles);
			return ResponseEntity.ok().build();
		} catch (final Exception e) {
			log.error(e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}

	public List<UUID> lookupResources(
			final SchemaObject who, final Schema.Permission permission, final Schema.Type type) throws Exception {
		final ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.lookupResources(type, permission, who, getCurrentConsistency());
	}

	public static boolean isServiceUser(final String id) {
		return API_SERVICE_USER_ID != null && API_SERVICE_USER_ID.equals(id);
	}
}
