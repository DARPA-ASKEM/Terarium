package software.uncharted.terarium.hmiserver.proxies;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.hmiserver.models.Model;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RegisterRestClient
@Path("/model")
@Produces(MediaType.APPLICATION_JSON)
public interface ModelProxy {

	@GET
	List<Model> getModels();

	@GET
	@Path("/{id}")
	Model getModel(
		@QueryParam("id") Long id
	);

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	Model createModel(
		Model model
	);

	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	Model updateModel(
		Long id,
		Model plan
	);

	@DELETE
	@Path("/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	Boolean deleteModel(
		Long id
	);
}
