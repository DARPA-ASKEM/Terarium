import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import * as workflowService from '@/services/workflow';
import { operation as ModelOp } from '@/components/workflow/ops/model/mod';
import { operation as ModelConfigOp } from '@/components/workflow/ops/model-config/mod';
import { operation as SimulateOp } from '@/components/workflow/ops/simulate-ciemss/mod';
import { operation as CalibrateOp } from '@/components/workflow/ops/calibrate-ciemss/mod';
import { operation as CalibrateEnsembleOp } from '@/components/workflow/ops/calibrate-ensemble-ciemss/mod';
import { operation as DatasetOp } from '@/components/workflow/ops/dataset/mod';
import { OperatorNodeSize } from '@/services/workflow';
import { v4 as uuidv4 } from 'uuid';
import _ from 'lodash';
import { getModelConfigurationById } from '@/services/model-configurations';
import { CalibrateEnsembleMappingRow } from '../../ops/calibrate-ensemble-ciemss/calibrate-ensemble-ciemss-operation';
import { convertToCalibrateMap } from '../scenario-template-utils';

/**
 * Creates a CalibrateEnsembleScenario based on a dataset, a number of models, and their respective mappings.
 * The mappings are translated into their respective calibrate nodes.
 *
 * The chart layout is structured as follows:
 * 1. For each model, a model configuration node is created.
 * 2. Each model configuration node is attached to its own simulate and calibrate nodes.
 * 3. A dataset node is connected to each calibrate node.
 * 4. Finally, all calibrate nodes are connected to the calibrate ensemble node.
 */
export class CalibrateEnsembleScenario extends BaseScenario {
	public static templateId = 'calibrate-ensemble';

	public static templateName = 'Calibrate an ensemble model';

	datasetSpec: { id: string };

	tabSpecs: {
		id: string;
		modelSpec: { id: string };
		modelConfigSpec: { id: string };
	}[];

	simulateSpec: { ids: string[] };

	timestampColName: string;

	calibrateMappings: CalibrateEnsembleMappingRow[];

	constructor() {
		super();
		this.datasetSpec = {
			id: ''
		};

		this.tabSpecs = [
			{
				id: uuidv4(),
				modelSpec: {
					id: ''
				},
				modelConfigSpec: {
					id: ''
				}
			},
			{
				id: uuidv4(),
				modelSpec: {
					id: ''
				},
				modelConfigSpec: {
					id: ''
				}
			}
		];

		this.simulateSpec = {
			ids: []
		};

		this.timestampColName = '';
		this.calibrateMappings = [];
		this.workflowName = '';
	}

	setDatasetSpec(id: string) {
		this.datasetSpec.id = id;
		this.timestampColName = '';
	}

	setModelSpec(modelId: string, tabId: string) {
		const tabSpec = this.tabSpecs.find((tab) => tab.id === tabId);
		if (!tabSpec) return;
		tabSpec.modelSpec.id = modelId;
		tabSpec.modelConfigSpec.id = '';
	}

	setModelConfigSpec(modelConfigId: string, tabId: string) {
		const tabSpec = this.tabSpecs.find((tab) => tab.id === tabId);
		if (!tabSpec) return;
		tabSpec.modelConfigSpec.id = modelConfigId;
	}

	setSimulateSpec(ids: string[]) {
		this.simulateSpec.ids = ids;
	}

	setTimeStepColName(name: string) {
		this.timestampColName = name;
	}

	addTabSpec() {
		this.tabSpecs.push({
			id: uuidv4(),
			modelSpec: {
				id: ''
			},
			modelConfigSpec: {
				id: ''
			}
		});
	}

	removeTabSpec(index: number) {
		this.tabSpecs.splice(index, 1);
	}

	addMappingRow() {
		this.calibrateMappings.push({
			newName: '',
			datasetMapping: '',
			modelConfigurationMappings: {}
		});
	}

	removeMappingRow(index: number) {
		this.calibrateMappings.splice(index, 1);
	}

	toJSON() {
		return {
			templateId: CalibrateEnsembleScenario.templateId,
			workflowName: this.workflowName,
			datasetSpec: this.datasetSpec,
			tabSpec: this.tabSpecs,
			simulateSpec: this.simulateSpec
		};
	}

	isValid(): boolean {
		return (
			!!this.workflowName &&
			!!this.datasetSpec.id &&
			this.tabSpecs.every((tab) => !!tab.modelSpec.id && !!tab.modelConfigSpec.id) &&
			!!this.timestampColName &&
			!_.isEmpty(this.calibrateMappings) &&
			// every key in the calibrate mappings modelConfigurationMappings object should have a value
			!this.calibrateMappings.some((row) => Object.values(row.modelConfigurationMappings).some((value) => !value)) &&
			!this.calibrateMappings.some((row) => !row.datasetMapping)
		);
	}

