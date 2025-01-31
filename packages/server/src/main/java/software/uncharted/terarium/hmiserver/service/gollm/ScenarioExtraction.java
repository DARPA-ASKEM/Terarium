package software.uncharted.terarium.hmiserver.service.gollm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelDistribution;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Initial;
import software.uncharted.terarium.hmiserver.utils.GreekDictionary;

/**
 * A class to handle scenario extraction from Document and Dataset via GoLLM task-runner. GoLLM responses for the model
 * configuration are in different JSON form depending on the source: Document:{ "conditions": [ { "name":
 * condition_name, "description": description, "initials": [ { "id": "initial_id", "value": 0.0 }, ... ], "parameters":
 * [ { "id": "parameter_id", "value": 0.0 }, ... ] }, ...] } Dataset: {"values":[{ "id": "initial_id", "value": 0.0,
 * "type": "initial" }, { "id": "parameter_id", "value": 0.0, "type": "parameter" }, ...]}
 */
@Slf4j
public class ScenarioExtraction {

	// Replace initial values in the model with the values from the condition
	public static void replaceInitial(final Initial initial, final JsonNode conditionInitial) {
		final String id = conditionInitial.get("id").asText();
		final String target = initial.getTarget();
		if (target.equals(id) || target.equals(GreekDictionary.englishToGreek(id))) {
			if (conditionInitial.has("value")) {
				final String value = String.valueOf(conditionInitial.get("value").doubleValue());
				initial.setExpression(value);
			}
		}
	}

	// Replace parameter values in the model with the values from the condition
	public static void replaceParameter(final ModelParameter parameter, final JsonNode conditionParameter) {
		final String id = conditionParameter.get("id").asText();
		if (
			parameter.getConceptReference().equals(id) ||
			parameter.getConceptReference().equals(GreekDictionary.englishToGreek(id))
		) {
			if (conditionParameter.has("value")) {
				final double value = conditionParameter.get("value").doubleValue();
				parameter.setValue(value);
				// set distribution to null if it is a value
				parameter.setDistribution(null);
			}
			if (conditionParameter.has("distribution")) {
				try {
					ObjectMapper mapper = new ObjectMapper();
					ModelDistribution distribution = mapper.treeToValue(
						conditionParameter.get("distribution"),
						ModelDistribution.class
					);
					parameter.setDistribution(distribution);
				} catch (JsonProcessingException e) {
					log.error("Failed to parse distribution for parameter: {}", parameter.getConceptReference());
				}
			}
		}
	}

	public static List<ModelParameter> getModelParameters(final JsonNode condition, final Model model) {
		final List<ModelParameter> parameters = model.getParameters();
		parameters.forEach(parameter -> {
			condition.forEach(conditionParameter -> {
				// Test if type exist and is parameter for Dataset extraction
				if (conditionParameter.has("type") && conditionParameter.get("type").asText().equals("initial")) {
					return;
				}
				replaceParameter(parameter, conditionParameter);
			});
		});
		return parameters;
	}

	public static List<Initial> getModelInitials(final JsonNode condition, final Model model) {
		final List<Initial> initials = model.getInitials();
		initials.forEach(initial -> {
			condition.forEach(conditionInitial -> {
				if (conditionInitial.has("type") && conditionInitial.get("type").asText().equals("parameter")) return;
				replaceInitial(initial, conditionInitial);
			});
		});
		return initials;
	}

	public static void setNullDefaultModelInitials(final Model model) {
		final List<Initial> initials = model.getInitials();
		initials.forEach(initial -> {
			initial.setExpression(null);
			initial.setExpressionMathml(null);
		});
	}

	public static void setNullDefaultModelParameters(final Model model) {
		final List<ModelParameter> parameters = model.getParameters();
		parameters.forEach(parameter -> {
			parameter.setValue(null);
			parameter.setDistribution(null);
		});
	}
}
