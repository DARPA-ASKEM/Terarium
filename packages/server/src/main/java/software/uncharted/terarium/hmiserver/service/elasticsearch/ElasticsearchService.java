package software.uncharted.terarium.hmiserver.service.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.cluster.ExistsComponentTemplateRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.msearch.RequestItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfigParam;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteAliasRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.elasticsearch.ingest.GetPipelineRequest;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;

@Service
@Data
@Slf4j
public class ElasticsearchService {

	private final ObjectMapper mapper;

	private final RestTemplateBuilder restTemplateBuilder;

	private RestTemplate restTemplate;

	private ElasticsearchClient client = null;

	private final ElasticsearchConfiguration config;

	protected RestTemplate getRestTemplate() {
		if (restTemplate == null) {
			initRestTemplate();
		}

		return restTemplate;
	}

	private void initRestTemplate() {
		RestTemplateBuilder builder = getRestTemplateBuilder();
		if (config.isAuthEnabled()) {
			builder = builder.basicAuthentication(config.getUsername(), config.getPassword());
		}
		this.restTemplate = builder.build();
	}

	public static RuntimeException handleException(final ElasticsearchException e) {
		String error = "ElasticsearchException: " + e.response().error().reason();
		if (e.response().error().rootCause() != null && e.response().error().rootCause().size() > 0) {
			error += ", root cause: " + e.response().error().rootCause().toString();
		}
		final ErrorCause causedBy = e.response().error().causedBy();
		if (causedBy != null) {
			error += ", caused by: " + causedBy.reason();
		}
		return new RuntimeException(error, e);
	}

	@PostConstruct
	public void init() {
		log.info("Connecting elasticsearch client to: {}", config.getUrl());

		final RestClientBuilder httpClientBuilder = RestClient.builder(HttpHost.create(config.getUrl()));

		if (config.isAuthEnabled()) {
			final String auth = config.getUsername() + ":" + config.getPassword();
			final String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			final Header header = new BasicHeader("Authorization", "Basic " + encodedAuth);

			httpClientBuilder.setDefaultHeaders(new Header[] { header });
		}

		final RestClient httpClient = httpClientBuilder.build();

		// Now you can create an ElasticsearchTransport object using the RestClient
		final ElasticsearchTransport transport = new RestClientTransport(httpClient, new JacksonJsonpMapper(mapper));

		client = new ElasticsearchClient(transport);

		try {
			client.ping();
		} catch (final IOException e) {
			log.error("Unable to ping Elasticsearch Rest Client", e);
		}
	}

