<template>
	<h6 class="py-3">Simulation Settings</h6>
	<section>
		<!-- Presets -->
		<div class="label-and-input">
			<label>Preset (optional)</label>
			<Dropdown
				:model-value="scenarioInstance.simulateSpec.preset"
				placeholder="Select an option"
				:options="[CiemssPresetTypes.Fast, CiemssPresetTypes.Normal]"
				@update:model-value="scenarioInstance.setPreset($event)"
			/>
		</div>

		<!-- Start & End -->
		<!--TODO: use tera-timestep-calendar instead for start/end times-->
		<div class="input-row">
			<div class="label-and-input">
				<label>Start Time</label>
				<tera-input-number :model-value="0" disabled />
			</div>
			<div class="label-and-input">
				<label>End Time</label>
				<tera-input-number
					:model-value="scenarioInstance.simulateSpec.endTime"
					@update:model-value="scenarioInstance.setEndTime($event)"
				/>
			</div>
		</div>

		<tera-checkbox
			label="Run simulations automatically"
			:model-value="scenarioInstance.simulateSpec.runSimulationsAutomatically"
			@update:model-value="scenarioInstance.setRunSimulationsAutomatically($event)"
		/>
	</section>
</template>

<script setup lang="ts">
import Dropdown from 'primevue/dropdown';
import { CiemssPresetTypes } from '@/types/common';
import teraCheckbox from '@/components/widgets/tera-checkbox.vue';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import { DecisionMakingScenario } from './decision-making/decision-making-scenario';
import { ValueOfInformationScenario } from './value-of-information/value-of-information-scenario';
import { HorizonScanningScenario } from './horizon-scanning/horizon-scanning-scenario';
import { SensitivityAnalysisScenario } from './sensitivity-analysis/sensitivity-analysis-scenario';

defineProps<{
	scenarioInstance:
		| DecisionMakingScenario
		| SensitivityAnalysisScenario
		| ValueOfInformationScenario
		| HorizonScanningScenario;
}>();
</script>

<style scoped>
section {
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);
	padding: var(--gap-4);
	background-color: var(--surface-100);
	border: 1px solid var(--surface-border-alt);
	border-radius: 4px;

	:deep(label) {
		padding: 0;
		font: inherit;
		color: var(--text-color);
	}
}

.label-and-input {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	margin-bottom: var(--gap-4);
}

.input-row {
	width: 100%;
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	align-items: center;
	gap: 0.5rem;

	& > * {
		flex: 1;
	}
}
</style>
