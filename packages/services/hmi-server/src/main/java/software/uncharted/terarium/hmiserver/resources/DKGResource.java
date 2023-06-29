package software.uncharted.terarium.hmiserver.resources;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import software.uncharted.terarium.hmiserver.exceptions.HmiResponseExceptionMapper;
import software.uncharted.terarium.hmiserver.models.mira.DKG;
import software.uncharted.terarium.hmiserver.proxies.mira.DKGProxy;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;

@Path("/api/dkg")
@Tag(name = "DKG REST Endpoints")
@Slf4j
public class DKGResource {
	@Inject
	@RestClient
	DKGProxy proxy;

	@GET
	public List<DKG> searchConcept(
		@QueryParam("curies") final String curies
	) {
		try {
			return proxy.getEntities(curies);
		} catch (RuntimeException e) {
			log.error("Unable to fetch DKG", e);
			return null;
		}
	}
}
