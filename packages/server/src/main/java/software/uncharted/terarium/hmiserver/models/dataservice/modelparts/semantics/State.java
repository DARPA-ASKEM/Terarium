package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelUnit;

@Data
@EqualsAndHashCode(callSuper = true)
@AMRSchemaType
@TSModel
public class State extends SupportAdditionalProperties implements Serializable, GroundedSemantic {

	@Serial
	private static final long serialVersionUID = -3188538135192357970L;

	private String id;

	@TSOptional
	private String name;

	@TSOptional
	private String description;

	@TSOptional
	private Grounding grounding;

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
}
