package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

@Data
@EqualsAndHashCode(callSuper = true)
@AMRSchemaType
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Variable extends SupportAdditionalProperties implements Serializable {

	@Serial
	private static final long serialVersionUID = 701749887670320136L;

	private String id;

	private String name;

	private List<VariableMetadata> metadata;

	private List<DataColumn> column;

	private Paper paper;

	private List<EquationVariable> equations;

	@Override
	public Variable clone() {
		final Variable clone = (Variable) super.clone();
		clone.id = this.id;
		clone.name = this.name;

		if (this.metadata != null) {
			clone.metadata = new ArrayList<>();
			for (final VariableMetadata metadata : this.metadata) {
				clone.metadata.add(metadata.clone());
			}
		}

		if (this.column != null) {
			clone.column = new ArrayList<>();
			for (final DataColumn column : this.column) {
				clone.column.add(column.clone());
			}
		}

		if (this.paper != null) {
			clone.paper = this.paper.clone();
		}

		if (this.equations != null) {
			clone.equations = new ArrayList<>();
			for (final EquationVariable equation : this.equations) {
				clone.equations.add(equation.clone());
			}
		}

		return clone;
	}
}
