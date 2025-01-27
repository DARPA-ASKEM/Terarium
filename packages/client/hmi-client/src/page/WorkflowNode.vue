<template>
	{{ activeProjectId }}
	<template v-if="node">
		<tera-model-workflow-wrapper v-if="isNodeOfOperationType(OperationType.MODEL)" :node="node" />
		<tera-stratify-mira v-else-if="isNodeOfOperationType(OperationType.STRATIFY_MIRA)" :node="node" />
		<tera-dataset-transformer v-else-if="isNodeOfOperationType(OperationType.DATASET_TRANSFORMER)" :node="node" />
		<tera-dataset-drilldown v-else-if="isNodeOfOperationType(OperationType.DATASET)" :node="node" />
		<tera-regridding-drilldown v-else-if="isNodeOfOperationType(OperationType.REGRIDDING)" :node="node" />
		<tera-calibrate-ciemss-drilldown v-else-if="isNodeOfOperationType(OperationType.CALIBRATION_CIEMSS)" :node="node" />
		<tera-calibrate-ensemble-ciemss-drilldown
			v-else-if="isNodeOfOperationType(OperationType.CALIBRATE_ENSEMBLE_CIEMSS)"
			:node="node"
		/>
		<tera-simulate-ciemss-drilldown v-else-if="isNodeOfOperationType(OperationType.SIMULATE_CIEMSS)" :node="node" />
		<tera-simulate-ensemble-ciemss-drilldown
			v-else-if="isNodeOfOperationType(OperationType.SIMULATE_ENSEMBLE_CIEMSS)"
			:node="node"
		/>
		<tera-funman v-else-if="isNodeOfOperationType(OperationType.FUNMAN)" :node="node" />
		<tera-code-asset-wrapper v-else-if="isNodeOfOperationType(OperationType.CODE)" :node="node" />
		<template v-else>
			<p>This OperationType is not know!</p>
			<pre>{{ node }}</pre>
		</template>
	</template>
	<p v-else>Loading...</p>
</template>

<script setup lang="ts">
import { Project } from '@/types/Types';
import { watch, ref } from 'vue';
import { Workflow, WorkflowNode, WorkflowOperationTypes as OperationType } from '@/types/workflow';
import * as workflowService from '@/services/workflow';
import TeraModelWorkflowWrapper from '@/components/workflow/ops/model/tera-model-drilldown.vue';
import TeraDatasetDrilldown from '@/components/workflow/ops/dataset/tera-dataset-drilldown.vue';
import TeraRegriddingDrilldown from '@/components/workflow/ops/regridding/tera-regridding.vue';
import TeraDatasetTransformer from '@/components/workflow/ops/dataset-transformer/tera-dataset-transformer.vue';
import TeraCalibrateCiemssDrilldown from '@/components/workflow/ops/calibrate-ciemss/tera-calibrate-ciemss-drilldown.vue';
import TeraCalibrateEnsembleCiemssDrilldown from '@/components/workflow/ops/calibrate-ensemble-ciemss/tera-calibrate-ensemble-ciemss-drilldown.vue';
import TeraSimulateCiemssDrilldown from '@/components/workflow/ops/simulate-ciemss/tera-simulate-ciemss-drilldown.vue';
import TeraSimulateEnsembleCiemssDrilldown from '@/components/workflow/ops/simulate-ensemble-ciemss/tera-simulate-ensemble-ciemss-drilldown.vue';
import TeraFunman from '@/components/workflow/ops/funman/tera-funman-drilldown.vue';
import TeraStratifyMira from '@/components/workflow/ops/stratify-mira/tera-stratify-drilldown.vue';
import TeraCodeAssetWrapper from '@/components/workflow/ops/code-asset/tera-code-asset-wrapper.vue';
import { activeProjectId } from '@/composables/activeProject';

const props = defineProps<{
	nodeId: WorkflowNode<any>['id'];
	projectId: Project['id'];
	workflowId: Workflow['id'];
}>();

const node = ref<WorkflowNode<any>>();

function isNodeOfOperationType(type: string): boolean {
	return node.value?.operationType === type;
}

async function updateNode() {
	const workflow = await workflowService.getWorkflow(props.workflowId);
	const foundNode = workflow.nodes.find((n) => n.id === props.nodeId);
	if (foundNode) node.value = foundNode;
}

watch(
	() => props.nodeId,
	() => updateNode(),
	{ immediate: true }
);
</script>
