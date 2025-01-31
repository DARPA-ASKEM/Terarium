package software.uncharted.terarium.hmiserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithUserDetails;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProjectSearchService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;

@Slf4j
public class ExtractionServiceTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DocumentAssetService documentAssetService;

	@Autowired
	private ModelService modelService;

	@Autowired
	private ExtractionService extractionService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private DatasetService datasetService;

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

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void variableExtractionTests() throws Exception {
		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setText("x = 0. y = 1. I = Infected population.")
			.setName("test-document-name")
			.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset, project.getId(), ASSUME_WRITE_PERMISSION);

		documentAsset = extractionService
			.extractVariables(project.getId(), documentAsset.getId(), new ArrayList<>(), ASSUME_WRITE_PERMISSION)
			.get();
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void variableExtractionWithModelTests() throws Exception {
		final ClassPathResource resource1 = new ClassPathResource("knowledge/extraction_text.txt");
		final byte[] content1 = Files.readAllBytes(resource1.getFile().toPath());

		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setText(new String(content1))
			.setName("test-document-name")
			.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset, project.getId(), ASSUME_WRITE_PERMISSION);

		final ClassPathResource resource2 = new ClassPathResource("knowledge/extraction_amr.json");
		final byte[] content2 = Files.readAllBytes(resource2.getFile().toPath());
		Model model = objectMapper.readValue(content2, Model.class);

		model = modelService.createAsset(model, project.getId(), ASSUME_WRITE_PERMISSION);

		documentAsset = extractionService
			.extractVariables(project.getId(), documentAsset.getId(), List.of(model.getId()), ASSUME_WRITE_PERMISSION)
			.get();
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void linkAmrTests() throws Exception {
		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setText("x = 0. y = 1. I = Infected population.")
			.setName("test-document-name")
			.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset, project.getId(), ASSUME_WRITE_PERMISSION);

		documentAsset = extractionService
			.extractVariables(project.getId(), documentAsset.getId(), new ArrayList<>(), ASSUME_WRITE_PERMISSION)
			.get();

		final ClassPathResource resource = new ClassPathResource("knowledge/sir.json");
		final byte[] content = Files.readAllBytes(resource.getFile().toPath());
		Model model = objectMapper.readValue(content, Model.class);

		model = modelService.createAsset(model, project.getId(), ASSUME_WRITE_PERMISSION);

		model = extractionService
			.alignAMR(project.getId(), documentAsset.getId(), model.getId(), ASSUME_WRITE_PERMISSION)
			.get();
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void pdfExtraction() throws Exception {
		final ClassPathResource resource = new ClassPathResource("knowledge/Bertozzi2020.pdf");
		final byte[] content = Files.readAllBytes(resource.getFile().toPath());

		final HttpEntity pdfFileEntity = new ByteArrayEntity(content, ContentType.create("application/pdf"));

		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setFileNames(List.of("SIR.pdf"))
			.setName("test-pdf-name")
			.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset, project.getId(), ASSUME_WRITE_PERMISSION);

		documentAssetService.uploadFile(documentAsset.getId(), "SIR.pdf", pdfFileEntity);

		documentAsset = extractionService
			.extractPDFAndApplyToDocument(documentAsset.getId(), null, ASSUME_WRITE_PERMISSION)
			.get();

		log.info("" + documentAsset.getExtractions());
	}
}
