package software.uncharted.terarium.hmiserver.service.data;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.multiphysics.DecapodesContext;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;

@Service
@RequiredArgsConstructor
public class DecapodesContextService {

	private final ElasticsearchService elasticService;
	private final ElasticsearchConfiguration elasticConfig;

	@Observed(name = "function_profile")
	public List<DecapodesContext> getDecapodesContexts(final Integer page, final Integer pageSize) throws IOException {
		final SearchRequest req = new SearchRequest.Builder()
			.index(elasticConfig.getDecapodesContextIndex())
			.size(pageSize)
			.query(q -> q.bool(b -> b.mustNot(mn -> mn.exists(e -> e.field("deletedOn")))))
			.sort(
				new SortOptions.Builder().field(new FieldSort.Builder().field("timestamp").order(SortOrder.Asc).build()).build()
			)
			.build();

		return elasticService.search(req, DecapodesContext.class);
	}

	@Observed(name = "function_profile")
	public Optional<DecapodesContext> getDecapodesContext(final UUID id) throws IOException {
		final DecapodesContext doc = elasticService.get(
			elasticConfig.getDecapodesContextIndex(),
			id.toString(),
			DecapodesContext.class
		);
		if (doc != null && doc.getDeletedOn() == null) {
			return Optional.of(doc);
		}
		return Optional.empty();
	}

	@Observed(name = "function_profile")
	public void deleteDecapodesContext(final UUID id) throws IOException {
		final Optional<DecapodesContext> decapodesContext = getDecapodesContext(id);
		if (decapodesContext.isEmpty()) {
			return;
		}
		decapodesContext.get().setDeletedOn(Timestamp.from(Instant.now()));
		updateDecapodesContext(decapodesContext.get());
	}

	@Observed(name = "function_profile")
	public DecapodesContext createDecapodesContext(final DecapodesContext decapodesContext) throws IOException {
		decapodesContext.setCreatedOn(Timestamp.from(Instant.now()));
		elasticService.index(
			elasticConfig.getDecapodesContextIndex(),
			decapodesContext.setId(UUID.randomUUID()).getId().toString(),
			decapodesContext
		);
		return decapodesContext;
	}

	@Observed(name = "function_profile")
	public Optional<DecapodesContext> updateDecapodesContext(final DecapodesContext decapodesContext) throws IOException {
		if (!elasticService.documentExists(elasticConfig.getDecapodesContextIndex(), decapodesContext.getId().toString())) {
			return Optional.empty();
		}
		decapodesContext.setUpdatedOn(Timestamp.from(Instant.now()));
		elasticService.index(
			elasticConfig.getDecapodesContextIndex(),
			decapodesContext.getId().toString(),
			decapodesContext
		);
		return Optional.of(decapodesContext);
	}
}
