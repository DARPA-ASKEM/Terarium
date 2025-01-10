<template>
	<tera-drilldown
		:node="node"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
	>
		<template #sidebar>
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isInputSettingsOpen"
				header="Compare settings"
				content-width="440px"
			>
				<template #content>
					<tera-drilldown-section class="px-3">
						<Button class="ml-auto" size="small" label="Run" @click="onRun" />
						<label>What do you want to compare?</label>
						<Dropdown
							v-model="knobs.selectedCompareOption"
							:options="compareOptions"
							option-label="label"
							option-value="value"
						/>
						<!-- Pascale asked me to hide this until the feature is implemented -->
						<!-- <tera-checkbox
							class="pt-2"
							v-model="isSimulationsFromSameModel"
							label="All simulations are from the same model"
							disabled
						/> -->
						<tera-checkbox
							class="mt-2 mb-4"
							v-model="areSimulationsFromSameModel"
							label="All simulations are from the same model"
							disabled
						/>
						<template v-if="knobs.selectedCompareOption === CompareValue.IMPACT">
							<label> Select simulation to use as a baseline (optional) </label>
							<Dropdown
								v-model="knobs.selectedDataset"
								:options="datasets"
								option-label="name"
								option-value="id"
								:loading="isFetchingDatasets"
								placeholder="Optional"
								@change="generateImpactCharts"
							/>
							<label>Comparison tables</label>
							<tera-checkbox v-model="isATESelected" label="Average treatment effect (ATE)" />
						</template>
						<!-- Pascale asked me to omit this timepoint selector, but I'm keeping it here until we are certain it's not needed -->
						<!--
						<label class="mt-2">Timepoint column</label>
						<Dropdown
							v-model="timepointHeaderName"
							:options="commonHeaderNames"
							placeholder="Select timepoint header"
							@change="generateChartData"
						/>
						<div class="mb-4" />
						-->
						<div class="flex flex-column gap-2" v-if="knobs.selectedCompareOption === CompareValue.RANK">
							<label>Specify criteria of interest:</label>
							<tera-criteria-of-interest-card
								v-for="(card, i) in node.state.criteriaOfInterestCards"
								:key="i"
								:card="card"
								:model-configurations="modelConfigurations"
								:variables="commonHeaderNames"
								@delete="deleteCriteria(i)"
								@update="(e) => updateCriteria(e, i)"
							/>
							<div>
								<Button
									class="pt-2"
									text
									icon="pi pi-plus"
									size="small"
									label="Add new criteria"
									@click="addCriteria"
								/>
							</div>
						</div>
					</tera-drilldown-section>
				</template>
			</tera-slider-panel>
		</template>

		<tera-drilldown-section :tabName="DrilldownTabs.Wizard">
			<div ref="outputPanel">
				<Accordion multiple :active-index="activeIndices">
					<AccordionTab header="Summary"> </AccordionTab>
					<template v-if="knobs.selectedCompareOption === CompareValue.IMPACT">
						<AccordionTab header="Variables">
							<template v-for="setting of selectedVariableSettings" :key="setting.id">
								<vega-chart
									:visualization-spec="variableCharts[setting.id]"
									:are-embed-actions-visible="false"
									expandable
								/>
							</template>
						</AccordionTab>
						<AccordionTab header="Comparison table"> </AccordionTab>
					</template>
					<template v-else>
						<AccordionTab header="Ranking results">
							<vega-chart :visualization-spec="rankingResultsChart" :are-embed-actions-visible="false" expandable />
						</AccordionTab>
						<AccordionTab header="Ranking criteria">
							<vega-chart
								v-for="(rankingCriteriaChart, index) in rankingCriteriaCharts"
								:visualization-spec="rankingCriteriaChart"
								:are-embed-actions-visible="false"
								:key="index"
								expandable
							/>
						</AccordionTab>
					</template>
				</Accordion>
			</div>
		</tera-drilldown-section>

		<tera-drilldown-section :tabName="DrilldownTabs.Notebook"> </tera-drilldown-section>

		<template #sidebar-right>
			<tera-slider-panel
				class="input-config"
				v-model:is-open="isOutputSettingsOpen"
				header="Output settings"
				content-width="360px"
			>
				<template #overlay v-if="knobs.selectedCompareOption === CompareValue.IMPACT">
					<tera-chart-settings-panel
						:annotations="
							[ChartSettingType.VARIABLE, ChartSettingType.VARIABLE_COMPARISON].includes(
								activeChartSettings?.type as ChartSettingType
							)
								? getChartAnnotationsByChartId(activeChartSettings?.id ?? '')
								: undefined
						"
						:active-settings="activeChartSettings"
						:generate-annotation="generateAnnotation"
						@delete-annotation="deleteAnnotation"
						@update-settings="updateActiveChartSettings"
						@close="setActiveChartSettings(null)"
					/>
				</template>
				<template #content v-if="knobs.selectedCompareOption === CompareValue.IMPACT">
					<div class="output-settings-panel">
						<tera-chart-control
							class="w-full"
							:chart-config="{
								selectedRun: 'fixme',
								selectedVariable: selectedVariableSettings.map((s) => s.selectedVariables[0])
							}"
							multi-select
							:show-remove-button="false"
							:variables="commonHeaderNames"
							@configuration-change="updateChartSettings($event.selectedVariable, ChartSettingType.VARIABLE)"
						/>
						<!-- plot options -->
						<div class="plot-options">
							<p class="mb-2">How do you want to plot the values?</p>
							<div v-for="option in plotOptions" class="flex align-items-center" :key="option.value">
								<RadioButton
									v-model="knobs.selectedPlotType"
									:value="option.value"
									name="plotValues"
									@change="generateImpactCharts"
								/>
								<label class="pl-2 py-1" :for="option.value">{{ option.label }}</label>
							</div>
						</div>
						<tera-chart-settings-item
							v-for="settings of chartSettings.filter((setting) => setting.type === ChartSettingType.VARIABLE)"
							:key="settings.id"
							:settings="settings"
							@open="setActiveChartSettings(settings)"
							@remove="removeChartSettings"
						/>
					</div>
				</template>
			</tera-slider-panel>
		</template>
	</tera-drilldown>
