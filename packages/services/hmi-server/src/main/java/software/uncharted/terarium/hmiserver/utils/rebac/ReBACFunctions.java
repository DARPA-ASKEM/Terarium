package software.uncharted.terarium.hmiserver.utils.rebac;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.authzed.api.v1.Core.ObjectReference;
import com.authzed.api.v1.Core.Relationship;
import com.authzed.api.v1.Core.RelationshipUpdate;
import com.authzed.api.v1.Core.SubjectReference;
import com.authzed.api.v1.PermissionService;
import com.authzed.api.v1.PermissionService.Consistency;
import com.authzed.api.v1.PermissionService.ReadRelationshipsResponse;
import com.authzed.api.v1.PermissionService.RelationshipFilter;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.grpcutil.BearerToken;

import io.grpc.ManagedChannel;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacPermissionRelationship;

public class ReBACFunctions {
    final PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService;

    public ReBACFunctions(ManagedChannel channel, BearerToken bearerToken) {
        this.permissionsService = PermissionsServiceGrpc
        .newBlockingStub(channel)
        .withCallCredentials(bearerToken);
    }

    private ObjectReference createObject(String type, String id) {
        return ObjectReference.newBuilder()
        .setObjectType(type)
        .setObjectId(id)
        .build();
    }

    private SubjectReference createSubject(String type, String id) {
        return SubjectReference.newBuilder()
        .setObject(createObject(type, id))
        .build();
    }

    private static String HAS_PERMISSION = "PERMISSIONSHIP_HAS_PERMISSION";

    public boolean checkPermission(SchemaObject subject, Schema.Permission permission, SchemaObject resource, Consistency consistency) throws Exception {
        return checkPermission(subject.type.toString(), subject.id, permission.toString(), resource.type.toString(), resource.id, consistency);
    }

    public boolean checkPermission(String subjectType, String subjectId, String permission, String resourceType, String resourceId, Consistency consistency) throws Exception {
        PermissionService.CheckPermissionRequest request = PermissionService.CheckPermissionRequest.newBuilder()
        .setConsistency(consistency)
        .setResource(createObject(resourceType, resourceId))
        .setSubject(createSubject(subjectType, subjectId))
        .setPermission(permission)
        .build();

        PermissionService.CheckPermissionResponse response = permissionsService.checkPermission(request);
        return response.getPermissionship().name().equalsIgnoreCase(HAS_PERMISSION);
    }

    public String createRelationship(SchemaObject subject, Schema.Relationship relationship, SchemaObject target) throws Exception {
        return createRelationship(subject.type.toString(), subject.id, relationship.toString(), target.type.toString(), target.id);
    }

    public String createRelationship(String subjectType, String subjectId, String relationship, String targetType, String targetId) throws Exception {
        PermissionService.WriteRelationshipsRequest request = PermissionService.WriteRelationshipsRequest.newBuilder()
        .addUpdates(
            RelationshipUpdate.newBuilder()
            .setOperation(RelationshipUpdate.Operation.OPERATION_CREATE)
            .setRelationship(
                Relationship.newBuilder()
                .setResource(createObject(targetType, targetId))
                .setRelation(relationship)
                .setSubject(createSubject(subjectType, subjectId))
                .build())
            .build())
        .build();

        PermissionService.WriteRelationshipsResponse response = permissionsService.writeRelationships(request);
        return response.getWrittenAt().getToken();
    }

	public List<RebacPermissionRelationship> getRelationship(SchemaObject resource, Consistency consistency) throws Exception {
		return getRelationship(resource.type.toString(), resource.id, consistency);
	}
	public List<RebacPermissionRelationship> getRelationship(String resourceType, String resourceId, Consistency consistency) throws Exception {
		List<RebacPermissionRelationship> relationships = new ArrayList<>();
		PermissionService.ReadRelationshipsRequest request = PermissionService.ReadRelationshipsRequest.newBuilder()
			.setConsistency(consistency)
			.setRelationshipFilter(
				RelationshipFilter.newBuilder()
					.setResourceType(resourceType)
					.setOptionalResourceId(resourceId))
			.build();

		Iterator<ReadRelationshipsResponse> iter = permissionsService.readRelationships(request);

		System.out.println("Relationships:");
		while (iter.hasNext()) {
			PermissionService.ReadRelationshipsResponse response = iter.next();
			ObjectReference subject = response.getRelationship().getSubject().getObject();
			ObjectReference resource = response.getRelationship().getResource();
			RebacPermissionRelationship rebacRelationship = new RebacPermissionRelationship(subject, response.getRelationship().getRelation(), resource);
			relationships.add(rebacRelationship);
		}
		return relationships;
	}
}
