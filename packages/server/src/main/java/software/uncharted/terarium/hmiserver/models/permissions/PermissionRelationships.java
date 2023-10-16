package software.uncharted.terarium.hmiserver.models.permissions;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

import java.util.ArrayList;
import java.util.List;

@TSModel
@Data
@Accessors(chain = true)
public class PermissionRelationships {
	private List<PermissionGroup> permissionGroups = new ArrayList<>();
	private List<PermissionUser> permissionUsers = new ArrayList<>();
	private List<PermissionProject> permissionProjects = new ArrayList<>();

	public void addUser(PermissionUser permissionUser, Schema.Relationship relationship) {
		permissionUser.setRelationship(relationship.toString());
		permissionUsers.add(permissionUser);
	}

	public void addGroup(PermissionGroup permissionGroup, Schema.Relationship relationship) {
		permissionGroup.setRelationship(relationship.toString());
		permissionGroups.add(permissionGroup);
	}

	public void addProject(String id, Schema.Relationship relationship) {
		permissionProjects.add(new PermissionProject(id, relationship.toString()));
	}
}
