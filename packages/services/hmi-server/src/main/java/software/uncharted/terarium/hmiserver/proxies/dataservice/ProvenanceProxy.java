package software.uncharted.terarium.hmiserver.proxies.dataservice;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.hmiserver.annotations.LogRestClientTime;
import software.uncharted.terarium.hmiserver.models.dataservice.Provenance;
import software.uncharted.terarium.hmiserver.models.dataservice.ProvenanceQueryParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "data-service")
@Path("/provenance")
@Produces(MediaType.APPLICATION_JSON)
public interface ProvenanceProxy {
	@GET
	@LogRestClientTime
	Response getProvenance();

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@LogRestClientTime
	Response createProvenance(
		Provenance provenance
	);

	@POST
	@Path("/search")
	@LogRestClientTime
	Response search(
		ProvenanceQueryParam body,
		@QueryParam("search_type") String searchType
	);

	@DELETE
	@Path("/hanging_nodes")
	@LogRestClientTime
	Response deleteHangingNodes();

	@DELETE
	@Path("/{id}")
	@LogRestClientTime
	Response deleteProvenance(
		@PathParam("id") String id
	);
}
