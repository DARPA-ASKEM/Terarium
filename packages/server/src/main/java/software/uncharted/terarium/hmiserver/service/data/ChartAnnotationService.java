package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.ChartAnnotation;
import software.uncharted.terarium.hmiserver.repository.data.ChartAnnotationRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
public class ChartAnnotationService extends TerariumAssetService<ChartAnnotation, ChartAnnotationRepository> {

	public ChartAnnotationService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final ChartAnnotationRepository repository,
		final S3ClientService s3ClientService
	) {
		super(
			objectMapper,
			config,
			projectService,
			projectAssetService,
			repository,
			s3ClientService,
			ChartAnnotation.class
		);
	}

	public List<ChartAnnotation> getAnnotationsByNodeId(final UUID nodeId, final Schema.Permission hasReadPermission) {
		return repository.findByNodeIdAndDeletedOnIsNull(nodeId);
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Chart annotations are not stored in S3");
	}
}
