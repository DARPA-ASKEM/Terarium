package software.uncharted.terarium.hmiserver.controller.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.models.extractionservice.ExtractionResponse;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ArtifactProxy;
import software.uncharted.terarium.hmiserver.proxies.knowledge.KnowledgeMiddlewareProxy;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaUnifiedProxy;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RequestMapping("/knowledge")
@RestController
@Slf4j
public class KnowledgeController {

	@Autowired
	KnowledgeMiddlewareProxy knowledgeMiddlewareProxy;

	@Autowired
	SkemaUnifiedProxy skemaUnifiedProxy;

	@Autowired
	ArtifactProxy artifactProxy;

	/**
	 * Retrieve the status of an extraction job
	 *
	 * @param id (String) the id of the extraction job
	 * @return the status of the extraction job
	 */
	@GetMapping("/status/{id}")
	public ResponseEntity<JsonNode> getTaskStatus(
		@PathVariable("id") final String id) {
		return ResponseEntity.ok(knowledgeMiddlewareProxy.getTaskStatus(id).getBody());
	}

	/**
	 * Post Equations to SKEMA Unified service to get an AMR
	 *
	 * @param requestMap (Map<String, Object>) JSON request body containing the following fields:
	 *  	- format		(String) the format of the equations. Options: "latex", "mathml".
	 *  	- framework (String) the type of AMR to return. Options: "regnet", "petrinet".
	 *  	- modelId   (String): the id of the model (to update) based on the set of equations
	 *  	- equations (List<String>): A list of LaTeX strings representing the functions that are used to convert to AMR model
	 * @return (ExtractionResponse): The response from the extraction service
	 */
	@PostMapping("/equations-to-model")
	public ResponseEntity<ExtractionResponse> postLaTeXToAMR(@RequestBody Map<String, Object> requestMap) {
		String format = (String) requestMap.getOrDefault("format", "latex");
		String framework = (String) requestMap.getOrDefault("framework", "petrinet");
		String modelId = (String) requestMap.get("modelId");
		List<String> equations = (List<String>) requestMap.getOrDefault("equations", Collections.emptyList());

		// http://knowledge-middleware.staging.terarium.ai/#/default/equations_to_amr_equations_to_amr_post
		return ResponseEntity.ok(knowledgeMiddlewareProxy.postEquationsToAMR(format, framework, modelId, equations).getBody());
	}

	;

	/**
	 * Transform source code to AMR
	 *
	 * @param codeId      (String): id of the code artifact
	 * @param name        (String): the name to set on the newly created model
	 * @param description (String): the description to set on the newly created model
	 * @return (ExtractionResponse)
	 */
	@PostMapping("/code-to-amr")
	ResponseEntity<ExtractionResponse> postCodeToAMR(
		@RequestParam("code_id") String codeId,
		@RequestParam("name") String name,
		@RequestParam("description") String description
	) {
		return ResponseEntity.ok(knowledgeMiddlewareProxy.postCodeToAMR(codeId, name, description).getBody());
	}


	/**
	 * Post a PDF to the extraction service
	 *
	 * @param annotateSkema (Boolean): Whether to annotate the PDF with Skema
	 * @param annotateMIT   (Boolean): Whether to annotate the PDF with AMR
	 * @param name          (String): The name of the PDF
	 * @param description   (String): The description of the PDF
	 *                      <p>
	 *                      Args:
	 *                      pdf (Object): The PDF file to upload
	 * @return response status of queueing this operation
	 */
	@PostMapping("/pdf-extractions")
	public ResponseEntity<JsonNode> postPDFExtractions(
		@RequestParam("document_id") String documentId,
		@RequestParam(name = "annotate_skema", defaultValue = "true") Boolean annotateSkema,
		@RequestParam(name = "annotate_mit", defaultValue = "true") Boolean annotateMIT,
		@RequestParam("name") String name,
		@RequestParam("description") String description
	) {
		return ResponseEntity.ok(knowledgeMiddlewareProxy.postPDFExtractions(documentId, annotateSkema, annotateMIT, name, description).getBody());
	}

	;

	/**
	 * Post a PDF to the extraction service to get text
	 *
	 * @param documentId (String): The ID of the document to extract text from
	 * @return response status of queueing this operation
	 */
	@PostMapping("/pdf-to-cosmos")
	public ResponseEntity<JsonNode> postPDFToCosmos(@RequestParam("document_id") String documentId) {
		return ResponseEntity.ok(knowledgeMiddlewareProxy.postPDFToCosmos(documentId).getBody());
	}

	/**
	 * Profile a model
	 *
	 * @param modelId    (String): The ID of the model to profile
	 * @param documentId (String): The text of the document to profile
	 * @return the profiled model
	 */
	@PostMapping("/profile-model/{model_id}")
	public ResponseEntity<JsonNode> postProfileModel(
		@PathVariable("model_id") String modelId,
		@RequestParam("document_id") String documentId
	) {
		return ResponseEntity.ok(knowledgeMiddlewareProxy.postProfileModel(modelId, documentId).getBody());
	}

	;

	/**
	 * Profile a dataset
	 *
	 * @param datasetId  (String): The ID of the dataset to profile
	 * @param documentId (String): The ID of the artifact to profile
	 * @return the profiled dataset
	 */
	@PostMapping("/profile-dataset/{dataset_id}")
	public ResponseEntity<JsonNode> postProfileDataset(
		@PathVariable("dataset_id") String datasetId,
		@RequestParam("document_id") String documentId
	) {
		return ResponseEntity.ok(knowledgeMiddlewareProxy.postProfileDataset(datasetId, documentId).getBody());
	}

	;


}
