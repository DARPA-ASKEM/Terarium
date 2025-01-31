package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn;
import software.uncharted.terarium.hmiserver.repository.data.DatasetRepository;
import software.uncharted.terarium.hmiserver.service.gollm.DatasetStatistics;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Slf4j
@Service
public class DatasetService extends TerariumAssetService<Dataset, DatasetRepository> {

	private final DatasetStatistics datasetStatistics;

	public DatasetService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final DatasetRepository repository,
		final S3ClientService s3ClientService,
		final DatasetStatistics datasetStatistics,
		final TaskService taskService
	) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, Dataset.class);
		this.datasetStatistics = datasetStatistics;
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		return config.getDatasetPath();
	}

	@Override
	@Observed(name = "function_profile")
	public Dataset createAsset(final Dataset asset, final UUID projectId, final Schema.Permission hasWritePermission)
		throws IOException {
		// If the columns are already set, don't add them again (happens when copying a dataset to another project).
		if (asset.getColumns() == null || asset.getColumns().isEmpty()) {
			extractColumns(asset);
		}
		verifyColumnRelationship(asset);
		return super.createAsset(asset, projectId, hasWritePermission);
	}

	@Override
	@Observed(name = "function_profile")
	public List<Dataset> createAssets(
		final List<Dataset> assets,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException {
		for (final Dataset asset : assets) {
			verifyColumnRelationship(asset);
		}
		return super.createAssets(assets, projectId, hasWritePermission);
	}

	@Override
	@Observed(name = "function_profile")
	public Optional<Dataset> updateAsset(
		final Dataset asset,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException, IllegalArgumentException {
		verifyColumnRelationship(asset);
		return super.updateAsset(asset, projectId, hasWritePermission);
	}

	// This method throws if it can't get the files
	@Observed(name = "function_profile")
	public Dataset extractColumnsFromFiles(final Dataset dataset) throws IOException {
		if (dataset.getColumns() != null && !dataset.getColumns().isEmpty()) {
			return dataset;
		}
		return extractColumns(dataset);
	}

	/**
	 * Extract the columns from the dataset files.
	 *
	 * @param dataset the dataset to extract the columns from
	 * @return the dataset with the columns extracted
	 * @throws IOException if the files cannot be read
	 */
	@Observed(name = "function_profile")
	private Dataset extractColumns(final Dataset dataset) throws IOException {
		if (dataset.getFileNames() != null) {
			for (final String filename : dataset.getFileNames()) {
				if (filename.endsWith(Dataset.NC_EXTENSION)) {
					continue;
				}
				final CSVParser csvParser = getCSVFileParser(filename, dataset.getId());
				if (csvParser == null) continue;
				final List<String> headers = new ArrayList<>(csvParser.getHeaderMap().keySet());
				addDatasetColumns(dataset, filename, headers);

				// Calculate the statistics for the columns
				try {
					final PresignedURL datasetUrl = getDownloadUrl(dataset.getId(), filename).orElseThrow(() ->
						new Exception("Download URL not found")
					);

					datasetStatistics.add(dataset, datasetUrl);
				} catch (final Exception e) {
					log.error("Error calculating statistics for dataset {}", dataset.getId(), e);
				}
			}
		}
		return dataset;
	}

	/**
	 * Add the columns to the dataset.
	 *
	 * @param dataset  the dataset to add the columns to
	 * @param fileName the name of the file
	 * @param headers  the headers to add
	 */
	@Observed(name = "function_profile")
	public static void addDatasetColumns(final Dataset dataset, final String fileName, final List<String> headers) {
		if (dataset.getColumns() == null) {
			dataset.setColumns(new ArrayList<>());
		}
		for (final String header : headers) {
			final DatasetColumn column = new DatasetColumn();
			column.setName(header);
			column.setFileName(fileName);
			column.setAnnotations(new ArrayList<>());
			column.setDataset(dataset);
			dataset.getColumns().add(column);
		}
	}

	/**
	 * Get the CSV file parser for the given file name and dataset id.
	 *
	 * @param filename  the name of the file to parse
	 * @param datasetId the id of the dataset
	 * @return the CSV file parser
	 * @throws IOException if the file cannot be read
	 */
	@Observed(name = "function_profile")
	public CSVParser getCSVFileParser(final String filename, final UUID datasetId) throws IOException {
		final Optional<byte[]> bytes = fetchFileAsBytes(datasetId, filename);
		if (bytes.isEmpty()) {
			return null;
		}

		final Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes.get()));
		return new CSVParser(
			reader,
			CSVFormat.Builder.create(CSVFormat.DEFAULT)
				.setAllowMissingColumnNames(true)
				.setHeader()
				.setSkipHeaderRecord(false)
				.build()
		);
	}

	/**
	 * Verify that the columns have the correct relationship to the dataset. This is
	 * needed
	 * because the columns are not directly related to the dataset in the database
	 * by default.
	 *
	 * @param asset the dataset to verify
	 */
	private static void verifyColumnRelationship(final Dataset asset) {
		Optional.ofNullable(asset.getColumns()).ifPresent(columns -> columns.forEach(column -> column.setDataset(asset)));
	}
}
