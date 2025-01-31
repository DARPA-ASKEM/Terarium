package software.uncharted.terarium.hmiserver.controller.dataservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectSearchService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;

public class ProjectControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private ProjectAssetService projectAssetService;

	@Autowired
	private DocumentAssetService documentAssetService;

	@Autowired
	private ProjectSearchService projectSearchService;

	Project project;

	@BeforeEach
	public void setup() throws IOException {
		projectSearchService.setupIndexAndAliasAndEnsureEmpty();
	}

	@AfterEach
	public void teardown() throws IOException {
		projectSearchService.teardownIndexAndAlias();
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateProject() throws Exception {
		mockMvc
			.perform(
				MockMvcRequestBuilders.post("/projects?name=test&userId=abc123&description=desc")
					.with(csrf())
					.contentType("application/json")
			)
			.andExpect(status().isCreated());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetProject() throws Exception {
		final Project project = projectService.createProject((Project) new Project().setName("test-name"));

		mockMvc.perform(MockMvcRequestBuilders.get("/projects/" + project.getId()).with(csrf())).andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUpdateProject() throws Exception {
		final Project project = projectService.createProject((Project) new Project().setName("test-name"));

		mockMvc
			.perform(
				MockMvcRequestBuilders.put("/projects/" + project.getId())
					.with(csrf())
					.contentType("application/json")
					.content(objectMapper.writeValueAsString(project))
			)
			.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteProject() throws Exception {
		final Project project = projectService.createProject((Project) new Project().setName("test-name"));

		mockMvc
			.perform(MockMvcRequestBuilders.delete("/projects/" + project.getId()).with(csrf()))
			.andExpect(status().isOk());

		Assertions.assertTrue(projectService.getProject(project.getId()).isEmpty());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateProjectAsset() throws Exception {
		final Project project = projectService.createProject((Project) new Project().setName("test-name"));

		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final ProjectAsset projectAsset = new ProjectAsset()
			.setAssetId(documentAsset.getId())
			.setAssetName("my-asset-name")
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

		final MvcResult res = mockMvc
			.perform(
				MockMvcRequestBuilders.get("/document-asset/" + documentAsset.getId())
					.param("types", AssetType.DOCUMENT.name())
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		final DocumentAsset results = objectMapper.readValue(res.getResponse().getContentAsString(), DocumentAsset.class);

		Assertions.assertNotNull(results);
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteProjectAsset() throws Exception {
		final Project project = projectService.createProject((Project) new Project().setName("test-name"));

		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		projectAssetService.createProjectAsset(project, AssetType.DOCUMENT, documentAsset, ASSUME_WRITE_PERMISSION);

		mockMvc
			.perform(
				MockMvcRequestBuilders.delete(
					"/projects/" + project.getId() + "/assets/" + AssetType.DOCUMENT.name() + "/" + documentAsset.getId()
				).with(csrf())
			)
			.andExpect(status().isOk());
	}
}
