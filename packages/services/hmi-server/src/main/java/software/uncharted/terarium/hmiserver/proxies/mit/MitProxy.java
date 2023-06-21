package software.uncharted.terarium.hmiserver.proxies.mit;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import software.uncharted.terarium.hmiserver.models.modelservice.PetriNet;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.List;

@RegisterRestClient(configKey = "mit")
public interface MitProxy {
	@POST
	@Path("/petri/get_places")
	String getPlaces(
		@QueryParam("code") final String code);

	@POST
	@Path("/petri/get_transitions")
	String getTransitions(
		@QueryParam("code") final String code);

	@POST
	@Path("/petri/get_arcs")
	String getArcs(
		@QueryParam("code") final String code);

	@POST
	@Path("/petri/get_pyacset")
	String getPyAcset(
		@QueryParam("places_str") final String places,
		@QueryParam("transitions_str") final String transitions,
		@QueryParam("arcs_str") final String arcs
	);

	@POST
	@Path("/annotation/find_text_vars")
	String findTextVars(
		@QueryParam("async") final String async,
		@QueryParam("text") final String text
	);

	@POST
	@Path("/annotation/link_annos_to_pyacset")
	String linkAnnotationsToAcset(
		@QueryParam("pyacset_str") final String pyacset,
		@QueryParam("annotations_str") final String annotations,
		@QueryParam("info_str") final String info
	);

	@POST
	@Path("/annotation/link_dataset_col_to_dkg")
	String linkDatasetColToDKG(
		@QueryParam("csv_str") final String csv,
		@QueryParam("doc") final String doc
	);

	@GET
	@Path("/response")
	String getResponse(@QueryParam("id") final String id);
}
