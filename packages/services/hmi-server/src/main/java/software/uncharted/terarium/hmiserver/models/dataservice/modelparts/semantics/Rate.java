package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import lombok.Data;
import lombok.experimental.Accessors;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.VariableStatement;
import java.util.List;

@Data
@Accessors(chain = true)
public class Rate {
	private String target;
	private String expression;
	@JsonAlias("expression_mathml")
	private String expressionMathml;
}
