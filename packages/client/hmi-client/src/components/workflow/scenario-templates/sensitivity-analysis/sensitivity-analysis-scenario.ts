import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import * as workflowService from '@/services/workflow';
import { operation as ModelOp } from '@/components/workflow/ops/model/mod';
import { operation as ModelConfigOp } from '@/components/workflow/ops/model-config/mod';
import { operation as SimulateCiemssOp } from '@/components/workflow/ops/simulate-ciemss/mod';
import { OperatorNodeSize } from '@/services/workflow';
import {
	createModelConfiguration,
	getModelConfigurationById,
	setParameterDistributions
} from '@/services/model-configurations';
import _ from 'lodash';
import { ChartSetting, ChartSettingSensitivity, ChartSettingType, CiemssPresetTypes } from '@/types/common';
import { updateChartSettingsBySelectedVariables, updateSensitivityChartSettingOption } from '@/services/chart-settings';
import { AssetType, ParameterSemantic } from '@/types/Types';
import { useProjects } from '@/composables/project';
import { createDefaultForecastSettings, runSimulations, switchToUniformDistribution } from '../scenario-template-utils';

export class SensitivityAnalysisScenario extends BaseScenario {
	public static templateId = 'sensitivity-analysis';

	public static templateName = 'Sensitivity analysis';

	modelSpec: { id: string };

	modelConfigSpec: { id: string };

	parameters: (ParameterSemantic | null)[];

	simulateSpec: { ids: string[]; endTime: number; preset: CiemssPresetTypes; runSimulationsAutomatically: boolean };

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
		this.parameters = [null];
	}

	setModelSpec(id: string) {
		this.modelSpec.id = id;
		this.modelConfigSpec.id = '';
		this.simulateSpec.ids = [];
		this.parameters = [null];
	}

	addParameter() {
		this.parameters.push(null);
	}

	removeParameter(index: number) {
		this.parameters.splice(index, 1);
	}

	setParameter(parameter: ParameterSemantic, index: number) {
		switchToUniformDistribution(parameter);
		this.parameters[index] = parameter;
	}

	setModelConfigSpec(id: string) {
		this.modelConfigSpec.id = id;
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
			templateId: SensitivityAnalysisScenario.templateId,
			workflowName: this.workflowName,
			modelSpec: this.modelSpec,
			modelConfigSpec: this.modelConfigSpec,
			simulateSpec: this.simulateSpec
		};
	}

	isValid(): boolean {
		return !!this.workflowName && !!this.modelSpec.id && !!this.modelConfigSpec.id && !_.isEmpty(this.simulateSpec.ids);
	}

	async createWorkflow() {
		const wf = new workflowService.WorkflowWrapper();
		wf.setWorkflowName(this.workflowName);
		wf.setWorkflowScenario(this.toJSON());

		// 1. Add nodes
		const modelNode = wf.addNode(
			ModelOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		const modelConfig = await getModelConfigurationById(this.modelConfigSpec.id);

		const distributionParameterMappings = this.parameters
			.filter((parameter) => parameter !== null)
			.map((parameter) => ({
				id: parameter.referenceId,
				distribution: parameter.distribution
			}));

		const name = modelConfig.name;
		modelConfig.name = `${name}_sensitivity`;
		modelConfig.description = `This is a configuration created from "${name}" using the sensitivity analysis scenario template.`;
		setParameterDistributions(modelConfig, distributionParameterMappings);

		const newModelConfig = await createModelConfiguration(modelConfig);
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
		const simulateNode = wf.addNode(
			SimulateCiemssOp,
			{ x: 0, y: 0 },
			{
				size: OperatorNodeSize.medium
			}
		);

		// 2. Add edges
		wf.addEdge(modelNode.id, modelNode.outputs[0].id, modelConfigNode.id, modelConfigNode.inputs[0].id, [
			{ x: 0, y: 0 },
			{ x: 0, y: 0 }
		]);
		wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, simulateNode.id, simulateNode.inputs[0].id, [
			{ x: 0, y: 0 },
			{ x: 0, y: 0 }
		]);

		// 3. Setting node states/outputs
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
				transientModelConfig: newModelConfig
			},
			output: {
				value: [newModelConfig.id],
				state: _.omit(modelConfigNode.state, ['transientModelConfig'])
			}
		});

		let simulateChartSettings: ChartSetting[] = [];
		simulateChartSettings = updateChartSettingsBySelectedVariables(
			simulateChartSettings,
			ChartSettingType.VARIABLE,
			this.simulateSpec.ids
		);

		simulateChartSettings = updateSensitivityChartSettingOption(simulateChartSettings as ChartSettingSensitivity[], {
			selectedVariables: this.simulateSpec.ids,
			selectedInputVariables: this.parameters.map((parameter) => parameter!.referenceId),
			timepoint: this.simulateSpec.endTime
		});

		wf.updateNode(simulateNode, {
			state: {
				chartSettings: simulateChartSettings,
				...this.getDefaultForecastSettings()
			}
		});

		if (this.simulateSpec.runSimulationsAutomatically) {
			await runSimulations(wf, this.getDefaultForecastSettings());
		}
		// 4. Run layout
		wf.runDagreLayout();

		return wf.dump();
	}
}
