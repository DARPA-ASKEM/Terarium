package software.uncharted.terarium.hmiserver.models.documentservice.responses;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.models.documentservice.Extraction;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class XDDExtractionsResponseOK extends XDDResponseOK {
    private List<Extraction> data;

    private Number total;

    private Number page;
}
