package software.uncharted.terarium.hmiserver.service.elasticsearch;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.rest.RestStatus;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.ingest.GetPipelineRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;

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

	@PostConstruct
	public void init() {
		log.info("Connecting elasticsearch client to: {}", config.getUrl());

		final RestClientBuilder httpClientBuilder = RestClient.builder(
			HttpHost.create(config.getUrl())
		);

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
	 * Create all indices that are not already present in the cluster
	 *
	 * @return True if the index exists, false otherwise
	 */
	public boolean containsIndex(final String indexName) {
		boolean exists = false;
		try {
			exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
		} catch (final IOException e) {
			log.error("Error checking existence of index {}", indexName, e);
		}
		return exists;
	}


	public void createIndex(final String index) throws IOException {

		final CreateIndexRequest req = new CreateIndexRequest.Builder().index(index).build();

		client.indices().create(req);
	}

	/**
	 * Returns true if the ES cluster contains the index template with the provided name, false otherwise
	 *
	 * @param name The name of the index template to check existence for
	 * @return True if the index template is contained in the cluster, false otherwise
	 */
	public boolean containsIndexTemplate(final String name) {
		final ExistsIndexTemplateRequest req = new ExistsIndexTemplateRequest.Builder().name(name).build();
		BooleanResponse exists = new BooleanResponse(false);
		try {
			exists = client.indices().existsIndexTemplate(req);
		} catch (final ElasticsearchStatusException e) {
			if (e.status() != RestStatus.NOT_FOUND) {
				log.error("Error checking existence of template, unexpected ElasticsearchStatusException result {}", name, e);
			}
		} catch (final IOException e) {
			log.error("Error checking existence of template {}", name, e);
		}
		return exists.value();
	}

	/**
	 * Put an index template to the cluster
	 *
	 * @param name         The name of the index template
	 * @param templateJson The index template json string
	 * @return True if the index template was successfully added, false otherwise
	 */
	public boolean putIndexTemplate(final String name, final String templateJson) {
		return putTyped(name, templateJson, "index template", "_index_template");
	}

	/**
	 * Check if the cluster contains the pipeline with the provided id
	 *
	 * @param id The name of the pipeline to check existence for
	 * @return True if the pipeline is contained in the cluster, false otherwise
	 */
	public boolean containsPipeline(final String id) {
		final GetPipelineRequest req = new GetPipelineRequest.Builder().id(id).build();

		try {
			client.ingest().getPipeline(req);
			return true;
		} catch (final ElasticsearchStatusException e) {
			if (e.status() != RestStatus.NOT_FOUND) {
				log.error("Error checking existence of template, unexpected ElasticsearchStatusException result {}", id, e);
			}
		} catch (final IOException e) {
			log.error("Error checking existence of pipeline {}", id, e);
		}
		return false;
	}

	/**
	 * Put a pipeline to the cluster
	 *
	 * @param name         The name of the pipeline
	 * @param pipelineJson The pipeline json string
	 * @return True if the pipeline was successfully added, false otherwise
	 */
	public boolean putPipeline(final String name, final String pipelineJson) {
		return putTyped(name, pipelineJson, "pipeline", "_ingest/pipeline");
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
		log.info("Putting " + typeName + ": {}", name);

		try {
			final HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			final HttpEntity<String> entity = new HttpEntity<>(typedJson, headers);
			final ResponseEntity<JsonNode> response = getRestTemplate().exchange(
				new URI(config.getUrl() + "/" + indexName + "/" + name),
				HttpMethod.PUT, entity,
				JsonNode.class);
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
	 * Search an index using a provided query (can be null for no query)
	 *
	 * @param <T>    The type of the document
	 * @param index  The index to search
	 * @param from   The starting index of the search
	 * @param size   The number of documents to return
	 * @param query  The query to use (can be null for no query)
	 * @param tClass The class of the document
	 * @return A list of found documents.
	 */
	public <T> List<T> search(final String index, final int from, final int size, final Query query, final Class<T> tClass) {
		log.info("Searching: {} from {} size {}", index, from, size);

		final SearchRequest req = new SearchRequest.Builder()
			.index(index)
			.from(from)
			.size(size)
			.query(query)
			.build();

		final List<T> docs = new ArrayList<>();
		try {
			final SearchResponse<T> res = client.search(req, tClass);
			for (final Hit<T> hit : res.hits().hits()) {
				docs.add(hit.source());
			}
		} catch (final IOException e) {
			log.error("Error searching index {}", index, e);
		}
		return docs;
	}

	/**
	 * Add a document to an index.
	 *
	 * @param <T>      The type of the document
	 * @param index    The index to add the document to
	 * @param id       The id of the document
	 * @param document The document to add
	 */
	public <T> void index(final String index, final String id, final T document) {
		log.info("Indexing: {} into {}", id, index);

		final IndexRequest<T> req = new IndexRequest.Builder<T>()
			.index(index)
			.id(id)
			.document(document)
			.refresh(Refresh.WaitFor)
			.build();

		try {
			client.index(req);
		} catch (final IOException e) {
			log.error("Error indexing document {} into {}", id, index, e);
		}
	}

	/**
	 * Remove a document from an index.
	 *
	 * @param index The index to remove the document from
	 * @param id    The id of the document to remove
	 */
	public void delete(final String index, final String id) {
		log.info("Deleting: {} from {}", id, index);

		final DeleteRequest req = new DeleteRequest.Builder()
			.index(index)
			.id(id)
			.refresh(Refresh.WaitFor)
			.build();

		try {
			client.delete(req);
		} catch (final IOException e) {
			log.error("Error deleting document {} from {}", id, index, e);
		}
	}

	/**
	 * Get a single document by id.
	 *
	 * @param <T>    The type of the document
	 * @param index  The index to get the document from
	 * @param id     The id of the document to get
	 * @param tClass The class of the document
	 * @return       The document if found, null otherwise
	 */
	public <T> T get(final String index, final String id, final Class<T> tClass) {
		log.info("Getting: {} from {}", id, index);

		final GetRequest req = new GetRequest.Builder()
			.index(index)
			.id(id)
			.build();
		try {
			final GetResponse<T> res = client.get(req, tClass);
			if (res.found()) {
				return res.source();
			}
		} catch (final IOException e) {
			log.error("Error getting id {} from index {}", id, index, e);
		}
		return null;
	}

}
