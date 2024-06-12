package software.uncharted.terarium.hmiserver.models.dataservice.model.configurations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.models.TerariumEntity;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors
@TSModel
@Entity
public abstract class Semantic extends TerariumEntity {

	@Column(columnDefinition = "text")
	private String source;

	private SemanticType type;
}
