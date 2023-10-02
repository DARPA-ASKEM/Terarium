package software.uncharted.terarium.hmiserver.controller.dataservice;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelFramework;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ModelProxy;

import java.util.List;


@RequestMapping("/models")
@RestController
@Slf4j
public class ModelResource {

	@Autowired
	ModelProxy proxy;

	@PostMapping("/frameworks")
	ResponseEntity<JsonNode> createFramework(
		@RequestBody final ModelFramework framework
	) {
		return ResponseEntity.ok(proxy.createFramework(framework).getBody());
	}

	@GetMapping("/frameworks/{name}")
	ResponseEntity<JsonNode> getFramework(
		@PathVariable("name") String name
	) {
		return ResponseEntity.ok(proxy.getFramework(name).getBody());
	}

	@DeleteMapping("/frameworks/{name}")
	ResponseEntity<JsonNode> deleteFramework(
		@PathVariable("name") String name
	) {
		return ResponseEntity.ok(proxy.deleteFramework(name).getBody());
	}

	@GetMapping("/descriptions")
	ResponseEntity<JsonNode> getDescriptions(
		@RequestParam(name = "page_size", defaultValue = "100") Integer pageSize,
		@RequestParam(name = "page", defaultValue = "0") Integer page
	) {
		return ResponseEntity.ok(proxy.getDescriptions(pageSize, page).getBody());
	}

	@GetMapping("/{id}/descriptions")
	ResponseEntity<JsonNode> getDescription(
		@PathVariable("id") String id
	) {
		return ResponseEntity.ok(proxy.getDescription(id).getBody());
	}

	/**
	 * Get a Model from the data-service
	 * Return Model
	 */
	@GetMapping("/{id}")
	ResponseEntity<Model> getModel(
		@PathVariable("id") String id
	) {
		Model model;

		// Fetch the model from the data-service
		try {
			model = proxy.getModel(id).getBody();
		} catch (RuntimeException e) {
			log.error("Unable to get the model" + id, e);
			return ResponseEntity.internalServerError().build();
		}

		if (model == null) {
			return ResponseEntity.noContent().build();
		}

		// Return the model
		return ResponseEntity.ok(model);
	}

	@PutMapping("/{id}")
	ResponseEntity<JsonNode> updateModel(
		@PathVariable("id") String id,
		@RequestBody Model model
	) {
		return ResponseEntity.ok(proxy.updateModel(id, model).getBody());
	}

	@PostMapping
	ResponseEntity<JsonNode> createModel(
		@RequestBody Model model
	) {
		return ResponseEntity.ok(proxy.createModel(model).getBody());
	}


	@GetMapping("/{id}/model_configurations")
	ResponseEntity<List<ModelConfiguration>> getModelConfigurations(
		@PathVariable("id") String id,
		@RequestParam("page_size") int pageSize
	) {
		return ResponseEntity.ok(proxy.getModelConfigurations(id, 100).getBody());
	}
}
