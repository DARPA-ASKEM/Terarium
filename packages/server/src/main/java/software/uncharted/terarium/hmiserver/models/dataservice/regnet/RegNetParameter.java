package software.uncharted.terarium.hmiserver.models.dataservice.regnet;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelDistribution;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelGrounding;

import java.io.Serial;
import java.io.Serializable;

@Data
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
	private ModelGrounding grounding;

	@TSOptional
	private ModelDistribution distribution;
}
