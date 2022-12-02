package software.uncharted.terarium.hmiserver.resources.modelservice;

import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import software.uncharted.terarium.hmiserver.proxies.modelservice.ModelServiceProxy;
import software.uncharted.terarium.hmiserver.models.modelservice.Graph;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/model-service/models")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Model Service REST Endpoint")
public class ModelResource {

	@RestClient
	ModelServiceProxy proxy;

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Create blank model")
	public Response createModel() {
		return proxy.createModel();
	}


	@POST
	@Path("/{modelId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Tag(name = "Add sub graph to model")
	public Response addModelParts(
		@PathParam("modelId") final String modelId,
		final Graph graph
	) {
		return proxy.addModelParts(modelId, graph);
	}


	@GET
	@Path("/{modelId}/json")
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Get JSON representation of model")
	public Response addModelParts(
		@PathParam("modelId") final String modelId
	) {
		return proxy.getModelJSON(modelId);
	}

}
