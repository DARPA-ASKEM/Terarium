package software.uncharted.terarium.hmiserver.models.dataservice.equation;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.dataservice.TerariumAsset;

import java.util.Map;

/**
 * The Equation Data Model
 */
@EqualsAndHashCode(callSuper = true)
@TSModel
@Data
@Accessors(chain = true)
public class Equation extends TerariumAsset {


	/** The userId of the user that created the equation **/
	@TSOptional
	private String userId;

	/** (Optional) Display/human name for the equation **/
	@TSOptional
	private String name;

	/** The type of equation (mathml or latex) **/
	@JsonAlias("equation_type")
	private EquationType equationType;

	/** String representation of the equation **/
	private String content;

	/** (Optional) Unformatted metadata about the equation **/
	@TSOptional
	private Map<String, JsonNode> metadata;

	/** (Optional) Source of the equation, whether a document or HMI generated **/
	@TSOptional
	private EquationSource source;

}