</template>

<script setup lang="ts">
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import { WorkflowNode, WorkflowPortStatus } from '@/types/workflow';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import { DrilldownTabs, ChartSettingType } from '@/types/common';
import { onMounted, ref, watch, computed } from 'vue';
import Button from 'primevue/button';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Dropdown from 'primevue/dropdown';
import { Dataset, InterventionPolicy, ModelConfiguration } from '@/types/Types';
import { getDataset, getRawContent } from '@/services/dataset';
import { getInterventionPolicyById } from '@/services/intervention-policy';
import { getModelConfigurationById } from '@/services/model-configurations';
import TeraCheckbox from '@/components/widgets/tera-checkbox.vue';
import RadioButton from 'primevue/radiobutton';
import { isEmpty, cloneDeep } from 'lodash';
import VegaChart from '@/components/widgets/VegaChart.vue';
import { deleteAnnotation } from '@/services/chart-settings';
import { createRankingInterventionsChart } from '@/services/charts';
import TeraChartSettingsItem from '@/components/widgets/tera-chart-settings-item.vue';
import TeraChartControl from '@/components/workflow/tera-chart-control.vue';
import TeraChartSettingsPanel from '@/components/widgets/tera-chart-settings-panel.vue';
import { useChartSettings } from '@/composables/useChartSettings';
import { useDrilldownChartSize } from '@/composables/useDrilldownChartSize';
import { useCharts, type ChartData } from '@/composables/useCharts';
import TeraCriteriaOfInterestCard from './tera-criteria-of-interest-card.vue';
import {
	blankCriteriaOfInterest,
	CompareDatasetsState,
	CompareValue,
	CriteriaOfInterestCard,
	PlotValue,
	RankOption,
	TimepointOption
} from './compare-datasets-operation';

const props = defineProps<{
	node: WorkflowNode<CompareDatasetsState>;
}>();

const emit = defineEmits(['update-state', 'close']);

const compareOptions: { label: string; value: CompareValue }[] = [
	{ label: 'Compare the impact of interventions', value: CompareValue.IMPACT },
	{ label: 'Rank interventions based on multiple criteria', value: CompareValue.RANK }
];

