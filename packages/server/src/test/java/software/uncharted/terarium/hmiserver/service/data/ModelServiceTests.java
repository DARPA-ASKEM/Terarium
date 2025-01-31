package software.uncharted.terarium.hmiserver.service.data;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;

public class ModelServiceTests extends TerariumApplicationTests {

	@Autowired
	ModelService modelService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private ProjectSearchService projectSearchService;

	Project project;

	static Model createModel(final String key) {
		final Model model = new Model();
		model.setPublicAsset(true);
		model.setName("test-model-name-" + key);
		model.setDescription("test-model-description-" + key);
		return model;
	}

	@BeforeEach
	public void setup() throws IOException {
		projectSearchService.setupIndexAndAliasAndEnsureEmpty();
		project = projectService.createProject(
			(Project) new Project().setPublicAsset(true).setName("test-project-name").setDescription("my description")
		);
	}

	@AfterEach
	public void teardown() throws IOException {
		projectSearchService.teardownIndexAndAlias();
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateModel() {
		final Model before = (Model) createModel("0").setId(UUID.randomUUID());
		try {
			final Model after = modelService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);

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
		final Model model = (Model) createModel("0").setId(UUID.randomUUID());
		try {
			modelService.createAsset(model, project.getId(), ASSUME_WRITE_PERMISSION);
			modelService.createAsset(model, project.getId(), ASSUME_WRITE_PERMISSION);
			Assertions.fail("Should have thrown an exception");
		} catch (final Exception e) {
			Assertions.assertTrue(e.getMessage().contains("already exists"));
		}
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetModels() throws IOException {
		modelService.createAsset(createModel("0"), project.getId(), ASSUME_WRITE_PERMISSION);
		modelService.createAsset(createModel("1"), project.getId(), ASSUME_WRITE_PERMISSION);
		modelService.createAsset(createModel("2"), project.getId(), ASSUME_WRITE_PERMISSION);

		final List<Model> sims = modelService.getPublicNotTemporaryAssets(0, 10);

		Assertions.assertEquals(3, sims.size());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetModelById() throws IOException {
		final Model model = modelService.createAsset(createModel("0"), project.getId(), ASSUME_WRITE_PERMISSION);
		final Model fetchedModel = modelService.getAsset(model.getId(), ASSUME_WRITE_PERMISSION).get();

		Assertions.assertEquals(model, fetchedModel);
		Assertions.assertEquals(model.getId(), fetchedModel.getId());
		Assertions.assertEquals(model.getCreatedOn(), fetchedModel.getCreatedOn());
		Assertions.assertEquals(model.getUpdatedOn(), fetchedModel.getUpdatedOn());
		Assertions.assertEquals(model.getDeletedOn(), fetchedModel.getDeletedOn());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUpdateModel() throws Exception {
		final Model model = modelService.createAsset(createModel("A"), project.getId(), ASSUME_WRITE_PERMISSION);
		model.setName("new name");

		final Model updatedModel = modelService.updateAsset(model, project.getId(), ASSUME_WRITE_PERMISSION).orElseThrow();

		Assertions.assertEquals(model, updatedModel);
		Assertions.assertNotNull(updatedModel.getUpdatedOn());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteModel() throws Exception {
		final Model model = modelService.createAsset(createModel("B"), project.getId(), ASSUME_WRITE_PERMISSION);

		modelService.deleteAsset(model.getId(), project.getId(), ASSUME_WRITE_PERMISSION);

		final Optional<Model> deleted = modelService.getAsset(model.getId(), ASSUME_WRITE_PERMISSION);

		Assertions.assertTrue(deleted.isEmpty());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCloneModel() throws Exception {
		Model model = createModel("A");

		model = modelService.createAsset(model, project.getId(), ASSUME_WRITE_PERMISSION);

		final Model cloned = model.clone();

		Assertions.assertNotEquals(model.getId(), cloned.getId());
		Assertions.assertEquals(model.getName(), cloned.getName());
	}
}
