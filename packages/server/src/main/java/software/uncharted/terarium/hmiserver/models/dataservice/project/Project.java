package software.uncharted.terarium.hmiserver.models.dataservice.project;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Accessors(chain = true)
@TSModel
@Entity
public class Project implements Serializable {

	@Serial
	private static final long serialVersionUID = -241733670076432802L;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@TSOptional
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private UUID id;

	@Schema(defaultValue = "My New Project")
	private String name;

	private String userId;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String userName;

	@TSOptional
	@Schema(defaultValue = "My Project Description")
	private String description;

	@TSOptional
	@CreationTimestamp
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	@Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
	private Timestamp createdOn;

	@TSOptional
	@UpdateTimestamp
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	@Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
	private Timestamp updatedOn;

	@TSOptional
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	@Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
	private Timestamp deletedOn;

	@OneToMany(mappedBy = "project")
	@Where(clause = "deleted_on IS NULL")
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	@ToString.Exclude
	@JsonManagedReference
	private List<ProjectAsset> projectAssets;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, defaultValue = "{}")
	private Map<String, String> metadata;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Boolean publicProject;

	@TSOptional
	@Transient
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String userPermission;
}
