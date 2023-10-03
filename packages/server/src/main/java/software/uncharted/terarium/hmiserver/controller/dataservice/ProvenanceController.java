package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceQueryParam;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ProvenanceProxy;


@RequestMapping("/provenance")
@RestController
public class ProvenanceController {

	@Autowired
	ProvenanceProxy proxy;

	@GetMapping("/id")
	public ResponseEntity<Provenance> getProvenance(@PathVariable("id") String id) {
		return ResponseEntity.ok(proxy.getProvenance(id).getBody());
	}

	@PostMapping
	public ResponseEntity<JsonNode> createProvenance(
		@RequestBody final Provenance provenance
	) {
		return ResponseEntity.ok(proxy.createProvenance(provenance).getBody());
	}

	@PostMapping("/connected-nodes")
	public ResponseEntity<JsonNode> searchConnectedNodes(
		@RequestBody final ProvenanceQueryParam body,
		@RequestParam(name = "search_type", defaultValue = "connected_nodes") String searchType
	) {
		return ResponseEntity.ok(proxy.search(body, searchType).getBody());
	}

	@DeleteMapping("/hanging-nodes")
	public ResponseEntity<JsonNode> deleteHangingNodes() {
		return ResponseEntity.ok(proxy.deleteHangingNodes().getBody());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<JsonNode> deleteProvenance(
		@PathVariable("id") final String id
	) {
		return ResponseEntity.ok(proxy.deleteProvenance(id).getBody());
	}
}
