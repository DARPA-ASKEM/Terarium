<template>
	<div class="datatable-container">
		<!-- Toggle histograms & column summary charts -->
		<div class="datatable-toolbar">
			<span class="datatable-toolbar-item">
				{{ columns?.length || 'No' }} columns | {{ rowCount || 'No' }} rows
				{{ rowCount != rawContent.csv.length ? '(' + rawContent.csv.length + ' showing)' : '' }}
			</span>
			<span class="datatable-toolbar-item" style="margin-left: auto">
				Show column summaries<InputSwitch v-model="showSummaries" />
			</span>
			<Button
				class="download-button datatable-toolbar-item"
				label="Download"
				icon="pi pi-download"
				text
				outlined
				disabled
			/>
			<span class="datatable-toolbar-item">
				<MultiSelect
					:model-value="selectedColumns"
					:options="rawContent.headers"
					@update:model-value="selectedColumns = rawContent.headers.filter((col) => $event.includes(col))"
					:maxSelectedLabels="1"
					placeholder="Select columns"
					filter
				>
					<template #value>
						<span class="columns-dropdown datatable-toolbar-item">
							<vue-feather type="columns" size="1.25rem" />
							<span>Columns</span>
						</span>
					</template>
				</MultiSelect>
			</span>
		</div>
		<!-- Datable -->
		<DataTable
			:class="previewMode ? 'p-datatable-xsm' : 'p-datatable-sm'"
			:value="rawContent.csv"
			:rows="rows"
			paginator
			:paginatorPosition="paginatorPosition ? paginatorPosition : `bottom`"
			:rowsPerPageOptions="[5, 10, 25, 50, 100]"
			paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
			currentPageReportTemplate="{first} to {last} of {totalRecords}"
			removableSort
			showGridlines
			scrollable
			scroll-height="flex"
			:tableStyle="tableStyle ? tableStyle : `width:auto`"
		>
			<Column
				v-for="(colName, index) of rawContent.headers"
				:key="index"
				:field="index.toString()"
				:header="colName"
				:style="previousHeaders && !previousHeaders.includes(colName) ? 'border-color: green' : ''"
				sortable
				:hidden="!selectedColumns.includes(colName)"
			>
				<template #header v-if="!previewMode && showSummaries">
					<!-- column summary charts below -->
					<div class="column-summary">
						<div class="column-summary-row" v-if="statistics.get(colName)?.maxValue">
							<span class="column-summary-label">Max:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.maxValue }}</span>
						</div>
						<Chart
							v-if="statistics.get(colName)?.chartData"
							class="histogram"
							type="bar"
							:height="480"
							:width="480"
							:data="statistics.get(colName)?.chartData"
							:options="CHART_OPTIONS"
						/>
						<div class="column-summary-row max" v-if="statistics.get(colName)?.minValue">
							<span class="column-summary-label">Min:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.minValue }}</span>
						</div>
						<div class="column-summary-row" v-if="statistics.get(colName)?.mean">
							<span class="column-summary-label">Mean:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.mean }}</span>
						</div>
						<div class="column-summary-row" v-if="statistics.get(colName)?.median">
							<span class="column-summary-label">Median:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.median }}</span>
						</div>
						<div class="column-summary-row" v-if="statistics.get(colName)?.sd">
							<span class="column-summary-label">SD:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.sd }}</span>
						</div>
						<div class="column-summary-row mt-2" v-if="statistics.get(colName)?.uniqueValues">
							<span class="column-summary-label">Unique values:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.uniqueValues }}</span>
						</div>
						<div class="column-summary-row" v-if="statistics.get(colName)?.missingValues">
							<span class="column-summary-label">Missing values:</span>
							<span class="column-summary-value">{{ statistics.get(colName)?.missingValues }}</span>
						</div>
						<div class="most-common-values-row" v-if="statistics.get(colName)?.most_common">
							<div class="most-common-values-list">
								<div
									v-for="(value, key) in statistics.get(colName)?.most_common || {}"
									:key="key"
									class="most-common-value"
								>
									<span>{{ key }}</span>
									<span style="margin-left: auto; text-align: right">{{ value }}</span>
								</div>
							</div>
						</div>
					</div>
				</template>
			</Column>
		</DataTable>
	</div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import type { CsvAsset, DatasetColumn, HistogramBin } from '@/types/Types';
import MultiSelect from 'primevue/multiselect';
import Button from 'primevue/button';
import Chart from 'primevue/chart';
import InputSwitch from 'primevue/inputswitch';
import { displayNumber } from '@/utils/number';

type ChartData = {
	labels: string[];
	datasets: {
		label: string;
		backgroundColor: string;
		hoverBackgroundColor: string;
		data: number[];
		categoryPercentage: number;
		barPercentage: number;
		minBarLength: number;
	}[];
};

type ColumnStats = {
	minValue?: string;
	maxValue?: string;
	mean?: string;
	median?: string;
	sd?: string;
	bins?: number[];
	chartData?: ChartData;
	uniqueValues?: string;
	missingValues?: number;
	most_common?: { [index: string]: number };
};

const props = defineProps<{
	rawContent: CsvAsset; // Temporary - this is also any in ITypeModel
	rows?: number;
	previewMode?: boolean;
	previousHeaders?: String[] | null;
	paginatorPosition?: 'bottom' | 'both' | 'top' | undefined;
	tableStyle?: String;
	columns?: DatasetColumn[];
	rowCount?: number;
}>();

