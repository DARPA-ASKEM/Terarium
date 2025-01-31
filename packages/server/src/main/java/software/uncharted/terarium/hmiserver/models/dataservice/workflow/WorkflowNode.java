package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class WorkflowNode extends SupportAdditionalProperties implements Serializable {

	private UUID id;
	private UUID workflowId;

	@TSIgnore
	private Long version;

	private Boolean isDeleted;

	private String createdBy;
	private Long createdAt;

	private String displayName;
	private String operationType;

	@TSOptional
	private String documentationUrl;

	@TSOptional
	private String imageUrl;

	// Position
	private Double x;
	private Double y;
	private Double width;
	private Double height;

	// State
	private JsonNode state;

	@TSOptional
	private UUID active;

	private List<InputPort> inputs;
	private List<OutputPort> outputs;

	private String status;

	private Boolean uniqueInputs;

	public WorkflowNode clone(final UUID workflowId) {
		final WorkflowNode clone = (WorkflowNode) super.clone();
		clone.setId(UUID.randomUUID());
		clone.setWorkflowId(workflowId);
		return clone;
	}

	public boolean getIsDeleted() {
		if (this.isDeleted == null) return false;

		return this.isDeleted;
	}
}
