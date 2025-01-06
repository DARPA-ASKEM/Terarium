package software.uncharted.terarium.hmiserver.operations;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.service.data.SimulationService;
import software.uncharted.terarium.hmiserver.service.data.WorkflowService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Accessors(chain = true)
@Slf4j
public class SimulateRunner {

	// services
	private final SimulationService simulationService;
	private final WorkflowService workflowService;
	private final ScheduledExecutorService executor;
	private final Schema.Permission permission;

	// tracking
	@Setter
	private UUID projectId;

	@Setter
	private UUID workflowId;

	@Setter
	private UUID nodeId;

	@Setter
	private UUID simulationId;

	@Setter
	private JsonNode metadata;

	public SimulateRunner(
		final WorkflowService workflowService,
		final SimulationService simulationService,
		final Schema.Permission permission
	) {
		this.workflowService = workflowService;
		this.simulationService = simulationService;
		this.permission = permission;
		this.executor = Executors.newScheduledThreadPool(1);
	}

	public void start() {
		final Runnable poller = () -> {
			try {
				final Optional<Simulation> result = this.simulationService.getAsset(this.simulationId, this.permission);
				final Simulation simulation = result.get();
				System.out.println("");
				System.out.println("");
				System.out.println("!! simulation");
				System.out.println(simulation);
				System.out.println("!! metadata");
				System.out.println(this.metadata);
				System.out.println("!! tracker");
				System.out.println("project " + this.projectId);
				System.out.println("workflow " + this.workflowId);
				System.out.println("node " + this.nodeId);
				System.out.println("");
				System.out.println("");
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				this.executor.shutdown();
			}
		};
		this.executor.scheduleAtFixedRate(poller, 5, 5, TimeUnit.SECONDS);
	}
}
