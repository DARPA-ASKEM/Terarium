<template>
	<tera-modal>
		<template #header>
			<h4>Other configuration values for '{{ otherValueList[0].target }}'</h4>
		</template>
		<section>
			<DataTable
				dataKey="id"
				class="value-border"
				tableStyle="min-width: 90rem"
				:rowsPerPageOptions="[10, 20, 50]"
				:value="otherValueList"
				v-model:selection="selectedRow"
				selectionMode="single"
				@row-click="onRowSelect"
				:metaKeySelection="false"
			>
				<Column sortable>
					<template #header>Configuration name</template>
					<template #body="{ data }">{{ data.name }}</template>
				</Column>
				<Column>
					<template #header>Description</template>
					<template #body="{ data }">{{ data.description }}</template>
				</Column>
				<Column sortable>
					<template #header>Source</template>
					<template #body="{ data }">{{ data.source }}</template>
				</Column>
				<Column sortable>
					<template #header>Value</template>
					<template #body="{ data }">
						<span class="value-label">Constants</span>
						<span class="value">{{ numberToNist(data.expression) || data.expression }}</span>
					</template>
				</Column>
			</DataTable>

			<div class="custom-input-section" :class="{ 'custom-selected': !selectedRow }" @click="onCustomSectionClick">
				<div class="grid">
					<div class="col-6">
						<tera-input-text
							placeholder="Add a source"
							v-model="customSource"
							@update:modelValue="onCustomSelectionChange"
							class="w-full"
							@click.stop
						/>
					</div>
					<div class="col-6 flex">
						<span class="ml-4 custom-input-label">Constant</span>
						<tera-input-number
							placeholder="Constant"
							v-model="customConstant"
							@update:modelValue="onCustomSelectionChange"
							class="w-full"
							@click.stop
						/>
					</div>
				</div>
			</div>
		</section>
		<template #footer>
			<Button label="Apply selected value" :disabled="!selection" @click="applySelectedValue" />
			<Button label="Cancel" severity="secondary" outlined @click="emit('close-modal')" />
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import Button from 'primevue/button';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraModal from '@/components/widgets/tera-modal.vue';
import { numberToNist } from '@/utils/number';
import { DistributionType } from '@/services/distribution';

const props = defineProps<{
	id: string;
	otherValueList: any[];
	otherValuesInputTypes: DistributionType;
}>();

const otherValueList = ref(props.otherValueList);
const selectedRow = ref(null);

const emit = defineEmits(['update-expression', 'update-source', 'close-modal']);

const customSource = ref('default');
const customConstant = ref(0);

const selection = ref<null | { id?: string; source?: string; constant?: number }>(null);

const onRowSelect = (event) => {
	const val = event.data;
	selectedRow.value = val;
	selection.value = { constant: val.expression, source: val.source };
};

const onCustomSectionClick = () => {
	selectedRow.value = null;
	selection.value = { constant: customConstant.value, source: customSource.value };
};

const onCustomSelectionChange = () => {
	if (!selectedRow.value) {
		onCustomSectionClick();
	}
};

function applySelectedValue() {
	emit('update-expression', { id: props.id, value: selection.value?.constant });
	emit('update-source', { id: props.id, value: selection.value?.source });
	emit('close-modal');
}
</script>

<style scoped>
.value-label {
	color: var(--text-color-subdued);
	padding-right: var(--gap-4);
}
.value {
	color: var(--text-color);
	white-space: nowrap;
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

.custom-input-label {
	align-items: center;
	display: flex;
	justify-content: left;
	padding-right: var(--gap-2);
}
</style>
