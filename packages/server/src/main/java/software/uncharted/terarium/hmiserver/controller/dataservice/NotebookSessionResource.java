package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.controller.SnakeCaseResource;
import software.uncharted.terarium.hmiserver.models.dataservice.NotebookSession;
import software.uncharted.terarium.hmiserver.proxies.dataservice.NotebookSessionProxy;

import java.util.List;

@RequestMapping("/code-notebook_sessions")
@RestController
@Slf4j
public class NotebookSessionResource implements SnakeCaseResource {

	@Autowired
	NotebookSessionProxy proxy;

	@GetMapping
	public ResponseEntity<List<NotebookSession>> getNotebookSessions() {
		return ResponseEntity.ok(proxy.getAssets(100, 0).getBody());
	}

	@PostMapping
	public ResponseEntity<JsonNode> createNotebookSession(@RequestBody Object config) {
		return ResponseEntity.ok(proxy.createAsset(convertObjectToSnakeCaseJsonNode(config)).getBody());
	}

	@GetMapping("/{id}")
	public ResponseEntity<NotebookSession> getNotebookSession(
		@PathVariable("id") String id
	) {
		return ResponseEntity.ok(proxy.getAsset(id).getBody());
	}

	@PutMapping("/{id}")
	public ResponseEntity<JsonNode> updateNotebookSession(
		@PathVariable("id") String id,
		NotebookSession config
	) {
		return ResponseEntity.ok(proxy.updateAsset(id, convertObjectToSnakeCaseJsonNode(config)).getBody());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<JsonNode> deleteNotebookSession(
		@PathVariable("id") String id
	) {
		return ResponseEntity.ok(proxy.deleteAsset(id).getBody());
	}
}
