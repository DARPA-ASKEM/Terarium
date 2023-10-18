package software.uncharted.terarium.hmiserver.proxies.simulationservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import software.uncharted.terarium.hmiserver.models.simulationservice.JobResponse;


@FeignClient(name = "ciemss-service", url = "${ciemss-service.url}")
public interface SimulationCiemssServiceProxy {
	@PostMapping("/simulate")
	ResponseEntity<JobResponse> makeForecastRun(
		@RequestBody JsonNode request
	);

	@PostMapping("/calibrate")
	ResponseEntity<JobResponse> makeCalibrateJob(
		@RequestBody JsonNode request
	);

	@PostMapping("/ensemble-simulate")
	ResponseEntity<JobResponse> makeEnsembleSimulateCiemssJob(
		@RequestBody JsonNode request
	);

	@PostMapping("/ensemble-calibrate")
	ResponseEntity<JobResponse> makeEnsembleCalibrateCiemssJob(
		JsonNode request
	);

	@GetMapping("/status/{runId}")
	ResponseEntity<JobResponse> getRunStatus(
		@PathVariable("runId") String runId
	);
}
