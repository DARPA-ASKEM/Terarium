package software.uncharted.terarium.hmiserver.controller.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.ClientEventType;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.code.Code;
import software.uncharted.terarium.hmiserver.models.dataservice.code.CodeFile;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelHeader;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceRelationType;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.models.extractionservice.ExtractionResponse;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.proxies.mit.MitProxy;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaUnifiedProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.ClientEventService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.ExtractionService;
import software.uncharted.terarium.hmiserver.service.data.CodeService;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.ProvenanceService;
import software.uncharted.terarium.hmiserver.service.notification.NotificationGroupInstance;
import software.uncharted.terarium.hmiserver.service.notification.NotificationService;
import software.uncharted.terarium.hmiserver.service.tasks.EquationsCleanupResponseHandler;
import software.uncharted.terarium.hmiserver.service.tasks.LatexToSympyResponseHandler;
import software.uncharted.terarium.hmiserver.service.tasks.SympyToAMRResponseHandler;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService.TaskMode;
import software.uncharted.terarium.hmiserver.utils.ByteMultipartFile;
import software.uncharted.terarium.hmiserver.utils.JsonUtil;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.StringMultipartFile;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/knowledge")
@RestController
@Slf4j
@RequiredArgsConstructor
public class KnowledgeController {

	private final ObjectMapper mapper;

	private final SkemaUnifiedProxy skemaUnifiedProxy;
	private final MitProxy mitProxy;

	private final DocumentAssetService documentService;
	private final DatasetService datasetService;
	private final ModelService modelService;
	private final ProvenanceService provenanceService;

	private final CodeService codeService;

	private final ExtractionService extractionService;
	private final TaskService taskService;

	private final ProjectService projectService;
	private final CurrentUserService currentUserService;
	private final ClientEventService clientEventService;
	private final NotificationService notificationService;

	private final EquationsCleanupResponseHandler equationsCleanupResponseHandler;

	private final Messages messages;

	@Value("${openai-api-key:}")
	String OPENAI_API_KEY;

	@PostConstruct
	void init() {
		taskService.addResponseHandler(equationsCleanupResponseHandler);
	}

	/**
	 * Clean up a list of equations
	 *
	 * @return List of cleaned-up equations
	 */
	@PostMapping("/clean-equations")
	@Secured(Roles.USER)
	public ResponseEntity<EquationCleanupResponse> getCleanedEquations(
		@RequestBody final List<String> equations,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		TaskRequest cleanupReq = cleanupEquationsTaskRequest(projectId, equations);
		TaskResponse cleanupResp = null;
		List<String> cleanedEquations = new ArrayList<>(equations); // Have original equations as a fallback
		boolean wasCleaned = false;

		try {
			cleanupResp = taskService.runTask(TaskMode.SYNC, cleanupReq);
			// Get the equations from the cleanup response
			if (cleanupResp != null && cleanupResp.getOutput() != null) {
				try {
					JsonNode output = mapper.readValue(cleanupResp.getOutput(), JsonNode.class);
					if (output.get("response") != null && output.get("response").get("equations") != null) {
						cleanedEquations.clear(); // Clear original equations before adding cleaned ones
						wasCleaned = true;
						for (JsonNode eq : output.get("response").get("equations")) {
							cleanedEquations.add(eq.asText());
						}
					}
				} catch (IOException e) {
					log.warn("Unable to retrieve cleaned-up equations from GoLLM response. Reverting to original equations.", e);
				}
			}
		} catch (final JsonProcessingException e) {
			log.warn("Unable to clean-up equations due to a JsonProcessingException. Reverting to original equations.", e);
		} catch (final TimeoutException e) {
			log.warn("Unable to clean-up equations due to a TimeoutException. Reverting to original equations.", e);
		} catch (final InterruptedException e) {
			log.warn("Unable to clean-up equations due to a InterruptedException. Reverting to original equations.", e);
		} catch (final ExecutionException e) {
			log.warn("Unable to clean-up equations due to a ExecutionException. Reverting to original equations.", e);
		}

		return ResponseEntity.ok(new EquationCleanupResponse(cleanedEquations, wasCleaned));
	}

	@Data
	public static class NotificationProperties {

