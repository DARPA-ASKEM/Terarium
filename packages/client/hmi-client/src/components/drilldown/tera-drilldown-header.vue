<template>
	<header>
		<div class="title-row">
			<h4 class="title"><slot /> <i v-if="props.tooltip" v-tooltip="tooltip" class="pi pi-info-circle" /></h4>
			<slot name="top-header-actions" />
			<Button class="close-mask" icon="pi pi-times" text rounded aria-label="Close" @click="emit('close')" />
		</div>
		<div class="tabs-row">
			<TabView v-if="views.length > 1" :active-index="activeIndex" @tab-change="onTabChange">
				<TabPanel v-for="(view, index) in views" :key="index" :header="view" />
			</TabView>
			<div class="actions">
				<slot name="actions" />
			</div>
			<a v-if="documentationUrl" :href="documentationUrl" rel="noopener noreferrer">Help</a>
		</div>
	</header>
</template>

<script setup lang="ts">
import Button from 'primevue/button';
import TabView, { TabViewChangeEvent } from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';

const props = defineProps<{
	activeIndex: number;
	views: string[];
	tooltip?: string;
	documentationUrl?: string;
}>();
const emit = defineEmits(['close', 'tab-change']);

const onTabChange = (event: TabViewChangeEvent) => {
	emit('tab-change', event);
};
</script>

<style scoped>
header {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	background-color: var(--surface-highlight);
	padding-top: 1rem;
	padding-left: 1.5rem;
	padding-right: 1.5rem;
	border-bottom: 1px solid var(--surface-border-light);
}

header > * {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.title-row {
	display: flex;
	align-items: center;
	gap: var(--gap-3);
}

.title-row > h4 > i {
	color: var(--text-color-secondary);
	margin-left: 1rem;
}

.p-button.p-button-icon-only.p-button-rounded {
	height: 24px;
	width: 24px;
}
.p-button:deep(.p-button-icon) {
	font-size: 16px;
	color: var(--text-color-primary);
}

header .title {
	white-space: nowrap;
	min-width: fit-content;
}

header .tabs-row {
	justify-content: space-between;
	align-items: end;
	gap: var(--gap-4);
}

header .tabs-row:deep(.p-tabview .p-tabview-panels) {
	padding: 0;
}

a {
	display: flex;
	align-items: center;
	color: var(--primary-color);
	margin-left: auto;
	margin-right: var(--gap-4);
	margin-bottom: 2px;
	font-size: var(--font-caption);
	padding: var(--gap-3);
	border-radius: var(--border-radius-medium);
}
a:hover {
	background-color: rgba(27, 128, 115, 0.04);
}

:deep(.p-tabview-header:not(.p-highlight) .p-tabview-nav-link) {
	background: var(--tab-backgroundcolor-unselected);
}

:deep(.p-tabview .p-tabview-nav li .p-tabview-nav-link:focus) {
	background-color: var(--surface-section);
}
:deep(.p-tabview .p-tabview-nav li .p-tabview-nav-link) {
	padding: var(--gap-3) var(--gap-6);
}

.actions {
	display: flex;
	justify-content: flex-end;
	gap: var(--gap-2);
	padding-bottom: var(--gap-1);
	flex: 1;
}

.close-mask {
	position: relative;
	overflow: visible;
}

.close-mask.p-button.p-button-icon-only:deep() {
	padding: 1rem;
}
.close-mask:before {
	content: '';
	position: absolute;
	top: -100px;
	right: -100px;
	bottom: -12px;
	left: -12px;
}
</style>
