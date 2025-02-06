<template>
	<main>
		<template v-if="model">
			<tera-operator-title>{{ model.header.name }}</tera-operator-title>
			<SelectButton
				class="p-button-xsm"
				:model-value="view"
				@change="if ($event.value) view = $event.value;"
				:options="viewOptions"
			/>
			<div class="container" v-if="model">
				<tera-model-diagram
					v-if="view === ModelNodeView.Diagram"
					:model="model"
					:key="model.id"
					:feature-config="{ isPreview: true }"
				/>
				<tera-model-equation v-else-if="view === ModelNodeView.Equation" :model="model" :is-editable="false" />
			</div>
			<Button label="Open" @click="emit('open-drilldown')" severity="secondary" outlined />
		</template>
		<template v-else>
			<Dropdown
				class="w-full p-dropdown-sm"
				:options="models"
				option-label="assetName"
				placeholder="Select a model"
				@update:model-value="onModelChange"
			/>
			<tera-operator-placeholder :node="node" />
		</template>
	</main>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { onMounted, ref, computed, watch } from 'vue';
import { getModel } from '@/services/model';
import { canPropagateResource } from '@/services/workflow';
import Dropdown from 'primevue/dropdown';
import SelectButton from 'primevue/selectbutton';
import Button from 'primevue/button';
import { AssetType } from '@/types/Types';
import type { Model, ProjectAsset } from '@/types/Types';
import TeraModelDiagram from '@/components/model/petrinet/tera-model-diagram.vue';
import TeraModelEquation from '@/components/model/petrinet/tera-model-equation.vue';
import { WorkflowNode } from '@/types/workflow';
import TeraOperatorTitle from '@/components/operator/tera-operator-title.vue';
import { useProjects } from '@/composables/project';
import TeraOperatorPlaceholder from '@/components/operator/tera-operator-placeholder.vue';
import { ModelOperationState } from './model-operation';

const props = defineProps<{
	node: WorkflowNode<ModelOperationState>;
}>();

const emit = defineEmits(['append-output', 'open-drilldown']);
const models = computed(() => useProjects().getActiveProjectAssets(AssetType.Model));

enum ModelNodeView {
	Diagram = 'Diagram',
	Equation = 'Equation'
}

const model = ref<Model | null>();
const view = ref(ModelNodeView.Diagram);
const viewOptions = ref([ModelNodeView.Diagram, ModelNodeView.Equation]);

async function getModelById(modelId: string) {
	model.value = await getModel(modelId);

	if (model.value && model.value.id) {
		const outputs = props.node.outputs;
		if (canPropagateResource(outputs)) {
			const state = _.cloneDeep(props.node.state);
			state.modelId = model.value?.id;

			emit(
				'append-output',
				{
					type: 'modelId',
					label: model.value.header.name,
					value: [model.value.id]
				},
				state
			);
		}
	}
}

async function onModelChange(chosenProjectModel: ProjectAsset) {
	await getModelById(chosenProjectModel.assetId);
}

onMounted(async () => {
	const state = props.node.state;
	if (state.modelId) {
		await getModelById(state.modelId);
	}
});

watch(
	() => props.node.active,
	async () => {
		const newModelId = props.node.outputs.find((d) => d.id === props.node.active)?.value?.[0];
		if (newModelId) {
			model.value = await getModel(newModelId);
		}
	}
);
</script>

<style scoped>
main {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
}

.p-selectbutton {
	width: 100%;
}

.p-selectbutton:deep(.p-button) {
	flex-grow: 1;
}

.container {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	overflow: hidden;
}
</style>
