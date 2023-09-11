package software.uncharted.terarium.hmiserver.resources.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import io.smallrye.mutiny.Multi;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.reactivestreams.Publisher;
import org.jboss.resteasy.annotations.SseElementType;
import software.uncharted.terarium.hmiserver.models.dataservice.Assets;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.proxies.dataservice.DatasetProxy;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ProjectProxy;
import software.uncharted.terarium.hmiserver.proxies.dataservice.SimulationProxy;
import software.uncharted.terarium.hmiserver.resources.SnakeCaseResource;
import software.uncharted.terarium.hmiserver.utils.Converter;
import software.uncharted.terarium.hmiserver.models.SimulationIntermediateResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;

@Path("/api/simulations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Simulation REST Endpoints")
@Slf4j
public class SimulationResource implements SnakeCaseResource {

	@Inject
	@RestClient
	SimulationProxy proxy;

	@Inject
	@RestClient
	ProjectProxy projectProxy;

	@Inject
	@RestClient
	DatasetProxy datasetProxy;

	@Inject
	@Channel("simulationStatus") Publisher<SimulationIntermediateResults> partialSimulationStream;

	@Broadcast
	@Channel("simulationStatus")
	Emitter<SimulationIntermediateResults> partialSimulationEmitter;

	@POST
	public Simulation createSimulation(final Simulation simulation){
		return proxy.createSimulation(Converter.convertObjectToSnakeCaseJsonNode(simulation));
	}

	@GET
	@Path("/{id}")
	public Response getSimulation(
		@PathParam("id") final String id
	) {
		return Response
			.ok(Response.Status.OK)
			.entity(proxy.getSimulation(id))
			.build();
	}

	@PUT
	@Path("/{id}")
	public Simulation updateSimulation(@PathParam("id") final String id, final Simulation simulation){
		return proxy.updateSimulation(id, simulation);
	}

	@DELETE
	@Path("/{id}")
	public String deleteSimulation(@PathParam("id") final String id){
		return proxy.deleteSimulation(id);
	}


	@GET
	@Path("/{id}/result")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getSimulation(
		@PathParam("id") final String id,
		@QueryParam("filename") final String filename
	) throws Exception {
		CloseableHttpClient httpclient = HttpClients.custom()
				.disableRedirectHandling()
				.build();

		final PresignedURL presignedURL = proxy.getDownloadURL(id, filename);
		final HttpGet get = new HttpGet(presignedURL.getUrl());
		final HttpResponse response = httpclient.execute(get);
		final String data = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

		// return response;
		return Response
			.ok(Response.Status.OK)
			.entity(data)
			.build();
	}

	/**
	 * Creates a new dataset from a simulation result, then add it to a project as a Dataset.
	 *
	 * @param id ID of the simulation to create a dataset from
	 * @param projectId ID of the project to add the dataset to
	 * @return Dataset the new dataset created
	 */
	@GET
	@Path("/{id}/add-result-as-dataset-to-project/{projectId}")
	public Response createFromSimulationResult(
		@PathParam("id") final String id,
		@PathParam("projectId") final String projectId,
		@QueryParam("datasetName") final String datasetName
	) {
		// Duplicate the simulation results to a new dataset
		final Dataset dataset = proxy.copyResultsToDataset(id);

		if(datasetName != null){
			try {
				dataset.setName(datasetName);
				datasetProxy.updateDataset(dataset.getId(),  convertObjectToSnakeCaseJsonNode(dataset));

			} catch (Exception e) {
				log.error("Failed to update dataset {} name", dataset.getId());
				return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Failed to update dataset name")
					.type("text/plain")
					.build();
			}
		}


		// Add the dataset to the project as an asset
		try {
			return projectProxy.createAsset(projectId, Assets.AssetType.DATASETS, dataset.getId());
		} catch (Exception ignored) {
			log.error("Failed to add simulation {} result as dataset to project {}", id, projectId);
			return Response
				.status(Response.Status.INTERNAL_SERVER_ERROR)
				.entity("Failed to add simulation result as dataset to project")
				.type("text/plain")
				.build();
		}
	}

	@GET
	@Path("/{jobId}/partial-result")
	@Produces(MediaType.SERVER_SENT_EVENTS)
	@SseElementType(MediaType.APPLICATION_JSON)
	@Tag(name = "Stream partial/intermediate simulation result associated with run ID")
	public Publisher<SimulationIntermediateResults> stream(
		@PathParam("jobId") final String jobId
	) {
		ObjectMapper mapper = new ObjectMapper();
		return Multi.createFrom().publisher(partialSimulationStream).filter(event -> event.getJobId().equals(jobId));
	}

	// When we finalize the SimulationIntermediateResults object this end point will need to be passed more parameters
	//TODO: https://github.com/DARPA-ASKEM/Terarium/issues/1757
	@PUT
	@Path("/{jobId}/create-partial-result")
	@Produces(MediaType.APPLICATION_JSON)
	@Tag(name = "Used to write to the simulation status channel providing a job ID")
	public Response createPartialResult(
		@PathParam("jobId") final String jobId
	) {
		Double progress = 0.01;
		SimulationIntermediateResults event = new SimulationIntermediateResults();
		event.setJobId(jobId);
		event.setProgress(progress);
		partialSimulationEmitter.send(event);
		return Response.ok().build();
	}
}
