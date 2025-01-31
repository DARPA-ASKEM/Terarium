package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Contributor;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionGroup;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionRole;
import software.uncharted.terarium.hmiserver.models.permissions.PermissionUser;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.RelationsipAlreadyExistsException.RelationshipAlreadyExistsException;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacObject;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacPermissionRelationship;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacProject;

@RequiredArgsConstructor
@Service
public class ProjectPermissionsService {

	final ReBACService reBACService;

	private List<Contributor> getContributorsByRelationships(
		final RebacProject rebacProject,
		final Schema.Relationship... relationships
	) throws Exception {
		final Map<String, Contributor> contributorMap = new HashMap<>();

		final List<RebacPermissionRelationship> permissionRelationships = rebacProject.getPermissionRelationships();
		for (final RebacPermissionRelationship permissionRelationship : permissionRelationships) {
			final Schema.Relationship relationship = permissionRelationship.getRelationship();
			if (relationships.length == 0 || List.of(relationships).contains(relationship)) {
				if (permissionRelationship.getSubjectType().equals(Schema.Type.USER)) {
					final PermissionUser user = reBACService.getUser(permissionRelationship.getSubjectId());
					if (user != null) {
						final String name = user.getFirstName() + " " + user.getLastName();
						if (!contributorMap.containsKey(name)) {
							contributorMap.put(name, new Contributor(name, user.getId(), relationship));
						}
					}
				} else if (permissionRelationship.getSubjectType().equals(Schema.Type.GROUP)) {
					final PermissionGroup group = reBACService.getGroup(permissionRelationship.getSubjectId());
					if (!contributorMap.containsKey(group.getName())) {
						contributorMap.put(group.getName(), new Contributor(group.getName(), null, relationship));
					}
				}
			}
		}

		return new ArrayList<>(contributorMap.values());
	}

	/**
	 * Capture the subet of RebacPermissionRelationships for a given Project which are capable of reading a project
	 *
	 * @param rebacProject the Project to collect RebacPermissionRelationships of.
	 * @return List of Users and Groups who have read capability of the rebacProject
	 */
	@Cacheable(value = "projectreaders", key = "#rebacProject.id", unless = "#result == null")
	public List<Contributor> getReaders(final RebacProject rebacProject) throws Exception {
		List<Contributor> readers = getContributorsByRelationships(
			rebacProject,
			Schema.Relationship.CREATOR,
			Schema.Relationship.ADMIN,
			Schema.Relationship.WRITER,
			Schema.Relationship.READER
		)
			.stream()
			.filter(contributor -> contributor.isUser()) // remove groups
			.toList();

		// add admin users
		PermissionRole role = reBACService.getAdminRole();
		if (role != null) {
			for (PermissionUser user : role.getUsers()) {
				final String name = user.getFirstName() + " " + user.getLastName();
				readers.add(new Contributor(name, user.getId(), Schema.Relationship.ADMIN));
			}
		}

		return readers;
	}

	/**
	 * Capture the subset of RebacPermissionRelationships for a given Project.
	 *
	 * @param rebacProject the Project to collect RebacPermissionRelationships of.
	 * @return List of Users and Groups who have edit capability of the rebacProject
	 */
	@Cacheable(value = "projectcontributors", key = "#rebacProject.id", unless = "#result == null")
	public List<Contributor> getContributors(final RebacProject rebacProject) throws Exception {
		return getContributorsByRelationships(
			rebacProject,
			Schema.Relationship.CREATOR,
			Schema.Relationship.ADMIN,
			Schema.Relationship.WRITER
		);
	}

	@Caching(
		evict = {
			@CacheEvict(value = "projectcontributors", key = "#what.id"),
			@CacheEvict(value = "projectreaders", key = "#what.id")
		}
	)
	public void setProjectPermissions(final RebacProject what, final RebacObject who, final String relationship)
		throws Exception {
		try {
			what.setPermissionRelationships(who, relationship);
		} catch (final RelationshipAlreadyExistsException ignore) {}
	}

	@Caching(
		evict = {
			@CacheEvict(value = "projectcontributors", key = "#what.id"),
			@CacheEvict(value = "projectreaders", key = "#what.id")
		}
	)
	public ResponseEntity<JsonNode> updateProjectPermissions(
		final RebacProject what,
		final RebacObject who,
		final String oldRelationship,
		final String newRelationship
	) throws Exception {
		try {
			what.removePermissionRelationships(who, oldRelationship);
			what.setPermissionRelationships(who, newRelationship);
			return ResponseEntity.ok().build();
		} catch (final RelationshipAlreadyExistsException e) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
	}

	@Caching(
		evict = {
			@CacheEvict(value = "projectcontributors", key = "#what.id"),
			@CacheEvict(value = "projectreaders", key = "#what.id")
		}
	)
	public void removeProjectPermissions(final RebacProject what, final RebacObject who, final String relationship)
		throws Exception {
		try {
			what.removePermissionRelationships(who, relationship);
		} catch (final RelationshipAlreadyExistsException ignore) {}
	}
}
