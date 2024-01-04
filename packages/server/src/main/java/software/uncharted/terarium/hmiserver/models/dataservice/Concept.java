package software.uncharted.terarium.hmiserver.models.dataservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class Concept implements Serializable {

	private String id;

	private String curie;

	private AssetType type;

	@JsonProperty("object_id")
	private String objectID;

	private OntologicalField status;
}
