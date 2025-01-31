package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputPort implements Serializable {

	private UUID id;
	private Long version;
	private String type;
	private String originalType;
	private String status;
	private String label;
	private ArrayNode value;
	private Boolean isOptional;

	// FIXME: backwards compatibility, to be removed
	private Boolean acceptMultiple;
}
