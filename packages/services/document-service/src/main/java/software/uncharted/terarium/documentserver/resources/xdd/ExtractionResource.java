package software.uncharted.terarium.documentserver.resources.xdd;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import software.uncharted.terarium.documentserver.responses.xdd.XDDExtractionsResponseOK;
import software.uncharted.terarium.documentserver.responses.xdd.XDDResponse;
import software.uncharted.terarium.documentserver.proxies.xdd.ExtractionProxy;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "XDD Extraction REST Endpoint")
@Path("/extractions")
public class ExtractionResource {

	@RestClient
	ExtractionProxy proxy;

	@GET
	@Path("/object")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Search XDD for extractions related to the document identified in the payload")
	public XDDResponse<XDDExtractionsResponseOK> searchExtractions(@QueryParam("doi") final String doi, @QueryParam("query_all") final String queryAll, @QueryParam("page") final Integer page, @QueryParam("ASKEM_CLASS") String askemClass) {
		return proxy.getExtractions(doi, queryAll, page, askemClass);
	}

	@GET
	@Path("/askem_autocomplete/{term}")
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Search XDD for extractions related to the document identified in the payload")
	public Response getAutocomplete(@PathParam("term") String term) {
		return proxy.getAutocomplete(term);
	}
}