	async createWorkflow() {
		const wf = new workflowService.WorkflowWrapper();
		wf.setWorkflowName(this.workflowName);
		wf.setWorkflowScenario(this.toJSON());

		// add dataset node
		const datasetNode = wf.addNode(
			DatasetOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		// add calibrate ensemble node
		const calibrateEnsembleNode = wf.addNode(
			CalibrateEnsembleOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		// attach to calibrate ensemble node
		wf.addEdge(
			datasetNode.id,
			datasetNode.outputs[0].id,
			calibrateEnsembleNode.id,
			calibrateEnsembleNode.inputs[0].id,
			[
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]
		);

		for (let i = 0; i < this.tabSpecs.length - 1; i++) {
			// add inputs
			workflowService.appendInputPort(calibrateEnsembleNode, {
				type: 'modelConfigId',
				label: 'Model configuration'
			});
		}

		let calibrateEnsembleNodeIndex = 1;

		//
		const calibrateMap = convertToCalibrateMap(this.calibrateMappings);

		// every tab has a model, model config, intervention (optional).  Attach accordingly.
		await Promise.all(
			this.tabSpecs.map(async (tab) => {
				const modelNode = wf.addNode(
					ModelOp,
					{ x: 0, y: 0 },
					{
						size: OperatorNodeSize.medium
					}
				);

				const modelConfig = await getModelConfigurationById(tab.modelConfigSpec.id);

				const modelConfigNode = wf.addNode(
					ModelConfigOp,
					{ x: 0, y: 0 },
					{
						size: OperatorNodeSize.medium
					}
				);

				const simulateNode = wf.addNode(
					SimulateOp,
					{ x: 0, y: 0 },
					{
						size: OperatorNodeSize.medium
					}
				);

				const calibrateNode = wf.addNode(
					CalibrateOp,
					{ x: 0, y: 0 },
					{
						size: OperatorNodeSize.medium
					}
				);

				// attach to model config node
				wf.addEdge(modelNode.id, modelNode.outputs[0].id, modelConfigNode.id, modelConfigNode.inputs[0].id, [
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]);

				// attach to simulate node
				wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, simulateNode.id, simulateNode.inputs[0].id, [
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]);

				// attach to calibrate node
				wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, calibrateNode.id, calibrateNode.inputs[0].id, [
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]);

				wf.addEdge(datasetNode.id, datasetNode.outputs[0].id, calibrateNode.id, calibrateNode.inputs[1].id, [
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]);

				wf.updateNode(modelNode, {
					state: {
						modelId: tab.modelSpec.id
					},
					output: {
						value: [tab.modelSpec.id]
					}
				});

				wf.updateNode(modelConfigNode, {
					state: {
						transientModelConfig: modelConfig
					},
					output: {
						value: [modelConfig.id],
						state: _.omit(modelConfigNode.state, ['transientModelConfig'])
					}
				});

				// attach to calibrate ensemble node
				wf.addEdge(
					calibrateNode.id,
					calibrateNode.outputs[0].id,
					calibrateEnsembleNode.id,
					calibrateEnsembleNode.inputs[calibrateEnsembleNodeIndex].id,
					[
						{ x: 0, y: 0 },
						{ x: 0, y: 0 }
					]
				);

				wf.updateNode(calibrateNode, {
					state: {
						timestampColName: this.timestampColName,
						mapping: calibrateMap.get(tab.modelConfigSpec.id)
					}
				});

				calibrateEnsembleNodeIndex++;
			})
		);

		wf.updateNode(datasetNode, {
			state: {
				datasetId: this.datasetSpec.id
			},
			output: {
				value: [this.datasetSpec.id]
			}
		});

		// Run layout
		// The schematic for calibrate ensemble s as follows
		//
		// Dataset
		//
		// Model   Config   Simulate Calibrate
		// Model   Config   Simulate Calibrate   EnsmbleCalibrate
		// Model   Config   Simulate Calibrate
		const anchorX = 100;
		const anchorY = 100;
		const nodeGapHorizontal = 400;
		const nodeGapVertical = 400;

		const modelNodes = wf.getNodes().filter((d) => d.operationType === ModelOp.name);
		modelNodes.forEach((modelNode, modelNodeIdx) => {
			modelNode.x = anchorX + nodeGapHorizontal;
			modelNode.y = anchorY + (modelNodeIdx + 1) * nodeGapVertical;

			const modelNeighbours = wf.getNeighborNodes(modelNode.id);
			const modelConfigNode = modelNeighbours.downstreamNodes[0];
			modelConfigNode.x = modelNode.x + nodeGapHorizontal;
			modelConfigNode.y = modelNode.y;

			const modelConfigNeighbors = wf.getNeighborNodes(modelConfigNode.id);
			const simulateNode = modelConfigNeighbors.downstreamNodes.find((n) => n.operationType === SimulateOp.name);
			const calibrateNode = modelConfigNeighbors.downstreamNodes.find((n) => n.operationType === CalibrateOp.name);

			if (simulateNode) {
				simulateNode.x = modelConfigNode.x + nodeGapHorizontal;
				simulateNode.y = modelConfigNode.y;
			}
			if (calibrateNode) {
				calibrateNode.x = modelConfigNode.x + 2 * nodeGapHorizontal;
				calibrateNode.y = modelConfigNode.y;
			}
		});

		datasetNode.x = anchorX + 3 * nodeGapHorizontal;
		datasetNode.y = anchorY;

		calibrateEnsembleNode.x = anchorX + 5 * nodeGapHorizontal;
		calibrateEnsembleNode.y = anchorY;

		return wf.dump();
	}
}
