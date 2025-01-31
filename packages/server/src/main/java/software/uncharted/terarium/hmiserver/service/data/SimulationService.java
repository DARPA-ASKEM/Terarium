package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.FileExport;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationUpdate;
import software.uncharted.terarium.hmiserver.repository.data.SimulationRepository;
import software.uncharted.terarium.hmiserver.repository.data.SimulationUpdateRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.service.s3.S3Service;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
@Slf4j
public class SimulationService extends TerariumAssetService<Simulation, SimulationRepository> {

	private final SimulationUpdateRepository simulationUpdateRepository;
	private final DatasetService datasetService;

	/**
	 * Constructor for SimulationService
	 *
	 * @param config              application config
	 * @param projectAssetService project asset service
	 * @param repository          simulation repository
	 * @param s3ClientService     S3 client service
	 */
	public SimulationService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final SimulationRepository repository,
		final S3ClientService s3ClientService,
		final SimulationUpdateRepository simulationUpdateRepository,
		final DatasetService datasetService
	) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, Simulation.class);
		this.simulationUpdateRepository = simulationUpdateRepository;
		this.datasetService = datasetService;
	}

	@Override
	protected String getAssetPath() {
		return config.getResultsPath();
	}

	private String getResultsPath(final UUID simId, final String filename) {
		return String.join("/", config.getResultsPath(), simId.toString(), filename);
	}

	private String getDatasetPath(final UUID datasetId, final String filename) {
		return String.join("/", config.getDatasetPath(), datasetId.toString(), filename);
	}

	public SimulationUpdate appendUpdateToSimulation(
		final UUID simulationId,
		final SimulationUpdate update,
		final Schema.Permission hasReadPermission
	) {
		final Simulation simulation = getAsset(simulationId, hasReadPermission).orElseThrow();

		update.setSimulation(simulation);
		final SimulationUpdate created = simulationUpdateRepository.save(update);

		simulation.getUpdates().add(created);

		repository.save(simulation);

		return created;
	}

	public Dataset createDatasetFromSimulation(
		final UUID simId,
		final String datasetName,
		final UUID projectId,
		final boolean addToProject,
		final UUID modelConfigurationId,
		final UUID interventionPolicyId,
		final Schema.Permission permission
	) {
		try {
			final Optional<Simulation> sim = this.getAsset(simId, permission);
			final Optional<Project> project = projectService.getProject(projectId);
			if (sim.isEmpty()) {
				throw new IllegalArgumentException("Simulation not found");
			}

			if (!project.isPresent() && addToProject) {
				throw new IllegalArgumentException("Project not found");
			}

			Dataset dataset = datasetService.createAsset(new Dataset(), projectId, permission);
			dataset.setName(datasetName);
			dataset.setDescription(sim.get().getDescription());
			dataset.setFileNames(sim.get().getResultFiles());
			dataset.setDataSourceDate(sim.get().getCompletedTime());
			dataset.setColumns(new ArrayList<>());

			// Set the metadata
			final ObjectNode metadata = objectMapper.createObjectNode();
			final ObjectNode simulationAttributes = objectMapper.createObjectNode();

			metadata.put("simulationId", simId.toString());
			if (modelConfigurationId != null) {
				simulationAttributes.put("modelConfigurationId", modelConfigurationId.toString());
			}
			if (interventionPolicyId != null) {
				simulationAttributes.put("interventionPolicyId", interventionPolicyId.toString());
			}
			metadata.set("simulationAttributes", simulationAttributes);

			dataset.setMetadata(metadata);

			// Attach the user to the dataset
			if (sim.get().getUserId() != null) {
				dataset.setUserId(sim.get().getUserId());
			}

			// Duplicate the simulation results to a new dataset
			this.copySimulationResultToDataset(sim.get(), dataset);

			// Set column names:
			dataset = datasetService.extractColumnsFromFiles(dataset);
			datasetService.updateAsset(dataset, projectId, permission);

			// Add dataset to project
			if (addToProject) {
				projectAssetService.createProjectAsset(project.get(), AssetType.DATASET, dataset, permission);
			}

			return dataset;
		} catch (final Exception e) {
			log.error("Failed to create dataset from simulation", e);
			throw new IllegalArgumentException("Failed to create dataset from simulation");
		}
	}

	@Observed(name = "function_profile")
	public void copySimulationResultToDataset(final Simulation simulation, final Dataset dataset) {
		final UUID simId = simulation.getId();
		if (simulation.getResultFiles() != null) {
			for (final String resultFile : simulation.getResultFiles()) {
				final String filename = S3Service.parseFilename(resultFile);
				final String srcPath = getResultsPath(simId, filename);
				final String destPath = getDatasetPath(dataset.getId(), filename);
				s3ClientService
					.getS3Service()
					.copyObject(config.getFileStorageS3BucketName(), srcPath, config.getFileStorageS3BucketName(), destPath);
			}
		}
	}

	@Observed(name = "function_profile")
	public void copyAssetFiles(
		final Simulation newAsset,
		final Simulation oldAsset,
		final Schema.Permission hasWritePermission
	) throws IOException {
		super.copyAssetFiles(newAsset, oldAsset, hasWritePermission);

		final String bucket = config.getFileStorageS3BucketName();
		final List<String> validResults = new ArrayList<>();
		if (oldAsset.getResultFiles() != null) {
			for (final String resultFile : oldAsset.getResultFiles()) {
				final String filename = S3Service.parseFilename(resultFile);
				final String srcKey = getResultsPath(oldAsset.getId(), filename);
				final String dstKey = getResultsPath(newAsset.getId(), filename);
				try {
					s3ClientService.getS3Service().copyObject(bucket, srcKey, bucket, dstKey);
					validResults.add(filename);
				} catch (final NoSuchKeyException e) {
					log.error("Failed to copy simulation result file {}, no object found, excluding from exported asset", e);
					continue;
				}
			}
			newAsset.setResultFiles(validResults);
		}
	}

	@Observed(name = "function_profile")
	public Map<String, FileExport> exportAssetFiles(final UUID simId, final Schema.Permission hasReadPermission)
		throws IOException {
		final Map<String, FileExport> files = super.exportAssetFiles(simId, hasReadPermission);

		// we also need to export the result files

		final Simulation simulation = getAsset(simId, Schema.Permission.WRITE).orElseThrow();
		if (simulation.getResultFiles() != null) {
			for (final String resultFile : simulation.getResultFiles()) {
				final String filename = S3Service.parseFilename(resultFile);
				final String key = getResultsPath(simId, filename);

				try {
					final ResponseInputStream<GetObjectResponse> stream = s3ClientService
						.getS3Service()
						.getObject(config.getFileStorageS3BucketName(), key);
					final byte[] bytes = stream.readAllBytes();

					final String contentType = stream.response().contentType();

					final FileExport fileExport = new FileExport();
					fileExport.setBytes(bytes);
					fileExport.setContentType(ContentType.parse(contentType));
					fileExport.setPathPrefix(config.getResultsPath());

					files.put(resultFile, fileExport);
				} catch (final NoSuchKeyException e) {
					log.error("Failed to export simulation result file {}, no object found, excluding from exported asset", e);
					continue;
				}
			}
		}

		return files;
	}
}
