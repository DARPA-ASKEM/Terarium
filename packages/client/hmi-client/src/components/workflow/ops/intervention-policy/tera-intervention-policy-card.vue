<template>
	<div :class="{ selected: selected }">
		<header>
			<h6>{{ interventionPolicy.name }}</h6>
			<Button text icon="pi pi-ellipsis-v" @click.stop="toggleContextMenu" />
		</header>
		<ContextMenu ref="contextMenu" :model="contextMenuItems"></ContextMenu>
		<p v-if="interventionPolicy.description">{{ interventionPolicy.description }}</p>
		<p>{{ formatTimestamp(interventionPolicy.createdOn) }}</p>
	</div>
</template>

<script setup lang="ts">
import { formatTimestamp } from '@/utils/date';
import ContextMenu from 'primevue/contextmenu';
import { ref } from 'vue';
import Button from 'primevue/button';
import { InterventionPolicy } from '@/types/Types';

defineProps<{
	interventionPolicy: InterventionPolicy;
	selected?: boolean;
}>();

const emit = defineEmits(['use-intervention', 'delete-intervention-policy']);

const contextMenu = ref();
const contextMenuItems = [
	{
		label: 'Use',
		icon: 'pi pi-arrow-right',
		command() {
			emit('use-intervention');
		}
	},
	{
		label: 'Delete',
		icon: 'pi pi-trash',
		command() {
			emit('delete-intervention-policy');
		}
	}
];

const toggleContextMenu = (event) => {
	contextMenu.value.toggle(event);
};
</script>

<style scoped>
div {
	background-color: var(--gray-0);
	border-radius: var(--border-radius);
	border: 1px solid var(--surface-border-light);
	border-left: 4px solid var(--surface-300);
	padding: var(--gap-1) var(--gap-1) var(--gap-3) var(--gap-3);
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
	&.selected {
		border-left-color: var(--primary-color);
	}

	&,
	&.selected {
		transition: border-left-color 15ms;
	}

	&:not(.card-selected):hover {
		background-color: var(--surface-highlight);
		cursor: pointer;
	}
}

header {
	align-items: center;
	display: flex;

	& > *:last-child {
		margin-left: auto;
	}
}

p {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
}

p + p {
	margin-top: var(--gap-2);
}
</style>
