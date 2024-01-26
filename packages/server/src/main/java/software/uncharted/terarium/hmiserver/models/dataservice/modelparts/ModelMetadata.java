package software.uncharted.terarium.hmiserver.models.dataservice.modelparts;

import java.util.List;
import java.util.Map;

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

@Data
@AMRSchemaType
@Accessors(chain = true)
@Deprecated
public class ModelMetadata implements SupportAdditionalProperties {
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

	@TSOptional
	private Card card;

	@TSOptional
	private List<String> provenance;
}