const datasets = ref<Dataset[]>([]);
const modelConfigurations = ref<ModelConfiguration[]>([]);
const interventionPolicies = ref<InterventionPolicy[]>([]);
const modelConfigIdToInterventionPolicyIdMap = ref<Record<string, string[]>>({});

const commonHeaderNames = ref<string[]>([]);
const timepointHeaderName = ref<string | null>(null);

const plotOptions = [
	{ label: 'Raw values', value: PlotValue.VALUE },
	{ label: 'Percent change', value: PlotValue.PERCENTAGE },
	{ label: 'Difference', value: PlotValue.DIFFERENCE }
];

const isInputSettingsOpen = ref(true);
const isOutputSettingsOpen = ref(true);
const activeIndices = ref([0, 1, 2]);

const isFetchingDatasets = ref(false);
const areSimulationsFromSameModel = ref(true);
const isATESelected = ref(false);

const onRun = () => {
	generateRankingCharts();
};

interface BasicKnobs {
	criteriaOfInterestCards: CriteriaOfInterestCard[];
	selectedCompareOption: CompareValue;
	selectedDataset: string | null;
	selectedPlotType: PlotValue;
}

const knobs = ref<BasicKnobs>({
	criteriaOfInterestCards: [],
	selectedCompareOption: CompareValue.IMPACT,
	selectedDataset: null,
	selectedPlotType: PlotValue.PERCENTAGE
});

const addCriteria = () => {
	knobs.value.criteriaOfInterestCards.push(blankCriteriaOfInterest);
};

const deleteCriteria = (index: number) => {
	knobs.value.criteriaOfInterestCards.splice(index, 1);
};

const updateCriteria = (card: Partial<CriteriaOfInterestCard>, index: number) => {
	Object.assign(knobs.value.criteriaOfInterestCards[index], card);
};

const {
	activeChartSettings,
	chartSettings,
	selectedVariableSettings,
	removeChartSettings,
	updateChartSettings,
	updateActiveChartSettings,
	setActiveChartSettings
} = useChartSettings(props, emit);

const outputPanel = ref(null);
const chartSize = useDrilldownChartSize(outputPanel);

const chartData = ref<ChartData | null>(null);
const rankingResultsChart = ref<any>(null);
const rankingCriteriaCharts = ref<any>([]);

const { generateAnnotation, getChartAnnotationsByChartId, useCompareDatasetCharts } = useCharts(
	props.node.id,
	null,
	null,
	chartData,
	chartSize,
	null,
	null
);
const selectedPlotType = computed(() => knobs.value.selectedPlotType);
const baselineName = computed(() => {
	const name = datasets.value.find((dataset) => dataset.id === knobs.value.selectedDataset)?.name;
	return name ? removeSuffixFromInputName(name) : null;
});
const variableCharts = useCompareDatasetCharts(selectedVariableSettings, selectedPlotType, baselineName);

const initialize = async () => {
	const state = cloneDeep(props.node.state);
	knobs.value = Object.assign(knobs.value, state);

	const inputs = props.node.inputs;
	const datasetInputs = inputs.filter(
		(input) => input.type === 'datasetId' && input.status === WorkflowPortStatus.CONNECTED
	);
	const datasetPromises = datasetInputs.map((input) => getDataset(input.value![0]));

	isFetchingDatasets.value = true;
	await Promise.all(datasetPromises).then((ds) => {
		ds.forEach((dataset) => {
			// Add dataset
			if (!dataset) return;
			datasets.value.push(dataset);

			// Collect model configuration id and intervention policy id
			const modelConfigurationId: string | undefined = dataset.metadata?.simulationAttributes?.modelConfigurationId;
			const interventionPolicyId: string | undefined = dataset.metadata?.simulationAttributes?.interventionPolicyId;

			if (!modelConfigurationId) return;
			if (!modelConfigIdToInterventionPolicyIdMap.value[modelConfigurationId]) {
				modelConfigIdToInterventionPolicyIdMap.value[modelConfigurationId] = [];
			}
			if (!interventionPolicyId) return;
			modelConfigIdToInterventionPolicyIdMap.value[modelConfigurationId].push(interventionPolicyId);
		});
	});
	isFetchingDatasets.value = false;

	if (!knobs.value.selectedDataset) knobs.value.selectedDataset = datasets.value[0]?.id ?? null;

	await generateImpactCharts();

	const modelConfigurationIds = Object.keys(modelConfigIdToInterventionPolicyIdMap.value);
	if (isEmpty(modelConfigurationIds)) return;
	const modelConfigurationPromises = modelConfigurationIds.map((id) => getModelConfigurationById(id));
	await Promise.all(modelConfigurationPromises).then((configs) => {
		modelConfigurations.value = configs.filter((config) => config !== null);
	});

	const interventionPolicyIds = Object.values(modelConfigIdToInterventionPolicyIdMap.value).flat();
	if (isEmpty(interventionPolicyIds)) return;
	const interventionPolicyPromises = interventionPolicyIds.map((id) => getInterventionPolicyById(id));
	await Promise.all(interventionPolicyPromises).then((policies) => {
		interventionPolicies.value = policies.filter((policy) => policy !== null);
	});

	generateRankingCharts();
};

