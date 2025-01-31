import _ from 'lodash';
import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import * as workflowService from '@/services/workflow';
import { operation as ModelOp } from '@/components/workflow/ops/model/mod';
import { operation as ModelConfigOp } from '@/components/workflow/ops/model-config/mod';
import { operation as SimulateCiemssOp } from '@/components/workflow/ops/simulate-ciemss/mod';
import { operation as CompareDatasetOp } from '@/components/workflow/ops/compare-datasets/mod';
import { operation as InterventionOp } from '@/components/workflow/ops/intervention-policy/mod';
import { OperatorNodeSize } from '@/services/workflow';
import { createModelConfiguration, getModelConfigurationById, getParameter } from '@/services/model-configurations';
import { ChartSetting, ChartSettingType, CiemssPresetTypes } from '@/types/common';
import { updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import { AssetType, InterventionPolicy, ParameterSemantic } from '@/types/Types';
import { DistributionType } from '@/services/distribution';
import { calculateUncertaintyRange } from '@/utils/math';
import {
	blankIntervention,
	createInterventionPolicy,
	flattenInterventionData,
	getInterventionPolicyById
} from '@/services/intervention-policy';
import { useProjects } from '@/composables/project';
import { cartesianProduct, createDefaultForecastSettings, runSimulations } from '../scenario-template-utils';
import { isInterventionPolicyBlank } from '../../ops/intervention-policy/intervention-policy-operation';

export interface HorizonScanningParameter {
	id: string;
	low: number;
	high: number;
}

/*
 * Horizon Scanning scenario

	Configure the model to represent the extremes of uncertainty for some
	parameters, then simulate into the near future with different intervention
	policies and compare the outcomes.

  Users can input a model, a model configuration, a set of interventions,
  and a set of parameters. For each parameter, a low and high value will be specified,
  resulting in the creation of new model configurations based on the cartesion product
	of the parameter extrema.

	Example:
	1 intervention, 2 parameters
  Model Node
  |
  +-- Model Config Node (Low Param 1, Low Param 2)
  |     |
  |     +-- Simulate Node (Intervention 1)
  |
  +-- Model Config Node (High Param 1, Low Param 2)
  |     |
  |     +-- Simulate Node (Intervention 1)
  |
  +-- Model Config Node (Low Param 1, High Param 2)
  |     |
  |     +-- Simulate Node (Intervention 1)
  |
  +-- Model Config Node (High Param 1, High Param 2)
        |
        +-- Simulate Node (Intervention 1)
 */
export class HorizonScanningScenario extends BaseScenario {
	public static templateId = 'horizon-scanning';

	public static templateName = 'Horizon scanning';

	modelSpec: { id: string };

	modelConfigSpec: { id: string };

	interventionSpecs: { id: string }[];

	newInterventionSpecs: { id: string; name: string }[];

	simulateSpec: { ids: string[]; endTime: number; preset: CiemssPresetTypes; runSimulationsAutomatically: boolean };

	parameters: (HorizonScanningParameter | null)[];

	constructor() {
		super();
		this.workflowName = '';
		this.modelSpec = {
			id: ''
		};
		this.modelConfigSpec = {
			id: ''
		};
		this.simulateSpec = {
			ids: [],
			preset: CiemssPresetTypes.Fast,
			endTime: 100,
			runSimulationsAutomatically: false
		};
		this.interventionSpecs = [{ id: '' }];
		this.newInterventionSpecs = [];
		this.parameters = [null];
	}

	setModelSpec(id: string) {
		this.modelSpec.id = id;
		this.modelConfigSpec.id = '';
		this.interventionSpecs = [{ id: '' }];
		this.simulateSpec.ids = [];
		this.parameters = [null];
	}

	setModelConfigSpec(id: string) {
		this.modelConfigSpec.id = id;
	}

	setSimulateSpec(ids: string[]) {
		this.simulateSpec.ids = ids;
	}

	addInterventionSpec() {
		this.interventionSpecs.push({ id: '' });
	}

	setInterventionSpec(id: string, index: number) {
		this.interventionSpecs[index].id = id;
	}

	deleteInterventionSpec(index: number) {
		this.interventionSpecs.splice(index, 1);
	}

	addParameter() {
		this.parameters.push(null);
	}

	removeParameter(index: number) {
		this.parameters.splice(index, 1);
	}

	setNewInterventionSpec(id: string, name: string) {
		this.newInterventionSpecs.push({ id, name });
	}

	setParameter(parameter: ParameterSemantic, index: number) {
		let low = 0;
		let high = 1;
		if (parameter.distribution.type === DistributionType.Constant) {
			const { min, max } = calculateUncertaintyRange(parameter.distribution.parameters.value, 10);
			low = min;
			high = max;
		} else if (parameter.distribution.type === DistributionType.Uniform) {
			low = parameter.distribution.parameters.minimum;
			high = parameter.distribution.parameters.maximum;
		}

		this.parameters[index] = { id: parameter.referenceId, low, high };
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
			templateId: HorizonScanningScenario.templateId,
			workflowName: this.workflowName,
			modelSpec: this.modelSpec,
			modelConfigSpec: this.modelConfigSpec,
			simulateSpec: this.simulateSpec,
			interventionSpecs: this.interventionSpecs
		};
	}

	isValid(): boolean {
		return (
			!!this.workflowName &&
			!!this.modelSpec.id &&
			!!this.modelConfigSpec.id &&
			!this.parameters.some((parameter) => !parameter) &&
			!_.isEmpty(this.simulateSpec.ids)
		);
	}

	async createWorkflow() {
		const wf = new workflowService.WorkflowWrapper();
		wf.setWorkflowName(this.workflowName);
		wf.setWorkflowScenario(this.toJSON());

		const fetchedInterventionPolicies: InterventionPolicy[] = [];

		// 1. Add model and compare dataset nodes
		const modelNode = wf.addNode(
			ModelOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		wf.updateNode(modelNode, {
			state: {
				modelId: this.modelSpec.id
			},
			output: {
				value: [this.modelSpec.id]
			}
		});

		const modelConfig = await getModelConfigurationById(this.modelConfigSpec.id);

		const baseModelConfigNode = wf.addNode(
			ModelConfigOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		const baseSimulateNode = wf.addNode(
			SimulateCiemssOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		wf.updateNode(baseModelConfigNode, {
			state: {
				transientModelConfig: modelConfig
			},
			output: {
				value: [modelConfig.id],
				state: _.omit(baseModelConfigNode.state, ['transientModelConfig'])
			}
		});

		const compareDatasetNode = wf.addNode(
			CompareDatasetOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		wf.addEdge(modelNode.id, modelNode.outputs[0].id, baseModelConfigNode.id, baseModelConfigNode.inputs[0].id, [
			{ x: 0, y: 0 },
			{ x: 0, y: 0 }
		]);

		wf.addEdge(
			baseModelConfigNode.id,
			baseModelConfigNode.outputs[0].id,
			baseSimulateNode.id,
			baseSimulateNode.inputs[0].id,
			[
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]
		);

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

		// add input ports for each simulation to the dataset transformer, this will be a matrix of intervention x parameter low and high
		const filledInterventionSpecs = this.interventionSpecs.filter((spec) => !!spec.id);
		for (let i = 0; i < (filledInterventionSpecs.length + 1) * 2 ** this.parameters.length; i++) {
			workflowService.appendInputPort(compareDatasetNode, {
				type: 'datasetId|simulationId',
				label: 'Dataset or Simulation'
			});
		}

		let compareDatasetIndex = 1;

		// chart settings for simulate node
		let simulateChartSettings: ChartSetting[] = [];
		simulateChartSettings = updateChartSettingsBySelectedVariables(
			simulateChartSettings,
			ChartSettingType.VARIABLE,
			this.simulateSpec.ids
		);

		wf.updateNode(baseSimulateNode, {
			state: {
				chartSettings: simulateChartSettings,
				...this.getDefaultForecastSettings()
			}
		});

		let compareDatasetChartSettings: ChartSetting[] = [];
		compareDatasetChartSettings = updateChartSettingsBySelectedVariables(
			compareDatasetChartSettings,
			ChartSettingType.VARIABLE,
			this.simulateSpec.ids
		);

		wf.updateNode(compareDatasetNode, {
			state: {
				chartSettings: compareDatasetChartSettings
			}
		});

		// Generate Cartesian product of parameter extrema
		const parameterExtrema = this.parameters.map((parameter) => [
			{ id: parameter!.id, label: 'Low', value: parameter!.low },
			{ id: parameter!.id, label: 'High', value: parameter!.high }
		]);

		const cartesianConfigs = cartesianProduct(parameterExtrema);
		// Create model configurations based on Cartesian product
		const modelConfigPromises = cartesianConfigs.map(async (config) => {
			const clonedModelConfig = _.cloneDeep(modelConfig);

			config.forEach((param) => {
				const foundParameter = getParameter(clonedModelConfig, param.id);
				if (foundParameter) {
					foundParameter.distribution.type = DistributionType.Constant;
					foundParameter.distribution.parameters = { value: param.value };
				}
			});

			clonedModelConfig.name = config.map((param) => `${param.id}${param.label}`).join('_');
			clonedModelConfig.description = `This is a configuration created from "${modelConfig.name}" with extreme values for the parameters: ${config.map((param) => `${param.id}: ${param.value}`).join(', ')} using the horizon scanning scenario template.`;

			const newModelConfig = await createModelConfiguration(clonedModelConfig);
			await useProjects().addAsset(
				AssetType.ModelConfiguration,
				newModelConfig.id,
				useProjects().activeProject.value?.id
			);

			const modelConfigNode = wf.addNode(
				ModelConfigOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);

			wf.addEdge(modelNode.id, modelNode.outputs[0].id, modelConfigNode.id, modelConfigNode.inputs[0].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);

			wf.updateNode(modelConfigNode, {
				state: {
					transientModelConfig: newModelConfig
				},
				output: {
					value: [newModelConfig.id],
					state: _.omit(modelConfigNode.state, ['transientModelConfig'])
				}
			});

			return modelConfigNode;
		});

		// Wait for all modelConfigPromises to resolve and flatten them
		const modelConfigNodes = (await Promise.all(modelConfigPromises)).flat();

		// 3. Add intervention nodes for each intervention and attach them to the model node
		// filter out any interventions that don't have an id
		const interventionPromises = this.interventionSpecs
			.filter((spec) => !!spec.id)
			.map(async (interventionSpec) => {
				let interventionPolicy: InterventionPolicy | null = await getInterventionPolicyById(interventionSpec.id);

				if (!interventionPolicy) {
					// create new intervention if in the new policy list
					interventionPolicy = await createInterventionPolicy(
						{
							name:
								this.newInterventionSpecs.find((newInterventionSpec) => newInterventionSpec.id === interventionSpec.id)
									?.name ?? 'New policy',
							description: 'This intervention policy was created using the horizon scanning scenario template.',
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

				// Add to list of intervention policies
				fetchedInterventionPolicies.push(interventionPolicy!);

				const interventionNode = wf.addNode(
					InterventionOp,
					{ x: 0, y: 0 },
					{
						size: OperatorNodeSize.medium
					}
				);

				wf.addEdge(modelNode.id, modelNode.outputs[0].id, interventionNode.id, interventionNode.inputs[0].id, [
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

				return interventionNode;
			});

		// Wait for all interventionPromises to resolve
		const interventionNodes = await Promise.all(interventionPromises);

		// 4. Add simulate nodes for each model config and intervention and attach them to the model config and intervention nodes
		// each intervention node will be connected to a simulate node along with each model config node
		modelConfigNodes.forEach((modelConfigNode) => {
			// For each model config node, we want to connect it to a simulate node, even if interventions aren't present
			const simulateNode = wf.addNode(
				SimulateCiemssOp,
				{ x: 0, y: 0 },
				{
					size: OperatorNodeSize.medium
				}
			);

			wf.updateNode(simulateNode, {
				state: {
					chartSettings: simulateChartSettings,
					...this.getDefaultForecastSettings()
				}
			});

			wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, simulateNode.id, simulateNode.inputs[0].id, [
				{ x: 0, y: 0 },
				{ x: 0, y: 0 }
			]);

			interventionNodes.forEach((interventionNode) => {
				const interventionPolicy = fetchedInterventionPolicies.find(
					(policy) => policy.id === interventionNode.outputs[0].value?.[0]
				);
				let chartSettingsClone = _.cloneDeep(simulateChartSettings);
				// apply intervention chart settings if the intervention policy is not blank
				if (!isInterventionPolicyBlank(interventionPolicy!)) {
					chartSettingsClone = updateChartSettingsBySelectedVariables(
						chartSettingsClone,
						ChartSettingType.INTERVENTION,
						Object.keys(_.groupBy(flattenInterventionData(interventionPolicy?.interventions ?? []), 'appliedTo'))
					);
				}
				const additionalSimNode = wf.addNode(
					SimulateCiemssOp,
					{ x: 0, y: 0 },
					{
						size: OperatorNodeSize.medium
					}
				);

				wf.updateNode(additionalSimNode, {
					state: {
						chartSettings: chartSettingsClone,
						...this.getDefaultForecastSettings()
					}
				});

				wf.addEdge(
					modelConfigNode.id,
					modelConfigNode.outputs[0].id,
					additionalSimNode.id,
					additionalSimNode.inputs[0].id,
					[
						{ x: 0, y: 0 },
						{ x: 0, y: 0 }
					]
				);
				wf.addEdge(
					interventionNode.id,
					interventionNode.outputs[0].id,
					additionalSimNode.id,
					additionalSimNode.inputs[1].id,
					[
						{ x: 0, y: 0 },
						{ x: 0, y: 0 }
					]
				);
				wf.addEdge(
					additionalSimNode.id,
					additionalSimNode.outputs[0].id,
					compareDatasetNode.id,
					compareDatasetNode.inputs[compareDatasetIndex].id,
					[
						{ x: 0, y: 0 },
						{ x: 0, y: 0 }
					]
				);
				compareDatasetIndex++;
			});

			wf.addEdge(
				simulateNode.id,
				simulateNode.outputs[0].id,
				compareDatasetNode.id,
				compareDatasetNode.inputs[compareDatasetIndex].id,
				[
					{ x: 0, y: 0 },
					{ x: 0, y: 0 }
				]
			);
			compareDatasetIndex++;
		});

		// Run simulations automatically if indicated
		if (this.simulateSpec.runSimulationsAutomatically) {
			await runSimulations(wf, this.getDefaultForecastSettings(), fetchedInterventionPolicies);
		}

		// 4. Run layout
		// The schematic for horizon-scanning is as follows
		//
		//  Model ->                 Intervention1, Intervention2, Intervention3
		//           ModelConfig 1
		//           ModelConfig 2                Forecasts-Grid                  CompareDataset
		//           ModelConfig 3
		//
		//
		const nodeGapHorizontal = 400;
		const nodeGapVertical = 400;
		modelNode.x = 100;
		modelNode.y = 500;

		// Build the XY axis
		interventionNodes.forEach((interventionNode, idx) => {
			interventionNode.x = modelNode.x + nodeGapHorizontal * (idx + 3);
			interventionNode.y = modelNode.y - 50;
		});

		const configNodes = wf.getNodes().filter((node) => node.operationType === ModelConfigOp.name);
		configNodes.forEach((modelConfigNode, idx) => {
			modelConfigNode.x = modelNode.x + nodeGapHorizontal;
			modelConfigNode.y = modelNode.y + nodeGapVertical * (idx + 1);
		});

		// Layout forecast
		const forecastNodes = wf.getNodes().filter((node) => node.operationType === SimulateCiemssOp.name);
		forecastNodes.forEach((node) => {
			const neighbors = wf.getNeighborNodes(node.id);
			const upstreamNodes = neighbors.upstreamNodes;

			node.x = modelNode.x + nodeGapHorizontal * 2; // Default
			upstreamNodes.forEach((upstreamNode) => {
				// Y
				if (upstreamNode.operationType === ModelConfigOp.name) {
					node.y = upstreamNode.y;
				}

				// X
				if (upstreamNode.operationType === InterventionOp.name) {
					node.x = upstreamNode.x;
				}
			});
		});

		// Data comparison
		if (interventionNodes.length > 0) {
			compareDatasetNode.x = modelNode.x + (3 + interventionNodes.length) * nodeGapHorizontal;
		} else {
			compareDatasetNode.x = modelNode.x + 3 * nodeGapHorizontal;
		}
		compareDatasetNode.y = 500;

		return wf.dump();
	}
}
