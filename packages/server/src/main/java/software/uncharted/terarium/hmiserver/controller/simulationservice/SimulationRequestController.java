package software.uncharted.terarium.hmiserver.controller.simulationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
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
import software.uncharted.terarium.hmiserver.controller.SnakeCaseController;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.model.configurations.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationEngine;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.SimulationType;
import software.uncharted.terarium.hmiserver.models.simulationservice.CalibrationRequestCiemss;
import software.uncharted.terarium.hmiserver.models.simulationservice.EnsembleCalibrationCiemssRequest;
import software.uncharted.terarium.hmiserver.models.simulationservice.EnsembleSimulationCiemssRequest;
import software.uncharted.terarium.hmiserver.models.simulationservice.JobResponse;
import software.uncharted.terarium.hmiserver.models.simulationservice.OptimizeRequestCiemss;
import software.uncharted.terarium.hmiserver.models.simulationservice.SimulationRequest;
import software.uncharted.terarium.hmiserver.operations.SimulateRunner;
import software.uncharted.terarium.hmiserver.proxies.simulationservice.SimulationCiemssServiceProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.ClientEventService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.SimulationService;
import software.uncharted.terarium.hmiserver.service.data.WorkflowService;
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

	private final SimulationCiemssServiceProxy simulationCiemssServiceProxy;

	private final ProjectService projectService;
	private final ProjectAssetService projectAssetService;
	private final SimulationService simulationService;
	private final WorkflowService workflowService;

	private final ModelConfigurationService modelConfigService;

	private final NotificationService notificationService;
	private final ClientEventService clientEventService;

	private final ObjectMapper objectMapper;

	@Data
	private static class SimulationRequestBody<T> {

		private JsonNode metadata;
		private T payload;
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	public ResponseEntity<Simulation> getSimulation(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);
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

	@PostMapping("ciemss/forecast-op")
	@Secured(Roles.USER)
	public ResponseEntity<Simulation> makeForecastRunCiemssOp(
		@RequestBody final SimulationRequestBody<SimulationRequest> request,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<ModelConfiguration> modelConfiguration = modelConfigService.getAsset(
			request.payload.getModelConfigId(),
			permission
		);
		if (modelConfiguration.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		request.payload.setEngine(SimulationEngine.CIEMSS.toString());
		final JobResponse res = simulationCiemssServiceProxy
			.makeForecastRun(convertObjectToSnakeCaseJsonNode(request.payload))
			.getBody();

		final Optional<Simulation> sim = simulationService.getAsset(UUID.fromString(res.getSimulationId()), permission);
		final Optional<Project> project = projectService.getProject(projectId);

		final SimulateRunner runner = new SimulateRunner(this.workflowService, this.simulationService, permission);
		runner
			.setProjectId(projectId)
			.setWorkflowId(UUID.fromString(request.metadata.get("workflowId").asText()))
			.setNodeId(UUID.fromString(request.metadata.get("nodeId").asText()))
			.setSimulationId(sim.get().getId())
			.setMetadata(request.metadata)
			.start();

		try {
			projectAssetService.createProjectAsset(project.get(), AssetType.SIMULATION, sim.get(), permission);
			return ResponseEntity.ok(sim.get());
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
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<ModelConfiguration> modelConfiguration = modelConfigService.getAsset(
			request.payload.getModelConfigId(),
			permission
		);
		if (modelConfiguration.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		request.payload.setEngine(SimulationEngine.CIEMSS.toString());
		final JobResponse res = simulationCiemssServiceProxy
			.makeForecastRun(convertObjectToSnakeCaseJsonNode(request.payload))
			.getBody();

		final Optional<Simulation> sim = simulationService.getAsset(UUID.fromString(res.getSimulationId()), permission);
		final Optional<Project> project = projectService.getProject(projectId);

		final SimulateRunner runner = new SimulateRunner(this.workflowService, this.simulationService, permission);
		runner
			.setProjectId(projectId)
			.setWorkflowId(null)
			.setNodeId(null)
			.setSimulationId(sim.get().getId())
			.setMetadata(request.metadata)
			.start();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			currentUserService,
			simulationService,
			sim.get().getId(),
			projectId,
			permission,
			request.metadata
		)
			.setInterval(2)
			.setThreshold(300)
			.setHalfTimeSeconds(2.0)
			.startPolling();

		try {
			projectAssetService.createProjectAsset(project.get(), AssetType.SIMULATION, sim.get(), permission);
			return ResponseEntity.ok(sim.get());
		} catch (final Exception e) {
			final String error = "Failed to create simulation";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping("ciemss/calibrate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeCalibrateJobCiemss(
		@RequestBody final SimulationRequestBody<CalibrationRequestCiemss> request,
		@RequestParam("project-id") final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);
		final Optional<ModelConfiguration> modelConfiguration = modelConfigService.getAsset(
			request.payload.getModelConfigId(),
			permission
		);
		if (modelConfiguration.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		final JobResponse res = simulationCiemssServiceProxy
			.makeCalibrateJob(convertObjectToSnakeCaseJsonNode(request.payload))
			.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			currentUserService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		final Optional<Simulation> sim = simulationService.getAsset(UUID.fromString(res.getSimulationId()), permission);
		final Optional<Project> project = projectService.getProject(projectId);
		projectAssetService.createProjectAsset(project.get(), AssetType.SIMULATION, sim.get(), permission);

		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/optimize")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeOptimizeJobCiemss(
		@RequestBody final SimulationRequestBody<OptimizeRequestCiemss> request,
		@RequestParam("project-id") final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<ModelConfiguration> modelConfiguration = modelConfigService.getAsset(
			request.payload.getModelConfigId(),
			permission
		);
		if (modelConfiguration.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		final JobResponse res = simulationCiemssServiceProxy
			.makeOptimizeJob(convertObjectToSnakeCaseJsonNode(request.payload))
			.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			currentUserService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		final Optional<Simulation> sim = simulationService.getAsset(UUID.fromString(res.getSimulationId()), permission);
		final Optional<Project> project = projectService.getProject(projectId);
		projectAssetService.createProjectAsset(project.get(), AssetType.SIMULATION, sim.get(), permission);

		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/ensemble-simulate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeEnsembleSimulateCiemssJob(
		@RequestBody final SimulationRequestBody<EnsembleSimulationCiemssRequest> request,
		@RequestParam("project-id") final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final JobResponse res = simulationCiemssServiceProxy
			.makeEnsembleSimulateCiemssJob(convertObjectToSnakeCaseJsonNode(request.payload))
			.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			currentUserService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		final Optional<Simulation> sim = simulationService.getAsset(UUID.fromString(res.getSimulationId()), permission);
		final Optional<Project> project = projectService.getProject(projectId);
		projectAssetService.createProjectAsset(project.get(), AssetType.SIMULATION, sim.get(), permission);

		return ResponseEntity.ok(res);
	}

	@PostMapping("ciemss/ensemble-calibrate")
	@Secured(Roles.USER)
	public ResponseEntity<JobResponse> makeEnsembleCalibrateCiemssJob(
		@RequestBody final SimulationRequestBody<EnsembleCalibrationCiemssRequest> request,
		@RequestParam("project-id") final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final JobResponse res = simulationCiemssServiceProxy
			.makeEnsembleCalibrateCiemssJob(convertObjectToSnakeCaseJsonNode(request.payload))
			.getBody();

		new SimulationRequestStatusNotifier(
			notificationService,
			clientEventService,
			currentUserService,
			simulationService,
			UUID.fromString(res.getSimulationId()),
			projectId,
			permission,
			request.metadata
		).startPolling();

		final Optional<Simulation> sim = simulationService.getAsset(UUID.fromString(res.getSimulationId()), permission);
		final Optional<Project> project = projectService.getProject(projectId);
		projectAssetService.createProjectAsset(project.get(), AssetType.SIMULATION, sim.get(), permission);

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
