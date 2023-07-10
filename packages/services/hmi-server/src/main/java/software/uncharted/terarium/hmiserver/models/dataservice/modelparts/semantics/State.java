package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelGrounding;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelUnit;
@Data
@Accessors(chain = true)
public class State {
    private String id;
    @TSOptional
    private String name;
    @TSOptional
    private String description;
    @TSOptional
    private ModelGrounding grounding;
    @TSOptional
    private ModelUnit units;
}
