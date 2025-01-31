<template>
	<tera-drilldown
		:node="node"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<tera-drilldown-section>
			<tera-dataset v-if="node.state.datasetId" :asset-id="node.state.datasetId" />
		</tera-drilldown-section>
	</tera-drilldown>
</template>

<script setup lang="ts">
// Proxy to use tera-dataset via a workflow context

import { WorkflowNode } from '@/types/workflow';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import type { CsvAsset, Dataset } from '@/types/Types';
import { onMounted, ref } from 'vue';
import { downloadRawFile, getDataset } from '@/services/dataset';
import { enrichDataset } from '@/components/dataset/utils';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import TeraDataset from '@/components/dataset/tera-dataset.vue';
import { DatasetOperationState } from './dataset-operation';

const dataset = ref<Dataset | null>(null);
const rawContent = ref<CsvAsset | null>(null);
const fetchingDataset = ref(false);
const props = defineProps<{
	node: WorkflowNode<DatasetOperationState>;
}>();
const emit = defineEmits(['close', 'update-state']);

onMounted(async () => {
	fetchingDataset.value = true;
	await fetchDataset();
	fetchingDataset.value = false;
});

const fetchDataset = async () => {
	if (!props.node.state?.datasetId) return;
	const datasetTemp: Dataset | null = await getDataset(props.node.state.datasetId);
	// We are assuming here there is only a single csv file. This may change in the future as the API allows for it.
	rawContent.value = await downloadRawFile(props.node.state.datasetId, datasetTemp?.fileNames?.[0] ?? '');
	if (datasetTemp) {
		dataset.value = enrichDataset(datasetTemp);
	}
};
</script>
