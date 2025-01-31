<template>
	<div class="strata-group">
		<div class="input-row">
			<label>Name of strata</label>
			<tera-input-text
				v-model="strataName"
				placeholder="e.g., Age group"
				@focusout="emit('update-self', updatedConfig)"
				class="common-input-height"
			/>
		</div>
		<div class="input-row">
			<label>Select variables and parameters to stratify</label>
			<MultiSelect
				v-model="selectedVariables"
				:options="props.modelNodeOptions"
				placeholder="Click to select"
				display="chip"
				@update:model-value="emit('update-self', updatedConfig)"
				class="common-input-height"
			/>
		</div>
		<div class="flex flex-column gap-2 mb-4 w-full">
			<label>Enter a comma separated list of labels for each group.</label>
			<Textarea
				v-model="labels"
				rows="1"
				placeholder="e.g., Young, Old"
				@focusout="emit('update-self', updatedConfig)"
				:autoResize="true"
				class="w-full"
				style="min-height: 2.35rem"
			/>
		</div>
		<div class="input-row">
			<div class="flex align-items-center gap-2">
				<Checkbox @change="emit('update-self', updatedConfig)" v-model="useStructure" binary />
				<label>Create new transitions between strata</label>
			</div>
			<div class="flex align-items-center gap-2">
				<Checkbox @change="emit('update-self', updatedConfig)" v-model="cartesianProduct" binary />
				<label>Allow existing interactions to involve multiple strata</label>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import Textarea from 'primevue/textarea';
import MultiSelect from 'primevue/multiselect';
import Checkbox from 'primevue/checkbox';
import { StratifyGroup } from '@/components/workflow/ops/stratify-mira/stratify-mira-operation';

const props = defineProps<{
	modelNodeOptions: string[];
	config: StratifyGroup;
}>();

const emit = defineEmits(['update-self']);

const strataName = ref(props.config.name);
const selectedVariables = ref<string[]>(props.config.selectedVariables);
const labels = ref(props.config.groupLabels);
const cartesianProduct = ref<boolean>(props.config.cartesianProduct);
const directed = ref<boolean>(props.config.directed); // Currently not used, assume to be true
const structure = ref<any>(props.config.structure); // Proxied by "useStructure"
const useStructure = ref<any>(props.config.useStructure);

const updatedConfig = computed<StratifyGroup>(() => ({
	borderColour: props.config.borderColour,
	name: strataName.value,
	selectedVariables: selectedVariables.value,
	groupLabels: labels.value,
	cartesianProduct: cartesianProduct.value,
	directed: directed.value,
	structure: structure.value,
	useStructure: useStructure.value
}));

watch(
	() => props.config,
	() => {
		strataName.value = props.config.name;
		selectedVariables.value = props.config.selectedVariables;
		labels.value = props.config.groupLabels;
		cartesianProduct.value = props.config.cartesianProduct;
		structure.value = props.config.structure;
		useStructure.value = props.config.useStructure;
	}
);
</script>

<style scoped>
.strata-group {
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: flex-start;
	gap: 0.5rem;
}

.sub-header {
	display: flex;
	padding-bottom: 0px;
	justify-content: flex-end;
	align-items: center;
	gap: var(--gap-4);
	align-self: stretch;
}

.subdued-text {
	color: var(--text-color-subdued);
}

.input-row {
	width: 100%;
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	margin-bottom: var(--gap-4);

	& > * {
		flex: 1;
	}
}

.label-and-input {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	width: 0; /* CSS is weird but for some reason this prevents the Multiselect from going nuts */
}

/* make all the inputs on this page the same height */
.common-input-height:deep(main) {
	height: 2.35rem;
}
</style>
