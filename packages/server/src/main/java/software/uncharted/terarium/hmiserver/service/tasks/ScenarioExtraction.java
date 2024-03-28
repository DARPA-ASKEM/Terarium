package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Initial;
import software.uncharted.terarium.hmiserver.utils.GreekDictionary;

import java.util.List;

public class ScenarioExtraction {
	public static List<ModelParameter> getModelParameters(JsonNode condition, Model modelCopy) {
		final List<ModelParameter> modelParameters = modelCopy.getParameters();
		modelParameters.forEach((parameter) -> {
			final String parameterId = parameter.getId();
			final JsonNode conditionParameters = condition.get("parameters");
			conditionParameters.forEach((conditionParameter) -> {
				// Get the parameter value from the condition
				final String id = conditionParameter.get("id").asText();

				// Test against the id of the parameter in greek alphabet or english
				if (parameterId.equals(id) || parameterId.equals(GreekDictionary.englishToGreek(id))) {
					parameter.setValue(conditionParameter.get("value").doubleValue());
				}
			});
		});
		return modelParameters;
	}

	public static List<Initial> getModelInitials(JsonNode condition, Model modelCopy) {
		final List<Initial> modelInitials = modelCopy.getInitials();
		modelInitials.forEach((initial) -> {
			final String target = initial.getTarget();
			final JsonNode conditionInitials = condition.get("initials");
			conditionInitials.forEach((conditionInitial) -> {
				// Get the initial value from the condition
				final String id = conditionInitial.get("id").asText();

				// Test against the id of the initial in greek alphabet or english
				if (target.equals(id) || target.equals(GreekDictionary.englishToGreek(id))) {
					initial.setExpression(String.valueOf(conditionInitial.get("value").doubleValue()));
				}
			});
		});
		return modelInitials;
	}
}
