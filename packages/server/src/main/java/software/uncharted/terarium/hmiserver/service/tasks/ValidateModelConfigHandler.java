package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.Simulation;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.SimulationService;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidateModelConfigHandler extends TaskResponseHandler {
	public static final String NAME = "funman_task:validate_modelconfig";

	private final ObjectMapper objectMapper;
	private final SimulationService simulationService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Properties {
		UUID simulationId;
	}

	@Override
	public TaskResponse onRunning(final TaskResponse resp) {
		// FIXME: remove when we distinguish between "initialized" vs "running" state
		if (resp.getOutput() == null) {
			return resp;
		}

		try {
			final JsonNode intermediateResult = objectMapper.readValue(resp.getOutput(), JsonNode.class);
			double progress = intermediateResult.get("progress").doubleValue();

			final Properties props = resp.getAdditionalProperties(Properties.class);
			final UUID simulationId = props.getSimulationId();
			Optional<Simulation> sim = simulationService.getAsset(simulationId);
			if (!sim.isEmpty()) {
				sim.get().setProgress(progress);
				simulationService.updateAsset(sim.get());
			}
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}

		return resp;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		final String resultFilename = "validation.json";
		try {
			// Parse validation result
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final UUID simulationId = props.getSimulationId();
			Optional<Simulation> sim = simulationService.getAsset(simulationId, assumePermission);
			if (sim.isEmpty()) {
				log.error("Cannot find Simulation " + simulationId + " for task " + resp.getId());
				throw new Error("Cannot find Simulation " + simulationId + " for task " + resp.getId());
			}

			// Retrive final result json
			final JsonNode result = objectMapper.readValue(resp.getOutput(), JsonNode.class);

			// Upload final result into S3
			final byte[] bytes = objectMapper.writeValueAsBytes(result.get("response"));
			final HttpEntity fileEntity = new ByteArrayEntity(bytes, ContentType.APPLICATION_OCTET_STREAM);
			simulationService.uploadFile(simulationId, resultFilename, fileEntity, ContentType.TEXT_PLAIN);

			// Mark simulation as completed, update result file
			sim.get().setStatus(ProgressState.COMPLETE);
			final ArrayList<String> resultFiles = new ArrayList<String>();
			resultFiles.add(resultFilename);
			sim.get().setResultFiles(resultFiles);

			// Save
			simulationService.updateAsset(sim.get(), assumePermission);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
		return resp;
	}
}
