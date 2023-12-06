package software.uncharted.terarium.hmiserver.models.dataservice;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

/**
 * Generic response from the dataservice, containing just the id from the
 * asset type which was created/modified/deleted.
 */
@Data
@TSModel
@Accessors(chain = true)
public class ResponseId {

	/** This represents the ID for the newly created TDS object **/
	private String id;
}
