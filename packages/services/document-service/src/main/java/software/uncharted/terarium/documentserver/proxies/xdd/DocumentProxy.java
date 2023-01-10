package software.uncharted.terarium.documentserver.proxies.xdd;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "xdd-document-service")
@Produces(MediaType.APPLICATION_JSON)
public interface DocumentProxy {
	@GET
	@Path("/api/articles")
	Response getDocuments(
		@QueryParam("docid") String docid,
		@QueryParam("doi") String doi,
		@QueryParam("title") String title,
		@QueryParam("term") String term,
		@QueryParam("dataset") String dataset,
		@QueryParam("include_score") String include_score,
		@QueryParam("include_highlights") String include_highlights,
		@QueryParam("inclusive") String inclusive,
		@QueryParam("full_results") String full_results,
		@QueryParam("max") String max,
		@QueryParam("per_page") String per_page,
		@QueryParam("dict") String dict,
		@QueryParam("facets") String facets,
		@QueryParam("min_published") String min_published,
		@QueryParam("max_published") String max_published,
		@QueryParam("pubname") String pubname,
		@QueryParam("publisher") String publisher,
		@QueryParam("additional_fields") String additional_fields,
		@QueryParam("match") String match
	);

	@GET
	@Path("/sets")
	Response getAvailableSets();

	@GET
	@Path("/api/dictionaries")
	Response getAvailableDictionaries(@QueryParam("all") @DefaultValue("") String all);

	@GET
	@Path("/sets/{set}/doc2vec/api/similar")
	Response getRelatedDocuments(
		@PathParam("set") String set,
		@QueryParam("docid") String docid
	);
}
