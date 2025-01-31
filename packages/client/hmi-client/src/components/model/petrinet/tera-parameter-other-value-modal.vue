<template>
	<tera-modal>
		<template #header>
			<h4>Other configuration values for '{{ otherValueList[0].referenceId }}'</h4>
		</template>
		<section>
			<DataTable
				class="value-border"
				:value="otherValueList"
				v-model:selection="selectedRow"
				selectionMode="single"
				dataKey="id"
				:rowsPerPageOptions="[10, 20, 50]"
				tableStyle="min-width: 90rem"
				@row-click="onRowSelect"
				:metaKeySelection="false"
			>
				<Column
					v-for="(col, index) in selectedColumns"
					:field="col.field"
					:header="col.header"
					:sortable="col.field !== 'stats'"
					:key="index"
					:style="`width: ${getColumnWidth(col.field)}%`"
				>
					<template #body="{ data }">
						<template v-if="col.field === 'name'">
							{{ data.name }}
						</template>
						<template v-if="col.field === 'description'">
							{{ data.description }}
						</template>
						<template v-if="col.field === 'source'">
							{{ data.source }}
						</template>
						<tera-line-graphic
							v-if="col.field === 'range'"
							:max-value="maxValue"
							:min-value="minValue"
							:distribution="data.distribution"
						/>
						<template v-if="col.field === 'value'">
							<template v-if="data.distribution.type === DistributionType.Constant">
								<span class="value-label">Constants</span>
								<span class="value">{{ getValueString(data.distribution.parameters.value?.toString()) }}</span>
							</template>
						</template>
						<template v-if="col.field === 'minimum'">
							<template v-if="data.distribution.type === DistributionType.Uniform">
								<div>
									<span class="value-label">Min</span>
									<span class="value">{{ getValueString(data.distribution.parameters.minimum?.toString()) }}</span>
								</div>
							</template>
						</template>
						<template v-if="col.field === 'maximum'">
							<template v-if="data.distribution.type === DistributionType.Uniform">
								<div>
									<span class="value-label">Max</span>
									<span class="value">{{ getValueString(data.distribution.parameters.maximum?.toString()) }}</span>
								</div>
							</template>
						</template>
					</template>
				</Column>
			</DataTable>

			<div class="custom-input-section" :class="{ 'custom-selected': !selectedRow }" @click="onCustomSectionClick">
				<div class="grid">
					<div class="col-5">
						<tera-input-text
							placeholder="Add a source"
							v-model="customSource"
							@update:modelValue="onCustomSelectionChange"
							class="w-full"
							@click.stop
						/>
					</div>
					<div class="col-3">
						<Dropdown v-model="numberType" :options="numberOptions" class="w-full" @click.stop />
					</div>
					<div class="col-4">
						<div v-if="numberType === numberOptions[0]" class="grid">
							<div class="col-12">
								<tera-input-number
									placeholder="Constant"
									v-model="customConstant"
									@update:modelValue="onCustomSelectionChange"
									class="w-full"
								/>
							</div>
						</div>
						<div v-else class="grid">
							<div class="col-6">
								<tera-input-number
									placeholder="Min"
									v-model="customMin"
									@update:modelValue="onCustomSelectionChange"
									class="w-full"
								/>
							</div>
							<div class="col-6">
								<tera-input-number
									placeholder="Max"
									v-model="customMax"
									@update:modelValue="onCustomSelectionChange"
									class="w-full"
								/>
							</div>
						</div>
					</div>
				</div>
			</div>
		</section>
		<template #footer>
			<Button label="Apply selected value" @click="applySelectedValue" :disabled="!selection" />
			<Button label="Cancel" severity="secondary" outlined @click="emit('close-modal')" />
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { displayNumber } from '@/utils/number';
import { extent } from 'd3';
import { DistributionType } from '@/services/distribution';
import { SemanticOtherValues } from '@/services/model-configurations';
import Dropdown from 'primevue/dropdown';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import { ref } from 'vue';
import Button from 'primevue/button';
import TeraModal from '@/components/widgets/tera-modal.vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import TeraLineGraphic from './tera-line-graphic.vue';

const props = defineProps<{
	id: any;
	otherValueList: SemanticOtherValues[];
}>();

const otherValueList = ref(props.otherValueList);
const selectedRow = ref(null);

const minValue: number = extent(otherValueList.value, (element) => element?.distribution?.parameters?.minimum)[0];
const maxValue: number = extent(otherValueList.value, (element) => element?.distribution?.parameters?.maximum)[1];

