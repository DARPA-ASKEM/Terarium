package software.uncharted.terarium.hmiserver.service.data;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.Artifact;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;

public class ArtifactServiceTests extends TerariumApplicationTests {

	@Autowired
	ArtifactService artifactService;

	@Autowired
	private ProjectService projectService;

	Project project;

	static ObjectMapper objectMapper = new ObjectMapper();

	Artifact createArtifact(final String key) {
		final Artifact artifact = new Artifact();
		artifact.setName("test-artifact-name-" + key);
		artifact.setDescription("test-artifact-description-" + key);
		artifact.setFileNames(Arrays.asList("never", "gonna", "give", "you", "up"));
		artifact.setPublicAsset(true);

		for (final String filename : artifact.getFileNames()) {
			try {
				artifactService.uploadFile(
						artifact.getId(), filename, ContentType.TEXT_PLAIN, new String("Test content").getBytes());
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		return artifact;
	}

	@BeforeEach
	public void setup() throws IOException {
		project = projectService.createProject(
				(Project) new Project().setName("test-project-name").setDescription("my description"));
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateArtifact() {
		final Artifact before = (Artifact) createArtifact("0").setId(UUID.randomUUID());
		try {
			final Artifact after = artifactService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);

			Assertions.assertEquals(before.getId(), after.getId());
			Assertions.assertNotNull(after.getId());
			Assertions.assertNotNull(after.getCreatedOn());

		} catch (final Exception e) {
			Assertions.fail(e);
		}
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCantCreateDuplicates() {
		final Artifact artifact = (Artifact) createArtifact("0").setId(UUID.randomUUID());
		try {
			artifactService.createAsset(artifact, project.getId(), ASSUME_WRITE_PERMISSION);
			artifactService.createAsset(artifact, project.getId(), ASSUME_WRITE_PERMISSION);
			Assertions.fail("Should have thrown an exception");

		} catch (final Exception e) {
			Assertions.assertTrue(e.getMessage().contains("already exists"));
		}
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetArtifacts() throws IOException {
		artifactService.createAsset(createArtifact("0"), project.getId(), ASSUME_WRITE_PERMISSION);
		artifactService.createAsset(createArtifact("1"), project.getId(), ASSUME_WRITE_PERMISSION);
		artifactService.createAsset(createArtifact("2"), project.getId(), ASSUME_WRITE_PERMISSION);

		final List<Artifact> sims = artifactService.getPublicNotTemporaryAssets(0, 10);

		Assertions.assertEquals(3, sims.size());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetArtifactById() throws IOException {
		final Artifact artifact = artifactService.createAsset(createArtifact("0"), project.getId(),
				ASSUME_WRITE_PERMISSION);
		final Artifact fetchedArtifact = artifactService
				.getAsset(artifact.getId(), ASSUME_WRITE_PERMISSION)
				.get();

		Assertions.assertEquals(artifact, fetchedArtifact);
		Assertions.assertEquals(artifact.getId(), fetchedArtifact.getId());
		Assertions.assertEquals(artifact.getCreatedOn(), fetchedArtifact.getCreatedOn());
		Assertions.assertEquals(artifact.getUpdatedOn(), fetchedArtifact.getUpdatedOn());
		Assertions.assertEquals(artifact.getDeletedOn(), fetchedArtifact.getDeletedOn());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUpdateArtifact() throws Exception {

		final Artifact artifact = artifactService.createAsset(createArtifact("A"), project.getId(),
				ASSUME_WRITE_PERMISSION);
		artifact.setName("new name");

		final Artifact updatedArtifact = artifactService.updateAsset(artifact, project.getId(), ASSUME_WRITE_PERMISSION)
				.orElseThrow();

		Assertions.assertEquals(artifact, updatedArtifact);
		Assertions.assertNotNull(updatedArtifact.getUpdatedOn());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteArtifact() throws Exception {

		final Artifact artifact = artifactService.createAsset(createArtifact("B"), project.getId(),
				ASSUME_WRITE_PERMISSION);

		artifactService.deleteAsset(artifact.getId(), project.getId(), ASSUME_WRITE_PERMISSION);

		final Optional<Artifact> deleted = artifactService.getAsset(artifact.getId(),
				ASSUME_WRITE_PERMISSION);

		Assertions.assertTrue(deleted.isEmpty());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCloneArtifact() throws Exception {

		Artifact artifact = createArtifact("A");

		artifact = artifactService.createAsset(artifact, project.getId(), ASSUME_WRITE_PERMISSION);

		final Artifact cloned = artifact.clone();

		Assertions.assertNotEquals(artifact.getId(), cloned.getId());
		Assertions.assertEquals(artifact.getName(), cloned.getName());
		Assertions.assertEquals(
				artifact.getFileNames().size(), cloned.getFileNames().size());
		Assertions.assertEquals(
				artifact.getFileNames().get(0), cloned.getFileNames().get(0));
	}
}
