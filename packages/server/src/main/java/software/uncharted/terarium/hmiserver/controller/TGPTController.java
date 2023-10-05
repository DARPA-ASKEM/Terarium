package software.uncharted.terarium.hmiserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequestMapping("/tgpt")
@RestController
@Slf4j
public class TGPTController {
	@Autowired
	ObjectMapper mapper;

	@Value("${tgpt.base.url}")
	String baseUrl;

	@Value("${tgpt.app.url}")
	String appUrl;

	@Value("${tgpt.ws.url}")
	String wsUrl;

	@Value("${tgpt.token}")
	String token;

	@GetMapping("/configuration")
	public ResponseEntity<ObjectNode> getConfiguration() {
		return ResponseEntity.ok(
			mapper.createObjectNode()
				.put("baseUrl", baseUrl)
				.put("appUrl", appUrl)
				.put("wsUrl", wsUrl)
				.put("token", token)
		);
	}
}
