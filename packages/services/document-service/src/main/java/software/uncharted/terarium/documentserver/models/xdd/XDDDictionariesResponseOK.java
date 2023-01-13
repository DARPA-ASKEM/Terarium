package software.uncharted.terarium.documentserver.models.xdd;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;


@Data
@Accessors(chain = true)
public class XDDDictionariesResponseOK extends XDDResponseOK {

	private List<Dictionary> data;
}
