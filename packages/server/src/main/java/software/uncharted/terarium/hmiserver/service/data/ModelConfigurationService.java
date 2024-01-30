package software.uncharted.terarium.hmiserver.service.data;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import lombok.RequiredArgsConstructor;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelConfiguration;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;

@Service
@RequiredArgsConstructor
public class ModelConfigurationService {

	private final ElasticsearchService elasticService;
	private final ElasticsearchConfiguration elasticConfig;

	public List<ModelConfiguration> getModelConfigurations(Integer page, Integer pageSize)
			throws IOException {

		final SearchRequest req = new SearchRequest.Builder()
				.index(elasticConfig.getModelConfigurationIndex())
				.size(pageSize)
				.query(q -> q.bool(b -> b
					.mustNot(mn -> mn.exists(e -> e.field("deletedOn")))
					.mustNot(mn -> mn.term(t -> t.field("temporary").value(true)))))
				.sort(new SortOptions.Builder()
						.field(new FieldSort.Builder().field("timestamp").order(SortOrder.Asc).build()).build())
				.build();

		return elasticService.search(req, ModelConfiguration.class);
	}

	public Optional<ModelConfiguration> getModelConfiguration(UUID id) throws IOException {
		ModelConfiguration doc = elasticService.get(elasticConfig.getModelConfigurationIndex(), id.toString(),
				ModelConfiguration.class);
		if (doc != null && doc.getDeletedOn() == null) {
			return Optional.of(doc);
		}
		return Optional.empty();
	}

	public void deleteModelConfiguration(UUID id) throws IOException {
		Optional<ModelConfiguration> modelConfiguration = getModelConfiguration(id);
		if (modelConfiguration.isEmpty()) {
			return;
		}
		modelConfiguration.get().setDeletedOn(Timestamp.from(Instant.now()));
		updateModelConfiguration(modelConfiguration.get());
	}

	public ModelConfiguration createModelConfiguration(ModelConfiguration modelConfiguration) throws IOException {
		modelConfiguration.setCreatedOn(Timestamp.from(Instant.now()));
		elasticService.index(elasticConfig.getModelConfigurationIndex(),
				modelConfiguration.setId(UUID.randomUUID()).getId().toString(),
				modelConfiguration);
		return modelConfiguration;
	}

	public Optional<ModelConfiguration> updateModelConfiguration(ModelConfiguration modelConfiguration)
			throws IOException {
		if (!elasticService.contains(elasticConfig.getModelConfigurationIndex(),
				modelConfiguration.getId().toString())) {
			return Optional.empty();
		}
		modelConfiguration.setUpdatedOn(Timestamp.from(Instant.now()));
		elasticService.index(elasticConfig.getModelConfigurationIndex(), modelConfiguration.getId().toString(),
				modelConfiguration);
		return Optional.of(modelConfiguration);
	}

}
