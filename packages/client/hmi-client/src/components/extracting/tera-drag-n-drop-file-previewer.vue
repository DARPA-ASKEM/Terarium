<template>
	<div class="file-title-container">
		<div class="file-name-container">
			<p class="file-name">
				{{ props.file?.name }}
			</p>
		</div>
		<div>
			<Button
				@click="emit('remove-file')"
				icon="pi pi-times"
				class="p-button-rounded p-button-text p-button-sm"
				style="max-width: 10px; max-height: 10px"
			/>
		</div>
	</div>
	<div class="file-preview" scrolling="no">
		<template v-if="props.showError">
			<div>Error Please Try Again</div>
		</template>
	</div>
</template>

<script setup lang="ts">
import Button from 'primevue/button';

const props = defineProps({
	file: {
		type: File,
		default: undefined
	},
	isProcessing: {
		type: Boolean,
		required: true
	},
	showError: {
		type: Boolean
	},
	progress: {
		type: Number,
		default: undefined
	}
});

const emit = defineEmits(['remove-file']);
</script>
<style scoped>
.file-title-container {
	background: var(--surface-50);
	padding: var(--gap-3);
	border-radius: var(--border-radius);
	border-left: solid 4px var(--primary-color);
	display: flex;
	justify-content: space-between;
	gap: var(--gap-2);
}
.file-preview {
	overflow: hidden !important;
}

.file-title-container {
	display: flex;
	flex-direction: row;
}

.file-name {
	font-size: var(--font-body-small);
	color: var(--text-color-primary);
	border: none;
}

.file-name-container {
	display: flex;
	gap: var(--gap-4);
	flex-direction: row;
}

.uploading-container {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}

.progress-message {
	font-size: var(--font-caption);
	color: var(--text-color-secondary);
	margin-top: var(--gap-2);
}
.file-preview embed {
	width: 100%;
	height: 100%;
}

.file-preview embed html {
	background-color: var(--surface-border-warning);
	height: 100%;
}

.ready {
	color: var(--primary-color);
	display: flex;
	gap: var(--gap-2);
	align-items: center;
}

.ready i {
	align-self: center;
	margin-bottom: var(--gap-0-5);
}
</style>
