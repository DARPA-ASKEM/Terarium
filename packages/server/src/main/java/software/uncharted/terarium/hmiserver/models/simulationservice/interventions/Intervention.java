package software.uncharted.terarium.hmiserver.models.simulationservice.interventions;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@Data
@TSModel
@Slf4j
public class Intervention {

	private String name;
	private List<StaticIntervention> staticInterventions = new ArrayList<>();
	private List<DynamicIntervention> dynamicInterventions = new ArrayList<>();

	@Override
	public Intervention clone() {
		Intervention intervention = new Intervention();
		intervention.setName(name);

		if (staticInterventions != null) {
			intervention.setStaticInterventions(new ArrayList<>());
			for (StaticIntervention staticIntervention : staticInterventions) {
				intervention.getStaticInterventions().add(staticIntervention.clone());
			}
		}
		if (dynamicInterventions != null) {
			intervention.setDynamicInterventions(new ArrayList<>());
			for (DynamicIntervention dynamicIntervention : dynamicInterventions) {
				intervention.getDynamicInterventions().add(dynamicIntervention.clone());
			}
		}
		return intervention;
	}

	// Check for no negative timesteps
	// Check for no duplicate time + appliedTo pairs in static interventions
	public Boolean validateIntervention() {
		for (int i = 0; i < this.staticInterventions.size(); i++) {
			final Number time = this.staticInterventions.get(i).getTimestep();
			if (time.doubleValue() < 0) {
				final String errorMessage = String.format(
					"The intervention %s has a timestep %s which is less than 0.",
					this.getName(),
					time.toString()
				);
				log.warn(errorMessage);
				return false;
			}
			for (int j = 0; j < this.staticInterventions.size(); j++) {
				final String appliedToOne = this.staticInterventions.get(i).getAppliedTo();
				final Number timeOne = this.staticInterventions.get(i).getTimestep();
				final String appliedToTwo = this.staticInterventions.get(j).getAppliedTo();
				final Number timeTwo = this.staticInterventions.get(j).getTimestep();
				if (Integer.compare(i, j) == 0 && appliedToOne.equals(appliedToTwo) && timeOne.equals(timeTwo)) {
					final String errorMessage = String.format(
						"The intervention %s has duplicate applied to: %s and time: %s pairs.",
						this.getName(),
						appliedToOne,
						timeOne
					);
					log.warn(errorMessage);
					return false;
				}
			}
		}
		return true;
	}
}
