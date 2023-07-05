package software.uncharted.terarium.hmiserver.proxies.dataservice;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.hmiserver.models.dataservice.Intermediate;
import software.uncharted.terarium.hmiserver.models.dataservice.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.ModelStub;
import software.uncharted.terarium.hmiserver.models.dataservice.ModelFramework;
import software.uncharted.terarium.hmiserver.models.dataservice.ModelOperationCopy;
import software.uncharted.terarium.hmiserver.models.dataservice.ModelConfiguration;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.List;

@RegisterRestClient(configKey = "data-service")
@Path("/models")
@Produces(MediaType.APPLICATION_JSON)
public interface ModelProxy {

	@POST
	@Path("/frameworks")
	@Consumes(MediaType.APPLICATION_JSON)
	Response createFramework(
		ModelFramework framework
	);

	@GET
	@Path("/frameworks/{name}")
	Response getFramework(
		@PathParam("name") String name
	);

	@DELETE
	@Path("/frameworks/{name}")
	Response deleteFramework(
		@PathParam("name") String name
	);

	@GET
	@Path("/intermediates/{id}")
	Response getIntermediate(
		@PathParam("id") String id
	);

	@DELETE
	@Path("/intermediates/{id}")
	Response deleteIntermediate(
		@PathParam("id") String id
	);

	@POST
	@Path("/intermediates")
	@Consumes(MediaType.APPLICATION_JSON)
	Response createIntermediate(
		Intermediate intermediate
	);

	@GET
	@Path("/descriptions")
	Response getDescriptions(
		@DefaultValue("100") @QueryParam("page_size") Integer pageSize,
		@DefaultValue("0") @QueryParam("page") Integer page
	);

	@GET
	@Path("/{id}/descriptions")
	Response getDescription(
		@PathParam("id") String id
	);

	@GET
	@Path("/{id}/parameters")
	Response getParameters(
		@PathParam("id") String id
	);

	@PUT
	@Path("/parameters/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	Response updateParameters(
		@PathParam("id") String id,
		Map<String, Object> parameters
	);

	@GET
	@Path("/{id}")
	Model getModel(
		@PathParam("id") String id
	);

	@POST
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	Response updateModel(
		@PathParam("id") String id,
		ModelStub model
	);

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	Response createModel(
		ModelStub model
	);

	@POST
	@Path("/opts/copy")
	@Consumes(MediaType.APPLICATION_JSON)
	Response copyModel(
		ModelOperationCopy modelOperationCopy
	);

	@GET
	@Path("/{id}/model_configurations")
	List<ModelConfiguration> getModelConfigurations(
			@PathParam("id") String id
	);
}
