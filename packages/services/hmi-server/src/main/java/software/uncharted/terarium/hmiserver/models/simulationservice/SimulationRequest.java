package software.uncharted.terarium.hmiserver.models.simulationservice;

import software.uncharted.terarium.hmiserver.models.dataservice.ModelConfiguration;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SimulationRequest implements Serializable {
	@JsonAlias("model_config_id")
	private String modelConfigId;

	@JsonAlias("time_span")
	private List<Double> timespan;
	private Object extra;
}
