package software.uncharted.terarium.hmiserver.models.simulationservice;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.Intervention;
import software.uncharted.terarium.hmiserver.models.simulationservice.parts.TimeSpan;

@Data
@Accessors(chain = true)
@TSModel
public class SimulationRequest implements Serializable {
	@JsonAlias("model_config_id")
	private UUID modelConfigId;

	@JsonAlias("time_span")
	private TimeSpan timespan;

	private JsonNode extra;

	private String engine;

	@TSOptional
	private List<Intervention> interventions;

	@Override
	public SimulationRequest clone() {

		final SimulationRequest clone = new SimulationRequest();

		clone.modelConfigId = this.modelConfigId;
		clone.setTimespan(
				this.timespan != null
						? new TimeSpan().setStart(timespan.getStart()).setEnd(timespan.getEnd())
						: null);
		clone.setExtra(this.extra.deepCopy());
		clone.setEngine(this.engine);
		clone.setInterventions(new ArrayList<>());
		for (final Intervention intervention : this.interventions) {
			clone.getInterventions()
					.add(intervention.clone());
		}

		return clone;
	}
}
