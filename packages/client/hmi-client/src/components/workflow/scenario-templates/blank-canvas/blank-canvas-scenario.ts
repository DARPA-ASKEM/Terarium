import { BaseScenario } from '@/components/workflow/scenario-templates/base-scenario';
import * as workflowService from '@/services/workflow';

export class BlankCanvasScenario extends BaseScenario {
	public static templateId = 'blank-canvas';

	public static templateName = 'Blank canvas';

	constructor() {
		super();
		this.workflowName = '';
	}

	toJSON() {
		return {
			templateId: BlankCanvasScenario.templateId,
			workflowName: this.workflowName
		};
	}

	async createWorkflow() {
		const wf = new workflowService.WorkflowWrapper();
		wf.setWorkflowName(this.workflowName);
		wf.setWorkflowScenario(this.toJSON());
		return wf.dump();
	}
}
