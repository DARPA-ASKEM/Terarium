package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentExtraction;
import software.uncharted.terarium.hmiserver.models.dataservice.document.ExtractionAssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectExport;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.Transform;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.Workflow;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowEdge;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowNode;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.service.TerariumAssetCloneService;

public class TerariumAssetCloneServiceTests extends TerariumApplicationTests {

	@Autowired
	WorkflowService workflowService;

	@Autowired
	DocumentAssetService documentService;

	@Autowired
	ProjectService projectService;

	@Autowired
	ProjectAssetService projectAssetService;

	@Autowired
	TerariumAssetCloneService cloneService;

	@Autowired
	private ProjectSearchService projectSearchService;

	Project project;

	static ObjectMapper objectMapper = new ObjectMapper();

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

	static Grounding createGrounding(final String key) {
		final DKG dkg = new DKG("curie:test", "maria", "", null, null);
		final Grounding grounding = new Grounding(dkg);
		final Map<String, String> modifiers = new HashMap<>();
		modifiers.put("hello", "world-" + key);
		modifiers.put("foo", "bar-" + key);
		grounding.setModifiers(modifiers);
		return grounding;
	}

	static DocumentExtraction createDocExtraction() {
		return new DocumentExtraction().setFileName("Hello World.pdf").setAssetType(ExtractionAssetType.FIGURE);
	}

	static DocumentAsset createDocument(final String key) throws Exception {
		final DocumentAsset documentAsset = new DocumentAsset();
		documentAsset.setName("test-document-name-" + key);
		documentAsset.setDescription("test-document-description-" + key);
		documentAsset.setFileNames(new ArrayList<>());
		documentAsset.getFileNames().add("science.pdf");
		documentAsset.getFileNames().add("science2.pdf");
		documentAsset.setGrounding(createGrounding(key));
		documentAsset.setMetadata(new HashMap<>());
		documentAsset.getMetadata().put("hello", objectMapper.readTree("{\"hello\": \"world-" + key + "\"}"));
		documentAsset.setPublicAsset(true);
		documentAsset.setAssets(new ArrayList<>());
		documentAsset.getAssets().add(createDocExtraction());
		return documentAsset;
	}

	static Workflow createWorkflow() throws Exception {
		final WorkflowNode a = new WorkflowNode().setId(UUID.randomUUID());
		final WorkflowNode b = new WorkflowNode().setId(UUID.randomUUID());
		final WorkflowNode c = new WorkflowNode().setId(UUID.randomUUID());
		final WorkflowNode d = new WorkflowNode().setId(UUID.randomUUID());

		final WorkflowEdge ab = new WorkflowEdge().setSource(a.getId()).setTarget(b.getId());
		final WorkflowEdge bc = new WorkflowEdge().setSource(b.getId()).setTarget(c.getId());
		final WorkflowEdge cd = new WorkflowEdge().setSource(c.getId()).setTarget(d.getId());

		return (Workflow) new Workflow()
			.setTransform(new Transform().setX(1).setY(2).setK(3))
			.setNodes(List.of(a, b, c, d))
			.setEdges(List.of(ab, bc, cd))
			.setPublicAsset(true)
			.setDescription("test-workflow-description-0")
			.setName("test-workflow-name-0");
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCloneAndPersistAssets() throws Exception {
		final int NUM_DOCUMENTS = 5;

		final List<DocumentAsset> documents = new ArrayList<>();
		for (int i = 0; i < NUM_DOCUMENTS; i++) {
			final DocumentAsset before = createDocument(Integer.toString(i));
			final DocumentAsset after = documentService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);
			documents.add(after);

			for (final String filename : after.getFileNames()) {
				documentService.uploadFile(
					after.getId(),
					filename,
					ContentType.TEXT_PLAIN,
					("This is my sample file containing" + filename).getBytes()
				);
			}
		}

		final Workflow before = createWorkflow();
		for (final WorkflowNode node : before.getNodes()) {
			node.getAdditionalProperties().put("hello", objectMapper.valueToTree("world"));
			for (final DocumentAsset doc : documents) {
				node.getAdditionalProperties().put(doc.getId().toString(), objectMapper.valueToTree(doc.getId().toString()));
			}
		}
		final Workflow workflow = workflowService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);

