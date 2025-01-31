<template>
	<p v-if="isLoading">Loading CSV...</p>
	<p v-else-if="error">Error loading CSV: {{ error }}</p>
	<DataTable
		v-else
		:size="'small'"
		showGridlines
		stripedRows
		removableSort
		:value="csvData"
		:globalFilter="true"
		:sortMode="'multiple'"
	>
		<Column
			v-for="col in columns"
			:key="col.field as string"
			:field="col.field"
			:header="col.header"
			:sortable="true"
			:style="col.field === columns[0].field ? { width: '16rem' } : {}"
		/>
	</DataTable>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { isEmpty } from 'lodash';
import DataTable from 'primevue/datatable';
import Column, { ColumnProps } from 'primevue/column';
import Papa from 'papaparse';

// Define props for the component
const props = defineProps({
	csvText: {
		type: String,
		required: true
	}
});

const csvData = ref([]);
const columns = ref<ColumnProps[]>([]);
const isLoading = ref(true);
const error = ref(null);

function rowReplaceEmptyKey(row: any) {
	Object.keys(row).forEach((key) => {
		if (isEmpty(key)) {
			row.empty = row[key];
			delete row[key];
		}
	});
	return row;
}

function onComplete(results: any) {
	// If the CSV has an empty header, just use 'empty' as the field name
	columns.value = results.meta.fields.map((field: string) => ({
		field: isEmpty(field) ? 'empty' : field,
		header: field
	}));
	csvData.value = results.data.map(rowReplaceEmptyKey);
	isLoading.value = false;
}

function onError(err: any) {
	error.value = err.message;
	isLoading.value = false;
}

// Configuration for PapaParse
// https://www.papaparse.com/docs#config
const config = {
	header: true,
	dynamicTyping: true,
	skipEmptyLines: true,
	complete: onComplete,
	error: onError
};

// Load CSV when component is mounted
onMounted(() => {
	try {
		Papa.parse(props.csvText, config);
	} catch (fetchError) {
		onError(fetchError);
	}
});
</script>

<style scoped>
.p-datatable {
	border: 1px solid var(--surface-border);
	border-radius: var(--border-radius);
}
:deep(th) {
	padding-left: var(--gap-2);
}
:deep(th:first-of-type) {
	border-top-left-radius: var(--border-radius);
}
:deep(th:last-of-type) {
	border-top-right-radius: var(--border-radius);
}
:deep(tr:last-of-type td:first-of-type) {
	border-bottom-left-radius: var(--border-radius);
}
:deep(tr:last-of-type td:last-of-type) {
	border-bottom-right-radius: var(--border-radius);
}
</style>
