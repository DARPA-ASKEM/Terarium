package software.uncharted.terarium.hmiserver.service.gollm;

import com.fasterxml.jackson.databind.JsonNode;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Initial;
import software.uncharted.terarium.hmiserver.utils.GreekDictionary;

import java.util.List;

/**
 * A class to handle scenario extraction from Document and Dataset via GoLLM task-runner.
 * GoLLM responses for the model configuration are in different JSON form depending on the source:
 *
 * Document:{
 * 		"conditions": [
 * 			{
 * 				"name": condition_name,
 *      	"description": description,
 *      	"initials": [ { "id": "initial_id", "value": 0.0 }, ... ],
 *      	"parameters": [ { "id": "parameter_id", "value": 0.0 }, ... ]
 *    	},
 *    	...]
 *    }
 *
 * Dataset: {"values":[{ "id": "initial_id", "value": 0.0, "type": "initial" },
 * 					 { "id": "parameter_id", "value": 0.0, "type": "parameter" },
 * 					 ...]}
 */
public class ScenarioExtraction {
	// Replace initial values in the model with the values from the condition
	public static void replaceInitial(Initial initial, JsonNode conditionInitial) {
		final String id = conditionInitial.get("id").asText();
		final String target = initial.getTarget();
		if (target.equals(id) || target.equals(GreekDictionary.englishToGreek(id))) {
			final String value = String.valueOf(conditionInitial.get("value").doubleValue());
			initial.setExpression(value);
		}
	}

	// Replace parameter values in the model with the values from the condition
	public static void replaceParameter(ModelParameter parameter, JsonNode conditionParameter) {
		final String id = conditionParameter.get("id").asText();
		if (parameter.getId().equals(id) || parameter.getId().equals(GreekDictionary.englishToGreek(id))) {
			final double value = conditionParameter.get("value").doubleValue();
			parameter.setValue(value);
		}
	}

	public static List<ModelParameter> getModelParameters(JsonNode condition, Model modelCopy) {
		final List<ModelParameter> modelParameters = modelCopy.getParameters();
		modelParameters.forEach((parameter) -> {
			condition.forEach((conditionParameter) -> {
				// Test if type exist and is parameter for Dataset extraction
				if (conditionParameter.has("type") && conditionParameter.get("type").asText().equals("initial")) {
					return;
				}
				replaceParameter(parameter, conditionParameter);
			});
		});
		return modelParameters;
	}

	public static List<Initial> getModelInitials(JsonNode condition, Model modelCopy) {
		final List<Initial> modelInitials = modelCopy.getInitials();
		modelInitials.forEach((initial) -> {
			final String target = initial.getTarget();
			condition.forEach((conditionInitial) -> {
				if (conditionInitial.has("type") && conditionInitial.get("type").asText().equals("parameter")) return;
				replaceInitial(initial, conditionInitial);
			});
		});
		return modelInitials;
	}
}