// Following two funcs are util like
function transposeArrays(arrays) {
	if (arrays.length === 0) return [];
	return arrays[0].map((_, colIndex) => arrays.map((row) => row[colIndex]));
}

function findDuplicates(strings: string[]): string[] {
	const duplicates: string[] = [];
	const seen: Record<string, number> = {};

	strings.forEach((str) => {
		if (seen[str]) {
			seen[str]++;
		} else {
			seen[str] = 1;
		}
	});
	Object.keys(seen).forEach((key) => {
		if (seen[key] > 1) {
			duplicates.push(key);
		}
	});
	return duplicates;
}

function generateRankingCharts() {
	// Reset charts
	rankingCriteriaCharts.value = [];
	rankingResultsChart.value = null;

	// Might be uneccessary
	const commonInterventionPolicyIds = props.node.state.criteriaOfInterestCards
		.map(({ selectedConfigurationId }) => {
			if (!selectedConfigurationId) return [];
			return modelConfigIdToInterventionPolicyIdMap.value?.[selectedConfigurationId] ?? [];
		})
		.flat();

	const allRankedCriteriaValues: { score: number; name: string }[][] = [];

	props.node.state.criteriaOfInterestCards.forEach((card) => {
		if (!card.selectedConfigurationId || !card.selectedVariable || !chartData.value) return;

		const variableData = chartData.value.resultSummary.filter((d) => d.headerName === card.selectedVariable);
		if (isEmpty(variableData)) return;

		const pointOfComparison =
			card.timepoint === TimepointOption.FIRST ? variableData[0] : variableData[variableData.length - 1];

		const rankingCriteriaValues: { score: number; name: string }[] = [];
		interventionPolicies.value.forEach((policy) => {
			// Skip this intervention policy if a configuration is not using it
			if (!policy.id || !policy.name || !commonInterventionPolicyIds.includes(policy.id)) return;

			rankingCriteriaValues.push({
				score: pointOfComparison[policy.name] ?? 0,
				name: policy.name ?? ''
			});
		});

		const sortedRankingCriteriaValues =
			card.rank === RankOption.MAXIMUM
				? rankingCriteriaValues.sort((a, b) => b.score - a.score)
				: rankingCriteriaValues.sort((a, b) => a.score - b.score);

		sortedRankingCriteriaValues.forEach((value, index) => {
			value.score = index + 1;
		});
		rankingCriteriaCharts.value.push(createRankingInterventionsChart(sortedRankingCriteriaValues, card.name));
		allRankedCriteriaValues.push(sortedRankingCriteriaValues);
	});

	// Sum up the scores of the same intervention policy
	const scoreMap: Record<string, number> = {};
	allRankedCriteriaValues.flat().forEach(({ score, name }) => {
		if (scoreMap[name]) {
			scoreMap[name] += score;
		} else {
			scoreMap[name] = score;
		}
	});

	const rankingResultsValues = Object.keys(scoreMap)
		.map((name) => ({
			name,
			score: scoreMap[name]
		}))
		.sort((a, b) => a.score - b.score);

	rankingResultsChart.value = createRankingInterventionsChart(rankingResultsValues, '');
}

function removeSuffixFromInputName(name: string): string {
	return name.replace(/ - \d+$/, '');
}

