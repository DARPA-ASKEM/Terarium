package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;

@JsonPropertyOrder({ "id", "name", "description", "grounding" })
public interface GroundedSemantic {
	String getId();
	void setId(String id);

	@TSOptional
	Grounding getGrounding();

	@TSOptional
	void setGrounding(Grounding grounding);

	@TSOptional
	String getName();

	@TSOptional
	void setName(String name);

	@TSOptional
	String getDescription();

	@TSOptional
	void setDescription(String description);
}
