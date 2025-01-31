import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import * as workflowService from '@/services/workflow';
import { operation as ModelOp } from '@/components/workflow/ops/model/mod';
import { operation as ModelConfigOp } from '@/components/workflow/ops/model-config/mod';
import { operation as CalibrateCiemssOp } from '@/components/workflow/ops/calibrate-ciemss/mod';
import { operation as DatasetOp } from '@/components/workflow/ops/dataset/mod';
import { OperatorNodeSize } from '@/services/workflow';
import { getModelConfigurationById } from '@/services/model-configurations';
import _ from 'lodash';
import { ChartSetting, ChartSettingType } from '@/types/common';
import { updateChartSettingsBySelectedVariables } from '@/services/chart-settings';

export class SituationalAwarenessScenario extends BaseScenario {
	public static templateId = 'situational-awareness';

	public static templateName = 'Situational awareness';

	modelSpec: { id: string };

	datasetSpec: { id: string };

	modelConfigSpec: { id: string };

	calibrateSpec: { ids: string[] };

	historicalInterventionSpec: { id: string };

	futureInterventionSpec: { id: string };

	constructor() {
		super();
		this.workflowName = '';
		this.modelSpec = {
			id: ''
		};
		this.datasetSpec = {
			id: ''
		};
		this.modelConfigSpec = {
			id: ''
		};
		this.calibrateSpec = {
			ids: []
		};
		this.historicalInterventionSpec = {
			id: ''
		};
		this.futureInterventionSpec = {
			id: ''
		};
	}

	setModelSpec(id: string) {
		this.modelSpec.id = id;
		this.modelConfigSpec.id = '';
		this.calibrateSpec.ids = [];
	}

	setDatasetSpec(id: string) {
		this.datasetSpec.id = id;
	}

	setModelConfigSpec(id: string) {
		this.modelConfigSpec.id = id;
	}

	setHistoricalInterventionSpec(id: string) {
		this.historicalInterventionSpec.id = id;
	}

	setFutureInterventionSpec(id: string) {
		this.futureInterventionSpec.id = id;
	}

	setCalibrateSpec(ids: string[]) {
		this.calibrateSpec.ids = ids;
	}

	toJSON() {
		return {
			templateId: SituationalAwarenessScenario.templateId,
			workflowName: this.workflowName,
			modelSpec: this.modelSpec,
			datasetSpec: this.datasetSpec,
			modelConfigSpec: this.modelConfigSpec,
			calibrateSpec: this.calibrateSpec
		};
	}

	isValid(): boolean {
		return (
			!!this.workflowName &&
			!!this.modelSpec.id &&
			!!this.datasetSpec.id &&
			!!this.modelConfigSpec.id &&
			!_.isEmpty(this.calibrateSpec.ids)
		);
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

		const datasetNode = wf.addNode(
			DatasetOp,
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
		const calibrateNode = wf.addNode(
			CalibrateCiemssOp,
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
		wf.addEdge(modelConfigNode.id, modelConfigNode.outputs[0].id, calibrateNode.id, calibrateNode.inputs[0].id, [
			{ x: 0, y: 0 },
			{ x: 0, y: 0 }
		]);
		wf.addEdge(datasetNode.id, datasetNode.outputs[0].id, calibrateNode.id, calibrateNode.inputs[1].id, [
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

		wf.updateNode(datasetNode, {
			state: {
				datasetId: this.datasetSpec.id
			},
			output: {
				value: [this.datasetSpec.id]
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

		let calibrateChartSettings: ChartSetting[] = [];
		calibrateChartSettings = updateChartSettingsBySelectedVariables(
			calibrateChartSettings,
			ChartSettingType.VARIABLE,
			this.calibrateSpec.ids
		);

		wf.updateNode(calibrateNode, {
			state: {
				chartSettings: calibrateChartSettings
			}
		});

		// 4. Run layout
		wf.runDagreLayout();

		return wf.dump();
	}
}
