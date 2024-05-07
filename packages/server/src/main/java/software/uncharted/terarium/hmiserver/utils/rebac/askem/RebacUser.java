package software.uncharted.terarium.hmiserver.utils.rebac.askem;

import java.util.List;
import java.util.UUID;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionGroup;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.SchemaObject;

public class RebacUser extends RebacObject {
	private ReBACService reBACService;

	public RebacUser(String id, ReBACService reBACService) {
		super(id);
		this.reBACService = reBACService;
	}

	public SchemaObject getSchemaObject() {
		return new SchemaObject(Schema.Type.USER, getId());
	}

	public boolean can(RebacProject rebacProject, Schema.Permission permission) throws Exception {
		return reBACService.can(getSchemaObject(), permission, rebacProject.getSchemaObject());
	}

	public boolean canRead(RebacProject rebacProject) throws Exception {
		return reBACService.can(getSchemaObject(), Schema.Permission.READ, rebacProject.getSchemaObject());
	}

	public boolean canWrite(RebacProject rebacProject) throws Exception {
		return reBACService.can(getSchemaObject(), Schema.Permission.WRITE, rebacProject.getSchemaObject());
	}

	public boolean canAdministrate(RebacObject rebacObject) throws Exception {
		return reBACService.can(getSchemaObject(), Schema.Permission.ADMINISTRATE, rebacObject.getSchemaObject());
	}

	public boolean isMemberOf(RebacGroup rebacGroup) throws Exception {
		return reBACService.isMemberOf(getSchemaObject(), rebacGroup.getSchemaObject());
	}

	public void createCreatorRelationship(RebacObject rebacObject)
			throws Exception, RelationshipAlreadyExistsException {
		reBACService.createRelationship(getSchemaObject(), rebacObject.getSchemaObject(), Schema.Relationship.CREATOR);
	}

	public PermissionGroup createGroup(String name) throws Exception, RelationshipAlreadyExistsException {
		PermissionGroup group = reBACService.createGroup(name);
		reBACService.createRelationship(
				getSchemaObject(), new SchemaObject(Schema.Type.GROUP, group.getId()), Schema.Relationship.CREATOR);
		return group;
	}

	public String getPermissionFor(RebacProject rebacProject) throws Exception {
		if (reBACService.isCreator(getSchemaObject(), rebacProject.getSchemaObject())) {
			return Schema.Relationship.CREATOR.toString();
		} else if (canAdministrate(rebacProject)) {
			return Schema.Relationship.ADMIN.toString();
		} else if (canWrite(rebacProject)) {
			return Schema.Relationship.WRITER.toString();
		} else if (canRead(rebacProject)) {
			return Schema.Relationship.READER.toString();
		}
		return "none";
	}

	public List<UUID> lookupProjects() throws Exception {
		return reBACService.lookupResources(getSchemaObject(), Schema.Permission.READ, Schema.Type.PROJECT);
	}
}
