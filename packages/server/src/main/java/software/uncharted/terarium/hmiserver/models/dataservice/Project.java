package software.uncharted.terarium.hmiserver.models.dataservice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

@Data
@Accessors(chain = true)
@TSModel
@Entity
public class Project implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@TSOptional
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private UUID id;

	@Schema(defaultValue = "My New Project")
	private String name;

	@TSOptional
	@Schema(defaultValue = "My Project Description")
	private String description;

	@CreationTimestamp
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Timestamp createdOn;

	@UpdateTimestamp
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Timestamp updatedOn;

	@TSOptional
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Timestamp deletedOn;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Assets assets;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, defaultValue = "{}")
	// Metadata that can be useful for the UI
	private Map<String, String> metadata;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Boolean publicProject;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String userPermission;

	/**
	 * Helper method to create a new project from an existing one, excluding UUID and timestamps. This
	 * will be useful for creating new projects to save in the database from the client for example
	 * @param oldProject project to copy from
	 * @return new project
	 */
	public static Project cloneFrom(Project oldProject){
		return new Project()
			.setName(oldProject.getName())
			.setDescription(oldProject.getDescription())
			.setAssets(oldProject.getAssets());
	}
}
