package software.uncharted.terarium.hmiserver.models.dataservice.modelparts;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.OdeSemantics;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.TypingSemantics;

@Data
@Accessors(chain = true)
public class ModelSemantics {
	private OdeSemantics ode;

	@TSOptional
	private SpanSemantics span;

	@TSOptional
	private TypingSemantics typing;
}