		final Project project = new Project();
		project.setName("test-project-name-0");
		project.setDescription("test-project-description-0");

		projectService.createProject(project);

		for (final DocumentAsset doc : documents) {
			projectAssetService.createProjectAsset(project, AssetType.DOCUMENT, doc, ASSUME_WRITE_PERMISSION);
		}

		projectAssetService.createProjectAsset(project, AssetType.WORKFLOW, workflow, ASSUME_WRITE_PERMISSION);

		final List<TerariumAsset> cloned = cloneService.cloneAndPersistAsset(
			project.getId(),
			workflow.getId(),
			AssetType.WORKFLOW
		);

		Assertions.assertEquals(1 + NUM_DOCUMENTS, cloned.size());
		Assertions.assertEquals(1, cloned.stream().filter(a -> a instanceof Workflow).count());
		Assertions.assertEquals(NUM_DOCUMENTS, cloned.stream().filter(a -> a instanceof DocumentAsset).count());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanExportImportProject() throws Exception {
		final int NUM_DOCUMENTS = 5;

		final List<DocumentAsset> documents = new ArrayList<>();
		for (int i = 0; i < NUM_DOCUMENTS; i++) {
			final DocumentAsset before = createDocument(Integer.toString(i));
			final DocumentAsset after = documentService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);
			documents.add(after);

			for (final String filename : after.getFileNames()) {
				documentService.uploadFile(
					after.getId(),
					filename,
					ContentType.TEXT_PLAIN,
					("This is my sample file containing" + filename).getBytes()
				);
			}
		}

		final Workflow before = createWorkflow();
		for (final WorkflowNode node : before.getNodes()) {
			node.getAdditionalProperties().put("hello", objectMapper.valueToTree("world"));
			for (final DocumentAsset doc : documents) {
				node.getAdditionalProperties().put(doc.getId().toString(), objectMapper.valueToTree(doc.getId().toString()));
			}
		}
		final Workflow workflow = workflowService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);

		final Project project = new Project();
		project.setName("test-project-name-0");
		project.setDescription("test-project-description-0");

		projectService.createProject(project);

		for (final DocumentAsset doc : documents) {
			projectAssetService.createProjectAsset(project, AssetType.DOCUMENT, doc, ASSUME_WRITE_PERMISSION);
		}

		projectAssetService.createProjectAsset(project, AssetType.WORKFLOW, workflow, ASSUME_WRITE_PERMISSION);

		final List<ProjectAsset> exportedAssets = projectAssetService.getProjectAssets(
			project.getId(),
			ASSUME_WRITE_PERMISSION
		);

		final ProjectExport projectExport = cloneService.exportProject(project.getId());

		Assertions.assertEquals(1 + NUM_DOCUMENTS, projectExport.getAssets().size());

		final Project importedProject = cloneService.importProject("test_user_id", "test_user_name", projectExport);

		Assertions.assertNotEquals(project.getId(), importedProject.getId());
		Assertions.assertEquals(project.getName(), importedProject.getName());
		Assertions.assertEquals(project.getDescription(), importedProject.getDescription());

		final List<ProjectAsset> importedAssets = projectAssetService.getProjectAssets(
			importedProject.getId(),
			ASSUME_WRITE_PERMISSION
		);

		Assertions.assertEquals(exportedAssets.size(), importedAssets.size());

		for (int i = 0; i < exportedAssets.size(); i++) {
			final ProjectAsset exportedAsset = exportedAssets.get(i);
			final ProjectAsset importedAsset = importedAssets.get(i);

			Assertions.assertNotEquals(exportedAsset.getAssetId(), importedAsset.getAssetId());
			Assertions.assertEquals(exportedAsset.getAssetType(), importedAsset.getAssetType());
		}
	}
}
