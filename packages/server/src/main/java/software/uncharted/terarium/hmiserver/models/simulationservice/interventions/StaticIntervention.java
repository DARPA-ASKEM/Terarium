package software.uncharted.terarium.hmiserver.models.simulationservice.interventions;

import lombok.Data;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@Data
@TSModel
public class StaticIntervention {

	private Number timestep;
	private Number value;

	@Override
	public StaticIntervention clone() {
		StaticIntervention staticIntervention = new StaticIntervention();
		staticIntervention.timestep = this.timestep;
		staticIntervention.value = this.value;
		return staticIntervention;
	}
}
