package software.uncharted.terarium.documentserver.proxies.xdd;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.documentserver.responses.xdd.XDDAutocompleteResponse;
import software.uncharted.terarium.documentserver.responses.xdd.XDDExtractionsResponseOK;
import software.uncharted.terarium.documentserver.responses.xdd.XDDResponse;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "xdd-extraction-service")
@Produces(MediaType.APPLICATION_JSON)
public interface ExtractionProxy {
	@GET
	@Path("/askem/object")
	XDDResponse<XDDExtractionsResponseOK> getExtractions(
		@QueryParam("doi") final String doi,
		@QueryParam("query_all") final String queryAll,
		@QueryParam("page") final Integer page,
		@QueryParam("ASKEM_CLASS") String askemClass
	);

	@GET
	@Path("askem_autocomplete/{term}")
	XDDAutocompleteResponse  getAutocomplete(
		@PathParam("term") final String term
	);
}
