package software.uncharted.terarium.hmiserver.controller.simulationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import feign.FeignException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.controller.SnakeCaseController;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelConfigurationLegacy;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationEngine;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationType;
import software.uncharted.terarium.hmiserver.models.simulationservice.CalibrationRequestCiemss;
import software.uncharted.terarium.hmiserver.models.simulationservice.CalibrationRequestJulia;
import software.uncharted.terarium.hmiserver.models.simulationservice.EnsembleCalibrationCiemssRequest;
import software.uncharted.terarium.hmiserver.models.simulationservice.EnsembleSimulationCiemssRequest;
import software.uncharted.terarium.hmiserver.models.simulationservice.JobResponse;
import software.uncharted.terarium.hmiserver.models.simulationservice.OptimizeRequestCiemss;
import software.uncharted.terarium.hmiserver.models.simulationservice.SimulationRequest;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.Intervention;
import software.uncharted.terarium.hmiserver.proxies.simulationservice.SimulationCiemssServiceProxy;
import software.uncharted.terarium.hmiserver.proxies.simulationservice.SimulationServiceProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.ClientEventService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationLegacyService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.SimulationService;
import software.uncharted.terarium.hmiserver.service.notification.NotificationService;
import software.uncharted.terarium.hmiserver.service.notification.SimulationRequestStatusNotifier;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/simulation-request")
@RestController
@Slf4j
@RequiredArgsConstructor
// TODO: Once we've moved this off of TDS remove the SnakeCaseController
// interface and import.
public class SimulationRequestController implements SnakeCaseController {

	private final CurrentUserService currentUserService;

	private final SimulationServiceProxy simulationServiceProxy;

	private final SimulationCiemssServiceProxy simulationCiemssServiceProxy;

	private final ProjectService projectService;
	private final SimulationService simulationService;

	private final ModelConfigurationLegacyService modelConfigService;

	private final NotificationService notificationService;
	private final ClientEventService clientEventService;

	private final ObjectMapper objectMapper;

	private class SimulationRequestBody<T> {
		private JsonNode metadata;
		private T payload;
	}

