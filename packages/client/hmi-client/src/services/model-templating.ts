import { v4 as uuidv4 } from 'uuid';
import { snakeCase, cloneDeep } from 'lodash';
import type { Position } from '@/types/common';
import type { ModelTemplateCard, ModelTemplates } from '@/types/model-templating';
import { DecomposedModelTemplateTypes } from '@/types/model-templating';
import { KernelSessionManager } from '@/services/jupyter';
import naturalConversion from './model-templates/natural-conversion.json';
import naturalProduction from './model-templates/natural-production.json';
import naturalDegredation from './model-templates/natural-degradation.json';
import controlledConversion from './model-templates/controlled-conversion.json';
import controlledProduction from './model-templates/controlled-production.json';
import controlledDegredation from './model-templates/controlled-degradation.json';
import observable from './model-templates/observable.json';

interface AddTemplateArguments {
	subject_name?: string;
	subject_initial_value?: string;
	outcome_name?: string;
	outcome_initial_value?: string;
	controller_name?: string;
	controller_initial_value?: string;
	parameter_name?: string;
	parameter_value?: number;
	parameter_units?: string;
	parameter_description?: string;
	template_expression?: string;
	template_name?: string;
	new_id?: string;
	new_name?: string;
	new_expression?: string;
}

export const modelTemplateOptions = [
	naturalConversion,
	naturalProduction,
	naturalDegredation,
	controlledConversion,
	controlledProduction,
	controlledDegredation,
	observable
].map((modelTemplate: any) => {
	// TODO: Add templateCard attribute to Model later
	modelTemplate.metadata.templateCard = {
		id: modelTemplate.header.name,
		name: modelTemplate.header.name,
		x: 0,
		y: 0
	} as ModelTemplateCard;
	return modelTemplate;
});

export function initializeModelTemplates() {
	const modelTemplates: ModelTemplates = {
		id: uuidv4(),
		transform: { x: 0, y: 0, k: 1 },
		models: [],
		junctions: []
	};
	return modelTemplates;
}

function findCardIndexById(modelTemplates: ModelTemplates, id: string) {
	return modelTemplates.models.findIndex(({ metadata }) => metadata.templateCard.id === id);
}

export function addJunction(modelTemplates: ModelTemplates, portPosition: Position) {
	modelTemplates.junctions.push({
		id: uuidv4(),
		x: portPosition.x + 500,
		y: portPosition.y - 10,
		edges: []
	});
}

export function junctionCleanUp(modelTemplates: ModelTemplates) {
	// If a junction ends up having one edge coming out of it, remove it
	modelTemplates.junctions = modelTemplates.junctions.filter(({ edges }) => edges.length > 1);
}

export function addCard(
	modelTemplates: ModelTemplates,
	kernelManager: KernelSessionManager,
	outputCode: Function,
	modelTemplate: any
) {
	const { name } = modelTemplate.header;

	// If a decomposed card is added, add it to the kernel
	if (Object.values(DecomposedModelTemplateTypes).includes(name)) {
		const addTemplateArguments: AddTemplateArguments = {};

		if (name !== DecomposedModelTemplateTypes.Observable) {
			const { transitions } = modelTemplate.model;
			const { rates, initials, parameters } = cloneDeep(modelTemplate.semantics.ode); // Clone to avoid mutation on initials when splitting controllers

			// Add parameters to the arguments
			addTemplateArguments.parameter_name = parameters[0].id;
			addTemplateArguments.parameter_value = parameters[0].value;
			addTemplateArguments.parameter_units = parameters[0].units;
			addTemplateArguments.parameter_description = parameters[0].description;
			addTemplateArguments.template_expression = rates[0].expression;
			addTemplateArguments.template_name = name;

			// Extract controller from initials and add it to the arguments
			if (
				name === DecomposedModelTemplateTypes.ControlledConversion ||
				name === DecomposedModelTemplateTypes.ControlledDegradation ||
				name === DecomposedModelTemplateTypes.ControlledProduction
			) {
				const { input, output } = transitions[0];
				if (input?.[0] === output?.[0]) {
					const index = initials.findIndex((initial) => initial.target === input[0]);
					const controller = initials[index];

					// Add controller to the arguments
					addTemplateArguments.controller_name = controller.target;
					addTemplateArguments.controller_initial_value = controller.expression;

					// Remove controller from initials
					initials.splice(index, 1);
				}
			}

			// Now that there are no controllers in initals we can add subject/outcome to the arguments
			if (
				name === DecomposedModelTemplateTypes.NaturalConversion ||
				name === DecomposedModelTemplateTypes.ControlledConversion
			) {
				// If it's a conversion template, the first two initials are the subject then outcome
				addTemplateArguments.subject_name = initials[0].target;
				addTemplateArguments.subject_initial_value = initials[0].expression;
				addTemplateArguments.outcome_name = initials[1].target;
				addTemplateArguments.outcome_initial_value = initials[1].expression;
			} else if (
				name === DecomposedModelTemplateTypes.NaturalProduction ||
				name === DecomposedModelTemplateTypes.ControlledProduction
			) {
				// If it's a production template, the first initial is the outcome
				addTemplateArguments.outcome_name = initials[0].target;
				addTemplateArguments.outcome_initial_value = initials[0].expression;
			} else {
				// If it's a degradation template, the first initial is the subject
				addTemplateArguments.subject_name = initials[0].target;
				addTemplateArguments.subject_initial_value = initials[0].expression;
			}
		} else {
			const { observables } = modelTemplate.semantics.ode;
			addTemplateArguments.new_id = observables[0].id;
			addTemplateArguments.new_name = modelTemplate.header.name;
			addTemplateArguments.new_expression = observables[0].expression_mathml;
		}

		kernelManager
			.sendMessage(`add_${snakeCase(name)}_template_request`, addTemplateArguments)
			.register(`add_${snakeCase(name)}_template_response`, (d) => {
				outputCode(d);
			});
	}
	// FIXME: There is some lag when placing a card if these are put in the register callback which feels off
	// Perhaps there can be some sort of transition state to show the card is being placed?
	modelTemplate.metadata.templateCard.id = uuidv4();
	modelTemplates.models.push(modelTemplate);
}

