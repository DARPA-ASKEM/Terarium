package software.uncharted.terarium.hmiserver.proxies.mira;

import com.fasterxml.jackson.databind.JsonNode;
import feign.FeignException;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.mira.Curies;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.models.mira.EntitySimilarityResult;

@FeignClient(name = "mira-api", url = "${mira-api.url}", path = "/api")
public interface MIRAProxy {
	@GetMapping("/entity/{curie}")
	ResponseEntity<DKG> getEntity(@PathVariable("curie") final String curie);

	@GetMapping("/search")
	ResponseEntity<List<DKG>> search(
			@RequestParam("q") final String q,
			@RequestParam("limit") final Integer limit,
			@RequestParam("offset") final Integer offset)
			throws FeignException;

	@GetMapping("/entities/{curies}")
	ResponseEntity<List<DKG>> getEntities(@PathVariable("curies") final String curies) throws FeignException;

	// This returns a MIRANet, not an AMR
	@PostMapping("/reconstruct_ode_semantics")
	ResponseEntity<JsonNode> reconstructODESemantics(@RequestBody final Object amr) throws FeignException;

	// This converts MIRANet (Petrinet) to AMR
	@PostMapping("/to_petrinet")
	ResponseEntity<Model> toPetrinet(@RequestBody Object obj) throws FeignException;

	@PostMapping("/entity_similarity")
	ResponseEntity<List<EntitySimilarityResult>> entitySimilarity(@RequestBody Curies obj) throws FeignException;
}
