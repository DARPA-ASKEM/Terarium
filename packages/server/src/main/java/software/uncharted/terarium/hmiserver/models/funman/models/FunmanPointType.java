package software.uncharted.terarium.hmiserver.models.funman.models;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@Data
@Accessors(chain = true)
@TSModel
public class FunmanPointType {
    private String type;
    private String label;
    private JsonNode values;

}
