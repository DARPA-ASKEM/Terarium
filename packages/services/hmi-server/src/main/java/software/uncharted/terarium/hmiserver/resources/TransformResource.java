package software.uncharted.terarium.hmiserver.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import software.uncharted.terarium.hmiserver.models.modelservice.PetriNet;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaRustProxy;
import software.uncharted.terarium.hmiserver.proxies.modelservice.ModelServiceProxy;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;


@Path("/api/transforms")
@Slf4j
public class TransformResource {
	@Inject
	@RestClient
	SkemaRustProxy skemaProxy;

	@Inject
	@RestClient
	ModelServiceProxy modelServiceProxy;

	@POST
	@Path("/mathml-to-acset")
	public Response mathML2ACSet(List<String> list) {
		return skemaProxy.convertMathML2ACSet(list);
	}

	@POST
	@Path("/acset-to-latex")
	public Response acet2Latex(PetriNet content) {
		return modelServiceProxy.petrinetToLatex(content);
	}
}
