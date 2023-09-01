package software.uncharted.terarium.hmiserver.model.dataservice.modelparts.metadata;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Equation {
	private String id;
	private String text;
	private String image;
}