async function generateImpactCharts() {
	if (datasets.value.length <= 1) return;

	const rawContents = await Promise.all(
		datasets.value.map((dataset) => {
			// Compare the summaries so find its csv file like this
			let summaryIndex = dataset?.fileNames?.findIndex((name) => name === 'result_summary.csv');
			// If the summary isn't found, use the first file (it could be the first one if you downloaded the summary csv)
			if (!summaryIndex || summaryIndex === -1) summaryIndex = 0;
			return getRawContent(dataset, -1, summaryIndex); // Setting the csv row limit to -1 to get all rows
		})
	);

	const transposedRawContents = rawContents.map((content) => ({ ...content, csv: transposeArrays(content?.csv) }));

	// Collect common header names if not done yet
	if (isEmpty(commonHeaderNames.value)) {
		const allColumnNames: string[] = [];
		transposedRawContents.forEach(({ headers }) => {
			if (headers) allColumnNames.push(...headers);
		});
		commonHeaderNames.value = findDuplicates(allColumnNames);

		// Convenient assumption that the timepoint header name starts with 't'
		timepointHeaderName.value =
			commonHeaderNames.value?.find((name) => name.toLowerCase().slice(0, 1) === 't') ?? commonHeaderNames.value[0];
	}
	if (!timepointHeaderName.value) return;

	// Find index of timepoint column
	const timepointIndex = transposedRawContents[0]?.headers?.indexOf(timepointHeaderName.value);
	if (timepointIndex === undefined || timepointIndex === -1) return;

	// Find dataset index of the selected dataset
	const selectedIndex = datasets.value.findIndex((dataset) => dataset.id === knobs.value.selectedDataset);
	if (selectedIndex === -1) return;

	const allData: any[] = [];

	// Go through every common header (column loop)
	commonHeaderNames.value?.forEach((headerName) => {
		if (headerName === timepointHeaderName.value) return;

		const headerIndex = transposedRawContents[0]?.headers?.indexOf(headerName);
		if (!headerIndex) return;

		const data: any = [];
		const variableNames: string[] = [];
		const referenceColumn = transposedRawContents[selectedIndex].csv[headerIndex]; // aka the column of baseline dataset we are subtracting from

		transposedRawContents.forEach(({ csv }, datasetIndex) => {
			const timepoints = csv[timepointIndex];
			const columnToSubtract = csv[headerIndex];

			// Removing the number suffix from the input name will help us match it with the intervention names in generateRankingCharts
			const name = removeSuffixFromInputName(`${datasets.value[datasetIndex].name}`);
			variableNames.push(name);

			referenceColumn.forEach((referencePoint: number, index: number) => {
				let value = 0;
				if (selectedPlotType.value === PlotValue.DIFFERENCE) {
					value = columnToSubtract[index] - referencePoint; // difference
				} else if (selectedPlotType.value === PlotValue.PERCENTAGE) {
					value = ((columnToSubtract[index] - referencePoint) / referencePoint) * 100; // percentage
				} else if (selectedPlotType.value === PlotValue.VALUE) {
					value = parseFloat(columnToSubtract[index]); // trajectory
				}
				if (data[index] === undefined) {
					data.push({
						headerName,
						[name]: value,
						timepoint_id: parseFloat(timepoints[index])
					});
				} else {
					data[index][name] = value;
				}
			});
		});
		allData.push(...data);
	});
	chartData.value = { result: [], resultSummary: allData, pyciemssMap: {}, translationMap: {} };
}

onMounted(() => {
	initialize();
});

watch(
	() => knobs.value,
	() => {
		const state = cloneDeep(props.node.state);
		state.criteriaOfInterestCards = knobs.value.criteriaOfInterestCards;
		state.selectedCompareOption = knobs.value.selectedCompareOption;
		state.selectedDataset = knobs.value.selectedDataset;
		state.selectedPlotType = knobs.value.selectedPlotType;
		emit('update-state', state);
	},
	{ deep: true }
);
</script>

<style scoped>
label {
	padding: var(--gap-2) 0;
}

.output-settings-panel {
	padding: var(--gap-4);
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	hr {
		border: 0;
		border-top: 1px solid var(--surface-border-alt);
		width: 100%;
	}
}

.plot-options {
	padding: var(--gap-3);
	background: var(--surface-200);
	border-radius: var(--border-radius);
	box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.1);
	margin-bottom: var(--gap-1);
}
</style>
