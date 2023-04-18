package software.uncharted.terarium.hmiserver.resources.modelservice;

import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import software.uncharted.terarium.hmiserver.models.modelservice.Graph;
import software.uncharted.terarium.hmiserver.models.modelservice.ModelCompositionParams;
import software.uncharted.terarium.hmiserver.models.modelservice.SimulateParams;
import software.uncharted.terarium.hmiserver.proxies.modelservice.ModelServiceProxy;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

@Path("/api/model-service/models")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Model Service REST Endpoint")
public class ModelResource {

	@RestClient
	ModelServiceProxy proxy;

	@Channel("user-event-requests")
	Emitter<String> userEventRequestEmitter;

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Create blank model")
	public Response createModel() {
		final Response response = proxy.createModel();
		final Map model = response.readEntity(Map.class);
		final String modelId = model.get("id").toString();
		try {
			userEventRequestEmitter.send(modelId);
		} catch (Error e) {

		}
		return Response.ok(Map.of("id", modelId)).build();
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
	public Response getModelJSON(
		@PathParam("modelId") final String modelId
	) {
		return proxy.getModelJSON(modelId);
	}


	@POST
	@Path("/{modelId}/simulate")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Tag(name = "Simulate the model")
	public Response simulate(
		@PathParam("modelId") final String modelId,
		final SimulateParams params
	) {
		return proxy.simulate(modelId, params);
	}

	@POST
	@Path("/model-composition")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Tag(name = "Compose two petri nets")
	public Response modelComposition(
		final ModelCompositionParams params
	) {
		return proxy.modelComposition(params);
	}

	@GET
	@Path("/stratify/{modelA}/{modelB}/{typeModel}")
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Stratify two petri nets")
	public Response stratify(
		@PathParam("modelA") final String modelA,
		@PathParam("modelB") final String modelB,
		@PathParam("typeModel") final String typeModel
	) {
		return proxy.stratify(modelA, modelB, typeModel);
	}
}
