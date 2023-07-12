package software.uncharted.terarium.hmiserver.resources.modelservice;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


import software.uncharted.terarium.hmiserver.proxies.modelservice.ModelServiceProxy;


@Path("/api/modeling-request")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Modeling Service REST Endpoint")
public class ModelingRequestResource {

	@RestClient
	ModelServiceProxy modelServiceProxy;

	@POST
	@Path("/stratify")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Tag(name = "Stratify two AMR models together")
	public Object stratify(
			final Object baseModel,
			final Object fluxModel
	) {
		System.out.println(baseModel);
		System.out.println(fluxModel);
		return modelServiceProxy.stratify(baseModel, fluxModel);
	}
}

