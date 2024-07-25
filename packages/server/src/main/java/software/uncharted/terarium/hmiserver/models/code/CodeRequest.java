package software.uncharted.terarium.hmiserver.models.code;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/** The CodeRequest instance to send to TA1 for model extraction from text */
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class CodeRequest implements Serializable {

	private List<String> files = List.of("test");
	private List<String> blobs;

	@JsonProperty("system_name")
	private String systemName = "";

	@JsonProperty("root_name")
	private String rootName = "";

	public CodeRequest(final String code) {
		blobs = List.of(code);
	}
}
