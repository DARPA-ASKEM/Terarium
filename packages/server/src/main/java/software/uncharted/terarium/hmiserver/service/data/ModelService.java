package software.uncharted.terarium.hmiserver.service.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import lombok.RequiredArgsConstructor;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelDescription;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;

@Service
@RequiredArgsConstructor
public class ModelService {

	private final ElasticsearchService elasticService;
	private final ElasticsearchConfiguration elasticConfig;
	private final ObjectMapper objectMapper;

	public List<ModelDescription> getDescriptions(Integer page, Integer pageSize) throws IOException {

		SourceConfig source = new SourceConfig.Builder()
				.filter(new SourceFilter.Builder().excludes("model", "semantics").build())
				.build();

		final SearchRequest req = new SearchRequest.Builder()
				.index(elasticConfig.getModelIndex())
				.from(page)
				.size(pageSize)
				.source(source)
				.build();

		return elasticService.search(req, Model.class).stream().map(m -> ModelDescription.fromModel(m)).toList();
	}

	public ModelDescription getDescription(UUID id) throws IOException {
		return ModelDescription
				.fromModel(elasticService.get(elasticConfig.getModelIndex(), id.toString(), Model.class));
	}

	public List<Model> searchModels(Integer page, Integer pageSize, JsonNode queryJson) throws IOException {

		Query query = null;
		if (queryJson != null) {
			query = new Query.Builder().withJson(
					new ByteArrayInputStream(objectMapper.writeValueAsString(queryJson).getBytes())).build();
		}

		SourceConfig source = new SourceConfig.Builder()
				.filter(new SourceFilter.Builder().excludes("model", "semantics").build())
				.build();

		final SearchRequest req = new SearchRequest.Builder()
				.index(elasticConfig.getModelIndex())
				.from(page)
				.size(pageSize)
				.source(source)
				.query(query)
				.build();
		return elasticService.search(req, Model.class);
	}

	public List<ModelConfiguration> getModelConfigurationsByModelId(UUID id, Integer page, Integer pageSize)
			throws IOException {

		final SearchRequest req = new SearchRequest.Builder()
				.index(elasticConfig.getModelConfigurationIndex())
				.from(page)
				.size(pageSize)
				.query(q -> q
						.bool(b -> b
								.mustNot(mn -> mn
										.exists(e -> e
												.field("deleted_on")))
								.must(m -> m
										.term(t -> t
												.field("model_id")
												.value(id.toString())))))
				.sort(new SortOptions.Builder()
						.field(new FieldSort.Builder().field("timestamp").order(SortOrder.Asc).build()).build())
				.build();

		return elasticService.search(req, ModelConfiguration.class);
	}

	public Model getModel(UUID id) throws IOException {
		return elasticService.get(elasticConfig.getModelIndex(), id.toString(), Model.class);
	}

	public void deleteModel(UUID id) throws IOException {
		elasticService.delete(elasticConfig.getModelIndex(), id.toString());
	}

	public Model createModel(Model model) throws IOException {
		model.setCreatedOn(Timestamp.from(Instant.now()));
		elasticService.index(elasticConfig.getModelIndex(), model.setId(UUID.randomUUID()).getId().toString(), model);
		return model;
	}

	public Optional<Model> updateModel(Model model) throws IOException {
		if (!elasticService.contains(elasticConfig.getModelIndex(), model.getId().toString())) {
			return Optional.empty();
		}
		model.setUpdatedOn(Timestamp.from(Instant.now()));
		elasticService.index(elasticConfig.getModelIndex(), model.getId().toString(), model);
		return Optional.of(model);
	}

}
