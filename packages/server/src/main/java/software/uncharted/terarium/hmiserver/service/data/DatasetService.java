package software.uncharted.terarium.hmiserver.service.data;

import org.springframework.stereotype.Service;

import io.micrometer.observation.annotation.Observed;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.repository.data.DatasetRepository;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;

@Service
public class DatasetService extends TerariumAssetServiceWithSearch<Dataset, DatasetRepository> {

	public DatasetService(
			final Config config,
			final ElasticsearchConfiguration elasticConfig,
			final ElasticsearchService elasticService,
			final ProjectAssetService projectAssetService,
			final S3ClientService s3ClientService,
			final DatasetRepository repository) {
		super(config, elasticConfig, elasticService, projectAssetService, s3ClientService, repository, Dataset.class);
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		return config.getDatasetPath();
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetIndex() {
		return elasticConfig.getDatasetIndex();
	}

	@Override
	@Observed(name = "function_profile")
	public String getAssetAlias() {
		return elasticConfig.getDatasetAlias();
	}
}
