package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.code.Code;
import software.uncharted.terarium.hmiserver.repository.data.CodeRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;

@Service
public class CodeService extends TerariumAssetServiceWithoutSearch<Code, CodeRepository> {

	public CodeService(
			final ObjectMapper objectMapper,
			final Config config,
			final ProjectService projectService,
			final ProjectAssetService projectAssetService,
			final CodeRepository repository,
			final S3ClientService s3ClientService) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, Code.class);
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		return config.getCodePath();
	}
}