const CATEGORYPERCENTAGE = 1.0;
const BARPERCENTAGE = 1.0;
const MINBARLENGTH = 1;
const CHART_OPTIONS = {
	responsive: true,
	elements: {
		bar: {
			borderWidth: 1,
			borderColor: '#FFFFFF'
		}
	},
	width: 10,
	indexAxis: 'y',
	plugins: {
		legend: {
			labels: {
				display: false
			},
			display: false
		},
		tooltip: {
			enabled: true,
			position: 'nearest',
			displayColors: false,
			backgroundColor: '#333333dd'
		}
	},
	scales: {
		x: {
			ticks: {
				display: false
			},
			grid: {
				display: false,
				drawBorder: false
			}
		},
		y: {
			ticks: {
				display: false
			},
			grid: {
				display: false,
				drawBorder: false,
				borderColor: getComputedStyle(document.documentElement).getPropertyValue('--surface-border-light')
			}
		}
	}
};

const showSummaries = ref(true);
const selectedColumns = ref<string[]>(props.rawContent.headers);
const statistics = ref<Map<string, ColumnStats>>(new Map());

// Given the bins for a column set up the object needed for the chart.
const setBarChartData = (bins: HistogramBin[]): ChartData => {
	const documentStyle = getComputedStyle(document.documentElement);
	const dummyLabels = bins.map((bin) => `[${bin.start}—${bin.end}]`);
	return {
		labels: dummyLabels,
		datasets: [
			{
				label: 'Count',
				backgroundColor: documentStyle.getPropertyValue('--primary-color'),
				hoverBackgroundColor: documentStyle.getPropertyValue('--primary-color-light'),
				data: bins.map((bin) => bin.count),
				categoryPercentage: CATEGORYPERCENTAGE,
				barPercentage: BARPERCENTAGE,
				minBarLength: MINBARLENGTH
			}
		]
	};
};

watch(
	() => props?.columns,
	(value, oldValue) => {
		if (!value || value === oldValue) return;
		const stats = new Map<string, ColumnStats>();
		value.forEach((column) => {
			const columnStats: ColumnStats = {};
			if (column.stats?.numericStats) {
				columnStats.minValue = displayNumber(column.stats.numericStats.min);
				columnStats.maxValue = displayNumber(column.stats.numericStats.max);
				columnStats.mean = displayNumber(column.stats.numericStats.mean);
				columnStats.median = displayNumber(column.stats.numericStats.median);
				columnStats.sd = displayNumber(column.stats.numericStats.std_dev);
				columnStats.chartData = setBarChartData(column.stats.numericStats.histogram_bins);
				columnStats.uniqueValues = displayNumber(column.stats.numericStats.unique_values);
			} else if (column.stats?.nonNumericStats) {
				columnStats.uniqueValues = displayNumber(column.stats.nonNumericStats.unique_values);
				columnStats.missingValues = column.stats.nonNumericStats.missing_values;
				columnStats.most_common = column.stats.nonNumericStats.most_common;
			}
			stats.set(column.name ?? '', columnStats);
		});
		statistics.value = stats;
	},
	{ immediate: true }
);
</script>

<style scoped>
.datatable-toolbar {
	display: flex;
	flex-direction: row;
	gap: var(--gap-4);
	padding-bottom: var(--gap-2);
}
.datatable-toolbar-item {
	display: flex;
	flex-direction: row;
	font-size: var(--font-caption);
	color: var(--text-color-subdued);
	align-items: center;
	gap: var(--gap-2);
}

.datatable-toolbar:deep(.p-multiselect .p-multiselect-label) {
	padding: var(--gap-2);
	font-size: var(--font-caption);
}

.p-datatable:deep(.p-column-header-content) {
	display: grid;
	grid-template-areas:
		'name sortIcon'
		'columnSummary columnSummary';
}

.p-datatable:deep(.p-column-title) {
	grid-area: name;
	padding-left: 0.5rem;
}
.p-datatable:deep(.p-sortable-column-icon) {
	grid-area: sortIcon;
}

.p-datatable.p-datatable-sm:deep(.p-datatable-thead > tr > th) {
	padding: 0.5rem 0;
	background-color: var(--surface-50);
}

.p-datatable.p-datatable-sm:deep(.p-datatable-tbody > tr > td) {
	padding: var(--gap-1) 0.5rem;
}
/* Histograms & Charts  */
.column-summary {
	position: relative;
	width: 100%;
	margin-top: 0.5rem;
	grid-area: columnSummary;
	height: 14rem;
}
.column-summary-row {
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	padding-left: 0.5rem;
	padding-right: 0.5rem;
	font-size: var(--font-caption);
	color: var(--text-color-subdued);
	font-weight: lighter;
}
.column-summary-row.max {
	position: relative;
	top: -0.5rem;
}

.most-common-values-row {
	font-size: var(--font-caption);
	font-weight: lighter;
	color: var(--text-color-subdued);
	padding-left: 0.5rem;
	padding-right: 0.5rem;
	padding-top: 0.5rem;
}
.most-common-values-list {
	display: flex;
	flex-direction: column;
	gap: var(--gap-1);
	width: 100%;
}
.most-common-value {
	display: flex;
	flex-direction: row;
	justify-content: space-between;
}
.histogram {
	height: 100px;
}

.p-datatable-flex-scrollable {
	overflow: hidden;
}
:deep(.download-button.p-button.p-button-text),
.columns-dropdown {
	color: var(--text-color-primary);
}
.datatable-container {
	display: flex;
	flex-direction: column;
	flex: 1;
	overflow: hidden;
}
</style>
