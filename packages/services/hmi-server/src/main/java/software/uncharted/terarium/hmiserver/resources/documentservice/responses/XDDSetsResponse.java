package software.uncharted.terarium.hmiserver.resources.documentservice.responses;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.json.bind.annotation.JsonbProperty;
import java.util.List;

@Data
@Accessors(chain = true)
public class XDDSetsResponse {

	private String description;

	@JsonbProperty("available_sets")
	private List<String> available_sets;

}
