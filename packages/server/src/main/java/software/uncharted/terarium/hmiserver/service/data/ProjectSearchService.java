package software.uncharted.terarium.hmiserver.service.data;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.HasChildQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddingType;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddings;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService.KnnHit;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService.KnnInnerHit;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService.KnnSearchResponse;
import software.uncharted.terarium.hmiserver.service.gollm.EmbeddingService;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProjectSearchService {

	protected final ObjectMapper objectMapper;
	protected final ElasticsearchConfiguration elasticConfig;
	protected final ElasticsearchService elasticService;
	protected final EmbeddingService embeddingService;
	protected final Environment env;

	protected boolean isRunningTestProfile() {
		final String[] activeProfiles = env.getActiveProfiles();

		for (final String profile : activeProfiles) {
			if ("test".equals(profile)) {
				return true;
			}
		}

		return false;
	}

	protected String getIndex() {
		return elasticConfig.getProjectIndex();
	}

	public String getAlias() {
		return elasticConfig.getProjectAlias();
	}

	public String getCurrentAssetIndex() throws IOException {
		return elasticService.getIndexFromAlias(getAlias());
	}

	@Data
	public static class PermissionJoin {

		private String name = null;
		private UUID parent = null;

		static PermissionJoin CreateProjectJoin() {
			final PermissionJoin join = new PermissionJoin();
			join.setName("project");
			join.setParent(null);
			return join;
		}

		static PermissionJoin CreatePermissionJoin(final UUID projectId) {
			final PermissionJoin join = new PermissionJoin();
			join.setName("permission");
			join.setParent(projectId);
			return join;
		}
	}

	@Data
	public static class ProjectAssetEmbedding {

		UUID assetId;
		AssetType assetType;
		TerariumAssetEmbeddingType embeddingType;
		private String embeddingId;
		private double[] vector;
		private long[] span;
	}

	@Data
	public static class ProjectDocument {

		private final String type = "project";
		private String name;
		private String userId; // the user id of who owns the project
		private Boolean publicAsset; // whether the project is public or not
		private Timestamp createdOn;
		private Timestamp updatedOn;

		@JsonProperty("asset_embeddings")
		private List<ProjectAssetEmbedding> assetEmbeddings = new ArrayList<>();

		private String embeddingSha256;

		// join field properties
		private PermissionJoin permissionJoin;
	}

	@Data
	public static class ProjectPermissionDocument {

		private final String type = "permission";
		private String userId; // the user id who holds the permission

		// join field properties
		private PermissionJoin permissionJoin;
	}

	/**
	 * Setup the index and alias for the asset this service manages and ensure it is
	 * empty
	 *
	 * @throws IOException If there is an error setting up the index and alias
	 */
	public void setupIndexAndAliasAndEnsureEmpty() throws IOException {
		log.info("Setting up index {} and alias {}", getIndex(), getAlias());
		final String index = getIndex();
		try {
			final String currentIndex = getCurrentAssetIndex();
			// if another index exists, delete it.
			if (!currentIndex.equals(index)) {
				elasticService.deleteIndex(currentIndex);
			}
		} catch (final Exception ignore) {}
		elasticService.createOrEnsureIndexIsEmpty(index);
		if (index == null || index.isEmpty()) {
			throw new RuntimeException("Index name is empty");
		}
		final String alias = getAlias();
		if (alias == null || alias.isEmpty()) {
			throw new RuntimeException("Alias name is empty");
		}
		elasticService.createAlias(index, alias);
	}

	/**
	 * Teardown the index and alias for the asset this service manages
	 *
	 * @throws IOException If there is an error tearing down the index and alias
	 */
	public void teardownIndexAndAlias() throws IOException {
		log.info("Tearing down index {}", getIndex());
		final String index = getCurrentAssetIndex();
		elasticService.deleteIndex(index);
	}

	/**
	 * Index a project
	 *
	 * @param project
	 * @throws IOException
	 */
	public void indexProject(final Project project) throws IOException {
		final ProjectDocument doc = new ProjectDocument();
		doc.setName(project.getName());
		doc.setUserId(project.getUserId());
		doc.setPublicAsset(project.getPublicAsset());
		doc.setCreatedOn(new Timestamp(System.currentTimeMillis()));
		doc.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
		doc.setPermissionJoin(PermissionJoin.CreateProjectJoin());

		final String routing = project.getId().toString();

		elasticService.indexWithRouting(getAlias(), project.getId().toString(), doc, routing);

		// ensure the project is embedded too
		generateAndUpsertProjectAssetEmbeddings(project.getId(), project);
	}

	/**
	 * Force an ES refresh
	 *
	 * @throws IOException
	 */
	public void forceESRefresh() throws IOException {
		elasticService.refreshIndex(getIndex());
	}

	/**
	 * Update a project
	 *
	 * @param project
	 * @throws IOException
	 */
	public void updateProject(final Project project) throws IOException {
		final ProjectDocument doc = new ProjectDocument();
		doc.setName(project.getName());
		doc.setUserId(project.getUserId());
		doc.setPublicAsset(project.getPublicAsset());
		doc.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
		doc.setPermissionJoin(PermissionJoin.CreateProjectJoin());

		elasticService.update(getAlias(), project.getId().toString(), doc);

		// ensure the project is embedded too
		generateAndUpsertProjectAssetEmbeddings(project.getId(), project);
	}

	/**
	 * Remove a project
	 *
	 * @param id
	 * @throws IOException
	 */
	public void removeProject(final UUID id) throws IOException {
		elasticService.delete(getAlias(), id.toString());
	}

	/**
	 * Get a project + user id permission id
	 *
	 * @param projectId
	 * @param userId
	 * @return
	 */
	private static String getPermissionId(final UUID projectId, final String userId) {
		return projectId.toString() + "_" + userId;
	}

	/**
	 * Add a project read permission for a user
	 *
	 * @param projectId
	 * @param userId
	 * @throws IOException
	 */
	public void addProjectPermission(final UUID projectId, final String userId) throws IOException {
		log.info("Adding project permission for user id: {}", userId);
		final ProjectPermissionDocument doc = new ProjectPermissionDocument();
		doc.setUserId(userId);
		doc.setPermissionJoin(PermissionJoin.CreatePermissionJoin(projectId));

		final String routing = projectId.toString();

		elasticService.indexWithRouting(getAlias(), getPermissionId(projectId, userId), doc, routing);
	}

	/**
	 * Remove a project read permission for a user
	 *
	 * @param projectId
	 * @param userId
	 * @throws IOException
	 */
	public void removeProjectPermission(final UUID projectId, final String userId) throws IOException {
		elasticService.delete(getAlias(), getPermissionId(projectId, userId));
	}

	/**
	 * Search for projects using a basic query
	 *
	 * @param userId
	 * @param from
	 * @param pageSize
	 * @param query
	 * @return
	 * @throws IOException
	 */
	public List<ProjectDocument> searchProjectsForUser(
		final String userId,
		final Integer from,
		final Integer pageSize,
		final Query query
	) throws IOException {
		final SearchRequest.Builder req = new SearchRequest.Builder()
			.index(getAlias())
			.from(from)
			.size(pageSize)
			.sort(
				new SortOptions.Builder()
					.field(new FieldSort.Builder().field("updatedOn").order(SortOrder.Desc).build())
					.build()
			);

		// add the permission query
		req.query(getProjectPermissionQuery(userId, query));

		return elasticService.search(req.build(), ProjectDocument.class);
	}

	/**
	 * Get a project permission query
	 *
	 * @param userId
	 * @param query
	 * @return
	 * @throws IOException
	 */
	private Query getProjectPermissionQuery(final String userId, final Query query) throws IOException {
		// Does this user have permission to see this project?
		final HasChildQuery hasChildQuery = HasChildQuery.of(h ->
			h.type("permission").query(QueryBuilders.term(t -> t.field("userId").value(userId)))
		);

		// Is the project public, does the user own it, or does the user have read
		// access?
		final Query permissionQuery = QueryBuilders.bool(b2 ->
			b2
				.should(sh -> sh.term(t -> t.field("publicAsset").value(true)))
				.should(sh -> sh.term(t -> t.field("userId").value(userId)))
				.should(sh -> sh.hasChild(hasChildQuery))
				.minimumShouldMatch("1")
		);

		// Only return proejcts, not permissions
		final Query onlyProjects = QueryBuilders.term(t -> t.field("type").value("project"));

		if (query != null) {
			return QueryBuilders.bool(b -> b.must(permissionQuery).must(onlyProjects).must(query));
		}

		return QueryBuilders.bool(b -> b.must(onlyProjects).must(permissionQuery));
	}

	private String getEmbeddingSha256(final Map<TerariumAssetEmbeddingType, String> embeddingTexts) {
		final String concatenatedText = embeddingTexts.values().stream().sorted().reduce((a, b) -> a + " " + b).orElse("");
		return DigestUtils.sha256Hex(concatenatedText);
	}

	/**
	 * Generate asset embeddings for a project
	 *
	 * @param projectId - the project id
	 * @param asset - the asset to generate embeddings for
	 * @param force - force the embedding to be generated even if the asset is temporary
	 * @return - a future that will be completed when the embedding is generated
	 */
	public Future<Void> generateAndUpsertProjectAssetEmbeddings(
		final UUID projectId,
		final TerariumAsset asset,
		final boolean force
	) throws IOException {
		//TODO: Temporary fix for performance issue. Disabling embeddings for now. Jan 29 2025

		/*if (force || (!isRunningTestProfile() && !asset.getTemporary())) {
			final Map<TerariumAssetEmbeddingType, String> embeddingTexts = asset.getEmbeddingsSourceByType();
			if (embeddingTexts == null) {
				log.warn("unable to get embedding sources for asset {}", asset.getId());
				return null;
			}

			// remove any null values
			embeddingTexts.values().removeIf(text -> text == null || text.isEmpty());

			if (embeddingTexts.isEmpty()) {
				log.warn("No embedding sources for asset {}, not indexing anything", asset.getId());
				return null;
			}

			// compute the new embedding sha
			final String newEmbeddingSha256 = getEmbeddingSha256(embeddingTexts);

			final ProjectDocument projectDoc = elasticService.get(getAlias(), projectId.toString(), ProjectDocument.class);

			if (projectDoc == null) {
				log.warn("Project {} not found, skipping", projectId);
				return null;
			}

			if (projectDoc.embeddingSha256 != null && projectDoc.embeddingSha256.equals(newEmbeddingSha256)) {
				log.info("Embedding for asset {} has not changed, skipping", asset.getId());
				return null;
			}

			log.info("Dispatch async embedding generation for asset {}", asset.getId());
			return CompletableFuture.runAsync(() -> {
				new Thread(() -> {
					try {
						final Map<TerariumAssetEmbeddingType, TerariumAssetEmbeddings> embeddings =
							embeddingService.generateEmbeddingsBySource(embeddingTexts);

						for (final Map.Entry<TerariumAssetEmbeddingType, TerariumAssetEmbeddings> entry : embeddings.entrySet()) {
							final TerariumAssetEmbeddings embedding = entry.getValue();

							final ProjectAssetEmbedding projectAssetEmbedding = new ProjectAssetEmbedding();
							projectAssetEmbedding.setAssetId(asset.getId());
							projectAssetEmbedding.setAssetType(AssetType.getAssetType(asset.getClass()));
							projectAssetEmbedding.setEmbeddingType(entry.getKey());
							projectAssetEmbedding.setEmbeddingId(embedding.getEmbeddings().get(0).getEmbeddingId());
							projectAssetEmbedding.setVector(embedding.getEmbeddings().get(0).getVector());
							projectAssetEmbedding.setSpan(embedding.getEmbeddings().get(0).getSpan());

							// update the embedding on the project
							if (projectDoc.getAssetEmbeddings() == null) {
								projectDoc.setAssetEmbeddings(List.of(projectAssetEmbedding));
							} else {
								int index = -1;
								for (int i = 0; i < projectDoc.getAssetEmbeddings().size(); i++) {
									if (
										projectDoc.getAssetEmbeddings().get(i).getAssetId().equals(asset.getId()) &&
										projectDoc.getAssetEmbeddings().get(i).getEmbeddingType().equals(entry.getKey())
									) {
										index = i;
										break;
									}
								}
								if (index != -1) {
									projectDoc.getAssetEmbeddings().set(index, projectAssetEmbedding);
								} else {
									projectDoc.getAssetEmbeddings().add(projectAssetEmbedding);
								}
							}
						}

						// save the new embedding sha256
						projectDoc.embeddingSha256 = newEmbeddingSha256;

						final String routing = projectId.toString();

						log.info("Writing asset embedding for project {} for asset {}", projectId, asset.getId());

						// Execute the update request
						elasticService.updateWithRouting(getAlias(), projectId.toString(), projectDoc, routing);
					} catch (final Exception e) {
						log.error("Failed to update embeddings for document {}", asset.getId(), e);
					}
				}).start();
			});
		}*/
		return null;
	}

	public Future<Void> generateAndUpsertProjectAssetEmbeddings(final UUID projectId, final TerariumAsset asset)
		throws IOException {
		return generateAndUpsertProjectAssetEmbeddings(projectId, asset, false);
	}

	/**
	 * Remove embeddings for a project asset
	 *
	 * @param projectId
	 * @param assetId
	 */
	public void removeProjectAssetEmbedding(final UUID projectId, final UUID assetId) {
		try {
			final ProjectDocument projectDoc = elasticService.get(getAlias(), projectId.toString(), ProjectDocument.class);

			if (projectDoc.getAssetEmbeddings() != null) {
				projectDoc.getAssetEmbeddings().removeIf(e -> e.getAssetId().equals(assetId));
			}

			final String routing = projectId.toString();

			elasticService.updateWithRouting(getAlias(), projectId.toString(), projectDoc, routing);
		} catch (final Exception e) {
			log.error("Failed to remove embeddings for document {}", assetId, e);
			throw new RuntimeException("Failed to remove embeddings for document " + assetId, e);
		}
	}

	@Data
	public static class ProjectSearchAsset {

		UUID assetId;
		AssetType assetType;
		TerariumAssetEmbeddingType embeddingType;
		Float score;
	}

	@Data
	@TSModel
	public static class ProjectSearchResponse {

		UUID projectId;
		Float score;
		List<ProjectSearchAsset> hits = new ArrayList<>();
	}

	/**
	 * Search for projects using a KNN search
	 *
	 * @param userId
	 * @param pageSize
	 * @param page
	 * @param text
	 * @param k
	 * @param numCandidates
	 * @param assetTypesToInclude
	 * @return
	 */
	public List<ProjectSearchResponse> searchProjectsKNN(
		final String userId,
		final Integer pageSize,
		final Integer page,
		final String text,
		final int k,
		final int numCandidates,
		List<AssetType> assetTypesToInclude
	) {
		if (assetTypesToInclude == null || assetTypesToInclude.isEmpty()) {
			assetTypesToInclude = AssetType.getAllAssetTypes();
		}

		try {
			if (k > numCandidates) {
				throw new IllegalArgumentException("k must be less than or equal to numCandidates");
			}

			final String searchText = (text == null) ? "" : text;
			final TerariumAssetEmbeddings embeddings = embeddingService.generateEmbeddings(searchText);

			final List<Float> vector = Arrays.stream(embeddings.getEmbeddings().get(0).getVector())
				.mapToObj(d -> (float) d)
				.collect(Collectors.toList());

			final List<FieldValue> assetTypeValues = AssetType.toJsonRepresentation(assetTypesToInclude)
				.stream()
				.map(FieldValue::of)
				.collect(Collectors.toList());

			final TermsQueryField termsQueryField = new TermsQueryField.Builder().value(assetTypeValues).build();

			final Query assetTypeQuery = new Query.Builder()
				.nested(n ->
					n
						.path("asset_embeddings")
						.query(q -> q.terms(t -> t.field("asset_embeddings.assetType").terms(termsQueryField)))
				)
				.build();

			final Query permsQuery = getProjectPermissionQuery(userId, assetTypeQuery);

			// prioritize direct name matches
			final float NAME_BOOST = 0.9f;
			final float KNN_BOOST = 0.1f;

			final Query titleMatchQuery = QueryBuilders.match(m -> m.field("name").query(searchText).boost(NAME_BOOST));

			final KnnQuery knn = new KnnQuery.Builder()
				.field("asset_embeddings.vector")
				.queryVector(vector)
				.k(k)
				.filter(permsQuery)
				.boost(KNN_BOOST)
				.numCandidates(numCandidates)
				.build();

			final List<String> EXCLUDE_FIELDS = List.of("asset_embeddings.vector");

			final KnnSearchResponse<ProjectDocument, ProjectAssetEmbedding> res = elasticService.knnSearchWithInnerHits(
				getAlias(),
				knn,
				titleMatchQuery,
				page,
				pageSize,
				EXCLUDE_FIELDS,
				ProjectDocument.class,
				ProjectAssetEmbedding.class
			);

			final List<ProjectSearchResponse> results = new ArrayList<>();

			for (final KnnHit<ProjectDocument, ProjectAssetEmbedding> hit : res.getHits()) {
				final ProjectDocument source = hit.getSource();
				if (source != null) {
					final ProjectSearchResponse response = new ProjectSearchResponse();
					response.projectId = hit.getId();
					response.score = hit.getScore();

					for (final KnnInnerHit<ProjectAssetEmbedding> innerHit : hit.getInnerHits()) {
						final ProjectSearchAsset asset = new ProjectSearchAsset();
						asset.assetId = innerHit.getSource().getAssetId();
						asset.assetType = innerHit.getSource().getAssetType();
						asset.embeddingType = innerHit.getSource().getEmbeddingType();
						asset.score = innerHit.getScore();

						response.hits.add(asset);
					}

					results.add(response);
				}
			}

			return results;
		} catch (final Exception e) {
			final String error = "Unable to get execute knn search";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}
}
