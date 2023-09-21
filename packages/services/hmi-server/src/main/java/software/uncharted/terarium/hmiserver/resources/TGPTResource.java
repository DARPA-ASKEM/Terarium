package software.uncharted.terarium.hmiserver.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.Optional;

@Path("/api/tgpt")
@Tag(name = "TGPT REST Endpoints")
@Slf4j
public class TGPTResource {
	@Inject
	ObjectMapper mapper;

	@ConfigProperty(name = "tgpt.baseUrl", defaultValue = "/beaker/")
	String baseUrl;

	@ConfigProperty(name = "tgpt.appUrl", defaultValue = "http://localhost:8080/beaker/")
	String appUrl;

	@ConfigProperty(name = "tgpt.wsUrl", defaultValue = "ws://localhost:8080/beaker_ws/")
	String wsUrl;

	@ConfigProperty(name = "tgpt.token", defaultValue = "89f73481102c46c0bc13b2998f9a4fce")
	String token;

	@GET
	@Path("/configuration")
	public Response getConfiguration() {
		return Response.ok(
				mapper.createObjectNode()
						.put("baseUrl", baseUrl)
						.put("appUrl", appUrl)
						.put("wsUrl", wsUrl)
						.put("token", token)
		).build();
	}
}
