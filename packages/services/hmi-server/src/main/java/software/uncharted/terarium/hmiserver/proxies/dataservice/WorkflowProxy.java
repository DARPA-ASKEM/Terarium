package software.uncharted.terarium.hmiserver.proxies.dataservice;

import software.uncharted.terarium.hmiserver.annotations.LogRestClientTime;
import software.uncharted.terarium.hmiserver.models.dataservice.Workflow;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.hmiserver.exceptions.HmiResponseExceptionMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;

@RegisterRestClient(configKey = "data-service")
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(HmiResponseExceptionMapper.class)
public interface WorkflowProxy {
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@LogRestClientTime
	Response getWorkflows();

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@LogRestClientTime
	Response createWorkflow(
		Workflow workflow
	);

	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@LogRestClientTime
	Response updateWorkflow(
		@PathParam("id") String id,
		Workflow workflow
	);


	@GET
	@Path("/{id}")
	@LogRestClientTime
	Response getWorkflowById(
		@PathParam("id") String id
	);
}
