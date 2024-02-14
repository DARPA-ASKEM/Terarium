package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

import java.io.Serial;
import java.io.Serializable;

@Data
@AMRSchemaType
@Accessors(chain = true)
public class VariableMetadata extends SupportAdditionalProperties implements Serializable {
	@Serial
	private static final long serialVersionUID = -7797913621713481462L;

	private String type;

	private String value;
}
