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
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;

@Slf4j
public class ExtractionServiceTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DocumentAssetService documentAssetService;

	@Autowired
	private ModelService modelService;

	@Autowired
	private ElasticsearchService elasticService;

	@Autowired
	private ExtractionService extractionService;

	@Autowired
	private ElasticsearchConfiguration elasticConfig;

	@BeforeEach
	public void setup() throws IOException {
		elasticService.createOrEnsureIndexIsEmpty(elasticConfig.getDocumentIndex());
	}

	@AfterEach
	public void teardown() throws IOException {
		elasticService.deleteIndex(elasticConfig.getDocumentIndex());
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void variableExtractionTests() throws Exception {

		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setText("x = 0. y = 1. I = Infected population.")
				.setName("test-document-name")
				.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset);

		documentAsset = extractionService
				.extractVariables(documentAsset.getId(), new ArrayList<>(), "epi")
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

		documentAsset = documentAssetService.createAsset(documentAsset);

		final ClassPathResource resource2 = new ClassPathResource("knowledge/extraction_amr.json");
		final byte[] content2 = Files.readAllBytes(resource2.getFile().toPath());
		Model model = objectMapper.readValue(content2, Model.class);

		model = modelService.createAsset(model);

		documentAsset = extractionService
				.extractVariables(documentAsset.getId(), List.of(model.getId()), "epi")
				.get();
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void linkAmrTests() throws Exception {

		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()

			.setText("x = 0. y = 1. I = Infected population.")
				.setName("test-document-name")
				.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset);

		documentAsset = extractionService
				.extractVariables(documentAsset.getId(), new ArrayList<>(), "epi")
				.get();

		final ClassPathResource resource = new ClassPathResource("knowledge/sir.json");
		final byte[] content = Files.readAllBytes(resource.getFile().toPath());
		Model model = objectMapper.readValue(content, Model.class);

		model = modelService.createAsset(model);

		model = extractionService.alignAMR(documentAsset.getId(), model.getId()).get();
	}

	// // @Test
	@WithUserDetails(MockUser.URSULA)
	public void cosmosPdfExtraction() throws Exception {

		final ClassPathResource resource = new ClassPathResource("knowledge/paper.pdf");
		final byte[] content = Files.readAllBytes(resource.getFile().toPath());

		final HttpEntity pdfFileEntity = new ByteArrayEntity(content, ContentType.create("application/pdf"));

		DocumentAsset documentAsset = (DocumentAsset) new DocumentAsset()
			.setFileNames(List.of("paper.pdf"))
				.setName("test-pdf-name")
				.setDescription("my description");

		documentAsset = documentAssetService.createAsset(documentAsset);

		documentAssetService.uploadFile(
				documentAsset.getId(), "paper.pdf", pdfFileEntity, ContentType.create("application/pdf"));

		documentAsset =
				extractionService.extractPDF(documentAsset.getId(), "epi").get();
	}
}
