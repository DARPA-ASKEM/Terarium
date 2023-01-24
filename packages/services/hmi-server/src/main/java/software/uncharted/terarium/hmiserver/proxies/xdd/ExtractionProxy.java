package software.uncharted.terarium.hmiserver.proxies.xdd;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "extraction-service")
@Produces(MediaType.APPLICATION_JSON)
public interface ExtractionProxy {
	@GET
	@Path("object")
	Response getExtractions(
		@QueryParam("doi") String doi,
		@QueryParam("query_all") String queryAll,
		@QueryParam("page") Integer page,
		@QueryParam("ASKEM_CLASS") String askemClass
	);
}
