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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectSearchService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;

public class DocumentControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DocumentAssetService documentAssetService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private ProjectSearchService projectSearchService;

	Project project;

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
	public void testItCanCreateDocument() throws Exception {
		final DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setName("test-document-name")
			.setDescription("my description");

		mockMvc
			.perform(
				MockMvcRequestBuilders.post("/document-asset")
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
					.contentType("application/json")
					.content(objectMapper.writeValueAsString(documentAsset))
			)
			.andExpect(status().isCreated());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetDocument() throws Exception {
		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.get("/document-asset/" + documentAsset.getId())
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteDocument() throws Exception {
		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.delete("/document-asset/" + documentAsset.getId())
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(status().isOk());

		Assertions.assertTrue(documentAssetService.getAsset(documentAsset.getId(), ASSUME_WRITE_PERMISSION).isEmpty());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUploadDocument() throws Exception {
		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		// Create a MockMultipartFile object
		final MockMultipartFile file = new MockMultipartFile(
			"file", // name of the file as expected in the request
			"filename.txt", // original filename
			"text/plain", // content type
			"file content".getBytes() // content of the file
		);

		// Perform the multipart file upload request
		mockMvc
			.perform(
				MockMvcRequestBuilders.multipart("/document-asset/" + documentAsset.getId() + "/upload-document")
					.file(file)
					.param("project-id", PROJECT_ID.toString())
					.queryParam("filename", "filename.txt")
					.with(csrf())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.with(request -> {
						request.setMethod("PUT");
						return request;
					})
			)
			.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUploadDocumentFromGithub() throws Exception {
		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.put("/document-asset/" + documentAsset.getId() + "/upload-document-from-github")
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
					.param("repo-owner-and-name", "unchartedsoftware/torflow")
					.param("path", "README.md")
					.param("filename", "torflow-readme.md")
					.contentType("application/json")
			)
			.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDownloadDocument() throws Exception {
		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final String content = "this is the file content for the testItCanDownloadDocument test";

		// Create a MockMultipartFile object
		final MockMultipartFile file = new MockMultipartFile(
			"file", // name of the file as expected in the request
			"filename.txt", // original filename
			"text/plain", // content type
			content.getBytes() // content of the file
		);

		// Perform the multipart file upload request
		mockMvc
			.perform(
				MockMvcRequestBuilders.multipart("/document-asset/" + documentAsset.getId() + "/upload-document")
					.file(file)
					.param("project-id", PROJECT_ID.toString())
					.queryParam("filename", "filename.txt")
					.with(csrf())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.with(request -> {
						request.setMethod("PUT");
						return request;
					})
			)
			.andExpect(status().isOk());

		final MvcResult res = mockMvc
			.perform(
				MockMvcRequestBuilders.get("/document-asset/" + documentAsset.getId() + "/download-document")
					.queryParam("filename", "filename.txt")
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		final String resultContent = res.getResponse().getContentAsString();

		Assertions.assertEquals(content, resultContent);
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDownloadDocumentAsText() throws Exception {
		final DocumentAsset documentAsset = documentAssetService.createAsset(
			(DocumentAsset) new DocumentAsset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final String content = "this is the file content for the testItCanDownloadDocument test";

		// Create a MockMultipartFile object
		final MockMultipartFile file = new MockMultipartFile(
			"file", // name of the file as expected in the request
			"filename.txt", // original filename
			"text/plain", // content type
			content.getBytes() // content of the file
		);

		// Perform the multipart file upload request
		mockMvc
			.perform(
				MockMvcRequestBuilders.multipart("/document-asset/" + documentAsset.getId() + "/upload-document")
					.file(file)
					.param("project-id", PROJECT_ID.toString())
					.queryParam("filename", "filename.txt")
					.with(csrf())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.with(request -> {
						request.setMethod("PUT");
						return request;
					})
			)
			.andExpect(status().isOk());

		final MvcResult res = mockMvc
			.perform(
				MockMvcRequestBuilders.get("/document-asset/" + documentAsset.getId() + "/download-document-as-text")
					.queryParam("filename", "filename.txt")
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		final String resultContent = res.getResponse().getContentAsString();

		Assertions.assertEquals(content, resultContent);
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetPublicModelWithoutProject() throws Exception {
		final DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setName("test-document-name")
			.setDescription("my description");
		documentAsset.setPublicAsset(true);

		final DocumentAsset createdDocumentAsset = documentAssetService.createAsset(
			documentAsset,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(MockMvcRequestBuilders.get("/document-asset/" + createdDocumentAsset.getId()).with(csrf()))
			.andExpect(status().isOk());
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCannotGetPrivateModelWithoutProject() throws Exception {
		final DocumentAsset documentAsset_public_not_temp = (DocumentAsset) new DocumentAsset()
			.setName("test-document-name")
			.setDescription("my description")
			.setPublicAsset(true)
			.setTemporary(false);
		final DocumentAsset documentAsset_public_temp = (DocumentAsset) new DocumentAsset()
			.setName("test-document-name")
			.setDescription("my description")
			.setPublicAsset(true)
			.setTemporary(true);
		final DocumentAsset documentAsset_not_public_temp = (DocumentAsset) new DocumentAsset()
			.setName("test-document-name")
			.setDescription("my description")
			.setPublicAsset(false)
			.setTemporary(true);

		final DocumentAsset createdDocumentAsset_public_temp = documentAssetService.createAsset(
			documentAsset_public_temp,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);
		final DocumentAsset createdDocumentAsset_public_not_temp = documentAssetService.createAsset(
			documentAsset_public_not_temp,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);
		final DocumentAsset createdDocumentAsset_not_public_temp = documentAssetService.createAsset(
			documentAsset_not_public_temp,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.get("/document-asset/" + createdDocumentAsset_public_not_temp.getId()).with(csrf())
			)
			.andExpect(status().isOk());
		mockMvc
			.perform(
				MockMvcRequestBuilders.get("/document-asset/" + createdDocumentAsset_not_public_temp.getId()).with(csrf())
			)
			.andExpect(status().isForbidden());
		mockMvc
			.perform(MockMvcRequestBuilders.get("/document-asset/" + createdDocumentAsset_public_temp.getId()).with(csrf()))
			.andExpect(status().isForbidden());
	}
}
