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
			<label :class="{ 'disabled-label': isEmpty(filterModelConfigurations) || isFetchingModelInformation }"
				>Select configuration representing best and generous estimates of the initial conditions</label
			>
			<Dropdown
				:model-value="scenario.modelConfigSpec.id"
				placeholder="Select a configuration"
				:options="filterModelConfigurations"
				option-label="name"
				option-value="id"
				@update:model-value="scenario.setModelConfigSpec($event)"
				:disabled="isEmpty(filterModelConfigurations) || isFetchingModelInformation"
				:loading="isFetchingModelInformation"
				class="mb-3"
			>
				<template #option="slotProps">
					<p>
						{{ slotProps.option.name }} <span class="subtext">({{ formatTimestamp(slotProps.option.createdOn) }})</span>
					</p>
				</template>
			</Dropdown>

			<label :class="{ 'disabled-label': !scenario.modelSpec.id }"
				>Select uncertain parameters of interest and adjust ranges to be explored if needed</label
			>
			<template v-for="(parameter, i) in scenario.parameters" :key="i">
				<div class="flex">
					<Dropdown
						class="flex-1"
						:model-value="parameter?.id"
						:options="modelParameters"
						option-label="referenceId"
						option-value="referenceId"
						placeholder="Select a parameter"
						:disabled="!scenario.modelSpec.id"
						:loading="isFetchingModelConfiguration || isFetchingModelInformation"
						@update:model-value="onParameterSelect($event, i)"
						filter
					>
						<template #option="slotProps">
							<span>{{ displayParameter(modelParameters, slotProps.option.referenceId) }}</span>
						</template>

						<template #value="slotProps">
							<span v-if="displayParameter(modelParameters, slotProps.value)">{{
								displayParameter(modelParameters, slotProps.value)
							}}</span>
							<span v-else>{{ slotProps.placeholder }}</span>
						</template>
					</Dropdown>
					<Button v-if="scenario.parameters.length > 1" icon="pi pi-trash" text @click="scenario.removeParameter(i)" />
				</div>
				<div v-if="parameter" class="distribution-container">
					<label class="p-0 white-space-nowrap">Extreme low:</label>
					<tera-input-number class="m-0" v-model="parameter.low" auto-width />
					<label class="p-0 ml-2 white-space-nowrap">Extreme high:</label>
					<tera-input-number class="m-0" v-model="parameter.high" auto-width />
				</div>
			</template>
			<div>
				<Button
					:disabled="!scenario.modelSpec.id"
					class="mt-2"
					label="Add parameter"
					icon="pi pi-plus"
					text
					size="small"
					@click="scenario.addParameter()"
				/>
			</div>

			<label :class="{ 'disabled-label': !scenario.modelSpec.id }">Planned intervention policy (optional)</label>
			<div v-for="(intervention, i) in scenario.interventionSpecs" :key="i" class="flex">
				<Dropdown
					ref="interventionDropdowns"
					class="flex-1 my-1"
					:model-value="intervention.id"
					:options="combinedInterventionPolicies"
					option-label="name"
					option-value="id"
					placeholder="Select an intervention policy (optional)"
					@update:model-value="scenario.setInterventionSpec($event, i)"
					:key="i"
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
					size="small"
					text
					icon="pi pi-trash"
					@click="scenario.deleteInterventionSpec(i)"
					:disabled="!scenario.modelSpec.id || isFetchingModelInformation"
				/>
			</div>
			<div>
				<Button
					class="py-2 mb-3"
					size="small"
					text
					icon="pi pi-plus"
					label="Add more interventions"
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
			<img :src="horizon" alt="Horizon scanning chart" class="" />
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
import _, { isEmpty } from 'lodash';
import { computed, ref, watch } from 'vue';
import Dropdown from 'primevue/dropdown';
import MultiSelect from 'primevue/multiselect';
import Button from 'primevue/button';
import { useProjects } from '@/composables/project';
import horizon from '@/assets/svg/template-images/horizon-thumbnail.svg';
import { HorizonScanningScenario } from '@/components/workflow/scenario-templates/horizon-scanning/horizon-scanning-scenario';
import { getInterventionPoliciesForModel, getModel, getModelConfigurationsForModel } from '@/services/model';
import { AssetType, InterventionPolicy, ModelConfiguration, ParameterSemantic } from '@/types/Types';
import { getModelConfigurationById, getParameter, getParameters } from '@/services/model-configurations';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import { sortDatesDesc, formatTimestamp } from '@/utils/date';
import { ScenarioHeader } from '../base-scenario';
import TeraScenarioTemplate from '../tera-scenario-template.vue';
import { displayParameter, usePolicyModel } from '../scenario-template-utils';
import teraNewPolicyModal from '../tera-new-policy-modal.vue';
import TeraSimulationSettings from '../tera-simulation-settings.vue';

