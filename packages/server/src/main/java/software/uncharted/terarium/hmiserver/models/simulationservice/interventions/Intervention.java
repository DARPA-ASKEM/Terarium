package software.uncharted.terarium.hmiserver.models.simulationservice.interventions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

@Data
@TSModel
public class Intervention {

	private String name;

	@TSOptional
	private UUID extractionDocumentId;

	@TSOptional
	private UUID extractionDatasetId;

	@TSOptional
	private Integer extractionPage;

	private List<StaticIntervention> staticInterventions = new ArrayList<>();
	private List<DynamicIntervention> dynamicInterventions = new ArrayList<>();

	@Override
	public Intervention clone() {
		Intervention intervention = new Intervention();
		intervention.setName(name);
		intervention.setExtractionDocumentId(extractionDocumentId);
		intervention.setExtractionPage(extractionPage);

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
}
