package software.uncharted.terarium.hmiserver.proxies.xdd;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "xdd-document-service")
@Produces(MediaType.APPLICATION_JSON)
public interface DocumentProxy {
	@GET
	@Path("articles")
	Response getDocuments(
		@QueryParam("doi") String doi,
		@QueryParam("title") String title,
		@QueryParam("term") String term,
		@QueryParam("dataset") String dataset,
		@QueryParam("include_score") String include_score,
		@QueryParam("full_results") String full_results,
		@QueryParam("max") String max,
		@QueryParam("per_page") String per_page,
		@QueryParam("dict") String dict
	);
}
