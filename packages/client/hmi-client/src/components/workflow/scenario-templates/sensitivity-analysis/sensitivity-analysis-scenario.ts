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
import { ChartSetting, ChartSettingType } from '@/types/common';
import { updateChartSettingsBySelectedVariables } from '@/services/chart-settings';
import { AssetType, ParameterSemantic } from '@/types/Types';
import { DistributionType } from '@/services/distribution';
import { calculateUncertaintyRange } from '@/utils/math';
import { useProjects } from '@/composables/project';

export class SensitivityAnalysisScenario extends BaseScenario {
	public static templateId = 'sensitivity-analysis';

	public static templateName = 'Sensitivity Analysis';

	modelSpec: { id: string };

	modelConfigSpec: { id: string };

	parameters: (ParameterSemantic | null)[];

	simulateSpec: { ids: string[] };

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
			ids: []
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
		// convert constants to distributions
		if (parameter.distribution.type === DistributionType.Constant) {
			parameter.distribution.type = DistributionType.Uniform;
			// +10% and -10% of the constant value
			const { min, max } = calculateUncertaintyRange(parameter.distribution.parameters.value, 10);
			parameter.distribution.parameters = { maximum: max, minimum: min };
		}

		this.parameters[index] = parameter;
	}

	setModelConfigSpec(id: string) {
		this.modelConfigSpec.id = id;
	}

	setCalibrateSpec(ids: string[]) {
		this.simulateSpec.ids = ids;
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

		modelConfig.name = `${modelConfig.name}_sensitivity`;
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

		simulateChartSettings = updateChartSettingsBySelectedVariables(
			simulateChartSettings,
			ChartSettingType.SENSITIVITY,
			this.simulateSpec.ids
		);

		wf.updateNode(simulateNode, {
			state: {
				chartSettings: simulateChartSettings
			}
		});

		// 4. Run layout
		wf.runDagreLayout();

		return wf.dump();
	}
}
