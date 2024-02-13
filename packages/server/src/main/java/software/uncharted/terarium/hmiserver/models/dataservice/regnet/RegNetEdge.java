package software.uncharted.terarium.hmiserver.models.dataservice.regnet;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
@TSModel
public class RegNetEdge extends SupportAdditionalProperties implements Serializable {
	@Serial
	private static final long serialVersionUID = -2875068447471585535L;

	private String source;

	private String target;

	private String id;

	private Boolean sign;

	@TSOptional
	private RegNetBaseProperties properties;
}
