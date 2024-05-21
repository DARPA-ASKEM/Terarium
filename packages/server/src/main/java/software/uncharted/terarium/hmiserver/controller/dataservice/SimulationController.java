package software.uncharted.terarium.hmiserver.controller.dataservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationEngine;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationStatusMessage;
import software.uncharted.terarium.hmiserver.proxies.simulationservice.SimulationCiemssServiceProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.SimulationEventService;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.SimulationService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/simulations")
@RestController
@Slf4j
@RequiredArgsConstructor
public class SimulationController {

	private final ObjectMapper mapper;

	private final SimulationService simulationService;

	private final DatasetService datasetService;

	private final CurrentUserService currentUserService;

	private final ProjectService projectService;
	private final ProjectAssetService projectAssetService;

	private final SimulationEventService simulationEventService;

	private final SimulationCiemssServiceProxy simulationCiemssServiceProxy;

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new simulation")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Simulation created.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Simulation.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the simulation", content = @Content)
	})
	public ResponseEntity<Simulation> createSimulation(
			@RequestBody final Simulation simulation, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			final Simulation sim = simulationService.createAsset(simulation, permission);

			return ResponseEntity.status(HttpStatus.CREATED).body(sim);
		} catch (final Exception e) {
			final String error = "Failed to create simulation.";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Get a simulation by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Simulation found.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Simulation.class))),
			@ApiResponse(responseCode = "204", description = "There are no simulations found and no errors occurred", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving simulations from the data store", content = @Content)
	})
	public ResponseEntity<Simulation> getSimulation(
			@PathVariable("id") final UUID id, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			Optional<Simulation> simulation = simulationService.getAsset(id, permission);

			if (simulation.isPresent()) {
				final Simulation sim = simulation.get();

				// If the simulation failed, then set an error message for the front end to
				// display nicely. We want to
				// save this to the simulaiton object
				// so that its available for the front end to display forever.
				if (sim.getStatus() != null
						&& (sim.getStatus().equals(ProgressState.FAILED)
								|| sim.getStatus().equals(ProgressState.ERROR))
						&& (sim.getStatusMessage() == null
								|| sim.getStatusMessage().isEmpty())) {
					if (sim.getEngine().equals(SimulationEngine.CIEMSS)) {
						// Pyciemss can give us a nice error message. Attempt to get it.
						final ResponseEntity<SimulationStatusMessage> statusResponse = simulationCiemssServiceProxy
								.getRunStatus(
										sim.getId().toString());
						if (statusResponse == null
								|| statusResponse.getBody() == null
								|| statusResponse.getBody().getErrorMsg() == null
								|| statusResponse.getBody().getErrorMsg().isEmpty()) {
							log.error(
									"Failed to get status for simulation {}.  Error code was {}",
									sim.getId(),
									statusResponse == null ? "null" : statusResponse.getStatusCode());
							sim.setStatusMessage("Failed running simulation " + sim.getId());
						} else {
							sim.setStatusMessage(statusResponse.getBody().getErrorMsg());
						}
					} else {
						sim.setStatusMessage("Failed running simulation " + sim.getId());
					}

					simulation = simulationService.updateAsset(sim, permission);
				}
			}

			return simulation.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent()
					.build());
		} catch (final Exception e) {
			final String error = String.format("Failed to get simulation %s", id);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a simulation by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Simulation updated.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Simulation.class))),
			@ApiResponse(responseCode = "404", description = "Simulation not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the simulation", content = @Content)
	})
	public ResponseEntity<Simulation> updateSimulation(
			@PathVariable("id") final UUID id,
			@RequestBody final Simulation simulation,
			@RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			simulation.setId(id);
			final Optional<Simulation> updated = simulationService.updateAsset(simulation, permission);
			return updated.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final Exception e) {
			final String error = String.format("Failed to update simulation %s", id);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Delete a simulation by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Simulation deleted.", content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)),
			@ApiResponse(responseCode = "500", description = "There was an issue deleting the simulation", content = @Content)
	})
	public String deleteSimulation(
			@PathVariable("id") final UUID id, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			simulationService.deleteAsset(id, permission);
			return "Simulation deleted";
		} catch (final Exception e) {
			final String error = String.format("Failed to delete simulation %s", id);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/result")
	@Secured(Roles.USER)
	@Operation(summary = "Get the result of a simulation by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Simulation result found.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Simulation.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving simulation results from the data store", content = @Content)
	})
	public ResponseEntity<String> getSimulationResults(
			@PathVariable("id") final UUID id, @RequestParam("filename") final String filename) {

		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {
			final Optional<PresignedURL> url = simulationService.getDownloadUrl(id, filename);
			if (url.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			final PresignedURL presignedURL = url.get();

			final HttpGet get = new HttpGet(presignedURL.getUrl());
			final HttpResponse response = httpclient.execute(get);
			final String data = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

			return ResponseEntity.ok(data);
		} catch (final Exception e) {
			final String error = String.format("Failed to get result of simulation %s", id);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Creates a new dataset from a simulation result, then add it to a project as a
	 * Dataset.
	 *
	 * @param id        ID of the simulation to create a dataset from
	 * @param projectId ID of the project to add the dataset to
	 * @return Dataset the new dataset created
	 */
	@GetMapping("/{id}/add-result-as-dataset-to-project/{project-id}")
	@Secured(Roles.USER)
	@Operation(summary = "Create a new dataset from a simulation result, then add it to a project as a Dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Dataset created and added to project.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProjectAsset.class))),
			@ApiResponse(responseCode = "404", description = "Simulation not found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the dataset or adding it to the project", content = @Content)
	})
	public ResponseEntity<ProjectAsset> createFromSimulationResult(
			@PathVariable("id") final UUID id,
			@PathVariable("project-id") final UUID projectId,
			@RequestParam("dataset-name") final String datasetName) {

		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			final Optional<Simulation> sim = simulationService.getAsset(id, permission);
			if (sim.isEmpty()) {
				return ResponseEntity.notFound().build();
			}

			// Create the dataset asset:
			final UUID simId = sim.get().getId();
			final Dataset dataset = datasetService.createAsset(new Dataset(), permission);
			dataset.setName(datasetName + " Result Dataset");
			dataset.setDescription(sim.get().getDescription());
			dataset.setMetadata(mapper.convertValue(Map.of("simulationId", simId.toString()), JsonNode.class));
			dataset.setFileNames(sim.get().getResultFiles());
			dataset.setDataSourceDate(sim.get().getCompletedTime());
			dataset.setColumns(new ArrayList<>());

			// Attach the user to the dataset
			if (sim.get().getUserId() != null) {
				dataset.setUserId(sim.get().getUserId());
			}

			// Duplicate the simulation results to a new dataset
			simulationService.copySimulationResultToDataset(sim.get(), dataset);
			datasetService.updateAsset(dataset, permission);

			// Add the dataset to the project as an asset
			final Optional<Project> project = projectService.getProject(projectId);
			if (project.isPresent()) {
				final Optional<ProjectAsset> asset = projectAssetService.createProjectAsset(project.get(),
						AssetType.DATASET, dataset, permission);
				// underlying asset does not exist
				return asset.map(projectAsset -> ResponseEntity.status(HttpStatus.CREATED).body(projectAsset))
						.orElseGet(() -> ResponseEntity.notFound().build());
			} else {
				log.error("Failed to add the dataset from simulation {} result", id);
				return ResponseEntity.internalServerError().build();
			}
		} catch (final IOException e) {
			final String error = String.format("Failed to add simulation %s result as dataset to project %s", id,
					projectId);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/download-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to download the simulation results")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Presigned url generated.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PresignedURL.class))),
			@ApiResponse(responseCode = "404", description = "There was no simulation found with the given ID", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the presigned url", content = @Content)
	})
	public ResponseEntity<PresignedURL> getDownloadURL(
			@PathVariable("id") final UUID id, @RequestParam("filename") final String filename) {

		try {
			final Optional<PresignedURL> url = simulationService.getDownloadUrl(id, filename);
			return url.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final Exception e) {
			final String error = "Unable to get download url";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/subscribe")
	@Secured(Roles.USER)
	@Operation(summary = "Subscribe to simulation events")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Subscribed to simulation events", content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)),
			@ApiResponse(responseCode = "500", description = "There was an issue subscribing to simulation events", content = @Content)
	})
	public ResponseEntity<Void> subscribe(@RequestParam("simulation-ids") final List<String> simulationIds) {

		simulationEventService.subscribe(simulationIds, currentUserService.get());
		return ResponseEntity.ok().build();
	}

	@GetMapping("/unsubscribe")
	@Secured(Roles.USER)
	@Operation(summary = "Unsubscribe from simulation events")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Unsubscribed from simulation events", content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)),
			@ApiResponse(responseCode = "500", description = "There was an issue unsubscribing from simulation events", content = @Content)
	})
	public ResponseEntity<Void> unsubscribe(@RequestParam("simulation-ids") final List<String> simulationIds) {

		simulationEventService.unsubscribe(simulationIds, currentUserService.get());
		return ResponseEntity.ok().build();
	}
}