		private UUID projectId;
		private UUID modelId;
		private UUID documentId;
		private UUID workflowId;
		private UUID nodeId;
	}

	/**
	 * Send the equations to the skema unified service to get the AMR
	 *
	 * @return UUID Model ID, or null if the model was not created or updated
	 */
	@PostMapping("/equations-to-model")
	@Secured(Roles.USER)
	public ResponseEntity<UUID> equationsToModel(
		@RequestBody final JsonNode req,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		// Parse the request
		UUID documentId = JsonUtil.parseUuidFromRequest(req, "documentId");
		UUID modelId = JsonUtil.parseUuidFromRequest(req, "modelId");

		// Create the notification properties
		final NotificationProperties notificationProperties = new NotificationProperties();
		notificationProperties.setProjectId(projectId);
		notificationProperties.setDocumentId(documentId);
		notificationProperties.setModelId(modelId);
		notificationProperties.setWorkflowId(JsonUtil.parseUuidFromRequest(req, "workflowId"));
		notificationProperties.setNodeId(JsonUtil.parseUuidFromRequest(req, "nodeId"));

		// Create the notification group
		final NotificationGroupInstance<NotificationProperties> notificationInterface = new NotificationGroupInstance<>(
			clientEventService,
			notificationService,
			ClientEventType.KNOWLEDGE_ENRICHMENT_MODEL,
			projectId,
			notificationProperties,
			currentUserService.get().getId()
		);

		notificationInterface.sendMessage("Beginning model enrichment using document extraction...");
		log.info("Beginning model {} enrichment using document {} extraction...", modelId, documentId);

		// Cleanup equations from the request
		List<String> equations = new ArrayList<>();
		if (req.get("equations") != null) {
			for (final JsonNode equation : req.get("equations")) {
				equations.add(equation.asText());
			}
		}
		TaskRequest cleanupReq = cleanupEquationsTaskRequest(projectId, equations);
		TaskResponse cleanupResp = null;
		try {
			cleanupResp = taskService.runTask(TaskMode.SYNC, cleanupReq);
		} catch (final JsonProcessingException e) {
			log.warn("Unable to clean-up equations due to a JsonProcessingException. Reverting to original equations.", e);
		} catch (final TimeoutException e) {
			log.warn("Unable to clean-up equations due to a TimeoutException. Reverting to original equations.", e);
		} catch (final InterruptedException e) {
			log.warn("Unable to clean-up equations due to a InterruptedException. Reverting to original equations.", e);
		} catch (final ExecutionException e) {
			log.warn("Unable to clean-up equations due to a ExecutionException. Reverting to original equations.", e);
		}

		notificationInterface.sendMessage("Equations cleaned up.");

		// get the equations from the cleanup response, or use the original equations
		JsonNode equationsReq = req.get("equations");
		if (cleanupResp != null && cleanupResp.getOutput() != null) {
			try {
				JsonNode output = mapper.readValue(cleanupResp.getOutput(), JsonNode.class);
				if (output.get("response") != null && output.get("response").get("equations") != null) {
					equationsReq = output.get("response").get("equations");
				}
			} catch (IOException e) {
				log.warn("Unable to retrieve cleaned-up equations from GoLLM response. Reverting to original equations.", e);
			}
		}

		// Get an AMR from Skema Unified Service or MIRA
		final Model responseAMR;
		String extractionService = "mira";
		if (req.get("extractionService") != null) {
			extractionService = req.get("extractionService").asText();
		}

		if (extractionService.equals("mira")) {
			final TaskRequest latexToSympyRequest = new TaskRequest();
			final TaskResponse latexToSympyResponse;
			final TaskRequest sympyToAMRRequest = new TaskRequest();
			final TaskResponse sympyToAMRResponse;

			try {
				// 1. LaTeX to sympy code
				final LatexToSympyResponseHandler.Input input = new LatexToSympyResponseHandler.Input();
				final List<String> equationList = new ArrayList<>();
				for (final JsonNode equation : equationsReq) {
					equationList.add(equation.asText());
				}
				input.setEquations(equationList);
				latexToSympyRequest.setType(TaskType.GOLLM);
				latexToSympyRequest.setInput(mapper.writeValueAsBytes(input));
				latexToSympyRequest.setScript(LatexToSympyResponseHandler.NAME);
				latexToSympyRequest.setUserId(currentUserService.get().getId());
				latexToSympyRequest.setUseCache(false); // Don't cache because LLM can give incorrect result
				latexToSympyResponse = taskService.runTaskSync(latexToSympyRequest);

				// 2. hand off
				final LatexToSympyResponseHandler.Response response = mapper.readValue(
					latexToSympyResponse.getOutput(),
					LatexToSympyResponseHandler.Response.class
				);
				StringBuilder codeBuilder = new StringBuilder();
				if (response.getResponse().get("equations").isArray()) {
					for (JsonNode codeNode : response.getResponse().get("equations")) {
						codeBuilder.append(codeNode.asText()).append("\n");
					}
				}
				final String code = codeBuilder.toString().trim();

				// 3. sympy code string to amr json
				sympyToAMRRequest.setType(TaskType.MIRA);
				sympyToAMRRequest.setInput(code.getBytes());
				sympyToAMRRequest.setScript(SympyToAMRResponseHandler.NAME);
				sympyToAMRRequest.setUserId(currentUserService.get().getId());
				sympyToAMRResponse = taskService.runTaskSync(sympyToAMRRequest);

				final JsonNode taskResponseJSON = mapper.readValue(sympyToAMRResponse.getOutput(), JsonNode.class);
				final ObjectNode amrNode = taskResponseJSON.get("response").get("amr").deepCopy();
				responseAMR = mapper.convertValue(amrNode, Model.class);
			} catch (final Exception e) {
				log.error(messages.get("task.mira.internal-error"), e);
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.mira.internal-error"));
			}
		} else {
			try {
				// Create a request for SKEMA with the cleaned-up equations.
				final JsonNode skemaRequest = mapper.createObjectNode().put("model", "petrinet").set("equations", equationsReq);
				responseAMR = skemaUnifiedProxy.consolidatedEquationsToAMR(skemaRequest).getBody();
				if (responseAMR == null) {
					log.warn("Skema Unified Service did not return a valid AMR based on the provided equations");
					throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, messages.get("skema.bad-equations"));
				}
			} catch (final FeignException e) {
				log.error(
					"An exception occurred while Skema Unified Service was trying to produce an AMR based on the provided equations",
					e
				);
				throw handleSkemaFeignException(e);
			} catch (final Exception e) {
				log.error(
					"An unhandled error occurred while Skema Unified Service was trying to produce an AMR based on the provided equations",
					e
				);
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("skema.internal-error"));
			}
		}

		// We only handle Petri Net models
		if (!responseAMR.isPetrinet()) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, messages.get("skema.bad-equations.petrinet"));
		}

		notificationInterface.sendMessage("AMR extracted via SKEMA.");

		// If no model id is provided, create a new model asset
		Model model;
		if (modelId == null) {
			try {
				model = modelService.createAsset(responseAMR, projectId, permission);
			} catch (final IOException e) {
				log.error("An error occurred while trying to create a model.", e);
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
			}

			notificationInterface.sendMessage("Model created.");
			// If a model id is provided, update the existing model
		} else {
			try {
				model = modelService
					.updateAsset(responseAMR, projectId, permission)
					.orElseThrow(() -> new IOException("Model not found"));
			} catch (final IOException | IllegalArgumentException e) {
				log.error("An error occurred while trying to update a model.", e);
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
			}

			notificationInterface.sendMessage("Model updated.");
		}

		notificationInterface.sendFinalMessage("Model from equations done.");

		// Return the model id
		return ResponseEntity.ok(model.getId());
	}

	@PostMapping("/base64-equations-to-model")
	@Secured(Roles.USER)
	public ResponseEntity<Model> base64EquationsToAMR(@RequestBody final JsonNode req) {
		try {
			return ResponseEntity.ok(skemaUnifiedProxy.base64EquationsToAMR(req).getBody());
		} catch (final FeignException e) {
			log.error("Error with Skema Unified Service while converting base64 equations to AMR", e);
			throw handleSkemaFeignException(e);
		}
	}

	@PostMapping("/base64-equations-to-latex")
	@Secured(Roles.USER)
	public ResponseEntity<String> base64EquationsToLatex(@RequestBody final JsonNode req) {
		try {
			return ResponseEntity.ok(skemaUnifiedProxy.base64EquationsToLatex(req).getBody());
		} catch (final FeignException e) {
			log.error("Error with Skema Unified Service while converting base64 equations to Latex", e);
			throw handleSkemaFeignException(e);
		}
	}

	/**
	 * Transform source code to AMR
	 *
	 * @param codeId       (String): id of the code artifact model
	 * @param dynamicsOnly (Boolean): whether to only run the amr extraction over
	 *                     specified dynamics from the code
	 *                     object in TDS
	 * @param llmAssisted  (Boolean): whether amr extraction is llm assisted
	 * @return Model
	 */
	@PostMapping("/code-to-amr")
	@Secured(Roles.USER)
	ResponseEntity<Model> postCodeToAMR(
		@RequestParam("code-id") final UUID codeId,
		@RequestParam(name = "project-id", required = false) final UUID projectId,
		@RequestParam(name = "name", required = false, defaultValue = "") final String name,
		@RequestParam(name = "description", required = false, defaultValue = "") final String description,
		@RequestParam(name = "dynamics-only", required = false, defaultValue = "false") Boolean dynamicsOnly,
		@RequestParam(name = "llm-assisted", required = false, defaultValue = "false") final Boolean llmAssisted
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Code> code = codeService.getAsset(codeId, permission);
		if (code.isEmpty()) {
			log.error(String.format("Unable to fetch the requested code asset with codeId: %s", codeId));
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("code.not-found"));
		}

		final Map<String, CodeFile> codeFiles = code.get().getFiles();

		final Map<String, String> codeContent = new HashMap<>();

		for (final Entry<String, CodeFile> file : codeFiles.entrySet()) {
			final String filename = file.getKey();
			final CodeFile codeFile = file.getValue();

			final String content;
			try {
				content = codeService.fetchFileAsString(codeId, filename).orElseThrow();
			} catch (final IOException e) {
				log.error("Unable to fetch code as a string", e);
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
			}

			if (dynamicsOnly && codeFile.getDynamics() != null && codeFile.getDynamics().getBlock() != null) {
				final List<String> blocks = codeFile.getDynamics().getBlock();
				for (final String block : blocks) {
					final String[] parts = block.split("-");
					final int startLine = Integer.parseInt(parts[0].substring(1));
					final int endLine = Integer.parseInt(parts[1].substring(1));

					final String[] codeLines = content.split("\n");
					final List<String> targetLines = Arrays.asList(codeLines).subList(startLine - 1, endLine);

					final String targetBlock = String.join("\n", targetLines);

					codeContent.put(filename, targetBlock);
				}
			} else {
				codeContent.put(filename, content);
				dynamicsOnly = false;
			}
		}

		final List<String> files = new ArrayList<>();
		final List<String> blobs = new ArrayList<>();

		final ResponseEntity<JsonNode> resp;

		if (dynamicsOnly) {
			for (final Entry<String, String> entry : codeContent.entrySet()) {
				files.add(entry.getKey());
				blobs.add(entry.getValue());
			}

			resp = skemaUnifiedProxy.snippetsToAMR(files, blobs);
		} else {
			final ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
			final ZipOutputStream zipf = new ZipOutputStream(zipBuffer, StandardCharsets.UTF_8);

			try {
				for (final Map.Entry<String, String> entry : codeContent.entrySet()) {
					final String codeName = entry.getKey();
					final String content = entry.getValue();
					final ZipEntry zipEntry = new ZipEntry(codeName);
					zipf.putNextEntry(zipEntry);
					zipf.write(content.getBytes(StandardCharsets.UTF_8));
					zipf.closeEntry();
				}
			} catch (final IOException e) {
				log.error("Unable to write to zip file", e);
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.write"));
			} finally {
				try {
					zipf.close();
				} catch (final IOException e) {
					log.error("Unable to close zip file", e);
					throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.write"));
				}
			}

			final ByteMultipartFile file = new ByteMultipartFile(zipBuffer.toByteArray(), "zip_file.zip", "application/zip");

			resp = llmAssisted ? skemaUnifiedProxy.llmCodebaseToAMR(file) : skemaUnifiedProxy.codebaseToAMR(file);
		}

		if (resp.getStatusCode().is4xxClientError()) {
			log.warn("Skema Unified Service did not return a valid AMR because the provided code was not valid");
			throw new ResponseStatusException(resp.getStatusCode(), messages.get("skema.bad-code"));
		} else if (resp.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
			log.warn("Skema Unified Service is currently unavailable");
			throw new ResponseStatusException(resp.getStatusCode(), messages.get("skema.service-unavailable"));
		} else if (!resp.getStatusCode().is2xxSuccessful()) {
			log.error(
				"An error occurred while Skema Unified Service was trying to produce an AMR based on the provided code"
			);
			throw new ResponseStatusException(resp.getStatusCode(), messages.get("skema.internal-error"));
		}

		Model model;
		try {
			model = mapper.treeToValue(resp.getBody(), Model.class);
		} catch (final IOException e) {
			log.error("Unable to convert response to model", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.unknown"));
		}

		if (model.getMetadata() == null) {
			model.setMetadata(new ModelMetadata());
		}

		// create the model
		if (!name.isEmpty()) {
			model.setName(name);
		}
		if (model.getMetadata() == null) {
			model.setMetadata(new ModelMetadata());
		}
		model.getMetadata().setCodeId(codeId.toString());

		if (!description.isEmpty()) {
			if (model.getHeader() == null) {
				model.setHeader(new ModelHeader());
			}
			model.getHeader().setDescription(description);
		}

		try {
			model = modelService.createAsset(model, projectId, permission);
		} catch (final IOException e) {
			log.error("Unable to create model", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		// update the code
		if (code.get().getMetadata() == null) {
			code.get().setMetadata(new HashMap<>());
		}
		code.get().getMetadata().put("model_id", model.getId().toString());

		try {
			codeService.updateAsset(code.get(), projectId, permission);
		} catch (final IOException e) {
			log.error("Unable to update code", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		// set the provenance
		final Provenance provenancePayload = new Provenance(
			ProvenanceRelationType.EXTRACTED_FROM,
			model.getId(),
			ProvenanceType.MODEL,
			codeId,
			ProvenanceType.CODE
		);
		provenanceService.createProvenance(provenancePayload);

		return ResponseEntity.ok(model);
	}

	// Create a model from code blocks
	@Operation(summary = "Create a model from code blocks")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Return the extraction job for code to amr",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ExtractionResponse.class)
				)
			),
			@ApiResponse(
				responseCode = "400",
				description = "Invalid input - code file may be missing name",
				content = @Content
			),
			@ApiResponse(responseCode = "500", description = "Error running code blocks to model", content = @Content)
		}
	)
	@PostMapping(value = "/code-blocks-to-model", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	public ResponseEntity<Model> codeBlocksToModel(
		@RequestParam(name = "project-id", required = false) final UUID projectId,
		@RequestPart final Code code,
		@RequestPart("file") final MultipartFile input
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		// 1. create code asset from code blocks
		final Code createdCode;
		try {
			createdCode = codeService.createAsset(code, projectId, permission);
		} catch (final IOException e) {
			log.error("Unable to create code asset", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		// 2. upload file to code asset
		final byte[] fileAsBytes;
		try {
			fileAsBytes = input.getBytes();
		} catch (final IOException e) {
			log.error("Unable to read file", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.read"));
		}
		final HttpEntity fileEntity = new ByteArrayEntity(fileAsBytes, ContentType.TEXT_PLAIN);
		final String filename = input.getOriginalFilename();

		if (filename == null) {
			log.warn("Filename is missing from the file");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("code.filename-needed"));
		}

		try {
			codeService.uploadFile(code.getId(), filename, fileEntity);
		} catch (final IOException e) {
			log.error("Unable to upload file to code asset", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		// add the code file to the code asset
		final CodeFile codeFile = new CodeFile();
		codeFile.setFileNameAndProgrammingLanguage(filename);

		if (code.getFiles() == null) {
			code.setFiles(new HashMap<>());
		}
		code.getFiles().put(filename, codeFile);

		try {
			codeService.updateAsset(code, projectId, permission);
		} catch (final IOException e) {
			log.error("Unable to update code asset", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		// 3. create model from code asset
		return postCodeToAMR(createdCode.getId(), projectId, "temp model", "temp model description", false, false);
	}

	/**
	 * Enrich a dataset metadata
	 *
	 * @param datasetId  (DocumentAsset): The ID of the dataset to profile
	 * @param documentId (String): The ID of the document to profile
	 * @return the profiled dataset
	 */
	@PostMapping("/profile-dataset/{dataset-id}")
	@Secured(Roles.USER)
	public ResponseEntity<Dataset> postProfileDataset(
		@PathVariable("dataset-id") final UUID datasetId,
		@RequestParam(name = "project-id", required = false) final UUID projectId,
		@RequestParam(name = "document-id", required = false) final Optional<UUID> documentId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		// Provenance call if a document id is provided
		final StringMultipartFile documentFile;
		if (documentId.isPresent()) {
			final DocumentAsset document = documentService.getAsset(documentId.get(), permission).orElseThrow();
			documentFile = new StringMultipartFile(document.getText(), documentId.get() + ".txt", "application/text");

			try {
				final Provenance provenancePayload = new Provenance(
					ProvenanceRelationType.EXTRACTED_FROM,
					datasetId,
					ProvenanceType.DATASET,
					documentId.get(),
					ProvenanceType.DOCUMENT
				);
				provenanceService.createProvenance(provenancePayload);
			} catch (final Exception e) {
				final String error = "Unable to create provenance for profile-dataset";
				log.error(error, e);
			}
		} else {
			documentFile = new StringMultipartFile(
				"There is no documentation for this dataset",
				"document.txt",
				"application/text"
			);
		}

		final Dataset dataset = datasetService.getAsset(datasetId, permission).orElseThrow();

		if (dataset.getFileNames() == null || dataset.getFileNames().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("dataset.files.not-found"));
		}
		final String filename = dataset.getFileNames().get(0);

		final String csvContents;
		try {
			csvContents = datasetService.fetchFileAsString(datasetId, filename).orElseThrow();
		} catch (final IOException e) {
			log.error("Unable to fetch file as string", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		final StringMultipartFile csvFile = new StringMultipartFile(csvContents, filename, "application/csv");

		final ResponseEntity<JsonNode> resp = mitProxy.dataCard(OPENAI_API_KEY, csvFile, documentFile);

		if (resp.getStatusCode().is4xxClientError()) {
			log.warn("MIT Text-reading did not return a valid dataset card because a provided resource was not valid");
			throw new ResponseStatusException(resp.getStatusCode(), messages.get("mit.file.unable-to-read"));
		} else if (resp.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
			log.warn("MIT Text-reading is currently unavailable");
			throw new ResponseStatusException(resp.getStatusCode(), messages.get("mit.service-unavailable"));
		} else if (!resp.getStatusCode().is2xxSuccessful()) {
			log.error(
				"An error occurred while MIT Text-reading was trying to produce a dataset card based on the provided resource"
			);
			throw new ResponseStatusException(resp.getStatusCode(), messages.get("mit.internal-error"));
		}

		final JsonNode card = resp.getBody();
		final JsonNode profilingResult = (card != null) ? card.get("DATA_PROFILING_RESULT") : mapper.createObjectNode();

		for (final DatasetColumn col : dataset.getColumns()) {
			final JsonNode annotation = profilingResult.get(col.getName());
			if (annotation == null) {
				log.warn("No annotations for column: {}", col.getName());
				continue;
			}

			final JsonNode dkgGroundings = annotation.get("dkg_groundings");
			if (dkgGroundings == null) {
				log.warn("No dkg_groundings for column: {}", col.getName());
				continue;
			}

			final Grounding groundings = new Grounding();
			for (final JsonNode g : annotation.get("dkg_groundings")) {
				if (g.size() < 2) {
					log.warn("Invalid dkg_grounding: {}", g);
					continue;
				}
				if (groundings.getIdentifiers() == null) {
					groundings.setIdentifiers(new ArrayList<>());
				}
				groundings.getIdentifiers().add(new DKG(g.get(0).asText(), g.get(1).asText(), "", null, null));
			}

			// remove groundings from an annotation object
			((ObjectNode) annotation).remove("dkg_groundings");

			col.setGrounding(groundings);
			col.setDescription(annotation.get("description").asText());
			col.updateMetadata(annotation);
		}

		// add card to metadata
		if (dataset.getMetadata() == null) {
			dataset.setMetadata(mapper.createObjectNode());
		}
		((ObjectNode) dataset.getMetadata()).set("dataCard", card);

		final Optional<Dataset> updatedDataset;
		try {
			updatedDataset = datasetService.updateAsset(dataset, projectId, permission);
		} catch (final IOException e) {
			log.error("Unable to update dataset", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}

		if (updatedDataset.isEmpty()) {
			log.error("An error occurred while updating the dataset asset, resulting in an empty dataset");
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("dataset.unable-to-update"));
		}

		return ResponseEntity.ok(updatedDataset.get());
	}

	@PostMapping("/align-model")
	@Secured(Roles.USER)
	@ApiResponses(
		value = {
			@ApiResponse(responseCode = "204", description = "Model as been align with document", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "Error aligning model with variable extracted from document",
				content = @Content
			)
		}
	)
	public ResponseEntity<Model> alignModel(
		@RequestParam("document-id") final UUID documentId,
		@RequestParam("model-id") final UUID modelId,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			return ResponseEntity.ok(extractionService.alignAMR(projectId, documentId, modelId, permission).get());
		} catch (final InterruptedException | ExecutionException e) {
			log.error("Error aligning model with document", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("skema.error.align-model")
			);
		}
	}

	/**
	 * Variables Extractions from Document with SKEMA
	 *
	 * @param documentId (String): The ID of the document to profile
	 * @param modelIds   (List<String>): The IDs of the models to use for extraction
	 * @param domain     (String): The domain of the document
	 * @return an accepted response, the request being handled asynchronously
	 */
	@PostMapping("/variable-extractions")
	public ResponseEntity<Void> variableExtractions(
		@RequestParam("document-id") final UUID documentId,
		@RequestParam(name = "model-ids", required = false) final List<UUID> modelIds,
		@RequestParam(name = "domain", defaultValue = "epi") final String domain,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);
		extractionService.extractVariables(
			projectId,
			documentId,
			modelIds == null ? new ArrayList<>() : modelIds,
			permission
		);
		return ResponseEntity.accepted().build();
	}

	/**
	 * Document Extractions
	 *
	 * @param documentId (String): The ID of the document to profile
	 * @return an accepted response, the request being handled asynchronously
	 */
	@PostMapping("/pdf-extractions")
	@Secured(Roles.USER)
	@Operation(summary = "Extracts information from the first PDF associated with the given document id")
	@ApiResponses(
		value = {
			@ApiResponse(responseCode = "202", description = "Extraction started on PDF", content = @Content),
			@ApiResponse(responseCode = "500", description = "Error running PDF extraction", content = @Content)
		}
	)
	public ResponseEntity<Void> pdfExtractions(
		@RequestParam("document-id") final UUID documentId,
		@RequestParam(name = "project-id", required = false) final UUID projectId,
		@RequestParam(name = "mode", required = false, defaultValue = "ASYNC") final TaskMode mode
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Future<DocumentAsset> f = extractionService.extractPDFAndApplyToDocument(documentId, projectId, permission);
		if (mode == TaskMode.SYNC) {
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				log.error("Error extracting PDF", e);
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("document.extraction.failed"));
			}
		}
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/equations-to-model-debug")
	@Secured(Roles.USER)
	@Operation(summary = "Generate AMR from latex ODE equations, DEBUGGING only")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dispatched successfully",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TaskResponse.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue dispatching the request", content = @Content)
		}
	)
	/**
	 * This is similiar to /equations-to-model endpoint, but rather than
	 * directly creating a model asset it returns artifact at different intersections
	 * and handoff for debugging
	 **/
	public ResponseEntity<JsonNode> latexToAMR(@RequestBody final String latex) {
		////////////////////////////////////////////////////////////////////////////////
		// 1. Convert latex string to python sympy code string
		//
		// Note this is a gollm string => string task
		////////////////////////////////////////////////////////////////////////////////
		final TaskRequest latexToSympyRequest = new TaskRequest();
		final TaskResponse latexToSympyResponse;
		String code = null;

		try {
			latexToSympyRequest.setType(TaskType.GOLLM);
			latexToSympyRequest.setInput(latex.getBytes());
			latexToSympyRequest.setScript(LatexToSympyResponseHandler.NAME);
			latexToSympyRequest.setUserId(currentUserService.get().getId());
			latexToSympyResponse = taskService.runTaskSync(latexToSympyRequest);

			final JsonNode node = mapper.readValue(latexToSympyResponse.getOutput(), JsonNode.class);
			code = node.get("response").asText();
		} catch (final TimeoutException e) {
			log.warn("Timeout while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("task.gollm.timeout"));
		} catch (final InterruptedException e) {
			log.warn("Interrupted while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.gollm.interrupted"));
		} catch (final ExecutionException e) {
			log.error("Error while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.gollm.execution-failure"));
		} catch (final Exception e) {
			log.error("Unexpected error", e);
		}

		if (code == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.gollm.execution-failure"));
		}

		////////////////////////////////////////////////////////////////////////////////
		// 2. Convert python sympy code string to amr
		//
		// This returns the AMR json, and intermediate data representations for debugging
		////////////////////////////////////////////////////////////////////////////////
		final TaskRequest sympyToAMRRequest = new TaskRequest();
		final TaskResponse sympyToAMRResponse;
		final JsonNode response;

		try {
			sympyToAMRRequest.setType(TaskType.MIRA);
			sympyToAMRRequest.setInput(code.getBytes());
			sympyToAMRRequest.setScript(SympyToAMRResponseHandler.NAME);
			sympyToAMRRequest.setUserId(currentUserService.get().getId());
			sympyToAMRResponse = taskService.runTaskSync(sympyToAMRRequest);
			response = mapper.readValue(sympyToAMRResponse.getOutput(), JsonNode.class);
			return ResponseEntity.ok().body(response);
		} catch (final TimeoutException e) {
			log.warn("Timeout while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, messages.get("task.mira.timeout"));
		} catch (final InterruptedException e) {
			log.warn("Interrupted while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.mira.interrupted"));
		} catch (final ExecutionException e) {
			log.error("Error while waiting for task response", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("task.mira.execution-failure"));
		} catch (final Exception e) {
			log.error("Unexpected error", e);
		}
		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.read"));
	}

	private ResponseStatusException handleSkemaFeignException(final FeignException e) {
		final HttpStatus statusCode = HttpStatus.resolve(e.status());
		if (statusCode != null && statusCode.is4xxClientError()) {
			log.warn("Skema Unified Service did not return a valid AMR based on the provided resources");
			return new ResponseStatusException(statusCode, messages.get("skema.bad-equations"));
		} else if (statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
			log.warn("Skema Unified Service is currently unavailable");
			return new ResponseStatusException(statusCode, messages.get("skema.service-unavailable"));
		} else if (statusCode != null && statusCode.is5xxServerError()) {
			log.error(
				"An error occurred while Skema Unified Service was trying to produce an AMR based on the provided resources"
			);
			return new ResponseStatusException(statusCode, messages.get("skema.internal-error"));
		}

		final HttpStatus httpStatus = (statusCode == null) ? HttpStatus.INTERNAL_SERVER_ERROR : statusCode;
		log.error(
			"An unknown error occurred while Skema Unified Service was trying to produce an AMR based on the provided resources"
		);
		return new ResponseStatusException(httpStatus, messages.get("generic.unknown"));
	}

	private TaskRequest cleanupEquationsTaskRequest(UUID projectId, List<String> equations) {
		final EquationsCleanupResponseHandler.Input input = new EquationsCleanupResponseHandler.Input();
		input.setEquations(equations);

		// Create the task
		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript(EquationsCleanupResponseHandler.NAME);
		req.setUserId(currentUserService.get().getId());

		try {
			req.setInput(mapper.writeValueAsBytes(input));
		} catch (final Exception e) {
			log.error("Unable to serialize input", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.write"));
		}

		req.setProjectId(projectId);

		final EquationsCleanupResponseHandler.Properties props = new EquationsCleanupResponseHandler.Properties();
		props.setProjectId(projectId);
		req.setAdditionalProperties(props);

		return req;
	}

	private static class EquationCleanupResponse {

		public List<String> cleanedEquations;
		public boolean wasCleaned;

		public EquationCleanupResponse(List<String> cleanedEquations, boolean wasCleaned) {
			this.cleanedEquations = cleanedEquations;
			this.wasCleaned = wasCleaned;
		}
	}
}
