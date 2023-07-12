package software.uncharted.terarium.hmiserver.models.simulationservice;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@Accessors(chain = true)
@TSModel
// Used to specify the location of a dataset for simulation-service
public class EnsembleModelConfigs {
    private String id;
    private Map<String, String> compartments;
    private float weight;

}
