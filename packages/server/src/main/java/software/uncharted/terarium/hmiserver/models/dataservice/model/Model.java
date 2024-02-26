package software.uncharted.terarium.hmiserver.models.dataservice.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumAssetThatSupportsAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelHeader;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelSemantics;

import java.io.Serial;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TSModel
public class Model extends TerariumAssetThatSupportsAdditionalProperties {

	@Serial
	private static final long serialVersionUID = 398195277271188277L;

	private ModelHeader header;

	@TSOptional
	private String userId;

	private Map<String, Object> model;

	@TSOptional
	private Object properties;

	@TSOptional
	private ModelSemantics semantics;

	@TSOptional
	private ModelMetadata metadata;

	private String test2;

	public Model() {
		super();
	}
	// Copy constructor
    public Model(final Model other) {
		super();
		this.setId(other.getId());
		this.setTemporary(other.getTemporary());
		this.setPublicAsset(other.getPublicAsset());

		if(other.getCreatedOn() != null) {
			this.setCreatedOn((Timestamp) other.getCreatedOn());
		}

		if(other.getUpdatedOn() != null) {
			this.setUpdatedOn((Timestamp) other.getUpdatedOn());
		}

		if(other.getDeletedOn() != null) {
			this.setDeletedOn((Timestamp) other.getDeletedOn());
		}

        this.header = other.header;
        this.userId = other.userId;
        this.model = new HashMap<>(other.model);
        this.properties = other.properties;
        this.semantics = other.semantics;
        this.metadata = other.metadata;
    }
}
