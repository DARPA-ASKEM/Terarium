import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import * as workflowService from '@/services/workflow';
import { operation as DocumentOp } from '@/components/workflow/ops/document/mod';
import { operation as ModelFromEquationOp } from '@/components/workflow/ops/model-from-equations/mod';
import { operation as ModelConfigOp } from '@/components/workflow/ops/model-config/mod';
import { operation as SimulateCiemssOp } from '@/components/workflow/ops/simulate-ciemss/mod';
import { operation as ModelCompareOp } from '@/components/workflow/ops/model-comparison/mod';
import { OperatorNodeSize } from '@/services/workflow';

/*
The user can select n amount of documents and set a goal.
The resulting workflow for 2 documents would be:
Document --> Model from Equations --> Model config --> Simulate
Document --> Model from Equations --> Model config --> Simulate
AND every Model from equations node will be attached to a single Compare model node
*/
export class ModelFromLiteratureScenario extends BaseScenario {
	public static templateId = 'model-from-literature';

	public static templateName = 'Reproduce models from literature';

	documentSpecs: { id: string }[];

	modelSelectionCriteria: string;

	constructor() {
		super();
		this.workflowName = '';
		this.documentSpecs = [{ id: '' }];
		this.modelSelectionCriteria = '';
	}

	setDocumentSpec(id: string, index: number) {
		this.documentSpecs[index].id = id;
	}

	addDocumentSpec() {
		this.documentSpecs.push({ id: '' });
	}

	deleteDocumentSpec(index: number) {
		this.documentSpecs.splice(index, 1);
	}

	setModelSelectionCriteria(criteria: string) {
		this.modelSelectionCriteria = criteria;
	}

	toJSON() {
		return {
			templateId: ModelFromLiteratureScenario.templateId,
			workflowName: this.workflowName,
			documentSpecs: this.documentSpecs,
			modelSelectionCriteria: this.modelSelectionCriteria
		};
	}

	isValid(): boolean {
		return !!this.workflowName && !this.documentSpecs.some((document) => !document.id);
	}

	async createWorkflow() {
		const wf = new workflowService.WorkflowWrapper();
		wf.setWorkflowName(this.workflowName);
		wf.setWorkflowScenario(this.toJSON());

		let compareModelsNode;

		// Add compare model node if there is more than 1 document spec
		if (this.documentSpecs.length > 1) {
			compareModelsNode = wf.addNode(
				ModelCompareOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);
			// Add input ports to compare model node
			for (let i = 0; i < this.documentSpecs.length - 1; i++) {
				workflowService.appendInputPort(compareModelsNode, {
					type: 'modelId',
					label: 'Model'
				});
			}
		}

		this.documentSpecs.forEach((docSpec, i) => {
			// Add nodes for each document spec
			const documentNode = wf.addNode(
				DocumentOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);

			const modelFromEquationsNode = wf.addNode(
				ModelFromEquationOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);

			const modelConfigNode = wf.addNode(
				ModelConfigOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);

			const simulateNode = wf.addNode(
				SimulateCiemssOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);

			// Add edges
			wf.addEdge(
				documentNode.id,
				documentNode.outputs[0].id,
				modelFromEquationsNode.id,
				modelFromEquationsNode.inputs[0].id,
				[
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]
			);

			wf.addEdge(
				modelFromEquationsNode.id,
				modelFromEquationsNode.outputs[0].id,
				modelConfigNode.id,
				modelConfigNode.inputs[0].id,
				[
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]
			);

			wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, simulateNode.id, simulateNode.inputs[0].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);

			wf.addEdge(documentNode.id, documentNode.outputs[0].id, modelConfigNode.id, modelConfigNode.inputs[1].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);

			if (compareModelsNode) {
				wf.addEdge(
					modelFromEquationsNode.id,
					modelFromEquationsNode.outputs[0].id,
					compareModelsNode.id,
					compareModelsNode.inputs[i].id,
					[
						{ x: 0, y: 0 },
						{ x: 0, y: 0 }
					]
				);
			}

			// Update document node state
			wf.updateNode(documentNode, {
				state: {
					documentId: docSpec.id
				},
				output: {
					value: [{ documentId: docSpec.id }]
				}
			});
		});

		// Update compare models node state if present
		if (compareModelsNode) {
			wf.updateNode(compareModelsNode, {
				state: {
					goal: this.modelSelectionCriteria
				}
			});
		}

		// Run layout
		// The schematics for model-from-literature
		//
		//  Document -> Model -> ModelConfig -> Simulate
		//  Document -> Model -> ModelConfig -> Simulate     ModelComparison
		//  Document -> Model -> ModelConfig -> Simulate
		const anchorX = 100;
		const anchorY = 100;
		const nodeGapHorizontal = 400;
		const nodeGapVertical = 400;

		const documentNodes = wf.getNodes().filter((d) => d.operationType === DocumentOp.name);
		documentNodes.forEach((documentNode, documentIdx) => {
			documentNode.x = anchorX;
			documentNode.y = anchorY + documentIdx * nodeGapVertical;

			const modelNode = wf.getNeighborNodes(documentNode.id).downstreamNodes[0];
			modelNode.x = documentNode.x + nodeGapHorizontal;
			modelNode.y = documentNode.y;

			const modelConfigNode = wf.getNeighborNodes(modelNode.id).downstreamNodes[0];
			modelConfigNode.x = modelNode.x + nodeGapHorizontal;
			modelConfigNode.y = modelNode.y;

			const simulateNode = wf.getNeighborNodes(modelConfigNode.id).downstreamNodes[0];
			simulateNode.x = modelConfigNode.x + nodeGapHorizontal;
			simulateNode.y = modelConfigNode.y;
		});

		if (compareModelsNode) {
			compareModelsNode.x = anchorX + 4 * nodeGapHorizontal;
			compareModelsNode.y = anchorY + Math.floor(0.5 * documentNodes.length) * nodeGapVertical;
		}

		return wf.dump();
	}
}
