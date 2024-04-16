package software.uncharted.terarium.hmiserver.models.documentservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Dictionary implements Serializable {

    @JsonProperty("dict_id")
    private Number dictId;

    private String name;

    @JsonProperty("base_classification")
    private String baseClassification;

    private String source;

    @JsonProperty("case_sensitive")
    private Boolean caseSensitive;

    @JsonProperty("last_updated")
    private Instant lastUpdated;
}
