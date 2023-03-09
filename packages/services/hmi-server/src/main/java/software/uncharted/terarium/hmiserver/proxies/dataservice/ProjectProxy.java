package software.uncharted.terarium.hmiserver.proxies.dataservice;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.hmiserver.exceptions.HmiResponseExceptionMapper;
import software.uncharted.terarium.hmiserver.models.dataservice.Assets;
import software.uncharted.terarium.hmiserver.models.dataservice.Project;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;


@RegisterRestClient(configKey = "data-service")
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(HmiResponseExceptionMapper.class)
public interface ProjectProxy {

	@GET
	List<Project> getProjects(
		@DefaultValue("50") @QueryParam("page_size") Integer pageSize,
		@DefaultValue("0") @QueryParam("page") Integer page
	);

	@GET
	@Path("/{id}")
	Response getProject(
		@PathParam("id") String id
	);

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	Response createProject(
		Project project
	);

	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	Response updateProject(
		@PathParam("id") String id,
		Project project
	);

	@DELETE
	@Path("/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	Response deleteProject(
		@PathParam("id") String id
	);

	@GET
	@Path("/{project_id}/assets")
	Assets getAssets(
		@PathParam("project_id") String projectId,
		@QueryParam("types") final List<String> types
	);

	@POST
	@Path("/{project_id}/assets/{resource_type}/{resource_id}")
	Response createAsset(
		@PathParam("project_id") String projectId,
		@PathParam("resource_type") String type, // ResourceType
		@PathParam("resource_id") String resourceId
	);

	@DELETE
	@Path("/{project_id}/assets/{resource_type}/{resource_id}")
	Response deleteAsset(
		@PathParam("project_id") String projectId,
		@PathParam("resource_type") String type, // ResourceType
		@PathParam("resource_id") String resourceId
	);
}
