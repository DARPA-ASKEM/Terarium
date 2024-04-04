package software.uncharted.terarium.hmiserver.models.dataservice.workflow;

import java.io.Serial;
import java.io.Serializable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;

/**
 * The workflow data structure is not very well defined. It is also meant to
 * carry operations each with their own unique
 * representations. As such this is just a pass-thru class for the proxy. The UI
 * has it's own typinging definition that is
 * not generated.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TSModel
@Entity
public class Workflow extends TerariumAsset implements Serializable {

	@Serial
	private static final long serialVersionUID = -1565930053830366145L;

	@Schema(defaultValue = "My New Workflow")
	private String name;

	private String description;

	private Transform transform;

	@JdbcTypeCode(SqlTypes.JSON)
	private Object nodes;

	@JdbcTypeCode(SqlTypes.JSON)
	private Object edges;

}