const header: ScenarioHeader = Object.freeze({
	title: 'Horizon scanning template',
	question: 'How do extreme scenarios impact the outcome of different interventions?',
	description:
		'Configure the model to represent the extremes of uncertainty for some parameters, then simulate into the near future with different intervention policies and compare the outcomes.',
	examples: ['Potential emergence of a new variant.', 'Rapidly waning immunity.']
});

const isFetchingModelInformation = ref(false);
const isFetchingModelConfiguration = ref(false);
const models = computed(() => useProjects().getActiveProjectAssets(AssetType.Model));

const modelConfigurations = ref<ModelConfiguration[]>([]);
const filterModelConfigurations = computed<ModelConfiguration[]>(() =>
	modelConfigurations.value
		.filter((mc) => isEmpty(mc.inferredParameterList))
		.sort((a, b) => sortDatesDesc(a.createdOn, b.createdOn))
);
const interventionPolicies = ref<InterventionPolicy[]>([]);
const modelStateOptions = ref<any[]>([]);
const modelParameters = ref<ParameterSemantic[]>([]);

const selectedModelConfiguration = ref<ModelConfiguration | null>(null);

const interventionDropdowns = ref();
const isPolicyModalVisible = ref(false);
const policyModalContext = ref<number | null>(null);

const combinedInterventionPolicies = computed(() =>
	[...props.scenario.newInterventionSpecs, ...interventionPolicies.value].sort((a: any, b: any) => {
		if (!a.createdOn) return -1;
		if (!b.createdOn) return 1;
		return sortDatesDesc(a.createdOn, b.createdOn);
	})
);

const props = defineProps<{
	scenario: HorizonScanningScenario;
}>();

const emit = defineEmits(['save-workflow']);

const { onOpenPolicyModel, addNewPolicy } = usePolicyModel(
	props,
	interventionDropdowns,
	policyModalContext,
	isPolicyModalVisible
);

const onParameterSelect = (parameterId: string, index: number) => {
	if (!selectedModelConfiguration.value) return;
	const parameter = _.cloneDeep(getParameter(selectedModelConfiguration.value, parameterId));
	if (!parameter) return;
	props.scenario.setParameter(parameter, index);
};

watch(
	() => props.scenario.modelSpec.id,
	async (modelId) => {
		if (!modelId) return;
		isFetchingModelInformation.value = true;
		const model = await getModel(modelId);
		if (!model) return;

		modelConfigurations.value = await getModelConfigurationsForModel(modelId);
		interventionPolicies.value = await getInterventionPoliciesForModel(modelId);

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

watch(
	() => props.scenario.modelConfigSpec.id,
	async (modelConfigId) => {
		if (!modelConfigId) {
			selectedModelConfiguration.value = null;
			return;
		}
		isFetchingModelConfiguration.value = true;
		selectedModelConfiguration.value = await getModelConfigurationById(modelConfigId);
		if (!selectedModelConfiguration.value) return;
		modelParameters.value = getParameters(selectedModelConfiguration.value).sort((a, b) =>
			a.referenceId.localeCompare(b.referenceId)
		);
		isFetchingModelConfiguration.value = false;
	}
);
</script>

<style scoped>
.distribution-container {
	display: flex;
	align-items: center;
	padding: var(--gap-2) var(--gap-1);
	margin: var(--gap-0-5) 0;
	background-color: var(--surface-100);
}

.disabled-label {
	color: var(--text-color-disabled);
}
</style>
