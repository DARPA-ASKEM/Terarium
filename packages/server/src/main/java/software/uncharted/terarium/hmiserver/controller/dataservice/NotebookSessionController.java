package software.uncharted.terarium.hmiserver.controller.dataservice;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.controller.SnakeCaseController;
import software.uncharted.terarium.hmiserver.models.dataservice.NotebookSession;
import software.uncharted.terarium.hmiserver.proxies.dataservice.NotebookSessionProxy;
import software.uncharted.terarium.hmiserver.security.Roles;

@RequestMapping("/code-notebook_sessions")
@RestController
@Slf4j
// TODO: Once we've moved this off of TDS remove the SnakeCaseController
// interface and import.
public class NotebookSessionController implements SnakeCaseController {

	@Autowired
	NotebookSessionProxy proxy;

	@GetMapping
	@Secured(Roles.USER)
	public ResponseEntity<List<NotebookSession>> getNotebookSessions() {
		return ResponseEntity.ok(proxy.getAssets(100, 0).getBody());
	}

	@PostMapping
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> createNotebookSession(@RequestBody Object config) {
		return ResponseEntity.ok(proxy.createAsset(convertObjectToSnakeCaseJsonNode(config)).getBody());
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<NotebookSession> getNotebookSession(
			@PathVariable("id") String id) {
		return ResponseEntity.ok(proxy.getAsset(id).getBody());
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> updateNotebookSession(
			@PathVariable("id") String id,
			@RequestBody NotebookSession config

	) {
		return ResponseEntity.ok(proxy.updateAsset(id, convertObjectToSnakeCaseJsonNode(config)).getBody());
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<JsonNode> deleteNotebookSession(
			@PathVariable("id") String id) {
		return ResponseEntity.ok(proxy.deleteAsset(id).getBody());
	}
}
