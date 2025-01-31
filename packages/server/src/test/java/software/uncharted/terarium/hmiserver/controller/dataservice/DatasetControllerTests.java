package software.uncharted.terarium.hmiserver.controller.dataservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectSearchService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;

public class DatasetControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatasetService datasetService;

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
	public void testItCanCreateDataset() throws Exception {
		final Dataset dataset = (Dataset) new Dataset().setName("test-dataset-name").setDescription("my description");

		mockMvc
			.perform(
				MockMvcRequestBuilders.post("/datasets")
					.with(csrf())
					.param("project-id", PROJECT_ID.toString())
					.contentType("application/json")
					.content(objectMapper.writeValueAsString(dataset))
			)
			.andExpect(status().isCreated());
	}

	//@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.get("/datasets/" + dataset.getId())
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.delete("/datasets/" + dataset.getId())
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(status().isOk());

		Assertions.assertTrue(datasetService.getAsset(dataset.getId(), ASSUME_WRITE_PERMISSION).isEmpty());
	}

	//@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUploadDatasetCSV() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final String content = "col0,col1,col2,col3\na,b,c,d\n";

		// Create a MockMultipartFile object
		final MockMultipartFile file = new MockMultipartFile(
			"file", // name of the file as expected in the request
			"filename.csv", // original filename
			"text/csv", // content type
			content.getBytes() // content of the file
		);

		// Perform the multipart file upload request
		mockMvc
			.perform(
				MockMvcRequestBuilders.multipart("/datasets/" + dataset.getId() + "/upload-csv")
					.file(file)
					.queryParam("filename", "filename.csv")
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.with(request -> {
						request.setMethod("PUT");
						return request;
					})
			)
			.andExpect(status().isOk());

		final Dataset updated = datasetService.getAsset(dataset.getId(), ASSUME_WRITE_PERMISSION).get();
		Assertions.assertEquals(4, updated.getColumns().size());
	}

	//@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUploadDatasetFromGithub() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(
				MockMvcRequestBuilders.put("/datasets/" + dataset.getId() + "/upload-csv-from-github")
					.with(csrf())
					.param("project-id", PROJECT_ID.toString())
					.param("repo-owner-and-name", "unchartedsoftware/torflow")
					.param("path", "README.md")
					.param("filename", "torflow-readme.md")
					.contentType("application/json")
			)
			.andExpect(status().isOk());
	}

	//@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDownloadDatasetCSV() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final String content = "col0,col1,col2,col3\na,b,c,d\n";

		// Create a MockMultipartFile object
		final MockMultipartFile file = new MockMultipartFile(
			"file", // name of the file as expected in the request
			"filename.csv", // original filename
			"text/csv", // content type
			content.getBytes() // content of the file
		);

		// Perform the multipart file upload request
		mockMvc
			.perform(
				MockMvcRequestBuilders.multipart("/datasets/" + dataset.getId() + "/upload-csv")
					.file(file)
					.queryParam("filename", "filename.csv")
					.param("project-id", PROJECT_ID.toString())
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
				MockMvcRequestBuilders.get("/datasets/" + dataset.getId() + "/download-csv")
					.queryParam("filename", "filename.csv")
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		final String resultContent = res.getResponse().getContentAsString();

		Assertions.assertTrue(resultContent.length() > 0);
	}

	//@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUploadDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final String content = "This is my small test dataset\n";

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
				MockMvcRequestBuilders.multipart("/datasets/" + dataset.getId() + "/upload-file")
					.file(file)
					.queryParam("filename", "filename.txt")
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.with(request -> {
						request.setMethod("PUT");
						return request;
					})
			)
			.andExpect(status().isOk());
	}

	//@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDownloadDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final String content = "col0,col1,col2,col3\na,b,c,d\n";

		// Create a MockMultipartFile object
		final MockMultipartFile file = new MockMultipartFile(
			"file", // name of the file as expected in the request
			"filename.csv", // original filename
			"text/csv", // content type
			content.getBytes() // content of the file
		);

		// Perform the multipart file upload request
		mockMvc
			.perform(
				MockMvcRequestBuilders.multipart("/datasets/" + dataset.getId() + "/upload-csv")
					.file(file)
					.queryParam("filename", "filename.csv")
					.param("project-id", PROJECT_ID.toString())
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
				MockMvcRequestBuilders.get("/datasets/" + dataset.getId() + "/download-file")
					.queryParam("filename", "filename.csv")
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(request().asyncStarted())
			.andDo(MvcResult::getAsyncResult)
			.andExpect(status().isOk())
			.andExpect(content().string(content))
			.andReturn();
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetUploadUrl() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-dataset-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		// Perform the multipart file upload request
		final MvcResult res = mockMvc
			.perform(
				MockMvcRequestBuilders.get("/datasets/" + dataset.getId() + "/upload-url")
					.queryParam("filename", "filename.csv")
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		final PresignedURL url = objectMapper.readValue(res.getResponse().getContentAsString(), PresignedURL.class);

		final String content = "col0,col1,col2,col3\na,b,c,d\n";
		final byte[] csvBytes = content.getBytes();
		final HttpEntity csvEntity = new ByteArrayEntity(csvBytes, ContentType.APPLICATION_OCTET_STREAM);

		final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build();

		final HttpPut put = new HttpPut(url.getUrl());
		put.setEntity(csvEntity);
		final HttpResponse response = httpclient.execute(put);
		final int status = response.getStatusLine().getStatusCode();

		Assertions.assertEquals(200, status);
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetDownloadUrl() throws Exception {
		final Dataset dataset = datasetService.createAsset(
			(Dataset) new Dataset().setName("test-document-name").setDescription("my description"),
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		// Perform the multipart file upload request
		MvcResult res = mockMvc
			.perform(
				MockMvcRequestBuilders.get("/datasets/" + dataset.getId() + "/upload-url")
					.queryParam("filename", "filename.txt")
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		PresignedURL url = objectMapper.readValue(res.getResponse().getContentAsString(), PresignedURL.class);

		final String content = "col0,col1,col2,col3\na,b,c,d\n";
		final byte[] csvBytes = content.getBytes();
		final HttpEntity csvEntity = new ByteArrayEntity(csvBytes, ContentType.APPLICATION_OCTET_STREAM);

		final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build();

		final HttpPut put = new HttpPut(url.getUrl());
		put.setEntity(csvEntity);
		HttpResponse response = httpclient.execute(put);
		final int status = response.getStatusLine().getStatusCode();

		Assertions.assertEquals(200, status);

		res = mockMvc
			.perform(
				MockMvcRequestBuilders.get("/datasets/" + dataset.getId() + "/download-url")
					.queryParam("filename", "filename.txt")
					.param("project-id", PROJECT_ID.toString())
					.with(csrf())
			)
			.andExpect(status().isOk())
			.andReturn();

		url = objectMapper.readValue(res.getResponse().getContentAsString(), PresignedURL.class);

		final HttpGet get = new HttpGet(url.getUrl());
		response = httpclient.execute(get);
		Assertions.assertEquals(response.getStatusLine().getStatusCode(), 200);
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetPublicModelWithoutProject() throws Exception {
		final Dataset dataset_public_not_temp = (Dataset) new Dataset()
			.setName("test-document-name")
			.setDescription("my description")
			.setPublicAsset(true)
			.setTemporary(false);
		final Dataset dataset_not_public_temp = (Dataset) new Dataset()
			.setName("test-document-name")
			.setDescription("my description")
			.setPublicAsset(false)
			.setTemporary(true);
		final Dataset dataset_public_temp = (Dataset) new Dataset()
			.setName("test-document-name")
			.setDescription("my description")
			.setPublicAsset(true)
			.setTemporary(true);

		final Dataset createdDataset_public_not_temp = datasetService.createAsset(
			dataset_public_not_temp,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);
		final Dataset createdDataset_public_temp = datasetService.createAsset(
			dataset_public_temp,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);
		final Dataset createdDataset_not_public_temp = datasetService.createAsset(
			dataset_not_public_temp,
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		mockMvc
			.perform(MockMvcRequestBuilders.get("/datasets/" + createdDataset_public_not_temp.getId()).with(csrf()))
			.andExpect(status().isOk());
		mockMvc
			.perform(MockMvcRequestBuilders.get("/datasets/" + createdDataset_not_public_temp.getId()).with(csrf()))
			.andExpect(status().is5xxServerError());
		mockMvc
			.perform(MockMvcRequestBuilders.get("/datasets/" + createdDataset_public_temp.getId()).with(csrf()))
			.andExpect(status().is5xxServerError());
	}
}
