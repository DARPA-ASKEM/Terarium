package software.uncharted.terarium.hmiserver.proxies.simulationservice;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import software.uncharted.terarium.hmiserver.models.simulationservice.SimulationRequest;
import software.uncharted.terarium.hmiserver.models.simulationservice.CalibrationRequest;


@RegisterRestClient(configKey = "simulation-service")
@Produces(MediaType.APPLICATION_JSON)
public interface SimulationServiceProxy {
	@POST
	@Path("/calls/simulate")
	@Consumes(MediaType.APPLICATION_JSON)
	Response makeForecastRun(
		SimulationRequest request
	);

	@GET
	@Path("/runs/{runId}/status")
	@Consumes(MediaType.APPLICATION_JSON)
	Response getRunStatus(
		@PathParam("runId") String runId
	);

	@GET
	@Path("/runs/{runId}/result")
	@Consumes(MediaType.APPLICATION_JSON)
	Response getRunResult(
		@PathParam("runId") String runId
	);

	@POST
	@Path("/calibrate")
	@Consumes(MediaType.APPLICATION_JSON)
	Response makeCalibrateJob(
		CalibrationRequest calibrationRequest
	);
}
