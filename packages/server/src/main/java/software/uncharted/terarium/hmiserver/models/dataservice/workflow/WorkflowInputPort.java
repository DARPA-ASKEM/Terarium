package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

@Data
@Accessors(chain = true)
public class WorkflowInputPort implements Serializable {

	private UUID id;
	private String type;
	private String originaltype;
	private String status;
	private String label;
	private List<JsonNode> value;
	private boolean isOptional;
}
