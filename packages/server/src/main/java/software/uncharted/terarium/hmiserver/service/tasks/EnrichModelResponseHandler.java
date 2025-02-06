package software.uncharted.terarium.hmiserver.service.tasks;

import static software.uncharted.terarium.hmiserver.utils.JsonToHTML.renderJsonToHTML;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelUnit;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Observable;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.State;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Transition;
import software.uncharted.terarium.hmiserver.models.dataservice.regnet.RegNetVertex;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.data.ModelService;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichModelResponseHandler extends TaskResponseHandler {

	public static final String NAME = "gollm:enrich_model";

	private final ObjectMapper objectMapper;
	private final ModelService modelService;

	@Override
	public String getName() {
		return NAME;
	}

	@Data
	public static class Input {

		@JsonProperty("document")
		String document;

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

		JsonNode modelCard;
		Enrichment modelEnrichment;
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
			final JsonNode node = objectMapper.readValue(resp.getOutput(), JsonNode.class);
			final Response response = objectMapper.treeToValue(node.get("response"), Response.class);

			final Model model = modelService
				.getAsset(props.getModelId(), ASSUME_WRITE_PERMISSION_ON_BEHALF_OF_USER)
				.orElseThrow();

			// update the model card
			final JsonNode card = response.modelCard;
			if (model.getMetadata() == null) {
				model.setMetadata(new ModelMetadata());
			}
			model.getMetadata().setGollmCard(card);
			model.getMetadata().setDescription(renderJsonToHTML(card).getBytes(StandardCharsets.UTF_8));

			// update the model with the enriched data
			for (final DescriptionsAndUnits state : response.modelEnrichment.states) {
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

			for (final DescriptionsAndUnits parameter : response.modelEnrichment.parameters) {
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

			for (final DescriptionsAndUnits observable : response.modelEnrichment.observables) {
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

			for (final Descriptions transition : response.modelEnrichment.transitions) {
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

			// Update State Grounding
			if (model.isRegnet()) {
				if (model.getVerticies() != null && !model.getVerticies().isEmpty()) {
					final List<RegNetVertex> vertices = model.getVerticies();
					TaskUtilities.getCuratedGrounding(vertices);
					model.setVerticies(vertices);
				}
			} else {
				if (model.getStates() != null && !model.getStates().isEmpty()) {
					final List<State> states = model.getStates();
					TaskUtilities.getCuratedGrounding(states);
					model.setStates(states);
				}
			}

			// Update Observable Grounding
			if (model.getObservables() != null && !model.getObservables().isEmpty()) {
				final List<Observable> observables = model.getObservables();
				TaskUtilities.getCuratedGrounding(observables);
				model.setObservables(observables);
			}

			// Update Parameter Grounding
			if (model.getParameters() != null && !model.getParameters().isEmpty()) {
				final List<ModelParameter> parameters = model.getParameters();
				TaskUtilities.getCuratedGrounding(parameters);
				model.setParameters(parameters);
			}

			// Update Transition Grounding
			if (model.getTransitions() != null && !model.getTransitions().isEmpty()) {
				final List<Transition> transitions = model.getTransitions();
				TaskUtilities.getCuratedGrounding(transitions);
				model.setTransitions(transitions);
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
