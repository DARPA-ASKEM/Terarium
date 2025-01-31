import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import { getModelConfigurationById } from '@/services/model-configurations';
import * as workflowService from '@/services/workflow';
import _ from 'lodash';
import { operation as ModelOp } from '@/components/workflow/ops/model/mod';
import { operation as ModelConfigOp } from '@/components/workflow/ops/model-config/mod';
import { operation as InterventionOp } from '@/components/workflow/ops/intervention-policy/mod';
import { operation as SimulateOp } from '@/components/workflow/ops/simulate-ciemss/mod';
import { operation as CompareDatasetsOp } from '@/components/workflow/ops/compare-datasets/mod';
import { OperatorNodeSize } from '@/services/workflow';
import {
	blankIntervention,
	createInterventionPolicy,
	flattenInterventionData,
	getInterventionPolicyById
} from '@/services/intervention-policy';
import { ChartSetting, ChartSettingType, CiemssPresetTypes } from '@/types/common';
import { updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import { AssetType, InterventionPolicy } from '@/types/Types';
import { useProjects } from '@/composables/project';
import { createDefaultForecastSettings, runSimulations } from '../scenario-template-utils';
import { isInterventionPolicyBlank } from '../../ops/intervention-policy/intervention-policy-operation';

export class DecisionMakingScenario extends BaseScenario {
	public static templateId = 'decision-making';

	public static templateName = 'Decision making';

	modelSpec: { id: string };

	modelConfigSpec: { id: string };

	interventionSpecs: { id: string }[];

	newInterventionSpecs: { id: string; name: string }[];

	simulateSpec: { ids: string[]; endTime: number; preset: CiemssPresetTypes; runSimulationsAutomatically: boolean };

	constructor() {
		super();
		this.modelSpec = {
			id: ''
		};

		// Start with 2 empty intervention specs
		this.interventionSpecs = [
			{
				id: ''
			},
			{ id: '' }
		];

		this.newInterventionSpecs = [];
		this.modelConfigSpec = {
			id: ''
		};
		this.simulateSpec = {
			ids: [],
			preset: CiemssPresetTypes.Fast,
			endTime: 100,
			runSimulationsAutomatically: false
		};
		this.workflowName = '';
	}

	setModelSpec(id: string) {
		this.modelSpec.id = id;
		this.modelConfigSpec.id = '';
		this.interventionSpecs.forEach((interventionSpec) => {
			interventionSpec.id = '';
		});
		this.simulateSpec.ids = [];
	}

	setModelConfigSpec(id: string) {
		this.modelConfigSpec.id = id;
	}

	addInterventionSpec() {
		this.interventionSpecs.push({ id: '' });
	}

	removeInterventionSpec(index: number) {
		this.interventionSpecs.splice(index, 1);
	}

	setInterventionSpec(id: string, index: number) {
		this.interventionSpecs[index].id = id;
	}

	setNewInterventionSpec(id: string, name: string) {
		this.newInterventionSpecs.push({ id, name });
	}

	setSimulateSpec(ids: string[]) {
		this.simulateSpec.ids = ids;
	}

	setPreset(preset: CiemssPresetTypes) {
		this.simulateSpec.preset = preset;
	}

	setEndTime(endTime: number) {
		this.simulateSpec.endTime = endTime;
	}

	setRunSimulationsAutomatically(runSimulationsAutomatically: boolean) {
		this.simulateSpec.runSimulationsAutomatically = runSimulationsAutomatically;
	}

	getDefaultForecastSettings() {
		return createDefaultForecastSettings(this.simulateSpec.endTime, this.simulateSpec.preset);
	}

	toJSON() {
		return {
			templateId: DecisionMakingScenario.templateId,
			workflowName: this.workflowName,
			modelSpec: this.modelSpec,
			modelConfigSpec: this.modelConfigSpec,
			interventionSpecs: this.interventionSpecs,
			simulateSpec: this.simulateSpec
		};
	}

	isValid(): boolean {
		return (
			!!this.workflowName &&
			!!this.modelSpec.id &&
			!!this.modelConfigSpec.id &&
			!this.interventionSpecs.some((interventionSpec) => !interventionSpec.id) &&
			!_.isEmpty(this.simulateSpec.ids)
		);
	}

	async createWorkflow() {
		const wf = new workflowService.WorkflowWrapper();
		wf.setWorkflowName(this.workflowName);
		wf.setWorkflowScenario(this.toJSON());

		const fetchedInterventionPolicies: InterventionPolicy[] = [];

		// 1. Add model and model config nodes and connect them
		const modelNode = wf.addNode(
			ModelOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		const modelConfig = await getModelConfigurationById(this.modelConfigSpec.id);
		const modelConfigNode = wf.addNode(
			ModelConfigOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		const compareDatasetNode = wf.addNode(
			CompareDatasetsOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		wf.addEdge(modelNode.id, modelNode.outputs[0].id, modelConfigNode.id, modelConfigNode.inputs[0].id, [
			{ x: 0, y: 0 },
			{ x: 0, y: 0 }
		]);

		wf.updateNode(modelNode, {
			state: {
				modelId: this.modelSpec.id
			},
			output: {
				value: [this.modelSpec.id]
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

		let simulateChartSettings: ChartSetting[] = [];
		simulateChartSettings = updateChartSettingsBySelectedVariables(
			simulateChartSettings,
			ChartSettingType.VARIABLE,
			this.simulateSpec.ids
		);

		let compareDatasetChartSettings: ChartSetting[] = [];
		compareDatasetChartSettings = updateChartSettingsBySelectedVariables(
			compareDatasetChartSettings,
			ChartSettingType.VARIABLE,
			this.simulateSpec.ids
		);

		// 2. Add base simulation (no interventions) and connect it to the compare datasets node
		const baseSimulateNode = wf.addNode(
			SimulateOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, baseSimulateNode.id, baseSimulateNode.inputs[0].id, [
			{ x: 0, y: 0 },
			{ x: 0, y: 0 }
		]);

		wf.updateNode(baseSimulateNode, {
			state: {
				chartSettings: simulateChartSettings,
				...this.getDefaultForecastSettings()
			}
		});

		wf.updateNode(compareDatasetNode, {
			state: {
				chartSettings: compareDatasetChartSettings
			}
		});

		wf.addEdge(
			baseSimulateNode.id,
			baseSimulateNode.outputs[0].id,
			compareDatasetNode.id,
			compareDatasetNode.inputs[0].id,
			[
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]
		);

		/* 3. Create intervention and simulate nodes for each intervention
		 model -> intervention -> simulate -> compare datasets
		 modelConfig -> simulate -> compare datasets */

		// add input ports for each simulation to the compare datasets (base simulation + interventions)
		for (let i = 0; i < this.interventionSpecs.length + 1; i++) {
			workflowService.appendInputPort(compareDatasetNode, {
				type: 'datasetId|simulationId',
				label: 'Dataset or Simulation'
			});
		}

		const promises = this.interventionSpecs.map(async (interventionSpec, i) => {
			let interventionPolicy: InterventionPolicy | null = await getInterventionPolicyById(interventionSpec.id);
			if (!interventionPolicy) {
				// create new intervention if in the new policy list
				interventionPolicy = await createInterventionPolicy(
					{
						name:
							this.newInterventionSpecs.find((newInterventionSpec) => newInterventionSpec.id === interventionSpec.id)
								?.name ?? 'New policy',
						description: 'This intervention policy was created using the decision making scenario template.',
						modelId: this.modelSpec.id,
						interventions: [blankIntervention]
					},
					true
				);

				await useProjects().addAsset(
					AssetType.InterventionPolicy,
					interventionPolicy!.id,
					useProjects().activeProject.value?.id
				);
			}

			fetchedInterventionPolicies.push(interventionPolicy!);

			let chartSettingsClone = _.cloneDeep(simulateChartSettings);
			// apply intervention chart settings if the intervention policy is not blank
			if (!isInterventionPolicyBlank(interventionPolicy)) {
				chartSettingsClone = updateChartSettingsBySelectedVariables(
					chartSettingsClone,
					ChartSettingType.INTERVENTION,
					Object.keys(_.groupBy(flattenInterventionData(interventionPolicy?.interventions ?? []), 'appliedTo'))
				);
			}

			const interventionNode = wf.addNode(
				InterventionOp,
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

			wf.addEdge(modelNode.id, modelNode.outputs[0].id, interventionNode.id, interventionNode.inputs[0].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);
			wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, simulateNode.id, simulateNode.inputs[0].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);
			wf.addEdge(interventionNode.id, interventionNode.outputs[0].id, simulateNode.id, simulateNode.inputs[1].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);

			wf.updateNode(interventionNode, {
				state: {
					interventionPolicy
				},
				output: {
					value: [interventionPolicy!.id],
					state: interventionNode.state
				}
			});

			wf.updateNode(simulateNode, {
				state: {
					chartSettings: chartSettingsClone,
					...this.getDefaultForecastSettings()
				}
			});

			wf.addEdge(
				simulateNode.id,
				simulateNode.outputs[0].id,
				compareDatasetNode.id,
				compareDatasetNode.inputs[i + 1].id,
				[
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]
			);
		});
		await Promise.all(promises);

		if (this.simulateSpec.runSimulationsAutomatically) {
			await runSimulations(wf, this.getDefaultForecastSettings(), fetchedInterventionPolicies);
		}

		// 4. Run layout
		// The schematic for decision-making is as follows
		//
		//                           Interventions
		//  Model -> ModelConfig ->                 -> CompareDataset
		//                           Forecasts
		//
		// wf.runDagreLayout();
		const nodeGapHorizontal = 325;

		modelNode.x = 100;
		modelNode.y = 500;

		modelConfigNode.x = modelNode.x + nodeGapHorizontal;
		modelConfigNode.y = 800;

		const neighbors = wf.getNeighborNodes(modelConfigNode.id);
		neighbors.downstreamNodes.forEach((forecastNode, forecastIdx) => {
			forecastNode.x = modelConfigNode.x + nodeGapHorizontal * (forecastIdx + 1);
			forecastNode.y = modelConfigNode.y + 150;

			// align the intervention node for each forecast
			const forecastNeighbours = wf.getNeighborNodes(forecastNode.id);
			const interventionNode = forecastNeighbours.upstreamNodes.filter(
				(op) => op.operationType === InterventionOp.name
			)[0];
			if (interventionNode) {
				interventionNode.x = forecastNode.x;
				interventionNode.y = forecastNode.y - 400;
			}
		});

		compareDatasetNode.x = modelConfigNode.x + nodeGapHorizontal * (neighbors.downstreamNodes.length + 1);
		compareDatasetNode.y = 500;
		return wf.dump();
	}
}
