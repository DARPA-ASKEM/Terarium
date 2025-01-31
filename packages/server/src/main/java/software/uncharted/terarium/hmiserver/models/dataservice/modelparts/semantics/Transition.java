package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;

@Data
@EqualsAndHashCode(callSuper = true)
@AMRSchemaType
@TSModel
public class Transition extends SupportAdditionalProperties implements Serializable, GroundedSemantic {

	@Serial
	private static final long serialVersionUID = -7703055318779858671L;

	private String id;

	private List<String> input;

	private List<String> output;

	@TSOptional
	private String name;

	@TSOptional
	private String description;

	@TSOptional
	private String expression;

	@TSOptional
	private Grounding grounding;

	@TSOptional
	private Properties properties;

	@Override
	public String getConceptReference() {
		return id;
	}

	@Override
	public void setConceptReference(String id) {
		this.id = id;
	}
}
