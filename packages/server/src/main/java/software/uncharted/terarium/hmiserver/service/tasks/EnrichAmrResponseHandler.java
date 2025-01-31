package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelUnit;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.ModelService;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichAmrResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:enrich_amr";

	private final ObjectMapper objectMapper;
	private final ModelService modelService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		@JsonProperty("research_paper")
		String researchPaper;

		@JsonProperty("amr")
		String amr;
	}

	@Data
	private static class Unit {

		String expression;
		String expressionMathml;
	}

	@Data
	private static class DescriptionsAndUnits {

		String id;
		String description;
		Unit units;
	}

	@Data
	private static class Descriptions {

		String id;
		String description;
	}

	@Data
	private static class Enrichment {

		List<DescriptionsAndUnits> states;
		List<DescriptionsAndUnits> parameters;
		List<DescriptionsAndUnits> observables;
		List<Descriptions> transitions;
	}

	@Data
	public static class Response {

		Enrichment response;
	}

	@Data
	public static class Properties {

		UUID projectId;
		UUID documentId;
		UUID modelId;
		Boolean overwrite;
	}

	@Override
	public TaskResponse onSuccess(final TaskResponse resp) {
		try {
			final Properties props = resp.getAdditionalProperties(Properties.class);
			final Response response = objectMapper.readValue(resp.getOutput(), Response.class);

			final Model model = modelService
				.getAsset(props.getModelId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER)
				.orElseThrow();

			for (final DescriptionsAndUnits state : response.response.states) {
				model
					.getInitials()
					.stream()
					.filter(initial -> initial.getTarget().equalsIgnoreCase(state.id))
					.findFirst()
					.ifPresent(initial -> {
						initial.setDescription(state.description);
						StreamSupport.stream(model.getModel().get("states").spliterator(), false)
							.filter(stateNode -> stateNode.path("id").asText().equals(state.id))
							.findFirst()
							.ifPresent(stateNode -> {
								((ObjectNode) stateNode).put("description", state.description);
								if (state.units != null) {
									((ObjectNode) stateNode).put("units", objectMapper.createObjectNode());
									((ObjectNode) stateNode.get("units")).put("expression", state.units.expression);
									((ObjectNode) stateNode.get("units")).put("expression_mathml", state.units.expressionMathml);
								}
							});
					});
			}

			for (final DescriptionsAndUnits parameter : response.response.parameters) {
				model
					.getParameters()
					.stream()
					.filter(param -> param.getConceptReference().equalsIgnoreCase(parameter.id))
					.findFirst()
					.ifPresent(param -> {
						param.setDescription(parameter.description);
						if (parameter.units != null) {
							param.setUnits(
								new ModelUnit()
									.setExpression(parameter.units.expression)
									.setExpressionMathml(parameter.units.expressionMathml)
							);
						}
					});
			}

			for (final DescriptionsAndUnits observable : response.response.observables) {
				model
					.getObservables()
					.stream()
					.filter(observe -> observe.getConceptReference().equalsIgnoreCase(observable.id))
					.findFirst()
					.ifPresent(observe -> {
						observe.setDescription(observable.description);
						if (observable.units != null) {
							observe.setUnits(
								new ModelUnit()
									.setExpression(observable.units.expression)
									.setExpressionMathml(observable.units.expressionMathml)
							);
						}
					});
			}

			for (final Descriptions transition : response.response.transitions) {
				model
					.getRates()
					.stream()
					.filter(rate -> rate.getTarget().equalsIgnoreCase(transition.id))
					.findFirst()
					.ifPresent(trans -> {
						trans.setDescription(transition.description);

						StreamSupport.stream(model.getModel().get("transitions").spliterator(), false)
							.filter(transitionNode -> transitionNode.path("id").asText().equals(transition.id))
							.findFirst()
							.ifPresent(transitionNode -> {
								((ObjectNode) transitionNode).put("description", transition.description);
							});
					});
			}

			modelService.updateAsset(model, model.getId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER);
		} catch (final Exception e) {
			log.error("Failed to enrich amr", e);
			throw new RuntimeException(e);
		}
		log.info("Model enriched successfully");
		return resp;
	}
}
