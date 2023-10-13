package software.uncharted.terarium.hmiserver.utils.rebac;


import com.authzed.api.v1.PermissionService.Consistency;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionGroup;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionUser;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacPermissionRelationship;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReBACService {

	Keycloak keycloak;

	private final Config config;

	@Value("${terarium.keycloak.realm}")
	String REALM_NAME;
	@Value("${spicedb.shared-key}")
	String SPICEDB_PRESHARED_KEY;
	@Value("${spicedb.target}")
	String SPICEDB_TARGET;
	@Value("${spicedb.launchmode}")
	String SPICEDB_LAUNCHMODE;

	private BearerToken spiceDbBearerToken;
	private ManagedChannel channel;
	private final SchemaManager schemaManager = new SchemaManager();

	public static final String PUBLIC_GROUP_NAME = "Public";
	public String PUBLIC_GROUP_ID;
	public static final String ASKEM_ADMIN_GROUP_NAME = "ASKEM Admins";
	public String ASKEM_ADMIN_GROUP_ID;


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
			channel = InProcessChannelBuilder
				.forName("TestSpiceDB")
				.build();
			return;
		} else {
			ManagedChannelBuilder builder = ManagedChannelBuilder.forTarget(SPICEDB_TARGET);
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

			PUBLIC_GROUP_ID = createGroup(PUBLIC_GROUP_NAME);
			ASKEM_ADMIN_GROUP_ID = createGroup(ASKEM_ADMIN_GROUP_NAME);

			UsersResource usersResource = keycloak.realm(REALM_NAME).users();
			List<UserRepresentation> users = usersResource.list();
			for (UserRepresentation userRepresentation : users) {
				if (userRepresentation.getEmail() == null || userRepresentation.getEmail().isBlank()) {
					continue;
				}
				UserResource userResource = usersResource.get(userRepresentation.getId());
				String userId = userRepresentation.getId();
				SchemaObject user = new SchemaObject(Schema.Type.USER, userId);
				SchemaObject publicGroup = new SchemaObject(Schema.Type.GROUP, PUBLIC_GROUP_ID);
				SchemaObject adminGroup = new SchemaObject(Schema.Type.GROUP, ASKEM_ADMIN_GROUP_ID);

				for (RoleRepresentation roleRepresentation : userResource.roles().getAll().getRealmMappings()) {
					if (roleRepresentation.getDescription().isBlank()) {
						switch (roleRepresentation.getName()) {
							case "user":
								try {
									createRelationship(user, publicGroup, Schema.Relationship.MEMBER);
								} catch (RelationshipAlreadyExistsException e) {
									log.error("Failed to add user {} to Public Group", userId, e);
								}
								break;
							case "admin":
								try {
										createRelationship(user, publicGroup, Schema.Relationship.ADMIN);
								} catch (RelationshipAlreadyExistsException e) {
									log.error("Failed to add admin {} to Public Group", userId, e);
								}
								try {
									createRelationship(user, adminGroup, Schema.Relationship.ADMIN);
								} catch (RelationshipAlreadyExistsException e) {
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
		}
	}

	private String getGroupId(String name) {
		List<GroupRepresentation> groups = keycloak.realm(REALM_NAME).groups().groups(name, true, 0, Integer.MAX_VALUE, true);
		for (GroupRepresentation group : groups) {
			if (group.getPath().equals("/" + name)) {
				return group.getId();
			}
		}
		return null;
	}

	private Response createGroupMaybeParent(String parentId, GroupRepresentation group) {
		if (parentId == null) {
			return keycloak.realm(REALM_NAME).groups().add(group);
		} else {
			GroupResource parentGroup = keycloak.realm(REALM_NAME).groups().group(parentId);
			return parentGroup.subGroup(group);
		}
	}

	private String createGroup(String parentId, String name) {
		GroupRepresentation groupRepresentation = new GroupRepresentation();
		groupRepresentation.setName(name);

		Response response = createGroupMaybeParent(parentId, groupRepresentation);
		switch (response.getStatus()) {
			case 201:
				return CreatedResponseUtil.getCreatedId(response);
			case 409:
				System.err.println("Conflicting Name");
				return null;
			default:
				System.err.println("Other Error: " + response.getStatus());
				return null;
		}
	}

	private String createGroup(String name) {
		return this.createGroup(null, name);
	}

	public PermissionGroup addGroup(String name) {
		return new PermissionGroup(
			createGroup(name),
			name);
	}

	public PermissionUser getUser(String id) {
		UsersResource usersResource = keycloak.realm(REALM_NAME).users();
		UserResource userResource = usersResource.get(id);
		UserRepresentation userRepresentation = userResource.toRepresentation();
		return new PermissionUser(
			userRepresentation.getId(),
			userRepresentation.getFirstName(),
			userRepresentation.getLastName(),
			userRepresentation.getEmail());
	}

	public List<PermissionUser> getUsers() {
		List<PermissionUser> response = new ArrayList<>();
		UsersResource usersResource = keycloak.realm(REALM_NAME).users();
		List<UserRepresentation> users = usersResource.list();
		for (UserRepresentation userRepresentation : users) {
			if (userRepresentation.getEmail() == null || userRepresentation.getEmail().isBlank()) {
				continue;
			}
			UserResource userResource = usersResource.get(userRepresentation.getId());
			boolean hasUserRole = false;
			for (RoleRepresentation roleRepresentation : userResource.roles().getAll().getRealmMappings()) {
				if (roleRepresentation.getDescription().isBlank()) {
					if (roleRepresentation.getName().equals("user")) {
						hasUserRole = true;
					}
				}
			}
			if (hasUserRole) {
				PermissionUser user = new PermissionUser(
					userRepresentation.getId(),
					userRepresentation.getFirstName(),
					userRepresentation.getLastName(),
					userRepresentation.getEmail());
				response.add(user);
			}
		}
		return response;
	}

	public List<PermissionGroup> getGroups() {
		List<PermissionGroup> response = new ArrayList<>();

		List<GroupRepresentation> groups = keycloak.realm(REALM_NAME).groups().groups();
		for (GroupRepresentation groupRepresentation : groups) {
			PermissionGroup group = new PermissionGroup(
				groupRepresentation.getId(),
				groupRepresentation.getName());
			response.add(group);
		}

		return response;
	}

	public PermissionGroup getGroup(String id) {
		GroupResource groupResource = keycloak.realm(REALM_NAME).groups().group(id);
		GroupRepresentation groupRepresentation = groupResource.toRepresentation();
		PermissionGroup permissionGroup = new PermissionGroup(
			groupRepresentation.getId(),
			groupRepresentation.getName());

		return permissionGroup;
	}

	public boolean canRead(SchemaObject who, SchemaObject what) throws Exception {
		Consistency full = Consistency.newBuilder().setFullyConsistent(true).build();
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.checkPermission(who, Schema.Permission.READ, what, full);
	}

	public boolean canWrite(SchemaObject who, SchemaObject what) throws Exception {
		Consistency full = Consistency.newBuilder().setFullyConsistent(true).build();
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.checkPermission(who, Schema.Permission.WRITE, what, full);
	}

	public boolean hasMembership(SchemaObject who, SchemaObject what) throws Exception {
		Consistency full = Consistency.newBuilder().setFullyConsistent(true).build();
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.checkPermission(who, Schema.Permission.MEMBERSHIP, what, full);
	}

	public boolean canAdministrate(SchemaObject who, SchemaObject what) throws Exception {
		Consistency full = Consistency.newBuilder().setFullyConsistent(true).build();
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.checkPermission(who, Schema.Permission.ADMINISTRATE, what, full);
	}

	public boolean isCreator(SchemaObject who, SchemaObject what) throws Exception {
		Consistency full = Consistency.newBuilder().setFullyConsistent(true).build();
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.hasRelationship(who, Schema.Relationship.CREATOR, what, full);
	}

	public void createRelationship(SchemaObject who, SchemaObject what, Schema.Relationship relationship) throws Exception, RelationshipAlreadyExistsException {
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		rebac.createRelationship(who, relationship, what);
	}

	public void removeRelationship(SchemaObject who, SchemaObject what, Schema.Relationship relationship) throws Exception, RelationshipAlreadyExistsException {
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		rebac.removeRelationship(who, relationship, what);
	}

	public List<RebacPermissionRelationship> getRelationships(SchemaObject what) throws Exception {
		Consistency full = Consistency.newBuilder().setFullyConsistent(true).build();
		ReBACFunctions rebac = new ReBACFunctions(channel, spiceDbBearerToken);
		return rebac.getRelationship(what, full);
	}
}
