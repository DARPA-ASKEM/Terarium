<template>
	<div class="chart-settings-quantiles">
		<h5>Data format</h5>
		<div>
			<RadioButton
				:model-value="radioButtonValue"
				@update:model-value="onRadioButtonChange"
				inputId="default"
				value="default"
			/>
			<label for="default" class="ml-2">Default format</label>
		</div>
		<div>
			<RadioButton
				:model-value="radioButtonValue"
				@update:model-value="onRadioButtonChange"
				inputId="quantile"
				value="quantile"
			/>
			<label for="quantile" class="ml-2">Quantiles (CDC Forecast Hub)</label>
		</div>
		<div class="pl-5">
			<MultiSelect
				:model-value="options.quantiles"
				:options="AVAILABLE_QUANTILES"
				:selectionLimit="10"
				placeholder="Select quantiles"
				@update:model-value="onMultiSelectChange"
				:disabled="!options.showQuantiles"
			>
				<template #option="slotProps">
					<span>{{ formatPercent(slotProps.option) }}</span>
				</template>
				<template v-slot:value>
					<template v-for="(quantile, index) in options.quantiles" :key="index">
						<template v-if="index > 0">,&nbsp;</template>
						<span> {{ formatPercent(quantile) }} </span>
					</template>
				</template>
			</MultiSelect>
		</div>
	</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import * as d3 from 'd3';
import { ChartSetting } from '@/types/common';
import RadioButton from 'primevue/radiobutton';
import MultiSelect from 'primevue/multiselect';
import { getQauntileChartSettingOptions } from '@/services/chart-settings';

// This ui component manages updating the quantiles options for all provided chart settings.
// Changes to the quantiles options are applied to all applicable chart settings and all settings have the same quantiles options.

const AVAILABLE_QUANTILES = [0.99, 0.975, 0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.65, 0.6, 0.55, 0.5];

const props = defineProps<{
	/** chart settings for the operator where this component is used in. */
	settings: ChartSetting[];
}>();
const emits = defineEmits(['update-options']);

const options = computed(
	() => getQauntileChartSettingOptions(props.settings) ?? { showQuantiles: false, quantiles: [] }
);

const radioButtonValue = computed(() => (options.value.showQuantiles ? 'quantile' : 'default'));
const onRadioButtonChange = (value: 'default' | 'quantile') => {
	emits('update-options', { ...options.value, showQuantiles: value === 'quantile' });
};

const onMultiSelectChange = (value: number[]) => {
	emits('update-options', { ...options.value, quantiles: value });
};

const formatPercent = (value: number) => d3.format('.0%')(value);
</script>
<style scoped>
.chart-settings-quantiles {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}
</style>
