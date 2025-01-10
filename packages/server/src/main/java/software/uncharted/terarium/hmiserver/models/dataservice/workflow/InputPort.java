package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

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
public class InputPort implements Serializable {

	private UUID id;
	private String type;
	private String originalType;
	private String status;
	private String label;
	private ArrayNode value;
	private Boolean isOptional;
}