	@Value("${terarium.sciml-queue}")
	private String SCIML_QUEUE;

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Simulation> getSimulation(
			@PathVariable("id") final UUID id,
			@RequestParam(name = "project-id", required = false) final UUID projectId) {
		final Schema.Permission permission =
				projectService.checkPermissionCanRead(currentUserService.get().getId(), projectId);

		try {
			final Optional<Simulation> sim = simulationService.getAsset(id, permission);
			if (sim.isEmpty()) {
				return ResponseEntity.noContent().build();
			}
			return ResponseEntity.ok(sim.get());
		} catch (final Exception e) {
			final String error = String.format("Failed to get result of simulation %s", id);
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping("/forecast")
	@Secured(Roles.USER)
	public ResponseEntity<Simulation> makeForecastRun(
			@RequestBody final SimulationRequestBody<SimulationRequest> request,
			@RequestParam(name = "project-id", required = false) final UUID projectId) {
		final Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);

		request.payload.setEngine(SimulationEngine.SCIML.toString());
		final JobResponse res = simulationServiceProxy
				.makeForecastRun(convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();

		final Simulation sim = new Simulation();
		sim.setId(UUID.fromString(res.getSimulationId()));
		sim.setType(SimulationType.SIMULATION);

		// Fire and forget
		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			sim.getId(),
			projectId,
			permission,
			request.metadata
		).setInterval(2).setThreshold(300).setHalfTimeSeconds(2.0).startPolling();

		sim.setExecutionPayload(objectMapper.convertValue(request.payload, JsonNode.class));
		sim.setStatus(ProgressState.QUEUED);
		sim.setEngine(SimulationEngine.SCIML);

		final Optional<Project> project = projectService.getProject(projectId);
		if (project.isPresent()) {
			sim.setProjectId(project.get().getId());
			sim.setUserId(project.get().getUserId());
		}

		try {
			final Optional<Simulation> updated = simulationService.updateAsset(sim, permission);
			if (updated.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(updated.get());
		} catch (final Exception e) {
			final String error = "Failed to create simulation";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping("ciemss/forecast")
	@Secured(Roles.USER)
	public ResponseEntity<Simulation> makeForecastRunCiemss(
			@RequestBody final SimulationRequestBody<SimulationRequest> request,
			@RequestParam(name = "project-id", required = false) final UUID projectId) {
		final Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);

		// Get model config's interventions and append them to requests:
		try {
			final Optional<ModelConfigurationLegacy> modelConfiguration =
					modelConfigService.getAsset(request.payload.getModelConfigId(), permission);
			if (modelConfiguration.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			final List<Intervention> modelInterventions =
					modelConfiguration.get().getInterventions();
			if (modelInterventions != null) {
				List<Intervention> allInterventions = request.payload.getInterventions();
				if (allInterventions == null) {
					allInterventions = new ArrayList<>();
				}
				allInterventions.addAll(modelInterventions);
				request.payload.setInterventions(allInterventions);
			}
		} catch (final IOException e) {
			final String error = "Server error has occured while fetching the model configuration";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}

		request.payload.setEngine(SimulationEngine.CIEMSS.toString());
		final JobResponse res = simulationCiemssServiceProxy
				.makeForecastRun(convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();

		final Simulation sim = new Simulation();
		sim.setId(UUID.fromString(res.getSimulationId()));
		sim.setType(SimulationType.SIMULATION);

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			sim.getId(),
			projectId,
			permission,
			request.metadata
		).setInterval(2).setThreshold(300).setHalfTimeSeconds(2.0).startPolling();

		sim.setExecutionPayload(objectMapper.convertValue(request, JsonNode.class));
		sim.setStatus(ProgressState.QUEUED);
		sim.setEngine(SimulationEngine.CIEMSS);

		final Optional<Project> project = projectService.getProject(projectId);
		if (project.isPresent()) {
			sim.setProjectId(project.get().getId());
			sim.setUserId(project.get().getUserId());
		}

		try {
			final Optional<Simulation> updated = simulationService.updateAsset(sim, permission);
			if (updated.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(updated.get());
		} catch (final Exception e) {
			final String error = "Failed to create simulation";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping("/calibrate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeCalibrateJob(@RequestBody final SimulationRequestBody<CalibrationRequestJulia> request,
	@RequestParam(name = "project-id", required = false) final UUID projectId) {
		final Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);
		final JobResponse res = simulationServiceProxy
				.makeCalibrateJob(SCIML_QUEUE, convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();
		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/calibrate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeCalibrateJobCiemss(
			@RequestBody final SimulationRequestBody<CalibrationRequestCiemss> request, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);
		// Get model config's interventions and append them to requests:
		try {
			final Optional<ModelConfigurationLegacy> modelConfiguration =
					modelConfigService.getAsset(request.payload.getModelConfigId(), permission);
			if (modelConfiguration.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			final List<Intervention> modelInterventions =
					modelConfiguration.get().getInterventions();
			if (modelInterventions != null) {
				List<Intervention> allInterventions = request.payload.getInterventions();
				if (allInterventions == null) {
					allInterventions = new ArrayList<>();
				}
				allInterventions.addAll(modelInterventions);
				request.payload.setInterventions(allInterventions);
			}
		} catch (final IOException e) {
			final String error = "Server error has occured while fetching the model configuration";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
		final JobResponse res = simulationCiemssServiceProxy
				.makeCalibrateJob(convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/optimize")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeOptimizeJobCiemss(
			@RequestBody final SimulationRequestBody<OptimizeRequestCiemss> request, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);

		// Get model config's interventions and append them to requests:
		try {
			final Optional<ModelConfigurationLegacy> modelConfiguration =
					modelConfigService.getAsset(request.payload.getModelConfigId(), permission);
			if (modelConfiguration.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			final List<Intervention> modelInterventions =
					modelConfiguration.get().getInterventions();
			if (modelInterventions != null) {
				List<Intervention> allInterventions = request.payload.getFixedStaticParameterInterventions();
				if (allInterventions == null) {
					allInterventions = new ArrayList<>();
				}
				allInterventions.addAll(modelInterventions);
				request.payload.setFixedStaticParameterInterventions(allInterventions);
			}
		} catch (final IOException e) {
			final String error = "Server error has occured while fetching the model configuration";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
		final JobResponse res = simulationCiemssServiceProxy
				.makeOptimizeJob(convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/ensemble-simulate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeEnsembleSimulateCiemssJob(
			@RequestBody final SimulationRequestBody<EnsembleSimulationCiemssRequest> request, @RequestParam("project-id") final UUID projectId) {
		Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);

		final JobResponse res = simulationCiemssServiceProxy
				.makeEnsembleSimulateCiemssJob(convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();
		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();
		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/ensemble-calibrate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeEnsembleCalibrateCiemssJob(
			@RequestBody final SimulationRequestBody<EnsembleCalibrationCiemssRequest> request, @RequestParam("project-id") final UUID projectId) {
		Schema.Permission permission =
				projectService.checkPermissionCanWrite(currentUserService.get().getId(), projectId);

		final JobResponse res = simulationCiemssServiceProxy
				.makeEnsembleCalibrateCiemssJob(convertObjectToSnakeCaseJsonNode(request.payload))
				.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		return ResponseEntity.ok(res);
	}

	@GetMapping("ciemss/cancel/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> cancelCiemssJob(@PathVariable("id") final UUID id) {
		try {
			return ResponseEntity.ok(simulationCiemssServiceProxy.cancelJob(id).getBody());
		} catch (final FeignException e) {
			final String error = "Unable to cancel ciemss job " + id.toString();
			final int status = e.status() >= 400 ? e.status() : 500;
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.valueOf(status), error);
		}
	}
}