	/**
	 * Check for the existence of an index.
	 *
	 * @return True if the index exists, false otherwise
	 */
	public boolean indexExists(final String indexName) throws IOException {
		try {
			return client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Check for the existence of a document in an index by id.
	 *
	 * @return True if the index exists, false otherwise
	 */
	public boolean documentExists(final String indexName, final String id) throws IOException {
		try {
			final GetRequest req = new GetRequest.Builder()
				.index(indexName)
				.id(id)
				.source(new SourceConfigParam.Builder().fetch(false).build())
				.build();

			final GetResponse<JsonNode> response = client.get(req, JsonNode.class);
			return response.found();
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/** Refresh an index. */
	public boolean refreshIndex(final String indexName) throws IOException {
		try {
			final RefreshRequest refreshRequest = new RefreshRequest.Builder().index(indexName).build();
			final RefreshResponse refreshResponse = client.indices().refresh(refreshRequest);

			// Check if the refresh was acknowledged
			return refreshResponse.shards().successful().longValue() > 0;
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Count the number of documents in an index.
	 *
	 * @param index
	 * @return
	 */
	public long count(final String index) throws IOException {
		try {
			final CountRequest countRequest = new CountRequest.Builder().index(index).build();
			final CountResponse countResponse = client.count(countRequest);
			return countResponse.count();
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Create the provided index.
	 *
	 * @param index
	 * @throws IOException
	 */
	public void createIndex(final String index) throws IOException {
		try {
			final CreateIndexRequest req = new CreateIndexRequest.Builder().index(index).build();

			client.indices().create(req);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Create the provided index.
	 *
	 * @param index The name of the index to create
	 * @throws IOException If an error occurs while creating the index
	 */
	public void createIndex(final String index, final String mapping) throws IOException {
		try {
			final JsonpMapper mapper = client._transport().jsonpMapper();
			final JsonParser parser = mapper.jsonProvider().createParser(new StringReader(mapping));

			log.info("Creating index {} with mapping", index);

			final CreateIndexRequest req = new CreateIndexRequest.Builder()
				.index(index)
				.mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
				.build();

			client.indices().create(req);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Create the provided index if it doesn't exist, if it does, delete it and
	 * re-create it.
	 *
	 * @param index
	 * @throws IOException
	 */
	public void createOrEnsureIndexIsEmpty(final String index) throws IOException {
		try {
			if (indexExists(index)) {
				deleteIndex(index);
			}
			log.info("Creating index {}", index);
			createIndex(index);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Returns true if the ES cluster contains the index template with the provided
	 * name, false otherwise
	 *
	 * @param name The name of the index template to check existence for
	 * @return True if the index template is contained in the cluster, false
	 *         otherwise
	 */
	public boolean containsIndexTemplate(final String name) throws IOException {
		try {
			final ExistsIndexTemplateRequest req = new ExistsIndexTemplateRequest.Builder().name(name).build();

			return client.indices().existsIndexTemplate(req).value();
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Put an index template to the cluster
	 *
	 * @param name         The name of the index template
	 * @param templateJson The index template json string
	 * @return True if the index template was successfully added, false otherwise
	 */
	public boolean putIndexTemplate(final String name, final String templateJson) {
		try {
			return putTyped(name, templateJson, "index template", "_index_template");
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Check if the cluster contains the pipeline with the provided id
	 *
	 * @param id The name of the pipeline to check existence for
	 * @return True if the pipeline is contained in the cluster, false otherwise
	 */
	public boolean containsPipeline(final String id) throws IOException {
		try {
			final GetPipelineRequest req = new GetPipelineRequest.Builder().id(id).build();
			return client.ingest().getPipeline(req).result().containsKey(id);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Put a pipeline to the cluster
	 *
	 * @param name         The name of the pipeline
	 * @param pipelineJson The pipeline json string
	 * @return True if the pipeline was successfully added, false otherwise
	 */
	public boolean putPipeline(final String name, final String pipelineJson) {
		try {
			return putTyped(name, pipelineJson, "pipeline", "_ingest/pipeline");
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Returns true if the ES cluster contains the component template with the
	 * provided name, false otherwise
	 *
	 * @param name The name of the index template to check existence for
	 * @return True if the component template is contained in the cluster, false
	 *         otherwise
	 */
	public boolean containsComponentTemplate(final String name) throws IOException {
		final ExistsComponentTemplateRequest req = new ExistsComponentTemplateRequest.Builder().name(name).build();

		return client.cluster().existsComponentTemplate(req).value();
	}

	/**
	 * Put a component template to the cluster
	 *
	 * @param name         The name of the index template
	 * @param templateJson The component template json string
	 * @return True if the component template was successfully added, false
	 *         otherwise
	 */
	public boolean putComponentTemplate(final String name, final String templateJson) {
		return putTyped(name, templateJson, "component template", "_component_template");
	}

	/**
	 * Put a typed object to the cluster
	 *
	 * @param name      The name of the object
	 * @param typedJson The object json string
	 * @param typeName  The type of the object
	 * @param indexName The index to put the object in
	 * @return True if the object was successfully added, false otherwise
	 */
	private boolean putTyped(final String name, final String typedJson, final String typeName, final String indexName) {
		try {
			final HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			final HttpEntity<String> entity = new HttpEntity<>(typedJson, headers);
			final ResponseEntity<JsonNode> response = getRestTemplate()
				.exchange(new URI(config.getUrl() + "/" + indexName + "/" + name), HttpMethod.PUT, entity, JsonNode.class);
			final JsonNode body = response.getBody();
			if (body != null) {
				return body.at("/acknowledged").asBoolean();
			}
		} catch (final Exception e) {
			log.error("Error putting " + typeName + " {}", name, e);
		}
		return false;
	}

	/**
	 * Search an index.
	 *
	 * @param <T>    The type of the document
	 * @param req    - The search request
	 * @param tClass The class of the document
	 * @return A list of found documents.
	 */
	public <T> List<T> search(final SearchRequest req, final Class<T> tClass) throws IOException {
		try {
			log.info("Searching: {}", req.index());

			final List<T> docs = new ArrayList<>();
			final SearchResponse<T> res = client.search(req, tClass);
			for (final Hit<T> hit : res.hits().hits()) {
				docs.add(hit.source());
			}
			return docs;
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Search an index.
	 *
	 * @param <T>    The type of the document
	 * @param req    - The search request
	 * @param tClass The class of the document
	 * @return A list of found documents.
	 */
	public <T> SearchResponse<T> searchWithResponse(final SearchRequest req, final Class<T> tClass) throws IOException {
		try {
			return client.search(req, tClass);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Add a document to an index.
	 *
	 * @param <T>      The type of the document
	 * @param index    The index to add the document to
	 * @param id       The id of the document
	 * @param document The document to add
	 */
	public <T> void index(final String index, final String id, final T document) throws IOException {
		try {
			log.info("Indexing: {} into {}", id, index);

			final IndexRequest<T> req = new IndexRequest.Builder<T>().index(index).id(id).document(document).build();

			client.index(req);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Add a document to an index with a routing key.
	 *
	 * @param <T>      The type of the document
	 * @param index    The index to add the document to
	 * @param id       The id of the document
	 * @param document The document to add
	 */
	public <T> void indexWithRouting(final String index, final String id, final T document, final String routing)
		throws IOException {
		try {
			log.info("Indexing: {} into {}", id, index);

			final IndexRequest<T> req = new IndexRequest.Builder<T>()
				.index(index)
				.id(id)
				.document(document)
				.routing(routing)
				.build();

			client.index(req);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Remove a document from an index.
	 *
	 * @param index The index to remove the document from
	 * @param id    The id of the document to remove
	 */
	public void delete(final String index, final String id) throws IOException {
		try {
			log.info("Deleting: {} from {}", id, index);

			final DeleteRequest req = new DeleteRequest.Builder().index(index).id(id).build();

			client.delete(req);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Update a document from an index.
	 *
	 * @param index   The index to remove the document from
	 * @param id      The id of the document to remove
	 * @param partial The partial document to update.
	 */
	public <T, Partial> void update(final String index, final String id, final Partial partial) throws IOException {
		try {
			log.info("Updating: {} from {}", id, index);

			final UpdateRequest<T, Partial> req = new UpdateRequest.Builder<T, Partial>()
				.index(index)
				.id(id)
				.doc(partial)
				.build();

			client.update(req, Void.class);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Update a document from an index.
	 *
	 * @param index   The index to remove the document from
	 * @param id      The id of the document to remove
	 * @param partial The partial document to update.
	 * @param routing The routing key for the document.
	 */
	public <T, Partial> void updateWithRouting(
		final String index,
		final String id,
		final Partial partial,
		final String routing
	) throws IOException {
		try {
			log.info("Updating: {} from {}", id, index);

			final UpdateRequest<T, Partial> req = new UpdateRequest.Builder<T, Partial>()
				.index(index)
				.id(id)
				.doc(partial)
				.routing(routing)
				.build();

			client.update(req, Void.class);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Remove an index.
	 *
	 * @param index The index to remove
	 */
	public void deleteIndex(final String index) throws IOException {
		try {
			log.info("Deleting index: {}", index);

			final DeleteIndexRequest deleteRequest = new DeleteIndexRequest.Builder().index(index).build();

			client.indices().delete(deleteRequest);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Get a single document by id.
	 *
	 * @param <T>    The type of the document
	 * @param index  The index to get the document from
	 * @param id     The id of the document to get
	 * @param tClass The class of the document
	 * @return The document if found, null otherwise
	 */
	public <T> T get(final String index, final String id, final Class<T> tClass) throws IOException {
		try {
			log.info("Getting: {} from {}", id, index);

			final GetRequest req = new GetRequest.Builder().index(index).id(id).build();

			final GetResponse<T> res = client.get(req, tClass);
			if (res.found()) {
				return res.source();
			}
			return null;
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public <T> SearchResponse<T> knnSearch(
		final String index,
		final KnnQuery knn,
		final Query query,
		final Integer page,
		final Integer pageSize,
		final List<String> excludes,
		final Class<T> tClass
	) throws IOException {
		try {
			log.info("KNN search on: {}", index);

			final SearchRequest.Builder builder = new SearchRequest.Builder()
				.index(index)
				.from(page)
				.source(s -> s.filter(f -> f.excludes(excludes)))
				.size(pageSize);

			if (knn != null) {
				if (knn.numCandidates() < knn.k()) {
					throw new IllegalArgumentException("Number of candidates must be greater than or equal to k");
				}

				if (query != null) {
					knn.filter().add(query);
				}

				builder.knn(knn);
			}

			final SearchRequest req = builder.build();

			return client.search(req, tClass);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public <T> MsearchResponse bulkKnnSearch(final String index, final List<KnnQuery> knnQueries, final Class<T> tClass)
		throws IOException {
		try {
			log.info("KNN search on: {}", index);

			List<RequestItem> items = new ArrayList<>();
			for (KnnQuery knnQuery : knnQueries) {
				RequestItem item = new RequestItem.Builder()
					.header(h -> h.index(index)) // Specify the index for the request
					.body(b -> b.knn(knnQuery)) // Add the KnnQuery as the body
					.build();

				items.add(item);
			}

			MsearchRequest request = new MsearchRequest.Builder().index(index).searches(items).build();

			return client.msearch(request, tClass);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	/**
	 * Bulk insert documents into an index.
	 *
	 * @param indexName      The index to insert the documents into
	 * @param esIndexContent The content of the index
	 */
	public void bulkInsert(final String indexName, final String esIndexContent) {
		try {
			final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

			final com.fasterxml.jackson.core.JsonParser parser = mapper.createParser(esIndexContent);
			while (parser.hasCurrentToken()) {
				final JsonNode node = mapper.readTree(parser);
				log.warn("Inserting " + node.asText());
				final String id = node.get("id").asText();
				bulkRequest.operations(op -> op.index(idx -> idx.index(indexName).id(id).document(node)));
			}
			log.warn("Bulk inserting documents into index {}", indexName);
			client.bulk(bulkRequest.build());
		} catch (final IOException e) {
			log.error("Error bulk inserting documents into index {}", indexName, e);
		}
	}

	@Data
	public static class KnnInnerHit<InnerType> {

		private UUID id;
		private InnerType source;
		private Float score;
	}

	@Data
	public static class KnnHit<Type, InnerType> {

		private UUID id;
		private Type source;
		private Float score;
		private List<KnnInnerHit<InnerType>> innerHits = new ArrayList<>();
	}

	@Data
	public static class KnnSearchResponse<Type, InnerType> {

		private List<KnnHit<Type, InnerType>> hits = new ArrayList<>();
		private long total;
	}

	private JsonNode getFirstField(final JsonNode jsonNode) {
		final Iterator<String> fieldNames = jsonNode.fieldNames();
		if (fieldNames.hasNext()) {
			return jsonNode.get(fieldNames.next());
		}
		return null;
	}

	public <Type, InnerType> KnnSearchResponse<Type, InnerType> knnSearchWithInnerHits(
		final String index,
		final KnnQuery knn,
		final Query query,
		final Integer page,
		final Integer pageSize,
		final List<String> excludes,
		final Class<Type> hitClass,
		final Class<InnerType> innerHitClass
	) throws IOException {
		try {
			log.info("KNN search on: {}", index);

			final SearchRequest.Builder builder = new SearchRequest.Builder()
				.index(index)
				.from(page)
				.source(s -> s.filter(f -> f.excludes(excludes)))
				.size(pageSize);

			if (knn != null) {
				if (knn.numCandidates() < knn.k()) {
					throw new IllegalArgumentException("Number of candidates must be greater than or equal to k");
				}
				builder.knn(knn);
			}

			if (query != null) {
				builder.query(query);
			}

			final SearchRequest req = builder.build();

			final String queryStr = JsonpUtils.toJsonString(req, new JacksonJsonpMapper(mapper));
			final ObjectNode jsonQuery = (ObjectNode) mapper.readTree(queryStr);

			// manually inject the inner hits because the java client is stupid
			for (final JsonNode knnJson : jsonQuery.get("knn")) {
				((ObjectNode) knnJson).set("inner_hits", mapper.createObjectNode());
			}

			final JsonNode res = rawSearch(index, jsonQuery);

			final KnnSearchResponse<Type, InnerType> response = new KnnSearchResponse<>();
			response.total = res.get("hits").get("total").get("value").asLong();

			for (final JsonNode hit : res.get("hits").get("hits")) {
				final KnnHit<Type, InnerType> knnHit = new KnnHit<>();
				knnHit.id = UUID.fromString(hit.get("_id").asText());
				knnHit.source = mapper.convertValue(hit.get("_source"), hitClass);
				knnHit.score = hit.get("_score").floatValue();

				if (hit.get("inner_hits") == null || getFirstField(hit.get("inner_hits")) == null) {
					continue;
				}

				for (final JsonNode innerHit : getFirstField(hit.get("inner_hits")).get("hits").get("hits")) {
					final KnnInnerHit<InnerType> knnInnerHit = new KnnInnerHit<>();
					knnInnerHit.id = UUID.fromString(innerHit.get("_id").asText());
					knnInnerHit.source = mapper.convertValue(innerHit.get("_source"), innerHitClass);
					knnInnerHit.score = innerHit.get("_score").floatValue();

					knnHit.innerHits.add(knnInnerHit);
				}

				response.hits.add(knnHit);
			}

			return response;
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public JsonNode rawSearch(final String index, final JsonNode body) throws IOException {
		try {
			final HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			final HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

			final ResponseEntity<JsonNode> response = getRestTemplate()
				.exchange(new URI(config.getUrl() + "/" + index + "/_search"), HttpMethod.POST, entity, JsonNode.class);

			return response.getBody();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Data
	public static class BulkOpResponse {

		private List<String> errors;
		private long took;
	}

	public BulkOpResponse bulkIndex(final String index, final List<TerariumAsset> docs) throws IOException {
		try {
			final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

			for (final TerariumAsset doc : docs) {
				if (doc.getId() == null) {
					throw new RuntimeException("Document id cannot be null");
				}
				bulkRequest.operations(op -> op.index(idx -> idx.index(index).id(doc.getId().toString()).document(doc)));
			}

			final BulkResponse bulkResponse = client.bulk(bulkRequest.build());

			final List<String> errors = new ArrayList<>();
			if (bulkResponse.errors()) {
				for (final BulkResponseItem item : bulkResponse.items()) {
					final ErrorCause error = item.error();
					if (error != null) {
						errors.add(error.reason());
					}
				}
			}

			final BulkOpResponse r = new BulkOpResponse();
			r.setErrors(errors);
			r.setTook(bulkResponse.took());
			return r;
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public BulkOpResponse bulkUpdate(final String index, final List<TerariumAsset> docs) throws IOException {
		try {
			final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

			final List<BulkOperation> operations = new ArrayList<>();
			for (final TerariumAsset doc : docs) {
				if (doc.getId() == null) {
					throw new RuntimeException("Document id cannot be null");
				}
				final UpdateOperation<Object, Object> updateOperation = new UpdateOperation.Builder<>()
					.index(index)
					.id(doc.getId().toString())
					.action(a -> a.doc(doc))
					.build();

				final BulkOperation operation = new BulkOperation.Builder().update(updateOperation).build();
				operations.add(operation);
			}
			// Add the BulkOperation to the BulkRequest
			bulkRequest.operations(operations);

			final BulkResponse bulkResponse = client.bulk(bulkRequest.build());

			final List<String> errors = new ArrayList<>();
			if (bulkResponse.errors()) {
				for (final BulkResponseItem item : bulkResponse.items()) {
					final ErrorCause error = item.error();
					if (error != null) {
						final String reason = error.reason();
						if (reason != null) {
							errors.add(error.reason());
						}
					}
				}
			}

			final BulkOpResponse r = new BulkOpResponse();
			r.setErrors(errors);
			r.setTook(bulkResponse.took());
			return r;
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public void transferAlias(final String alias, final String oldIndex, final String newIndex) throws IOException {
		try {
			log.info("Transfering alias {} from index {} to index {}", alias, oldIndex, newIndex);
			// Remove alias from old index
			final DeleteAliasRequest deleteAliasRequest = new DeleteAliasRequest.Builder()
				.index(oldIndex)
				.name(alias)
				.build();
			client.indices().deleteAlias(deleteAliasRequest);

			// Add alias to new index
			final PutAliasRequest putAliasRequest = new PutAliasRequest.Builder().index(newIndex).name(alias).build();
			client.indices().putAlias(putAliasRequest);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public void createAlias(final String index, final String alias) throws IOException {
		try {
			log.info("Creating alias {} for index {}", alias, index);
			final PutAliasRequest putAliasRequest = new PutAliasRequest.Builder().index(index).name(alias).build();
			client.indices().putAlias(putAliasRequest);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public void deleteAlias(final String index, final String alias) throws IOException {
		try {
			log.info("Deleting alias {} for index {}", alias, index);
			final DeleteAliasRequest deleteAliasRequest = new DeleteAliasRequest.Builder().index(index).name(alias).build();
			client.indices().deleteAlias(deleteAliasRequest);
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public String getIndexFromAlias(final String alias) throws IOException {
		try {
			final GetAliasRequest request = new GetAliasRequest.Builder().name(alias).build();
			final GetAliasResponse response = client.indices().getAlias(request);
			return response.result().keySet().iterator().next();
		} catch (final ElasticsearchException e) {
			throw handleException(e);
		}
	}

	public boolean aliasExists(final String alias) {
		try {
			final GetAliasRequest request = new GetAliasRequest.Builder().name(alias).build();
			final GetAliasResponse response = client.indices().getAlias(request);
			return response.result().size() != 0;
		} catch (final Exception e) {
			return false;
		}
	}

	public static String emphasis(final String s, final int boost) {
		return s + "^" + boost;
	}

	/**
	 * Insert the given documents into the given index with the given ids
	 *
	 * @param index     The index to insert the documents into
	 * @param documents the documents to insert
	 * @param ids       The ids of the documents to insert, parallel to the
	 *                  documents
	 * @return The bulk response
	 * @throws IOException              If the bulk insert fails
	 * @throws IllegalArgumentException If the number of documents and ids are not
	 *                                  the same
	 */
	public BulkResponse bulkInsert(final String index, final List documents, final List<String> ids) throws IOException {
		if (ids != null && documents.size() != ids.size()) {
			throw new IllegalArgumentException("The number of documents and ids must be the same");
		}

		final List<BulkOperation> bulkOperations = new ArrayList<>();
		for (int i = 0; i < documents.size(); i++) {
			final String id = ids != null ? ids.get(i) : UUID.randomUUID().toString();
			final Object document = documents.get(i);
			bulkOperations.add(BulkOperation.of(b -> b.index(op -> op.index(index).id(id).document(document))));
		}

		log.info("Elasticsearch | Bulk | Inserting {} documents into index {}", documents.size(), index);
		return client.bulk(BulkRequest.of(bulkRequest -> bulkRequest.index(index).operations(bulkOperations)));
	}

	/**
	 * Checks if the given index exists
	 *
	 * @param name The index name
	 * @return The boolean response, empty if the call fails
	 */
	public Boolean hasIndex(final String name) {
		try {
			return client.indices().exists(e -> e.index(name)).value();
		} catch (final IOException e) {
			log.error("Error checking if index exists {}", name, e);
		}
		return null;
	}
}