export function updateDecomposedCardName(
	modelTemplates: ModelTemplates,
	kernelManager: KernelSessionManager,
	name: string,
	id: string
) {
	const index = findCardIndexById(modelTemplates, id);
	const model = modelTemplates.models[index];

	kernelManager
		.sendMessage('replace_template_name_request', {
			old_name: model.header.name,
			new_name: name
		})
		.register('replace_template_name_response', (d) => {
			console.log(d);

			model.header.name = name;
			model.metadata.templateCard.name = name;
			if (model.model.transitions[0] && model.semantics.ode.rates[0]) {
				model.model.transitions[0].id = name;
				model.model.transitions[0].properties.name = name;
				model.semantics.ode.rates[0].target = name;
			}
		});
}

export function removeCard(
	modelTemplates: ModelTemplates,
	kernelManager: KernelSessionManager,
	id: string
) {
	const index = findCardIndexById(modelTemplates, id);

	// This is done by name should be id later
	kernelManager
		.sendMessage('remove_template_request', {
			template_name: modelTemplates.models[index].metadata.templateCard.name
		})
		.register('remove_template_response', () => {
			// Remove edges connected to the card
			modelTemplates.junctions.forEach((junction) => {
				junction.edges = junction.edges.filter((edge) => edge.target.cardId !== id);
			});
			junctionCleanUp(modelTemplates);
			// Remove card
			modelTemplates.models.splice(index, 1);
		});
}

export function addEdge(
	modelTemplates: ModelTemplates,
	kernelManager: KernelSessionManager,
	junctionId: string,
	target: { cardId: string; portId: string },
	portPosition: Position,
	interpolatePointsFn?: Function
) {
	kernelManager
		.sendMessage('replace_state_name_request', { template_name: 'Natural conversion' })
		.register('replace_state_name_response', (d) => {
			const index = modelTemplates.junctions.findIndex(({ id }) => id === junctionId);
			const junctionToDrawFrom = modelTemplates.junctions[index];

			const points: Position[] = [
				{ x: junctionToDrawFrom.x + 10, y: junctionToDrawFrom.y + 10 },
				{ x: portPosition.x, y: portPosition.y }
			];

			modelTemplates.junctions[index].edges.push({
				id: uuidv4(),
				target,
				points: interpolatePointsFn ? interpolatePointsFn(...points) : points
			});

			console.log(d);
		});
}

// TODO: There isn't a way to remove edges in the UI yet
// export function removeEdge(modelTemplates: ModelTemplates) {}

export function flattenedToDecomposed(
	decomposedTemplates: ModelTemplates,
	kernelManager: KernelSessionManager,
	outputCode: Function
) {
	kernelManager.sendMessage('amr_to_templates', {}).register('amr_to_templates_response', (d) => {
		// Insert template card data into decomposed models
		let yPos = 100;

		d.content.templates.forEach((modelTemplate: any) => {
			modelTemplate.metadata.templateCard = {
				id: modelTemplate.header.name,
				name: modelTemplate.header.name,
				x: 100,
				y: yPos
			} as ModelTemplateCard;
			yPos += 200;

			addCard(decomposedTemplates, kernelManager, outputCode, modelTemplate);
		});
	});
}