const columns = ref([
	{ field: 'name', header: 'Configuration name' },
	{ field: 'description', header: 'Description' },
	{ field: 'source', header: 'Source' },
	{ field: 'range', header: 'Range' },
	{ field: 'value', header: 'Value' },
	{ field: 'minimum', header: '' },
	{ field: 'maximum', header: '' }
]);

const emit = defineEmits(['update-parameter', 'update-source', 'close-modal']);

const customSource = ref('default');
const numberType = ref(DistributionType.Constant);
const customConstant = ref(0);
const customMin = ref(undefined);
const customMax = ref(undefined);

const numberOptions = [DistributionType.Constant, DistributionType.Uniform];

const selectedColumns = ref(columns.value);

interface OtherValueSelection {
	type: DistributionType;
	source?: string;
	constant?: number;
	min?: number;
	max?: number;
}

const selection = ref<null | OtherValueSelection>(null);

const onRowSelect = (event) => {
	const val = event.data;
	selectedRow.value = val;
	selection.value =
		val.distribution.type === DistributionType.Constant
			? {
					constant: val.distribution.parameters.value,
					source: val.source,
					type: val.distribution.type
				}
			: {
					min: val.distribution.parameters.minimum,
					max: val.distribution.parameters.maximum,
					source: val.source,
					type: val.distribution.type
				};
};

const onCustomSectionClick = () => {
	selectedRow.value = null;
	selection.value =
		numberType.value === DistributionType.Constant
			? {
					constant: customConstant.value,
					source: customSource.value,
					type: DistributionType.Constant
				}
			: {
					min: customMin.value,
					max: customMax.value,
					source: customSource.value,
					type: DistributionType.Uniform
				};
};

const onCustomSelectionChange = () => {
	if (!selectedRow.value) {
		onCustomSectionClick();
	}
};

function getValueString(value) {
	return value ? displayNumber(value) : '';
}

function getColumnWidth(columnField: string) {
	switch (columnField) {
		case 'name':
			return 25;
		case 'source':
			return 15;
		case 'range':
			return 20;
		case 'value':
			return 12;
		case 'minimum':
			return 12;
		case 'maximum':
			return 12;
		default:
			return 100;
	}
}

function applySelectedValue() {
	if (selection.value?.type === DistributionType.Constant) {
		emit('update-parameter', {
			id: props.id,
			distribution: { type: selection.value.type }
		});
		emit('update-parameter', {
			id: props.id,
			distribution: {
				parameters: { value: selection.value?.constant },
				type: selection.value.type
			}
		});
		emit('update-source', { id: props.id, value: selection.value?.source });
	} else if (selection.value?.type === DistributionType.Uniform) {
		emit('update-parameter', {
			id: props.id,
			distribution: { type: selection.value.type }
		});
		emit('update-parameter', {
			id: props.id,
			distribution: {
				parameters: { minimum: selection.value?.min, maximum: selection.value?.max },
				type: selection.value.type
			}
		});
		emit('update-source', { id: props.id, value: selection.value?.source });
	}
	emit('close-modal');
}
</script>

<style scoped>
.value-label {
	color: var(--surface-600);
	padding-right: var(--gap-4);
}
.value {
	color: var(--surface-900);
}

/* Change style for Primevue component */
:deep(td[role='cell'] > div.p-dropdown) {
	height: 2.5rem;
	width: 100%;
}

:deep(th) {
	padding-left: var(--gap-4);
}

:deep(.p-datatable .p-datatable-tbody > tr) {
	cursor: pointer;
	& > td:first-child {
		border-left: 4px solid var(--surface-300);
	}
}

:deep(.p-datatable .p-datatable-tbody > tr.p-highlight),
:deep(.p-datatable .p-datatable-tbody > tr.p-highlight:hover) {
	background: var(--surface-highlight);
	& > td:first-child {
		border-left: 4px solid var(--primary-color);
	}
}

.custom-input-section {
	border-left: 4px solid var(--surface-300);
	padding: var(--gap-3) var(--gap-4);
	background: var(--surface-0);
	height: 4rem;
	cursor: pointer;
	border-bottom: 1px solid var(--surface-border-light);
}

.custom-input-section:hover {
	background: var(--surface-50);
}

.custom-input-section.custom-selected {
	background: var(--surface-highlight);
	border-left: 4px solid var(--primary-color);
}
.custom-input-section:deep(.tera-input) {
	margin-bottom: 0;
	& main input {
		height: 1.85rem;
	}
}
.custom-input-section:deep(.p-dropdown) {
	height: 2.5rem;
}
</style>
