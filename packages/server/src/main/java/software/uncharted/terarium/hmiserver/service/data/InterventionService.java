package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.simulationservice.interventions.InterventionPolicy;
import software.uncharted.terarium.hmiserver.repository.data.InterventionRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;

@Service
public class InterventionService extends TerariumAssetService<InterventionPolicy, InterventionRepository> {

	public InterventionService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final InterventionRepository repository,
		final S3ClientService s3ClientService
	) {
		super(
			objectMapper,
			config,
			projectService,
			projectAssetService,
			repository,
			s3ClientService,
			InterventionPolicy.class
		);
	}

	@Override
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Interventions are not stored in S3");
	}
}
