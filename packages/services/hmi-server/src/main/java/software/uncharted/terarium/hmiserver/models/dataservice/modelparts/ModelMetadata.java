package software.uncharted.terarium.hmiserver.models.dataservice.modelparts;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.VariableStatement;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.Annotations;

@Data
@Accessors(chain = true)
public class ModelMetadata {
	@JsonProperty("processed_at")
	@TSOptional
	private Long processedAt;

	@JsonProperty("processed_by")
	@TSOptional
	private String processedBy;

	@JsonProperty("variable_statements")
	@TSOptional
	private List<VariableStatement> variableStatements;

	@JsonProperty("annotations")
	@TSOptional
	private Annotations annotations;

	@JsonProperty("attributes")
	private List<JsonNode> attributes;
}
