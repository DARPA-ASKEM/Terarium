import { cloneDeep } from 'lodash';
import { WorkflowNode } from '@/types/workflow';

export interface NotebookHistory {
	code: string;
	timestamp: number;
}

// A common pattern used to save code from a notebook within an operator
// This is ready to be ported to nodes such as tera-model-edit, tera-model-config and tera-stratify-mira
// I just don't know if we are okay with ruining the states of the nodes that already exist with these differently named properties
export const saveCodeToState = (node: WorkflowNode, code: string, hasCodeRun: boolean) => {
	const state = cloneDeep(node.state);
	if (!state.notebookHistory || !state.hasCodeRun) return state;

	state.hasCodeRun = hasCodeRun;
	// for now only save the last code executed, may want to save all code executed in the future
	const notebookHistoryLength = node.state.notebookHistory.length;
	const timestamp = Date.now();
	if (notebookHistoryLength > 0) {
		state.notebookHistory[0] = { code, timestamp };
	} else {
		state.notebookHistory.push({ code, timestamp });
	}
	return state;
};
