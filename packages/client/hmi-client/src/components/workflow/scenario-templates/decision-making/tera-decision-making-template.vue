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

			<label :class="{ 'disabled-label': isEmpty(modelConfigurations) || isFetchingModelInformation }"
				>Select configuration representing best starting point pre-interventions</label
			>
			<Dropdown
				class="mb-3"
				:model-value="scenario.modelConfigSpec.id"
				placeholder="Select a configuration"
				:options="sortedConfigurations"
				option-label="name"
				option-value="id"
				@update:model-value="scenario.setModelConfigSpec($event)"
				:disabled="isEmpty(modelConfigurations) || isFetchingModelInformation"
				:loading="isFetchingModelInformation"
			>
				<template #option="slotProps">
					<p>
						{{ slotProps.option.name }} <span class="subtext">({{ formatTimestamp(slotProps.option.createdOn) }})</span>
					</p>
				</template>
			</Dropdown>

			<template v-for="(intervention, i) in scenario.interventionSpecs" :key="intervention">
				<label :class="{ 'disabled-label': !scenario.modelSpec.id || isFetchingModelInformation }"
					>Select intervention policy {{ i + 1 }}</label
				>
				<div class="flex">
					<Dropdown
						ref="interventionDropdowns"
						class="flex-1 mb-3"
						:model-value="intervention.id"
						placeholder="Select an intervention policy"
						:options="combinedInterventionPolicies"
						option-label="name"
						option-value="id"
						@update:model-value="scenario.setInterventionSpec($event, i)"
						:disabled="!scenario.modelSpec.id || isFetchingModelInformation"
						:loading="isFetchingModelInformation"
						filter
					>
						<template #filtericon>
							<Button label="Create new policy" icon="pi pi-plus" size="small" text @click="onOpenPolicyModel(i)" />
						</template>
						<template #option="slotProps">
							<p>
								{{ slotProps.option.name }}
								<span class="subtext">
									({{ slotProps.option.createdOn ? formatTimestamp(slotProps.option.createdOn) : 'Created by you' }})
								</span>
							</p>
						</template>
					</Dropdown>
					<Button
						v-if="scenario.interventionSpecs.length > 1"
						text
						icon="pi pi-trash"
						size="small"
						@click="scenario.removeInterventionSpec(i)"
						class="mb-3"
						:disabled="!scenario.modelSpec.id || isFetchingModelInformation"
					/>
				</div>
			</template>
			<div>
				<Button
					class="my-2"
					text
					icon="pi pi-plus"
					label="Add a new intervention"
					size="small"
					@click="scenario.addInterventionSpec()"
					:disabled="!scenario.modelSpec.id || isFetchingModelInformation"
				/>
			</div>
		</template>

		<template #outputs>
			<label :class="{ 'disabled-label': isEmpty(modelStateOptions) || isFetchingModelInformation }"
				>Select an output metric</label
			>
			<MultiSelect
				:disabled="isEmpty(modelStateOptions) || isFetchingModelInformation"
				:model-value="scenario.simulateSpec.ids"
				placeholder="Select output metrics"
				option-label="id"
				option-value="id"
				:options="modelStateOptions"
				@update:model-value="scenario.setSimulateSpec($event)"
				:loading="isFetchingModelInformation"
				filter
			/>
			<tera-simulation-settings :scenario-instance="scenario" />
		</template>
	</tera-scenario-template>
	<tera-new-policy-modal
		:is-visible="isPolicyModalVisible"
		@close="isPolicyModalVisible = false"
		@create="addNewPolicy"
	/>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { AssetType, InterventionPolicy, ModelConfiguration } from '@/types/Types';
import { useProjects } from '@/composables/project';
import { getInterventionPoliciesForModel, getModel, getModelConfigurationsForModel } from '@/services/model';
import { isEmpty } from 'lodash';
import Dropdown from 'primevue/dropdown';
import MultiSelect from 'primevue/multiselect';
import Button from 'primevue/button';
import { sortDatesDesc, formatTimestamp } from '@/utils/date';
import { ScenarioHeader } from '../base-scenario';
import { DecisionMakingScenario } from './decision-making-scenario';
import TeraScenarioTemplate from '../tera-scenario-template.vue';
import TeraNewPolicyModal from '../tera-new-policy-modal.vue';
import { usePolicyModel } from '../scenario-template-utils';
import teraSimulationSettings from '../tera-simulation-settings.vue';

const header: ScenarioHeader = Object.freeze({
	title: 'Decision making template',
	question: 'What is the impact of different interventions?',
	description:
		'Runs a simulation for the baseline (no intervention) and each intervention policy and then shows the relative impact of each intervention policy relative to the baseline.',
	examples: [
		'What is the impact of several combinations of vaccination and NPI levels?',
		'Which is better: implement an intervention in all locations, select locations, or not at all?'
	]
});
const isFetchingModelInformation = ref(false);
const models = computed(() => useProjects().getActiveProjectAssets(AssetType.Model));

const interventionDropdowns = ref();
const modelConfigurations = ref<ModelConfiguration[]>([]);
const interventionPolicies = ref<InterventionPolicy[]>([]);
const modelStateOptions = ref<any[]>([]);
const isPolicyModalVisible = ref(false);
// which intervention index is being edited
const policyModalContext = ref<number | null>(null);

const combinedInterventionPolicies = computed(() =>
	[...props.scenario.newInterventionSpecs, ...interventionPolicies.value].sort((a: any, b: any) => {
		if (!a.createdOn) return -1;
		if (!b.createdOn) return 1;
		return sortDatesDesc(a.createdOn, b.createdOn);
	})
);

const sortedConfigurations = computed(() =>
	[...modelConfigurations.value].sort((a, b) => sortDatesDesc(a.createdOn, b.createdOn))
);

const props = defineProps<{
	scenario: DecisionMakingScenario;
}>();

const emit = defineEmits(['save-workflow']);

const { onOpenPolicyModel, addNewPolicy } = usePolicyModel(
	props,
	interventionDropdowns,
	policyModalContext,
	isPolicyModalVisible
);

watch(
	() => props.scenario.modelSpec.id,
	async (modelId) => {
		if (!modelId) return;
		isFetchingModelInformation.value = true;
		const model = await getModel(modelId);
		if (!model) return;

		await Promise.all([getModelConfigurationsForModel(modelId), getInterventionPoliciesForModel(modelId)]).then(
			([mc, ip]) => {
				modelConfigurations.value = mc;
				interventionPolicies.value = ip;
			}
		);

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
