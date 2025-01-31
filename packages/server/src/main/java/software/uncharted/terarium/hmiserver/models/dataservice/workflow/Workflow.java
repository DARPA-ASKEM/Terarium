package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Entity
public class Workflow extends TerariumAsset {

	@Serial
	private static final long serialVersionUID = -1565930053830366145L;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Transform transform;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private List<WorkflowNode> nodes;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private List<WorkflowEdge> edges;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private JsonNode scenario;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Map<UUID, WorkflowAnnotation> annotations;

	@Override
	public Workflow clone() {
		final Workflow clone = new Workflow();

		cloneSuperFields(clone);

		if (this.transform != null) {
			clone.transform = new Transform()
				.setX(this.transform.getX())
				.setY(this.transform.getY())
				.setK(this.transform.getK());
		}

		final Map<UUID, UUID> oldToNew = new HashMap<>();

		clone.setNodes(new ArrayList<>());
		for (final WorkflowNode node : nodes) {
			final WorkflowNode clonedNode = node.clone(clone.getId());
			oldToNew.put(node.getId(), clonedNode.getId());
			clone.getNodes().add(clonedNode);
		}

		clone.setEdges(new ArrayList<>());
		for (final WorkflowEdge edge : edges) {
			final UUID clonedSourceId = oldToNew.get(edge.getSource());
			final UUID clonedTargetId = oldToNew.get(edge.getTarget());
			final WorkflowEdge clonedEdge = edge.clone(clone.getId(), clonedSourceId, clonedTargetId);
			clone.getEdges().add(clonedEdge);
		}

		clone.setScenario(this.getScenario());
		clone.setAnnotations(this.getAnnotations());
		return clone;
	}
}
