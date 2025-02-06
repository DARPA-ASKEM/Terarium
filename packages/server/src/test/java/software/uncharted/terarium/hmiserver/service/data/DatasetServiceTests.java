package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.mira.DKG;

@Slf4j
public class DatasetServiceTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper mapper;

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

	static Grounding createGrounding(final String key) {
		final DKG dkg = new DKG("curie:test", "maria", "", null, null);
		final Grounding grounding = new Grounding(dkg);
		final Map<String, String> context = new HashMap<>();
		context.put("hello", "world-" + key);
		context.put("foo", "bar-" + key);
		grounding.setModifiers(context);
		return grounding;
	}

	Dataset createDataset() throws Exception {
		return createDataset("A");
	}

	Dataset createDataset(final String key) throws Exception {
		final ObjectMapper mapper = new ObjectMapper();

		final DatasetColumn column1 = new DatasetColumn();
		column1.setName("Title");
		column1.setDataType(DatasetColumn.ColumnType.STRING);
		column1.setDescription("hello world");
		column1.setMetadata(mapper.createObjectNode().put("hello", "world-" + key).put("foo", "bar-" + key));
		column1.setGrounding(createGrounding(key));
		final DatasetColumn column2 = new DatasetColumn();
		column2.setName("Value");
		column2.setDataType(DatasetColumn.ColumnType.FLOAT);
		column2.setDescription("3.1415926");
		column2.setMetadata(mapper.createObjectNode().put("hello", "world-" + key).put("foo", "bar-" + key));
		column2.setGrounding(createGrounding(key));

		final Dataset dataset = new Dataset();
		dataset.setName("test-dataset-name-" + key);
		dataset.setDescription("test-dataset-description-" + key);
		dataset.setColumns(new ArrayList<>());
		dataset.getColumns().add(column1);
		dataset.getColumns().add(column2);
		dataset.setGrounding(createGrounding(key));
		dataset.setMetadata(mapper.createObjectNode().put("hello", "world-" + key).put("foo", "bar-" + key));
		dataset.setPublicAsset(true);

		return dataset;
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateDataset() throws Exception {
		final Dataset before = (Dataset) createDataset().setId(UUID.randomUUID());
		final Dataset after = datasetService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);

		Assertions.assertEquals(before.getId(), after.getId());
		Assertions.assertNotNull(after.getId());
		Assertions.assertNotNull(after.getCreatedOn());
		Assertions.assertEquals(after.getColumns().size(), 2);

		for (final DatasetColumn col : after.getColumns()) {
			Assertions.assertNotNull(col.getId());
			Assertions.assertNotNull(col.getCreatedOn());
			Assertions.assertNotNull(col.getGrounding());
			Assertions.assertNotNull(col.getGrounding().getId());
			Assertions.assertNotNull(col.getGrounding().getCreatedOn());
			Assertions.assertNotNull(col.getGrounding().getIdentifiers());
			Assertions.assertEquals(col.getGrounding().getIdentifiers().size(), 1);
			Assertions.assertNotNull(col.getGrounding().getModifiers());
			Assertions.assertEquals(col.getGrounding().getModifiers().size(), 2);
		}

		Assertions.assertNotNull(after.getGrounding());
		Assertions.assertNotNull(after.getGrounding().getId());
		Assertions.assertNotNull(after.getGrounding().getCreatedOn());
		Assertions.assertNotNull(after.getGrounding().getIdentifiers());
		Assertions.assertNotNull(after.getGrounding().getModifiers());
		Assertions.assertEquals(after.getGrounding().getModifiers().size(), 2);
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateDatasetAndAddColumnsLater() throws Exception {
		final Dataset before = (Dataset) createDataset().setId(UUID.randomUUID());
		before.setColumns(null); // clear columns
		final Dataset after = datasetService.createAsset(before, project.getId(), ASSUME_WRITE_PERMISSION);

		Assertions.assertNull(after.getColumns());

		final DatasetColumn column1 = new DatasetColumn();
		column1.setName("Title");
		column1.setDataType(DatasetColumn.ColumnType.STRING);
		column1.setDescription("hello world");
		column1.setMetadata(mapper.createObjectNode().put("hello", "world").put("foo", "bar"));
		column1.setGrounding(createGrounding("test"));
		final DatasetColumn column2 = new DatasetColumn();
		column2.setName("Value");
		column2.setDataType(DatasetColumn.ColumnType.FLOAT);
		column2.setDescription("3.1415926");
		column2.setMetadata(mapper.createObjectNode().put("hello", "world").put("foo", "bar"));
		column2.setGrounding(createGrounding("another"));

		after.setColumns(new ArrayList<>());
		after.getColumns().add(column1);
		after.getColumns().add(column2);

		final Dataset updated = datasetService.updateAsset(after, project.getId(), ASSUME_WRITE_PERMISSION).orElseThrow();

		Assertions.assertEquals(updated.getColumns().size(), 2);
		for (final DatasetColumn col : updated.getColumns()) {
			Assertions.assertNotNull(col.getId());
			Assertions.assertNotNull(col.getCreatedOn());
			Assertions.assertNotNull(col.getGrounding());
			Assertions.assertNotNull(col.getGrounding().getId());
			Assertions.assertNotNull(col.getGrounding().getCreatedOn());
			Assertions.assertNotNull(col.getGrounding().getIdentifiers());
			Assertions.assertEquals(col.getGrounding().getIdentifiers().size(), 1);
			Assertions.assertNotNull(col.getGrounding().getModifiers());
			Assertions.assertEquals(col.getGrounding().getModifiers().size(), 2);
		}
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCantCreateDuplicates() throws Exception {
		final Dataset dataset = (Dataset) createDataset().setId(UUID.randomUUID());

		datasetService.createAsset(dataset, project.getId(), ASSUME_WRITE_PERMISSION);

		try {
			datasetService.createAsset(dataset, project.getId(), ASSUME_WRITE_PERMISSION);
			Assertions.fail("Should have thrown an exception");
		} catch (final IllegalArgumentException e) {
			Assertions.assertTrue(e.getMessage().contains("already exists"));
		}
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetDatasets() throws Exception {
		datasetService.createAsset(createDataset("0"), project.getId(), ASSUME_WRITE_PERMISSION);
		datasetService.createAsset(createDataset("1"), project.getId(), ASSUME_WRITE_PERMISSION);
		datasetService.createAsset(createDataset("2"), project.getId(), ASSUME_WRITE_PERMISSION);

		final List<Dataset> datasets = datasetService.getPublicNotTemporaryAssets(0, 3);

		Assertions.assertEquals(3, datasets.size());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(createDataset(), project.getId(), ASSUME_WRITE_PERMISSION);

		final Dataset fetchedDataset = datasetService.getAsset(dataset.getId(), ASSUME_WRITE_PERMISSION).get();

		Assertions.assertEquals(dataset, fetchedDataset);
		Assertions.assertEquals(dataset.getId(), fetchedDataset.getId());
		Assertions.assertEquals(dataset.getCreatedOn(), fetchedDataset.getCreatedOn());
		Assertions.assertEquals(dataset.getUpdatedOn(), fetchedDataset.getUpdatedOn());
		Assertions.assertEquals(dataset.getDeletedOn(), fetchedDataset.getDeletedOn());
		Assertions.assertEquals(dataset.getGrounding(), fetchedDataset.getGrounding());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUpdateDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(createDataset(), project.getId(), ASSUME_WRITE_PERMISSION);
		dataset.setName("new name");

		final Dataset updatedDataset = datasetService
			.updateAsset(dataset, project.getId(), ASSUME_WRITE_PERMISSION)
			.orElseThrow();

		Assertions.assertEquals(dataset, updatedDataset);
		Assertions.assertNotNull(updatedDataset.getUpdatedOn());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteDataset() throws Exception {
		final Dataset dataset = datasetService.createAsset(createDataset(), project.getId(), ASSUME_WRITE_PERMISSION);

		datasetService.deleteAsset(dataset.getId(), project.getId(), ASSUME_WRITE_PERMISSION);

		final Optional<Dataset> deleted = datasetService.getAsset(dataset.getId(), ASSUME_WRITE_PERMISSION);

		Assertions.assertTrue(deleted.isEmpty());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCloneDataset() throws Exception {
		Dataset dataset = createDataset();
		dataset = datasetService.createAsset(dataset, project.getId(), ASSUME_WRITE_PERMISSION);

		final Dataset cloned = dataset.clone();

		Assertions.assertNotEquals(dataset.getId(), cloned.getId());
		Assertions.assertEquals(dataset.getGrounding().getIdentifiers(), cloned.getGrounding().getIdentifiers());
		Assertions.assertEquals(dataset.getGrounding().getModifiers(), cloned.getGrounding().getModifiers());
		Assertions.assertEquals(dataset.getColumns().size(), cloned.getColumns().size());
		for (int i = 0; i < dataset.getColumns().size(); i++) {
			Assertions.assertEquals(dataset.getColumns().get(i).getName(), cloned.getColumns().get(i).getName());
			Assertions.assertEquals(
				dataset.getColumns().get(i).getDescription(),
				cloned.getColumns().get(i).getDescription()
			);
			Assertions.assertEquals(dataset.getColumns().get(i).getDataType(), cloned.getColumns().get(i).getDataType());
			Assertions.assertEquals(
				dataset.getColumns().get(i).getAnnotations(),
				cloned.getColumns().get(i).getAnnotations()
			);
			Assertions.assertEquals(dataset.getColumns().get(i).getMetadata(), cloned.getColumns().get(i).getMetadata());
			Assertions.assertEquals(
				dataset.getColumns().get(i).getGrounding().getIdentifiers(),
				cloned.getColumns().get(i).getGrounding().getIdentifiers()
			);
			Assertions.assertEquals(
				dataset.getColumns().get(i).getGrounding().getModifiers(),
				cloned.getColumns().get(i).getGrounding().getModifiers()
			);
		}
	}
}
