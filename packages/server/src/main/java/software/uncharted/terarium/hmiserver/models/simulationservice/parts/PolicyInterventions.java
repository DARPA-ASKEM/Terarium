package software.uncharted.terarium.hmiserver.models.simulationservice.parts;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

@Data
@Accessors(chain = true)
@TSModel
// Interventions applied by the user within the optimization box.
public class PolicyInterventions {
	// This denotes whether the intervention is on a start date, or a parameter value.
	// https://github.com/DARPA-ASKEM/pyciemss-service/blob/main/service/models/operations/optimize.py#L99
	private String interventionType;

	@JsonAlias("param_names")
	private List<String> paramNames;

	@TSOptional
	@JsonAlias("param_values")
	private List<Double> paramValues;

	@TSOptional
	@JsonAlias("start_time")
	private List<Integer> startTime;

	@Override
	public String toString() {
		return " { Parameter Names: " + this.paramNames + " start time: " + startTime.toString() + " } ";
	}
}
