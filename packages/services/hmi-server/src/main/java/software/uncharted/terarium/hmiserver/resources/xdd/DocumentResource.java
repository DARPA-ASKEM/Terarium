package software.uncharted.terarium.hmiserver.resources.xdd;

import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import software.uncharted.terarium.hmiserver.proxies.xdd.DocumentProxy;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/xdd/documents")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "XDD Documents REST Endpoint")
public class DocumentResource {

	@RestClient
	DocumentProxy proxy;

	// NOTE: the query parameters match the proxy version and the type XDDSearchPayload
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Get all xdd documents via proxy")
	public Response getDocuments(
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
		@QueryParam("match") String match,
		@QueryParam("known_entities") String known_entities,
		@QueryParam("fields") String fields
	) {
		// only go ahead with the query if at least one param is present
		if (docid != null || doi != null || term != null) {
			// for a more direct search, if doi is valid, then make sure other params are null
			if (docid != null || doi != null) {
				title = null;
				term = null;
				dataset = null;
				include_score = null;
				include_highlights = null;
				inclusive = null;
				full_results = null;
				max = null;
				per_page = null;
				dict = null;
				min_published = null;
				max_published = null;
				pubname = null;
				publisher = null;
				additional_fields = null;
				match = null;
			}
			return proxy.getDocuments(
				docid, doi, title, term, dataset, include_score, include_highlights, inclusive, full_results, max, per_page, dict, facets,
				min_published, max_published, pubname, publisher, additional_fields, match, known_entities, fields);
		}
		return Response.noContent().build();
	}

}
