package software.uncharted.terarium.hmiserver.models.simulationservice;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.TimeSpan;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.OptimizedIntervention;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.OptimizeExtra;


import java.util.List;
import java.io.Serializable;

@Data
@Accessors(chain = true)
@TSModel
// Used to kick off a Optimize job in simulation-service
public class OptimizeRequestCiemss implements Serializable {
	@JsonAlias("model_config_id")
	private String modelConfigId;
	private TimeSpan timespan;
	@TSOptional
	private OptimizedIntervention interventions;
	@JsonAlias("step_size")
	@TSOptional
	private Double stepSize;
	private List<String> qoi;
	@JsonAlias("risk_bound")
	private Double riskBound;
	@JsonAlias("initial_guess_interventions")
	private List<Double> initialGuessInterventions;
	@JsonAlias("bounds_interventions")
	private List<List<Double>> boundsInterventions;
	private OptimizeExtra extra;
	private String engine;
	@JsonAlias("user_id")
	private String userId;
}
