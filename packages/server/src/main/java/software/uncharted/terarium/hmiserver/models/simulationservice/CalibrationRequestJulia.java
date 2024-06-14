package software.uncharted.terarium.hmiserver.models.simulationservice;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.DatasetLocation;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.TimeSpan;

@Data
@Accessors(chain = true)
@TSModel
// Used to kick off a calibration job in simulation-service
public class CalibrationRequestJulia implements Serializable {
	@JsonAlias("model_config_id")
	private String modelConfigId;

	private Object extra;

	@TSOptional
	private TimeSpan timespan;

	@TSOptional
	private JsonNode metadata;

	private DatasetLocation dataset;
	private String engine;
}
