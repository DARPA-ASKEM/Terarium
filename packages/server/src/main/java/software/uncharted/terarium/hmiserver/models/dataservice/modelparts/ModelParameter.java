package software.uncharted.terarium.hmiserver.models.dataservice.modelparts;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.GroundedSemantic;

@Data
@EqualsAndHashCode(callSuper = true)
@AMRSchemaType
public class ModelParameter extends SupportAdditionalProperties implements Serializable, GroundedSemantic {

	@Serial
	private static final long serialVersionUID = -8680842000646488249L;

	private String id;

	@TSOptional
	private String name;

	@TSOptional
	private String description;

	@TSOptional
	private Double value;

	@TSOptional
	private Grounding grounding;

	@TSOptional
	private ModelDistribution distribution;

	@TSOptional
	private ModelUnit units;

	@Override
	public String getConceptReference() {
		return id;
	}

	@Override
	public void setConceptReference(String id) {
		this.id = id;
	}

	@Override
	public ModelParameter clone() {
		final ModelParameter clone = (ModelParameter) super.clone();
		clone.setConceptReference(this.getConceptReference());
		clone.setName(this.getName());
		clone.setDescription(this.getDescription());
		clone.setValue(this.getValue());

		if (this.getGrounding() != null) clone.setGrounding(this.getGrounding().clone());

		if (this.getDistribution() != null) clone.setDistribution(this.getDistribution().clone());

		if (this.getUnits() != null) clone.setUnits(this.getUnits().clone());

		return clone;
	}
}
