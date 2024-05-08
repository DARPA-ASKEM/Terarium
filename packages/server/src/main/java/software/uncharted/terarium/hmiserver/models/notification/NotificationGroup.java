package software.uncharted.terarium.hmiserver.models.notification;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumEntity;

@Data
@Accessors(chain = true)
@TSModel
@Entity
public class NotificationGroup extends TerariumEntity {

	@Serial
	private static final long serialVersionUID = -3382397588627700379L;

	@NotNull
	private String userId;

	@NotNull
	private String type;

	@TSOptional
	private UUID projectId;

	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	@OneToMany(mappedBy = "notificationGroup", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	@OrderBy("createdOn DESC")
	@JsonManagedReference
	private final List<NotificationEvent> notificationEvents = new ArrayList<>();

}
