package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WorkflowPositions implements Serializable {

	@Data
	public static class Position {

		private Double x;
		private Double y;
	}

	private Map<UUID, Position> nodes;
	private Map<UUID, List<Position>> edges;
}
