package software.uncharted.terarium.hmiserver.controller.dataservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectSearchService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;

public class AssetControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private DocumentAssetService documentAssetService;

	@Autowired
	private ProjectSearchService projectSearchService;

	@BeforeEach
	public void setup() throws IOException {
		projectSearchService.setupIndexAndAliasAndEnsureEmpty();
	}

	@AfterEach
	public void teardown() throws IOException {
		projectSearchService.teardownIndexAndAlias();
	}

	private static final String TEST_ASSET_NAME_1 = "test-asset-name-1";
	private static final String TEST_ASSET_NAME_2 = "test-asset-name-2";
	private static final String TEST_ASSET_NAME_UNUSED = "test-asset-name-unused";

	Project project;

	Project project2;

	@Test
	@WithUserDetails(MockUser.ADAM)
	public void testErrorConditions() throws Exception {
		// Test that we get a 404 if we provide a project id that doesn't exist
		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" +
					AssetType.DOCUMENT.name() +
					"/" +
					TEST_ASSET_NAME_UNUSED +
					"?project-id=" +
					UUID.randomUUID()
				).with(csrf())
			)
			.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(MockUser.ADAM)
	public void testItCanVerifyAssetNameAvailabilityGenerally() throws Exception {
		// Test that we get a 204 if the asset name is available
		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" + AssetType.DOCUMENT.name() + "/" + TEST_ASSET_NAME_UNUSED
				).with(csrf())
			)
			.andExpect(status().isNoContent());

		// Test that we get a 409 if the asset name is not available
		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" + AssetType.DOCUMENT.name() + "/" + TEST_ASSET_NAME_1
				).with(csrf())
			)
			.andExpect(status().isConflict());

		// Test that we get a 409 if the asset name is not available
		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" + AssetType.DOCUMENT.name() + "/" + TEST_ASSET_NAME_2
				).with(csrf())
			)
			.andExpect(status().isConflict());

		// Test that we get a 204 if the asset name is available because of different
		// asset type
		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" + AssetType.CODE.name() + "/" + TEST_ASSET_NAME_1
				).with(csrf())
			)
			.andExpect(status().isNoContent());
	}

	@Test
	@WithUserDetails(MockUser.ADAM)
	public void testItCanVerifyAssetNameAvailabilityInProjects() throws Exception {
		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" +
					AssetType.DOCUMENT.name() +
					"/" +
					TEST_ASSET_NAME_UNUSED +
					"?project-id=" +
					project.getId()
				).with(csrf())
			)
			.andExpect(status().isNoContent());

		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" +
					AssetType.DOCUMENT.name() +
					"/" +
					TEST_ASSET_NAME_1 +
					"?project-id=" +
					project.getId()
				).with(csrf())
			)
			.andExpect(status().isConflict());

		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" +
					AssetType.DOCUMENT.name() +
					"/" +
					TEST_ASSET_NAME_2 +
					"?project-id=" +
					project.getId()
				).with(csrf())
			)
			.andExpect(status().isNoContent());

		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" +
					AssetType.CODE.name() +
					"/" +
					TEST_ASSET_NAME_1 +
					"?project-id=" +
					project.getId()
				).with(csrf())
			)
			.andExpect(status().isNoContent());

		mockMvc
			.perform(
				MockMvcRequestBuilders.get(
					"/assets/asset-name-available/" +
					AssetType.DOCUMENT.name() +
					"/" +
					TEST_ASSET_NAME_2 +
					"?project-id=" +
					project2.getId()
				).with(csrf())
			)
			.andExpect(status().isConflict());
	}

	/**
	 * Helper method to set up a scenario where we have two projects each with one
	 * document asset.
	 *
	 * @throws Exception
	 */
	@BeforeEach
	public void setUpScenario() throws Exception {
		project = projectService.createProject((Project) new Project().setName("test-proj-1"));

		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName(TEST_ASSET_NAME_1).setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final ProjectAsset projectAsset = new ProjectAsset()
			.setAssetId(documentAsset.getId())
			.setAssetName(documentAsset.getName())
			.setAssetType(AssetType.DOCUMENT);

		mockMvc
			.perform(
				MockMvcRequestBuilders.post(
					"/projects/" + project.getId() + "/assets/" + AssetType.DOCUMENT.name() + "/" + documentAsset.getId()
				)
					.with(csrf())
					.contentType("application/json")
					.content(objectMapper.writeValueAsString(projectAsset))
			)
			.andExpect(status().isOk());

		project2 = projectService.createProject((Project) new Project().setName("test-proj-2"));

		final DocumentAsset documentAsset2 = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName(TEST_ASSET_NAME_2).setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final ProjectAsset projectAsset2 = new ProjectAsset()
			.setAssetId(documentAsset.getId())
			.setAssetName(documentAsset.getName())
			.setAssetType(AssetType.DOCUMENT);

		mockMvc
			.perform(
				MockMvcRequestBuilders.post(
					"/projects/" + project2.getId() + "/assets/" + AssetType.DOCUMENT.name() + "/" + documentAsset2.getId()
				)
					.with(csrf())
					.contentType("application/json")
					.content(objectMapper.writeValueAsString(projectAsset2))
			)
			.andExpect(status().isOk());
	}
}
