package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelGrounding;

import java.io.Serial;
import java.io.Serializable;

@Data
@AMRSchemaType
@Accessors(chain = true)
public class Properties extends SupportAdditionalProperties implements Serializable {
	@Serial
	private static final long serialVersionUID = 3598901970449574610L;

	private String name;

	@TSOptional
	private ModelGrounding grounding;

	@TSOptional
	private String description;
}
