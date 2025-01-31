<template>
	<div class="chart-settings">
		<h5 v-if="title">{{ title }}</h5>
		<!-- Ensemble chart options -->
		<template v-if="type === ChartSettingType.VARIABLE_ENSEMBLE">
			<tera-checkbox
				:disabled="_.isEmpty(selectedOptions)"
				label="Show individual models"
				:model-value="Boolean(ensembleChartOptions?.showIndividualModels)"
				@update:model-value="toggleEnsembleChartOption('showIndividualModels', $event)"
			/>
			<!-- Disabling following two checkboxes for now since their functionalities aren't implemented yet -->
			<!-- <tera-checkbox
				class="pl-5"
				:disabled="selectedOptions.length === 0"
				label="Relative to ensemble"
				:model-value="Boolean(ensembleChartOptions.relativeToEnsemble)"
				@update:model-value="toggleEnsembleChartOption('relativeToEnsemble', $event)"
			/>
			<tera-checkbox
				v-if="isSimulateEnsembleSettings"
				label="Show individual models with weights"
				:disabled="selectedOptions.length === 0"
				:model-value="Boolean(ensembleChartOptions.showIndividualModelsWithWeight)"
				@update:model-value="toggleEnsembleChartOption('showIndividualModelsWithWeight', $event)"
			/> -->
		</template>

		<!-- Original chart control for non-comparison charts -->
		<tera-chart-control
			v-if="type !== ChartSettingType.VARIABLE_COMPARISON"
			:chart-config="{
				selectedRun: 'fixme',
				selectedVariable: selectedOptions ?? []
			}"
			:multi-select="true"
			:show-remove-button="false"
			:variables="selectOptions"
			@configuration-change="$emit('selection-change', $event.selectedVariable, type)"
		/>

		<slot></slot>

		<!-- Sensitivity analysis settings -->
		<template v-if="type === ChartSettingType.SENSITIVITY && sensitivityOptions">
			<div class="mb-2"></div>
			<!--FIXME: It might be better to move these inside the panel so that they can be controlled at an individual chart settings level -->
			<label :class="_.isEmpty(selectedOptions) ? 'disabled' : ''">Select parameter(s) of interest</label>
			<MultiSelect
				:disabled="_.isEmpty(selectedOptions)"
				placeholder="Select parameters"
				:model-value="sensitivityOptions.selectedInputOptions"
				:options="sensitivityOptions.inputOptions"
				@change="
					$emit('sensitivity-selection-change', {
						selectedInputVariables: $event.value,
						timepoint: sensitivityOptions.timepoint
					})
				"
			>
				<template v-slot:value>
					<template v-for="(variable, index) in sensitivityOptions.selectedInputOptions" :key="index">
						<template v-if="index > 0">,&nbsp;</template>
						<span> {{ variable }} </span>
					</template>
				</template>
			</MultiSelect>

			<div class="mb-2"></div>
			<label :class="_.isEmpty(selectedOptions) ? 'disabled' : ''">Select time slice of interest</label>
			<tera-input-number
				:disabled="_.isEmpty(selectedOptions)"
				:model-value="sensitivityOptions.timepoint"
				@update:model-value="
					$emit('sensitivity-selection-change', {
						selectedInputVariables: sensitivityOptions.selectedInputOptions,
						timepoint: $event
					})
				"
			/>
			<div class="mb-1"></div>
		</template>
		<template v-if="type === ChartSettingType.VARIABLE_COMPARISON">
			<!-- TODO: Move this part to it's own component, tera-chart-settings-item-comparison or inside tera-char-settings-item -->
			<div v-for="s of targetSettings" :key="s.id" class="settings-item">
				<div class="content">
					<tera-chart-control
						:chart-config="{
							selectedRun: 'fixme',
							selectedVariable: (comparisonSelectedOptions ?? {})[s.id] ?? []
						}"
						:multi-select="true"
						:show-remove-button="false"
						:variables="selectOptions"
						@configuration-change="$emit('comparison-selection-change', s.id, $event.selectedVariable)"
					/>
				</div>
				<div class="actions">
					<Button icon="pi pi-cog" text rounded @click="$emit('open', s)" />
					<Button icon="pi pi-times" text rounded @click="$emit('remove', s.id)" />
				</div>
			</div>
		</template>
		<template v-else>
			<tera-chart-settings-item
				v-for="s of targetSettings"
				:key="s.id"
				:settings="s"
				@open="$emit('open', s)"
				@remove="$emit('remove', s.id)"
			/>
		</template>
	</div>
</template>

<script setup lang="ts">
import TeraCheckbox from '@/components/widgets/tera-checkbox.vue';
import TeraChartSettingsItem from '@/components/widgets/tera-chart-settings-item.vue';
import TeraChartControl from '@/components/workflow/tera-chart-control.vue';
import MultiSelect from 'primevue/multiselect';
import { ChartSetting, ChartSettingType } from '@/types/common';
import { computed } from 'vue';
import Button from 'primevue/button';
import { EnsembleVariableChartSettingOption, getEnsembleChartSettingOptions } from '@/services/chart-settings';
import _ from 'lodash';
import TeraInputNumber from './tera-input-number.vue';

const props = defineProps<{
	title?: string; // Optional title for the settings panel
	settings: ChartSetting[];
	type: ChartSettingType;
	/**
	 * Available dropdown select options.
	 */
	selectOptions: string[];
	/**
	 * Selected dropdown options.
	 */
	selectedOptions?: string[];
	/**
	 * Selected dropdown options for comparison charts
	 */
	comparisonSelectedOptions?: { [settingId: string]: string[] };
	isSimulateEnsembleSettings?: boolean;
	sensitivityOptions?: {
		inputOptions: string[];
		selectedInputOptions: string[];
		timepoint: number;
	};
}>();
const emits = defineEmits([
	'open',
	'remove',
	'selection-change',
	'toggle-ensemble-variable-setting-option',
	'sensitivity-selection-change',
	'comparison-selection-change'
]);

// Settings of the same type that we want to interact with.
const targetSettings = computed(() => props.settings.filter((s) => s.type === props.type));

// ------------------- Ensemble chart options -------------------
const ensembleChartOptions = computed(() => getEnsembleChartSettingOptions(targetSettings.value));
const toggleEnsembleChartOption = (option: EnsembleVariableChartSettingOption, value: boolean) => {
	emits('toggle-ensemble-variable-setting-option', option, value);
};
</script>
<style scoped>
.chart-settings {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}
.disabled {
	color: var(--gray-400);
}
.settings-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--gap-2) var(--gap-3);
	background: var(--surface-0);
	border-left: 4px solid var(--primary-color);
	border-radius: var(--border-radius);
	margin-top: var(--gap-2);
}
.settings-item .content {
	background: transparent;
	border: transparent;
	width: 16rem;
}
.content {
	flex: 1;
}
.actions {
	display: flex;
	gap: var(--gap-2);
}
</style>
