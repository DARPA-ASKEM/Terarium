package software.uncharted.terarium.hmiserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipInputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.ClientEventType;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.document.ExtractedDocumentPage;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceRelationType;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaUnifiedProxy;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaUnifiedProxy.IntegratedTextExtractionsBody;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProvenanceService;
import software.uncharted.terarium.hmiserver.service.gollm.EmbeddingService;
import software.uncharted.terarium.hmiserver.service.notification.NotificationGroupInstance;
import software.uncharted.terarium.hmiserver.service.notification.NotificationService;
import software.uncharted.terarium.hmiserver.service.tasks.ExtractEquationsResponseHandler;
import software.uncharted.terarium.hmiserver.service.tasks.ExtractTablesResponseHandler;
import software.uncharted.terarium.hmiserver.service.tasks.ExtractTextResponseHandler;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.utils.JsonUtil;
import software.uncharted.terarium.hmiserver.utils.StringMultipartFile;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionService {

	private final DocumentAssetService documentService;
	private final ModelService modelService;
	private final SkemaUnifiedProxy skemaUnifiedProxy;
	private final ObjectMapper objectMapper;
	private final ClientEventService clientEventService;
	private final NotificationService notificationService;
	private final TaskService taskService;
	private final ProvenanceService provenanceService;
	private final EmbeddingService embeddingService;
	private final CurrentUserService currentUserService;

	private static final String RESPONSE_CACHE_KEY = "extraction-service-response-cache";

	// TTL = Time to live, the maximum time a key will be in the cache before it is
	// evicted, regardless of activity.
	@Value("${terarium.extractionService.response-cache-ttl-seconds:604800}") // 7 days
	private long CACHE_TTL_SECONDS;

	// Max idle = The maximum time a key can be idle in the cache before it is
	// evicted.
	@Value("${terarium.extractionService.response-cache-max-idle-seconds:86400}") // 24 hours
	private long CACHE_MAX_IDLE_SECONDS;

	private final RedissonClient redissonClient;

	private RMapCache<String, ExtractPDFResponse> responseCache;

	@Value("${terarium.extractionService.poolSize:10}")
	private int POOL_SIZE;

	private ExecutorService executor;
	private final Environment env;

	private boolean isRunningLocalProfile() {
		final String[] activeProfiles = env.getActiveProfiles();
		for (final String profile : activeProfiles) {
			if ("local".equals(profile)) {
				return true;
			}
		}
		return false;
	}

	@PostConstruct
	void init() {
		executor = Executors.newFixedThreadPool(POOL_SIZE);

		// use a distributed cache and lock so that these can be synchronized across
		// multiple instances of the hmi-server
		responseCache = redissonClient.getMapCache(RESPONSE_CACHE_KEY);

		if (isRunningLocalProfile()) {
			// sanity check for local development to clear the caches
			responseCache.clear();
		}
	}

	@Data
	private static class Properties {

		private final UUID documentId;
	}

	public static String removeFileExtension(final String filename) {
		final int lastIndexOfDot = filename.lastIndexOf(".");
		if (lastIndexOfDot != -1) {
			return filename.substring(0, lastIndexOfDot);
		}
		return filename;
	}

	static class ExtractionFile {

		ExtractionFile(final String filename, final byte[] bytes, final ContentType contentType) {
			this.filename = filename;
			this.bytes = bytes;
			this.contentType = contentType;
		}

		ContentType contentType;
		String filename;
		byte[] bytes;
	}

	enum FailureType {
		TABLE_FAILURE("tables"),
		EQUATION_FAILURE("equations"),
		VARIABLE_FAILURE("variables");

		private final String humanReadable;

		FailureType(final String humanReadable) {
			this.humanReadable = humanReadable;
		}

		public String getHumanReadable() {
			return humanReadable;
		}
	}

	static class ExtractPDFResponse {

		TextExtraction textExtraction;
		EquationExtraction equationExtraction;
		TableExtraction tableExtraction;
		ArrayNode variableAttributes;
		Set<FailureType> failures = new HashSet<>();
		boolean completed = false;
	}

	private ExtractPDFResponse runExtractPDF(
		final ExtractPDFResponse extractionResponse,
		final String documentName,
		final byte[] documentContents,
		final String userId,
		final NotificationGroupInstance<Properties> notificationInterface
	) {
		try {
			notificationInterface.sendMessage("Starting text extraction...");
			log.info("Starting text extraction for document: {}", documentName);

			// run text extraction if needed
			Future<TextExtraction> textExtractionFuture;
			if (extractionResponse.textExtraction == null) {
				textExtractionFuture = extractTextFromPDF(notificationInterface, documentName, documentContents);
			} else {
				final CompletableFuture<TextExtraction> cf = new CompletableFuture<>();
				textExtractionFuture = cf;
				cf.complete(extractionResponse.textExtraction);
			}

			// run equation extraction if needed
			Future<EquationExtraction> equationExtractionFuture;
			if (extractionResponse.equationExtraction == null) {
				equationExtractionFuture = extractEquationsFromPDF(notificationInterface, documentContents, userId);
			} else {
				final CompletableFuture<EquationExtraction> cf = new CompletableFuture<>();
				equationExtractionFuture = cf;
				cf.complete(extractionResponse.equationExtraction);
			}

			// run table extraction if needed
			Future<TableExtraction> tableExtractionFuture;
			if (extractionResponse.tableExtraction == null) {
				tableExtractionFuture = extractTablesFromPDF(notificationInterface, userId, documentContents);
			} else {
				final CompletableFuture<TableExtraction> cf = new CompletableFuture<>();
				tableExtractionFuture = cf;
				cf.complete(extractionResponse.tableExtraction);
			}

			// wait for text extraction
			final TextExtraction textExtraction = textExtractionFuture.get();
			notificationInterface.sendMessage("Text extraction complete!");
			log.info("Text extraction complete for document: {}", documentName);
			extractionResponse.textExtraction = textExtraction;

			try {
				// wait for equation extraction
				final EquationExtraction equationExtraction = equationExtractionFuture.get();
				notificationInterface.sendMessage("Equation extraction complete!");
				log.info("Equation extraction complete for document: {}", documentName);
				extractionResponse.equationExtraction = equationExtraction;
				extractionResponse.failures.remove(FailureType.EQUATION_FAILURE);
			} catch (final Exception e) {
				notificationInterface.sendMessage("Equation extraction failed, continuing");
				log.error("Equation extraction failed for document: {}", documentName, e);
				extractionResponse.failures.add(FailureType.EQUATION_FAILURE);
			}

			try {
				// wait for table extraction
				final TableExtraction tableExtraction = tableExtractionFuture.get();
				notificationInterface.sendMessage("Table extraction complete!");
				log.info("Table extraction complete for document: {}", documentName);
				extractionResponse.tableExtraction = tableExtraction;
				extractionResponse.failures.remove(FailureType.TABLE_FAILURE);
			} catch (final Exception e) {
				notificationInterface.sendMessage("Table extraction failed, continuing");
				log.error("Table extraction failed for document: {}", documentName, e);
				extractionResponse.failures.add(FailureType.TABLE_FAILURE);
			}

			// flag as completed
			extractionResponse.completed = true;

			return extractionResponse;
		} catch (final FeignException e) {
			final String error = "Transitive service failure";
			log.error(error, e.contentUTF8(), e);
			throw new ResponseStatusException(
				e.status() < 100 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.valueOf(e.status()),
				error + ": " + e.getMessage()
			);
		} catch (final RuntimeException e) {
			notificationInterface.sendError(e.getMessage());
			log.error(e.getMessage(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (final Exception e) {
			final String error = "Unable to extract pdf";
			log.error(error, e);
			notificationInterface.sendError("Extraction failed, unexpected error.");
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	public DocumentAsset applyExtractPDFResponse(
		final UUID documentId,
		final UUID projectId,
		final ExtractPDFResponse extractionResponse,
		final Schema.Permission hasWritePermission
	) throws IOException {
		final DocumentAsset document = documentService.getAsset(documentId, hasWritePermission).get();

		if (extractionResponse.textExtraction != null) {
			document.setText(extractionResponse.textExtraction.documentText);
		}

		if (document.getExtractions() == null) {
			document.setExtractions(new ArrayList<>());
		}

		for (final String page : extractionResponse.textExtraction.documentTextPerPage) {
			final ExtractedDocumentPage p = new ExtractedDocumentPage();
			p.setText(page);
			p.setPageNumber(document.getExtractions().size());

			document.getExtractions().add(p);
		}

		if (document.getMetadata() == null) {
			document.setMetadata(new HashMap<>());
		}

		if (extractionResponse.variableAttributes != null) {
			document.getMetadata().put("attributes", extractionResponse.variableAttributes);
		}

		if (extractionResponse.equationExtraction != null) {
			document
				.getMetadata()
				.put("equations", objectMapper.valueToTree(extractionResponse.equationExtraction.equations));

			int pageNum = 0;
			for (final JsonNode page : extractionResponse.equationExtraction.equations) {
				final ArrayNode equationsForPage = (ArrayNode) page;
				for (final JsonNode equation : equationsForPage) {
					document.getExtractions().get(pageNum).getEquations().add(equation);
				}
				pageNum++;
			}
		}

		if (extractionResponse.tableExtraction != null) {
			document.getMetadata().put("tables", objectMapper.valueToTree(extractionResponse.tableExtraction.tables));

			int pageNum = 0;
			for (final JsonNode page : extractionResponse.tableExtraction.tables) {
				final ArrayNode tablesForPage = (ArrayNode) page;
				for (final JsonNode table : tablesForPage) {
					document.getExtractions().get(pageNum).getTables().add(table);
				}
				pageNum++;
			}
		}

		return documentService.updateAsset(document, projectId, hasWritePermission).orElseThrow();
	}

	private static String sha256(final byte[] input) {
		try {
			// Create a MessageDigest instance for SHA-256
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");

			// Convert the input string to bytes and compute the hash
			final byte[] hashBytes = digest.digest(input);

			// Convert the hash bytes to a hexadecimal string
			final StringBuilder hexString = new StringBuilder();
			for (final byte b : hashBytes) {
				final String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) hexString.append('0');
				hexString.append(hex);
			}

			return hexString.toString();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Future<DocumentAsset> extractPDFAndApplyToDocument(
		final UUID documentId,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) {
		final DocumentAsset document = documentService.getAsset(documentId, hasWritePermission).get();
		if (document.getFileNames().isEmpty()) {
			throw new RuntimeException("No files found on document");
		}

		final String userId = currentUserService.get().getId();

		final NotificationGroupInstance<Properties> notificationInterface = new NotificationGroupInstance<>(
			clientEventService,
			notificationService,
			ClientEventType.EXTRACTION_PDF,
			projectId,
			new Properties(documentId),
			currentUserService.get().getId()
		);

		return executor.submit(() -> {
			try {
				notificationInterface.sendMessage("Beginning document extraction...");
				final String filename = document.getFileNames().get(0);

				final byte[] documentContents = documentService.fetchFileAsBytes(documentId, filename).get();

				final String documentSHA = sha256(documentContents);

				log.info("Document SHA: {}, checking cache", documentSHA);

				ExtractPDFResponse extractionResponse;
				if (responseCache.containsKey(documentSHA)) {
					log.info("Returning cached response for document {} for SHA: {}", documentId, documentSHA);

					extractionResponse = responseCache.get(documentSHA);

					notificationInterface.sendMessage("Cached response found for document extraction");
				} else {
					log.info("No cached response found for text from document {} for SHA: {}", documentId, documentSHA);

					extractionResponse = new ExtractPDFResponse();
				}

				if (!extractionResponse.completed || !extractionResponse.failures.isEmpty()) {
					if (!extractionResponse.completed) {
						notificationInterface.sendMessage("No extraction found in cache, dispatching extraction request...");
					} else if (extractionResponse.failures.isEmpty()) {
						notificationInterface.sendMessage(
							"Cached response found for document extraction, but with failures, re-running failed extractions"
						);
					}

					extractionResponse = runExtractPDF(
						extractionResponse,
						filename,
						documentContents,
						userId,
						notificationInterface
					);

					if (extractionResponse == null) {
						throw new RuntimeException("Extraction failed");
					}

					// cache even if there are partial failures
					responseCache.put(
						documentSHA,
						extractionResponse,
						CACHE_TTL_SECONDS,
						TimeUnit.SECONDS,
						CACHE_MAX_IDLE_SECONDS,
						TimeUnit.SECONDS
					);
				} else {
					notificationInterface.sendMessage("Cached response found for successful document extraction");
				}

				notificationInterface.sendMessage("Applying extraction results to document");
				log.info("Applying extraction results to document {}", documentId);
				final DocumentAsset doc = applyExtractPDFResponse(
					documentId,
					projectId,
					extractionResponse,
					hasWritePermission
				);

				notificationInterface.sendMessage("Extractions applied to document. Finalizing response.");

				if (!extractionResponse.failures.isEmpty()) {
					// create a comma separated list of failures in human-readable form
					final String failures = String.join(
						", ",
						extractionResponse.failures.stream().map(FailureType::getHumanReadable).toArray(String[]::new)
					);
					notificationInterface.sendFinalMessage(
						"Extraction completed with failures (" + failures + ")",
						ProgressState.ERROR
					);
				} else {
					notificationInterface.sendFinalMessage("Extraction complete");
				}

				return doc;
			} catch (final Exception e) {
				log.error(e.getMessage(), e);
				notificationInterface.sendFinalMessage("Extraction failed", ProgressState.ERROR);
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
			}
		});
	}

	private DocumentAsset runVariableExtraction(
		final NotificationGroupInstance<Properties> notificationInterface,
		final UUID projectId,
		final UUID documentId,
		final List<UUID> modelIds,
		final Schema.Permission hasWritePermission
	) {
		notificationInterface.sendMessage("Starting variable extraction.");
		try {
			// Fetch the text from the document
			final DocumentAsset document = documentService.getAsset(documentId, hasWritePermission).orElseThrow();
			notificationInterface.sendMessage("Document found, fetching text.");
			if (document.getText() == null || document.getText().isEmpty()) {
				throw new RuntimeException("No text found in paper document");
			}

			// add optional models
			final List<Model> models = new ArrayList<>();
			for (final UUID modelId : modelIds) {
				models.add(modelService.getAsset(modelId, hasWritePermission).orElseThrow());
			}
			notificationInterface.sendMessage("Model(s) found, added to extraction request.");

			// Create a collection to hold the variable extractions
			JsonNode collection = null;

			notificationInterface.sendMessage("Sending request to be processes by MIT.");

			final IntegratedTextExtractionsBody body = new IntegratedTextExtractionsBody(document.getText(), models);

			log.info("Sending variable extraction request to SKEMA using MIT only");
			final ResponseEntity<JsonNode> resp = skemaUnifiedProxy.integratedTextExtractions(true, false, body);

			notificationInterface.sendMessage("Response received.");
			if (resp.getStatusCode().is2xxSuccessful()) {
				for (final JsonNode output : resp.getBody().get("outputs")) {
					if (!output.has("errors") || output.get("errors").isEmpty()) {
						collection = output.get("data");
						break;
					}
				}
			} else {
				throw new RuntimeException("non successful response.");
			}

			if (collection == null) {
				throw new RuntimeException("No variables extractions returned");
			}

			notificationInterface.sendMessage("Organizing and saving the extractions.");
			final ArrayNode attributes = objectMapper.createArrayNode();
			for (final JsonNode attribute : collection.get("attributes")) {
				attributes.add(attribute);
			}

			// add the attributes to the metadata
			if (document.getMetadata() == null) {
				document.setMetadata(new HashMap<>());
			}
			document.getMetadata().put("attributes", attributes);

			if (modelIds.size() > 0) {
				for (final UUID modelId : modelIds) {
					notificationInterface.sendMessage("Attempting to align models for model: " + modelId);
					try {
						runAlignAMR(notificationInterface, projectId, documentId, modelId, hasWritePermission);
						notificationInterface.sendMessage("Model " + modelId + " aligned successfully");
					} catch (final Exception e) {
						notificationInterface.sendMessage("Failed to align model: " + modelId + ", continuing...");
					}
				}
			}

			// update the document
			return documentService.updateAsset(document, projectId, hasWritePermission).orElseThrow();
		} catch (final FeignException e) {
			final String error = "Transitive service failure";
			log.error(error, e.contentUTF8(), e);
			throw new ResponseStatusException(
				e.status() < 100 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.valueOf(e.status()),
				error + ": " + e.getMessage()
			);
		} catch (final Exception e) {
			final String error = "Variable extraction on document: " + documentId + " failed.";
			log.error(error, e);
			notificationInterface.sendError(error + " — " + e.getMessage());
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	public Future<DocumentAsset> extractVariables(
		final UUID projectId,
		final UUID documentId,
		final List<UUID> modelIds,
		final Schema.Permission hasWritePermission
	) {
		// Set up the client interface
		final NotificationGroupInstance<Properties> notificationInterface = new NotificationGroupInstance<>(
			clientEventService,
			notificationService,
			ClientEventType.EXTRACTION,
			null,
			new Properties(documentId),
			currentUserService.get().getId()
		);
		notificationInterface.sendMessage("Variable extraction task submitted...");

		return executor.submit(() -> {
			final DocumentAsset doc = runVariableExtraction(
				notificationInterface,
				projectId,
				documentId,
				modelIds,
				hasWritePermission
			);
			notificationInterface.sendFinalMessage("Extraction complete");
			return doc;
		});
	}

	public Future<Model> alignAMR(
		final UUID projectId,
		final UUID documentId,
		final UUID modelId,
		final Schema.Permission hasWritePermission
	) {
		final NotificationGroupInstance<Properties> notificationInterface = new NotificationGroupInstance<>(
			clientEventService,
			notificationService,
			ClientEventType.EXTRACTION,
			null,
			new Properties(documentId),
			currentUserService.get().getId()
		);

		notificationInterface.sendMessage("Model alignment task submitted...");

		return executor.submit(() -> {
			final Model model = runAlignAMR(notificationInterface, projectId, documentId, modelId, hasWritePermission);
			notificationInterface.sendFinalMessage("Alignment complete");
			return model;
		});
	}

	public Model runAlignAMR(
		final NotificationGroupInstance<Properties> notificationInterface,
		final UUID projectId,
		final UUID documentId,
		final UUID modelId,
		final Schema.Permission hasWritePermission
	) {
		try {
			notificationInterface.sendMessage("Starting model alignment...");

			final DocumentAsset document = documentService.getAsset(documentId, hasWritePermission).orElseThrow();

			final Model model = modelService.getAsset(modelId, hasWritePermission).orElseThrow();

			final String modelString = objectMapper.writeValueAsString(model);

			if (document.getMetadata() == null) {
				document.setMetadata(new HashMap<>());
			}
			if (document.getMetadata().get("attributes") == null) {
				throw new RuntimeException("No attributes found in document");
			}

			final JsonNode attributes = objectMapper.valueToTree(document.getMetadata().get("attributes"));

			final ObjectNode extractions = objectMapper.createObjectNode();
			extractions.set("attributes", attributes);

			final String extractionsString = objectMapper.writeValueAsString(extractions);

			final StringMultipartFile amrFile = new StringMultipartFile(modelString, "amr.json", "application/json");
			final StringMultipartFile extractionFile = new StringMultipartFile(
				extractionsString,
				"extractions.json",
				"application/json"
			);

			final ResponseEntity<JsonNode> res;
			try {
				res = skemaUnifiedProxy.linkAMRFile(amrFile, extractionFile);
			} catch (final FeignException e) {
				final String error = "Transitive service failure";
				log.error(error, e.contentUTF8(), e);
				throw new ResponseStatusException(
					e.status() < 100 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.valueOf(e.status()),
					error + ": " + e.getMessage()
				);
			}
			if (!res.getStatusCode().is2xxSuccessful()) {
				throw new ResponseStatusException(res.getStatusCode(), "Unable to link AMR file");
			}

			final JsonNode modelJson = objectMapper.valueToTree(model);

			// overwrite all updated fields
			JsonUtil.recursiveSetAll((ObjectNode) modelJson, res.getBody());

			final Model updatedModel = objectMapper.treeToValue(modelJson, Model.class);

			if (updatedModel.getMetadata() == null) {
				updatedModel.setMetadata(new ModelMetadata());
			}

			// add document gollm card to model
			final JsonNode card = document.getMetadata().get("gollmCard");
			if (card != null) {
				updatedModel.getMetadata().setGollmCard(card);
			}

			// update the model
			modelService.updateAsset(model, projectId, hasWritePermission);

			// create provenance
			final Provenance provenance = new Provenance(
				ProvenanceRelationType.EXTRACTED_FROM,
				modelId,
				ProvenanceType.MODEL,
				documentId,
				ProvenanceType.DOCUMENT
			);
			provenanceService.createProvenance(provenance);

			// update model embeddings

			// TODO: do you want to update the model asset embeddings in the project index?

			// if (card != null && model.getPublicAsset() && !model.getTemporary()) {
			// 	final String cardText = objectMapper.writeValueAsString(card);
			// 	if (cardText != null) {
			// 		try {
			// 			final TerariumAssetEmbeddings embeddings = embeddingService.generateEmbeddings(cardText);

			// 			modelService.uploadEmbeddings(modelId, embeddings, hasWritePermission);
			// 			notificationInterface.sendMessage("Embeddings created");
			// 		} catch (final Exception e) {
			// 			log.warn("Unable to generate embedding vectors for model");
			// 		}
			// 	}
			// }

			return model;
		} catch (final FeignException e) {
			final String error = "Transitive service failure";
			log.error(error, e.contentUTF8(), e);
			throw new ResponseStatusException(
				e.status() < 100 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.valueOf(e.status()),
				error + ": " + e.getMessage()
			);
		} catch (final Exception e) {
			final String error = "SKEMA link_amr request from document: " + documentId + " failed.";
			log.error(error, e);
			notificationInterface.sendError(error + " — " + e.getMessage());
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	public static byte[] zipEntryToBytes(final ZipInputStream zipInputStream) throws IOException {
		final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		final byte[] buffer = new byte[1024];
		int len;
		while ((len = zipInputStream.read(buffer)) > 0) {
			byteArrayOutputStream.write(buffer, 0, len);
		}

		final byte[] bytes = byteArrayOutputStream.toByteArray();
		if (bytes.length == 0) {
			throw new IOException("Empty file found in zip");
		}

		return bytes;
	}

	@Value("${terarium.taskrunner.equation_extraction.gpu-endpoint}")
	private String EQUATION_EXTRACTION_GPU_ENDPOINT;

	static class EquationExtraction {

		List<JsonNode> equations = new ArrayList<>();
	}

	public Future<EquationExtraction> extractEquationsFromPDF(
		final NotificationGroupInstance<Properties> notificationInterface,
		final byte[] pdf,
		final String userId
	) throws TimeoutException, InterruptedException, ExecutionException, IOException {
		final int REQUEST_TIMEOUT_MINUTES = 10;

		int responseCode = HttpURLConnection.HTTP_BAD_GATEWAY;
		if (!EQUATION_EXTRACTION_GPU_ENDPOINT.isEmpty()) {
			final URL url = URI.create(String.format("%s/health", EQUATION_EXTRACTION_GPU_ENDPOINT)).toURL();
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			responseCode = connection.getResponseCode();
		}

		final TaskRequest req = new TaskRequest();
		req.setTimeoutMinutes(REQUEST_TIMEOUT_MINUTES);
		req.setInput(pdf);
		req.setScript(ExtractEquationsResponseHandler.NAME);
		req.setUserId(userId);

		if (responseCode == HttpURLConnection.HTTP_OK) {
			// use GPU impl
			req.setType(TaskType.EQUATION_EXTRACTION_GPU);
		} else {
			// otherwise fallback to CPU impl
			req.setType(TaskType.EQUATION_EXTRACTION_CPU);
		}

		return executor.submit(() -> {
			final TaskResponse resp = taskService.runTaskSync(req);

			final byte[] outputBytes = resp.getOutput();
			final ExtractEquationsResponseHandler.ResponseOutput output = objectMapper.readValue(
				outputBytes,
				ExtractEquationsResponseHandler.ResponseOutput.class
			);

			// Collect keys
			final List<String> keys = new ArrayList<>();
			final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = output.getResponse().fields();
			while (fieldsIterator.hasNext()) {
				final Map.Entry<String, JsonNode> field = fieldsIterator.next();
				keys.add(field.getKey());
			}

			// Sort keys
			Collections.sort(keys, (s1, s2) -> Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2)));

			final EquationExtraction extraction = new EquationExtraction();
			for (final String key : keys) {
				final int pageIndex = Integer.parseInt(key);
				if (pageIndex >= extraction.equations.size()) {
					for (int i = extraction.equations.size(); i < pageIndex; i++) {
						extraction.equations.add(objectMapper.createArrayNode());
					}
				}
				extraction.equations.add(output.getResponse().get(key));
			}

			return extraction;
		});
	}

	static class TextExtraction {

		String documentText;
		List<String> documentTextPerPage = new ArrayList<>();
	}

	public Future<TextExtraction> extractTextFromPDF(
		final NotificationGroupInstance<Properties> notificationInterface,
		final String userId,
		final byte[] pdf
	) throws TimeoutException, InterruptedException, ExecutionException, IOException {
		final int REQUEST_TIMEOUT_MINUTES = 10;

		final TaskRequest req = new TaskRequest();
		req.setTimeoutMinutes(REQUEST_TIMEOUT_MINUTES);
		req.setInput(pdf);
		req.setScript(ExtractTextResponseHandler.NAME);
		req.setUserId(userId);
		req.setType(TaskType.TEXT_EXTRACTION);

		return executor.submit(() -> {
			final TaskResponse resp = taskService.runTaskSync(req);

			final byte[] outputBytes = resp.getOutput();
			final ExtractTextResponseHandler.ResponseOutput output = objectMapper.readValue(
				outputBytes,
				ExtractTextResponseHandler.ResponseOutput.class
			);

			final TextExtraction extraction = new TextExtraction();

			extraction.documentText = "";
			for (final JsonNode page : output.getResponse()) {
				extraction.documentText += page.asText() + "\n";
				extraction.documentTextPerPage.add(page.asText());
			}

			return extraction;
		});
	}

	static class TableExtraction {

		List<JsonNode> tables = new ArrayList<>();
	}

	public Future<TableExtraction> extractTablesFromPDF(
		final NotificationGroupInstance<Properties> notificationInterface,
		final String userId,
		final byte[] pdf
	) throws TimeoutException, InterruptedException, ExecutionException, IOException {
		final int REQUEST_TIMEOUT_MINUTES = 10;

		final TaskRequest req = new TaskRequest();
		req.setTimeoutMinutes(REQUEST_TIMEOUT_MINUTES);
		req.setInput(pdf);
		req.setScript(ExtractTablesResponseHandler.NAME);
		req.setUserId(userId);
		req.setType(TaskType.TABLE_EXTRACTION);

		return executor.submit(() -> {
			final TaskResponse resp = taskService.runTaskSync(req);

			final byte[] outputBytes = resp.getOutput();
			final ExtractTablesResponseHandler.ResponseOutput output = objectMapper.readValue(
				outputBytes,
				ExtractTablesResponseHandler.ResponseOutput.class
			);

			// Collect keys
			final List<String> keys = new ArrayList<>();
			final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = output.getResponse().fields();
			while (fieldsIterator.hasNext()) {
				final Map.Entry<String, JsonNode> field = fieldsIterator.next();
				keys.add(field.getKey());
			}

			// Sort keys
			Collections.sort(keys, (s1, s2) -> Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2)));

			final TableExtraction extraction = new TableExtraction();

			for (final String key : keys) {
				final int pageIndex = Integer.parseInt(key);

				if (pageIndex >= extraction.tables.size()) {
					for (int i = extraction.tables.size(); i < pageIndex; i++) {
						extraction.tables.add(objectMapper.createArrayNode());
					}
				}

				final JsonNode page = output.getResponse().get(key);
				if (page.isArray()) {
					final ArrayNode pageOfTables = objectMapper.createArrayNode();
					for (final JsonNode table : page) {
						JsonNode t = table;
						if (table.isTextual()) {
							t = objectMapper.readTree(table.asText());
						}
						pageOfTables.add(t);
					}
					extraction.tables.add(pageOfTables);
				}
			}

			return extraction;
		});
	}
}
