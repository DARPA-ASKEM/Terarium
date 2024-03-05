package software.uncharted.terarium.hmiserver.controller.miraservice;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.mira.Curies;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.models.mira.EntitySimilarityResult;
import software.uncharted.terarium.hmiserver.proxies.mira.MIRAProxy;
import software.uncharted.terarium.hmiserver.security.Roles;

@RequestMapping("/mira")
@RestController
@Slf4j
public class MIRAController {
	@Autowired
	MIRAProxy proxy;

	@GetMapping("/{curies}")
	@Secured(Roles.USER)
	public ResponseEntity<List<DKG>> searchConcept(
			@PathVariable("curies") final String curies) {
		try {
			ResponseEntity<List<DKG>> response = proxy.getEntities(curies);
			if (response.getStatusCode().is2xxSuccessful()) {
				return ResponseEntity.ok(response.getBody());
			}
			return ResponseEntity.internalServerError().build();
		} catch (FeignException.NotFound e) { // Handle 404 errors
			log.info("Could not find resource in the DKG", e);
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			log.error("Unable to fetch DKG", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@GetMapping("/search")
	@Secured(Roles.USER)
	public ResponseEntity<List<DKG>> search(
			@RequestParam("q") final String q,
			@RequestParam(required = false, name = "limit", defaultValue = "10") final Integer limit,
			@RequestParam(required = false, name = "offset", defaultValue = "0") final Integer offset) {
		try {
			ResponseEntity<List<DKG>> response = proxy.search(q, limit, offset);
			if (response.getStatusCode().is2xxSuccessful()) {
				return ResponseEntity.ok(response.getBody());
			}
			return ResponseEntity.internalServerError().build();
		} catch (FeignException.NotFound e) { // Handle 404 errors
			log.info("Could not find resource in the DKG", e);
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			log.error("Unable to fetch DKG", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	// This rebuilds the semantics ODE via MIRA
	// 1. Send AMR to MIRA => MIRANet
	// 2. Send MIRANet to MIRA to convert back to AMR Petrinet
	// 3. Send AMR back
	@PostMapping("/reconstruct_ode_semantics")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> reconstructODESemantics(
			Object amr) {
		return ResponseEntity.ok(proxy.reconstructODESemantics(amr).getBody());

	}

	@PostMapping("/entity_similarity")
	@Secured(Roles.USER)
	public ResponseEntity<List<EntitySimilarityResult>> entitySimilarity(
			@RequestBody Curies obj) {
		return ResponseEntity.ok(proxy.entitySimilarity(obj).getBody());
	}
}
