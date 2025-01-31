<template>
	<section class="drag-n-drop">
		<div
			:class="dragOver != true ? 'dropzone-container' : 'dropzone-container-dragOver'"
			@drop="onDrop"
			@dragover="onDragOver"
			@dragleave="onDragLeave"
		>
			<input
				id="fileInput"
				type="file"
				ref="fileInput"
				@change="onFileChange"
				multiple
				:accept="fileInputAcceptedExtensions"
				class="hidden-input"
			/>
			<label for="fileInput" class="file-label">
				<div v-if="dragOver" class="drop-zone">
					<div><i class="pi pi-upload" style="font-size: 2.5rem" /></div>
					<div>Release mouse button to<br />add files to import</div>
				</div>
				<div v-else class="drop-zone">
					<div><i class="pi pi-upload" style="font-size: 2.5rem" /></div>
					<div class="drop-zone-text">
						Drop {{ acceptTypes[0] === AcceptedTypes.PROJECTCONFIG ? 'your project' : 'resources' }} here <br />
						or <span class="text-link">click to open a file browser</span>
					</div>
				</div>
			</label>
		</div>
		<div v-if="importFiles.length > 0">
			<div class="preview-container mt-4" v-if="importFiles.length">
				<div v-for="file in importFiles" :key="file.name" class="file-preview" scrolling="no">
					<TeraDragAndDropFilePreviewer
						:file="file"
						:is-processing="isProcessing"
						:progress="props.progress"
						@remove-file="removeFile(importFiles.indexOf(file))"
					>
					</TeraDragAndDropFilePreviewer>
				</div>
				<div v-if="isProcessing" class="uploading-container">
					<p>Uploading...</p>
					<ProgressBar :value="props.progress"></ProgressBar>
				</div>
				<div v-else class="empty-uploading-container"></div>
			</div>
		</div>
	</section>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { AcceptedExtensions, AcceptedTypes } from '@/types/common';
import ProgressBar from 'primevue/progressbar';
import TeraDragAndDropFilePreviewer from './tera-drag-n-drop-file-previewer.vue';

const emit = defineEmits(['import-completed', 'imported-files-updated']);
const importFiles = ref(<File[]>[]);
const fileInput = ref(<HTMLInputElement | null>null);
const dragOver = ref(false);
const isProcessing = ref(false);
const processResponse = ref(<{ file: string; response: { text: string } }[]>[]);

const csvDescription = ref('');

const props = defineProps({
	progress: {
		type: Number,
		default: undefined
	},
	// list of accepted files types
	acceptTypes: {
		type: Array<AcceptedTypes>,
		required: true,
		validator: (value: Array<string>) => Object.values(AcceptedTypes).every((v) => value.includes(v))
	},
	acceptExtensions: {
		type: Array<AcceptedExtensions>,
		required: true,
		validator: (value: Array<string>) => Object.values(AcceptedExtensions).every((v) => value.includes(v))
	},
	// custom import action can be passed in as prop
	importAction: {
		type: Function,
		required: true
	}
});

const fileInputAcceptedExtensions = computed(() => props.acceptExtensions.map((v) => `.${v}`).join(','));

/**
 * Add file event
 * @param {File[]|undefined} addedFiles
 * @returns {any}
 */
const addFiles = (addedFiles: File[] | undefined) => {
	if (addedFiles !== undefined && addedFiles.length > 0) {
		for (let i = 0; i < addedFiles.length; i++) {
			// if we have a file type specified, check to see if it is an accepted type
			// if we do not have a file type specified, check to see if the name ends with an accepted extension
			const addedFile = addedFiles[i];
			if (
				props.acceptTypes.includes(addedFile.type as AcceptedTypes) ||
				props.acceptExtensions.includes(addedFile.name.split('.').pop() as AcceptedExtensions)
			) {
				// only add files that weren't added before
				const index = importFiles.value.findIndex((item) => item.name === addedFile.name);
				if (index === -1) {
					importFiles.value.push(addedFile);
				} else {
					console.log(`${addedFile.name} File already added!`);
				}
			}
		}
	}
	processFiles(importFiles.value);
};

/**
 * File selection event
 * @returns {any}
 */
const onFileChange = () => {
	if (fileInput.value !== null) {
		addFiles(fileInput.value ? Array.from(fileInput.value.files as FileList) : undefined);
	}
};

/**
 * On file drop event
 * @param {DragEvent} event
 * @returns {any}
 */
const onDrop = (event: DragEvent) => {
	event.preventDefault();
	dragOver.value = false;
	const addedFiles = event.dataTransfer?.files ? Array.from(event.dataTransfer?.files) : undefined;
	addFiles(addedFiles);
};

/**
 * File drag over event
 * @param {DragEvent} event
 * @returns {any}
 */
const onDragOver = (event: DragEvent) => {
	event.preventDefault();
	dragOver.value = true;
};

/**
 * File drag leave event
 * @param {DragEvent} event
 * @returns {any}
 */
const onDragLeave = (event: DragEvent) => {
	event.preventDefault();
	dragOver.value = false;
};

/**
 * remove a file from list
 * @param {number} index
 * @returns {any}
 */
const removeFile = (index: number) => {
	importFiles.value.splice(index, 1);
};

async function processFiles(files) {
	isProcessing.value = true;
	const r = await props.importAction(files, csvDescription.value);
	processResponse.value = await Promise.all(r);
	isProcessing.value = false;
	emit('import-completed', processResponse.value);
	files.value = [];
}

watch(
	() => importFiles.value.length,
	() => {
		emit('imported-files-updated', importFiles.value);
	}
);

// Make these methods available to parent components
defineExpose({
	addFiles,
	importFiles
});
</script>

<style scoped>
.drag-n-drop {
	/* flex: 0.5; */
	width: 100%;
	overflow-y: auto;
	height: 100%;
	overflow-x: hidden;
}

.dropzone-container {
	flex-direction: column;
	display: flex;
	padding: 1rem;
	border: 1px dashed var(--surface-border);
	border-radius: var(--border-radius);
	background-color: var(--surface-secondary);
}

.dropzone-container-dragOver {
	flex-direction: column;
	display: flex;
	padding: 1rem;
	border: 1px solid var(--primary-color);
	border-radius: var(--border-radius);
	background-color: var(--surface-highlight);
}

.hidden-input {
	opacity: 0;
	overflow: hidden;
	position: absolute;
	width: 1px;
	height: 1px;
}

.file-label {
	font-size: var(--font-body-small);
	flex-direction: column;
	cursor: pointer;
	align-items: center;
	padding: 2.5rem 0 2.5rem 0;
}
label.file-label {
	display: flex;
}

.text-link {
	color: var(--primary-color);
}

.options-container {
	display: flex;
	flex-direction: row;
}

.extraction-mode {
	margin-right: 10px;
}

.preview-container {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}

.file-preview {
	display: flex;
	flex-direction: column;
}

.import-button {
	display: flex;
	flex-direction: column;
	margin-top: 2rem;
	border-radius: 0.25rem;
	cursor: pointer;
	border: 1px solid var(--surface-border);
}

.drop-zone {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	align-items: center;
	text-align: center;
}
.drop-zone-text {
	text-align: center;
}

i {
	color: var(--text-color-secondary);
}

.uploading-container {
	padding-top: var(--gap-2);
	display: flex;
	flex-direction: column;
	height: 2rem;
	gap: var(--gap-1);
	font-size: var(--font-caption);
	color: var(--text-color-secondary);
}
.empty-uploading-container {
	height: 2rem;
}
</style>
