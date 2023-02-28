package software.uncharted.terarium.hmiserver.resources.documentservice.responses;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.models.documentservice.Dictionary;


import java.util.List;


@Data
@Accessors(chain = true)
public class XDDDictionariesResponseOK extends XDDResponseOK {

	private List<Dictionary> data;
}
