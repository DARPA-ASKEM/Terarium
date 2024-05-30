package software.uncharted.terarium.hmiserver.service.data;

import io.micrometer.observation.annotation.Observed;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.User;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.repository.UserRepository;
import software.uncharted.terarium.hmiserver.repository.data.ProjectRepository;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacProject;
import software.uncharted.terarium.hmiserver.utils.rebac.askem.RebacUser;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProjectService {

	final ProjectRepository projectRepository;
	final UserRepository userRepository;
	final ReBACService reBACService;
	final Messages messages;

	@Observed(name = "function_profile")
	public List<Project> getProjects() {
		return projectRepository.findAll();
	}

	@Observed(name = "function_profile")
	public List<Project> getProjects(final List<UUID> ids) {
		return projectRepository.findAllById(ids);
	}

	@Observed(name = "function_profile")
	public List<Project> getActiveProjects(final List<UUID> ids) {
		return projectRepository.findAllByIdInAndDeletedOnIsNull(ids);
	}

	@Observed(name = "function_profile")
	public Optional<Project> getProject(final UUID id) {
		final Optional<Project> project = projectRepository.getByIdAndDeletedOnIsNull(id);
		if (project.isPresent() && project.get().getUserId() != null) {
			final Optional<User> user = userRepository.findById(project.get().getUserId());
			user.ifPresent(value -> project.get().setUserName(value.getName()));
		}
		return project;
	}

	@Observed(name = "function_profile")
	public Project createProject(final Project project) {
		return projectRepository.save(project);
	}

	@Observed(name = "function_profile")
	public Optional<Project> updateProject(final Project project) {
		if (!projectRepository.existsById(project.getId())) {
			return Optional.empty();
		}

		final Project existingProject =
				projectRepository.getByIdAndDeletedOnIsNull(project.getId()).orElseThrow();

		// merge the existing project with values from the new project
		final Project mergedProject = Project.mergeProjectFields(existingProject, project);

		return Optional.of(projectRepository.save(mergedProject));
	}

	@Observed(name = "function_profile")
	public boolean delete(final UUID id) {
		final Optional<Project> project = getProject(id);
		if (project.isEmpty()) return false;
		project.get().setDeletedOn(Timestamp.from(Instant.now()));
		projectRepository.save(project.get());
		return true;
	}

	public Schema.Permission checkPermissionCanReadOrNone(final String userId, final UUID projectId)
			throws ResponseStatusException {
		try {
			final RebacUser rebacUser = new RebacUser(userId, reBACService);
			final RebacProject rebacProject = new RebacProject(projectId, reBACService);
			if (rebacUser.can(rebacProject, Schema.Permission.READ)) {
				return Schema.Permission.READ;
			}
			return Schema.Permission.NONE;
		} catch (final Exception e) {
			log.error("Error updating project", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	public Schema.Permission checkPermissionCanRead(final String userId, final UUID projectId)
			throws ResponseStatusException {
		try {
			final RebacUser rebacUser = new RebacUser(userId, reBACService);
			final RebacProject rebacProject = new RebacProject(projectId, reBACService);
			if (rebacUser.can(rebacProject, Schema.Permission.READ)) {
				return Schema.Permission.READ;
			}
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-update"));
		} catch (final Exception e) {
			log.error("Error check project permission", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	public Schema.Permission checkPermissionCanWrite(final String userId, final UUID projectId)
			throws ResponseStatusException {
		try {
			final RebacUser rebacUser = new RebacUser(userId, reBACService);
			final RebacProject rebacProject = new RebacProject(projectId, reBACService);
			if (rebacUser.can(rebacProject, Schema.Permission.WRITE)) {
				return Schema.Permission.WRITE;
			}
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-update"));
		} catch (final Exception e) {
			log.error("Error check project permission", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}

	public Schema.Permission checkPermissionCanAdministrate(final String userId, final UUID projectId)
			throws ResponseStatusException {
		try {
			final RebacUser rebacUser = new RebacUser(userId, reBACService);
			final RebacProject rebacProject = new RebacProject(projectId, reBACService);
			if (rebacUser.can(rebacProject, Schema.Permission.ADMINISTRATE)) {
				return Schema.Permission.ADMINISTRATE;
			}
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-update"));
		} catch (final Exception e) {
			log.error("Error check project permission", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("rebac.service-unavailable"));
		}
	}
}
