package software.uncharted.terarium.hmiserver.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.EventType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@TSModel
@Accessors(chain = true)
@NoArgsConstructor
@Table(indexes = {
	@Index(columnList = "timestampmillis"),
	@Index(columnList = "projectid"),
	@Index(columnList = "username"),
	@Index(columnList = "type"),
	@Index(columnList = "value")
})
public class Event implements Serializable {
	@Id
	@TSOptional
	private String id = UUID.randomUUID().toString();

	@Column(nullable = false)
	@TSOptional
	private Long timestampMillis = Instant.now().toEpochMilli();

	@TSOptional
	private Long projectId;

	@Column(nullable = false)
	@TSOptional
	private String username;

	@Column(nullable = false)
	private EventType type;

	@Column(columnDefinition = "TEXT")
	@TSOptional
	private String value;
}
