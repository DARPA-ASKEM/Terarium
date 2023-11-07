package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelFramework;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceQueryParam;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.proxies.dataservice.DocumentProxy;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ModelProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ProvenanceProxy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RequestMapping("/models")
@RestController
@Slf4j
public class ModelController {

	@Autowired
	ModelProxy modelProxy;

	@Autowired
	DocumentProxy documentProxy;

	@Autowired
	ProvenanceProxy provenanceProxy;

	@PostMapping("/frameworks")
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> createFramework(
			@RequestBody final ModelFramework framework) {
		return ResponseEntity.ok(modelProxy.createFramework(framework).getBody());
	}

	@GetMapping("/frameworks/{name}")
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> getFramework(
			@PathVariable("name") String name) {
		return ResponseEntity.ok(modelProxy.getFramework(name).getBody());
	}

	@DeleteMapping("/frameworks/{name}")
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> deleteFramework(
			@PathVariable("name") String name) {
		return ResponseEntity.ok(modelProxy.deleteFramework(name).getBody());
	}

	@GetMapping("/descriptions")
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> getDescriptions(
			@RequestParam(name = "page_size", defaultValue = "100") Integer pageSize,
			@RequestParam(name = "page", defaultValue = "0") Integer page) {
		return ResponseEntity.ok(modelProxy.getDescriptions(pageSize, page).getBody());
	}

	@GetMapping("/{id}/descriptions")
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> getDescription(
			@PathVariable("id") String id) {
		return ResponseEntity.ok(modelProxy.getDescription(id).getBody());
	}

	/**
	 * Get a Model from the data-service
	 * Return Model
	 */
	@GetMapping("/{id}")
	@Secured(Roles.USER)
	ResponseEntity<Model> getModel(
			@PathVariable("id") String id) {
		Model model;
		final ObjectMapper mapper = new ObjectMapper();

		// Fetch the model from the data-service
		try {
			model = modelProxy.getModel(id).getBody();
		} catch (RuntimeException e) {
			log.error("Unable to get the model" + id, e);
			return ResponseEntity.internalServerError().build();
		}

		if (model == null) {
			return ResponseEntity.noContent().build();
		}

		// Find the Document Assets linked via provenance to the model
		ProvenanceQueryParam body = new ProvenanceQueryParam();
		body.setRootId(id);
		body.setRootType(ProvenanceType.MODEL);
		body.setTypes(List.of(ProvenanceType.DOCUMENT));
		final JsonNode provenanceResults = provenanceProxy.search(body, "models_from_document").getBody();
		final JsonNode resultsNode = provenanceResults.get("result");

		// If there are results, fetch the Document Assets, gather their extractions
		if (resultsNode != null && resultsNode.isArray() && !resultsNode.isEmpty()) {
			final List<String> documentIds = mapper.convertValue(resultsNode, new TypeReference<List<String>>() {});
			final List<JsonNode> extractions = new ArrayList<>();
			documentIds.forEach(documentId -> {
				final DocumentAsset document = documentProxy.getAsset(documentId).getBody();
				final JsonNode documentExtractions = mapper.convertValue(document.getMetadata().get("attributes"), JsonNode.class);
				final List<JsonNode> documentExtractionsAsList = mapper.convertValue(documentExtractions, new TypeReference<List<JsonNode>>() {});
				extractions.addAll(documentExtractionsAsList);
			});

			model.setMetadata(model.getMetadata().setAttributes(extractions));
		}

		// Return the model
		return ResponseEntity.ok(model);
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> updateModel(
			@PathVariable("id") String id,
			@RequestBody Model model) {
		return ResponseEntity.ok(modelProxy.updateModel(id, model).getBody());
	}

	@PostMapping
	@Secured(Roles.USER)
	ResponseEntity<JsonNode> createModel(
			@RequestBody Model model) {
		return ResponseEntity.ok(modelProxy.createModel(model).getBody());
	}

	@GetMapping("/{id}/model_configurations")
	@Secured(Roles.USER)
	ResponseEntity<List<ModelConfiguration>> getModelConfigurations(
			@PathVariable("id") String id,
			@RequestParam(value = "page_size", required = false, defaultValue = "100") int pageSize) {
		return ResponseEntity.ok(modelProxy.getModelConfigurations(id, pageSize).getBody());
	}
}
