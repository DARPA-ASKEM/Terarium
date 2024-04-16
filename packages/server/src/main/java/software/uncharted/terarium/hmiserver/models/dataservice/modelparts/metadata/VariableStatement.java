package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

@Data
@EqualsAndHashCode(callSuper = true)
@AMRSchemaType
@Accessors(chain = true)
public class VariableStatement extends SupportAdditionalProperties implements Serializable {
	@Serial
	private static final long serialVersionUID = 7471587288767843423L;

	private String id;

	private Variable variable;

	@TSOptional
	private StatementValue value;

	@TSOptional
	private List<VariableStatementMetadata> metadata;

	@TSOptional
	private ProvenanceInfo provenance;
}
