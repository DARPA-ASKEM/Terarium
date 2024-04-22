package software.uncharted.terarium.taskrunner.models.task;

import java.io.Serializable;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Data
public class TaskResponse implements Serializable {
	private UUID id;
	private String script;
	private TaskStatus status;
	private byte[] output;
	private Object additionalProperties;
	protected String userId;
	private String stdout;
	private String stderr;
}
