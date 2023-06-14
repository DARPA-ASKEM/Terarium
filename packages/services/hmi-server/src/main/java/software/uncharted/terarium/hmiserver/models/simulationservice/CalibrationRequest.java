package software.uncharted.terarium.hmiserver.models.simulationservice;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@Accessors(chain = true)
@TSModel
// Used to kick off a calibration job in simulation-service
public class CalibrationRequest implements Serializable {
	@JsonAlias("model_config_id")
	private String modelConfigId;
	private Object extra;
	private List<Integer> timespan;
	private DatasetLocation dataset;
	private String engine;
}
