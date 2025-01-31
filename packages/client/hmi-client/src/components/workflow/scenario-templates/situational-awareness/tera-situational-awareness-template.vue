<template>
	<tera-scenario-template :header="header" :scenario-instance="scenario" @save-workflow="emit('save-workflow')">
		<template #inputs>
			<label>Select a model</label>
			<Dropdown
				:model-value="scenario.modelSpec.id"
				:options="models"
				option-label="assetName"
				option-value="assetId"
				placeholder="Select a model"
				@update:model-value="scenario.setModelSpec($event)"
				class="mb-3"
			/>

			<!-- TODO: adding intervention policies -->
			<!-- <label>Select an intervention policy (historical)</label>
			<Dropdown
			:model-value="scenario.historicalInterventionSpec.id"
			placeholder="Optional"
			:options="interventionPolicies"
			option-label="name"
			option-value="id"
			@update:model-value="scenario.setHistoricalInterventionSpec($event)"
			:disabled="isEmpty(interventionPolicies) || isFetchingModelInformation" />

			<label>Select an intervention policy (known future)</label>
			<Dropdown
			:model-value="scenario.futureInterventionSpec.id"
			placeholder="Optional"
			:options="interventionPolicies"
			option-label="name"
			option-value="id"
			@update:model-value="scenario.setFutureInterventionSpec($event)"
			:disabled="isEmpty(interventionPolicies) || isFetchingModelInformation" /> -->

			<label :class="{ 'disabled-label': isEmpty(sortedConfigurations) || isFetchingModelInformation }"
				>Select configuration representing best and generous estimates of the initial conditions</label
			>
			<Dropdown
				:model-value="scenario.modelConfigSpec.id"
				placeholder="Select a configuration"
				:options="sortedConfigurations"
				option-label="name"
				option-value="id"
				@update:model-value="scenario.setModelConfigSpec($event)"
				:disabled="isEmpty(sortedConfigurations) || isFetchingModelInformation"
				:loading="isFetchingModelInformation"
				class="mb-3"
			>
				<template #option="slotProps">
					<p>
						{{ slotProps.option.name }} <span class="subtext">({{ formatTimestamp(slotProps.option.createdOn) }})</span>
					</p>
				</template>
			</Dropdown>

			<label>Select a dataset</label>
			<Dropdown
				:model-value="scenario.datasetSpec.id"
				:options="datasets"
				option-label="assetName"
				option-value="assetId"
				placeholder="Select a dataset"
				@update:model-value="scenario.setDatasetSpec($event)"
			/>
		</template>

		<template #outputs>
			<label :class="{ 'disabled-label': isEmpty(modelStateOptions) || isFetchingModelInformation }"
				>Select an output metric</label
			>
			<MultiSelect
				:disabled="isEmpty(modelStateOptions) || isFetchingModelInformation"
				:model-value="scenario.calibrateSpec.ids"
				placeholder="Select output metrics"
				option-label="id"
				option-value="id"
				:options="modelStateOptions"
				@update:model-value="scenario.setCalibrateSpec($event)"
				filter
				:loading="isFetchingModelInformation"
			/>
			<img :src="calibrate" alt="Calibrate chart" class="mt-3" />
		</template>
	</tera-scenario-template>
</template>

<script setup lang="ts">
import Dropdown from 'primevue/dropdown';
import { computed, ref, watch } from 'vue';
import { useProjects } from '@/composables/project';
import { AssetType, ModelConfiguration } from '@/types/Types';
import { getModel, getModelConfigurationsForModel } from '@/services/model';
import { isEmpty } from 'lodash';
import MultiSelect from 'primevue/multiselect';
import calibrate from '@/assets/svg/template-images/calibration-thumbnail.svg';
import { sortDatesDesc, formatTimestamp } from '@/utils/date';
import { SituationalAwarenessScenario } from './situational-awareness-scenario';
import TeraScenarioTemplate from '../tera-scenario-template.vue';
import { ScenarioHeader } from '../base-scenario';

const header: ScenarioHeader = Object.freeze({
	title: 'Situational awareness template',
	question: "What's likely to happen next?",
	description:
		'Calibrates the model to historical data to obtain the best estimate of parameters for the present, then forecasts into the near future.',
	examples: [
		'Anticipate the arrival of new variants.',
		'Evaluate the potential impact of growing vaccine hesitancy and declining NPIs.'
	]
});

const isFetchingModelInformation = ref(false);
const models = computed(() => useProjects().getActiveProjectAssets(AssetType.Model));
const datasets = computed(() => useProjects().getActiveProjectAssets(AssetType.Dataset));

const modelConfigurations = ref<ModelConfiguration[]>([]);
const modelStateOptions = ref<any[]>([]);

const props = defineProps<{
	scenario: SituationalAwarenessScenario;
}>();

const emit = defineEmits(['save-workflow']);

const sortedConfigurations = computed(() =>
	[...modelConfigurations.value].sort((a, b) => sortDatesDesc(a.createdOn, b.createdOn))
);

watch(
	() => props.scenario.modelSpec.id,
	async (modelId) => {
		if (!modelId) return;
		isFetchingModelInformation.value = true;
		const model = await getModel(modelId);
		if (!model) return;
		modelConfigurations.value = await getModelConfigurationsForModel(modelId);
		// TODO: adding intervention policies
		// interventionPolicies.value = await getInterventionPoliciesForModel(modelId);

		// Set the first model configuration as the default
		if (!isEmpty(modelConfigurations.value)) {
			props.scenario.setModelConfigSpec(modelConfigurations.value[0].id!);
		}

		const modelOptions: any[] = model.model.states;

		model.semantics?.ode.observables?.forEach((o) => {
			modelOptions.push(o);
		});
		modelStateOptions.value = modelOptions;
		isFetchingModelInformation.value = false;
	},
	{ immediate: true }
);
</script>
<style scoped>
.disabled-label {
	color: var(--text-color-disabled);
}
</style>
