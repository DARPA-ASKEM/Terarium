package software.uncharted.terarium.hmiserver.models.dataservice.modelparts;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

@Data
@EqualsAndHashCode(callSuper = true)
@AMRSchemaType
@Accessors(chain = true)
public class ModelDistribution extends SupportAdditionalProperties implements Serializable {

	@Serial
	private static final long serialVersionUID = -5426742497090710018L;

	private String type;

	private Map<String, Object> parameters;

	@Override
	public ModelDistribution clone() {
		ModelDistribution clone = (ModelDistribution) super.clone();
		clone.setParameters(this.getParameters());
		clone.setType(this.getType());
		return clone;
	}
}
