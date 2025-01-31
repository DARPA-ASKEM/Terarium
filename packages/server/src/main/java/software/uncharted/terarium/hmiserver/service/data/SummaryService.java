package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.Summary;
import software.uncharted.terarium.hmiserver.repository.data.SummaryRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
public class SummaryService extends TerariumAssetService<Summary, SummaryRepository> {

	public SummaryService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final SummaryRepository repository,
		final S3ClientService s3ClientService
	) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, Summary.class);
	}

	public List<Summary> getSummaries(final List<UUID> ids, final Schema.Permission hasReadPermission) {
		return repository.findAllByIdInAndDeletedOnIsNull(ids);
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Summaries are not stored in S3");
	}
}
