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
// Used to specify any interventions provided by the AMR and given to the simulation-service.
public class OptimizedIntervention {
  private String selection;

  @JsonAlias("param_names")
  private List<String> paramNames;

  @TSOptional
  @JsonAlias("param_values")
  private List<Integer> paramValues;

  @TSOptional
  @JsonAlias("start_time")
  private List<Integer> startTime;

  @Override
  public String toString() {
    return " { Parameter Names: "
        + this.paramNames
        + " start time: "
        + startTime.toString()
        + " } ";
  }
}
