package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics;

import lombok.Data;
import lombok.experimental.Accessors;

import com.fasterxml.jackson.annotation.JsonAlias;

import software.uncharted.terarium.hmiserver.annotations.TSOptional;

@Data
@Accessors(chain = true)
public class Rate {
	private String target;
	private String expression;
	@JsonAlias("expression_mathml")
	@TSOptional
	private String expression_mathml;
}
