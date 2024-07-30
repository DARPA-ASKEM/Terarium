package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.FileExport;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationUpdate;
import software.uncharted.terarium.hmiserver.repository.data.SimulationRepository;
import software.uncharted.terarium.hmiserver.repository.data.SimulationUpdateRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.service.s3.S3Service;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
@Slf4j
public class SimulationService extends TerariumAssetServiceWithoutSearch<Simulation, SimulationRepository> {

	private final SimulationUpdateRepository simulationUpdateRepository;

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
		final SimulationUpdateRepository simulationUpdateRepository
	) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, Simulation.class);
		this.simulationUpdateRepository = simulationUpdateRepository;
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

		final SimulationUpdate updated = simulationUpdateRepository.save(update);

		updated.setSimulation(simulation);
		simulation.getUpdates().add(updated);

		repository.save(simulation);

		return updated;
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
					fileExport.setFullpath(key);

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
