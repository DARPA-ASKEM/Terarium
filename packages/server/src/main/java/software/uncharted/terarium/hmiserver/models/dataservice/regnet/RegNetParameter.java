package software.uncharted.terarium.hmiserver.models.dataservice.regnet;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelDistribution;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TSModel
public class RegNetParameter extends SupportAdditionalProperties implements Serializable {

	@Serial
	private static final long serialVersionUID = 3640205037391991531L;

	private String id;

	@TSOptional
	private String description;

	@TSOptional
	private Double value;

	@TSOptional
	private Grounding grounding;

	@TSOptional
	private ModelDistribution distribution;
}
