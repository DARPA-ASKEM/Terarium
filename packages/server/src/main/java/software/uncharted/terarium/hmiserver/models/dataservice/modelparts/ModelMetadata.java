package software.uncharted.terarium.hmiserver.models.dataservice.modelparts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.Annotations;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.Card;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.VariableStatement;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.MetadataIntervention;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@AMRSchemaType
@Accessors(chain = true)
public class ModelMetadata extends SupportAdditionalProperties implements Serializable {
	@Serial
	private static final long serialVersionUID = 1847034755264399454L;

	@TSOptional
	@JsonProperty("processed_at")
	private Long processedAt;

	@TSOptional
	@JsonProperty("processed_by")
	private String processedBy;

	@TSOptional
	@JsonProperty("variable_statements")
	private List<VariableStatement> variableStatements;

	@TSOptional
	private Annotations annotations;

	@TSOptional
	private List<JsonNode> attributes;

	@TSOptional
	private Map<String, Object> timeseries;

	/* Link user input string `source` to a parameter/variables of a model. */
	@TSOptional
	private Map<String, Object> sources;

	@TSOptional
	private Card card;

	@TSOptional
	@JsonProperty("gollmCard")
	private JsonNode gollmCard;

	@TSOptional
	private List<String> provenance;

	@TSOptional
	@JsonProperty("templateCard")
	private Object templateCard;

	@TSOptional
	private List<MetadataIntervention> interventions;
}
