<template>
	<Editor v-model="editorContent" :class="{ readonly: !hasEditPermission }" :readonly="!hasEditPermission" />
	<!-- empty state image -->
	<section v-if="editorContent.length === 0" class="empty-state">
		<Vue3Lottie :animationData="EmptySeed" :height="150" loop autoplay />
		<p class="helpMessage"><strong>Use this space however you like.</strong></p>
		<div class="flex gap-1">
			<p class="helpMessage">Just click and start typing.</p>
		</div>
	</section>

	<!-- empty state message -->
	<Panel
		v-if="showWelcomeMessage && editorContent.length === 0"
		header="Hey there! Not sure where to start?"
		class="welcome-message"
	>
		<template #icons>
			<Button icon="pi pi-times" text rounded @click="showWelcomeMessage = false" />
		</template>
		<div class="p-message-content">
			<p>Here are some things you can try:</p>
			<ul>
				<li><b>Upload stuff.</b> Drag documents, models and datasets onto the resources panel to upload files.</li>
				<li>
					<b>Build a model.</b> Extract equations from documents or use LaTex to create a model that fits what you need.
				</li>
				<li>
					<b>Create a workflow.</b> Configure your model, calibrate it with observational data, set intervention
					policies and run simulations for various scenarios.
				</li>
			</ul>
		</div>
	</Panel>
</template>

<script setup lang="ts">
import { useProjects } from '@/composables/project';
import { update } from '@/services/project';
import { b64DecodeUnicode, b64EncodeUnicode } from '@/utils/binary';
import Editor from 'primevue/editor';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { Vue3Lottie } from 'vue3-lottie';
import EmptySeed from '@/assets/images/lottie-empty-seed.json';
import Panel from 'primevue/panel';
import Button from 'primevue/button';

const AUTO_SAVE_DELAY = 3000;

const { activeProject, refresh } = useProjects();
const lastSavedContent = computed(() => b64DecodeUnicode(activeProject.value?.overviewContent ?? ''));
const hasEditPermission = computed(() => ['creator', 'writer'].includes(activeProject.value?.userPermission ?? ''));
const editorContent = ref('');

let autoSaveIntervalId: number | null = null;
const startAutoSave = () => {
	autoSaveIntervalId = window.setInterval(saveContent, AUTO_SAVE_DELAY);
};
const stopAutoSave = () => {
	if (autoSaveIntervalId === null) return;
	window.clearInterval(autoSaveIntervalId);
	autoSaveIntervalId = null;
};

const isContentSameAsLastSaved = computed(
	() => lastSavedContent.value.length === editorContent.value.length && lastSavedContent.value === editorContent.value
);
const saveContent = async () => {
	if (!activeProject.value) return;
	if (isContentSameAsLastSaved.value) return;
	const res = await update({ ...activeProject.value, overviewContent: b64EncodeUnicode(editorContent.value) });
	// Note that an error has happened when res is null since the `update` function's swallowing the error and returning null instead of throwing it.
	if (!res) {
		stopAutoSave();
		return;
	}
	// This will update the last saved content to the current content
	await refresh();
};

onMounted(() => {
	editorContent.value = lastSavedContent.value;
});

watch(editorContent, () => {
	if (!hasEditPermission.value) return;
	// If the content changes, start the auto-save timer if it's not already running
	if (autoSaveIntervalId !== null) return;
	startAutoSave();
});

onUnmounted(() => {
	stopAutoSave();
});

const showWelcomeMessage = ref(true);
</script>

<style scoped>
/* Editor toolbar formatting */
.readonly :deep(.p-editor-toolbar) {
	display: none;
}

.p-editor-container {
	display: flex;
	flex-direction: column;
}

:deep(.p-editor-content) {
	height: 0;
	flex-grow: 1;
}

.p-editor-container:deep(.p-editor-toolbar),
.p-editor-container:deep(.p-editor-content) {
	border-radius: 0px;
	/* Some variables seem to be read from what's specified in the quill packages snow theme not in _editor.scss so they are overriden here. */
	border-color: var(--surface-border-light);
	border-top: none;
}

:deep(.ql-active) {
	background-color: var(--surface-highlight) !important;
	color: var(--text-color-subdued) !important;
}

:deep(.ql-active .ql-stroke) {
	stroke: var(--text-color-subdued) !important;
}

:deep(.ql-selected) {
	background-color: var(--surface-highlight) !important;
}

/* Empty state */
.empty-state {
	position: absolute;
	width: calc(100% - 240px);
	height: 100%;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: var(--gap-2);
	text-align: center;
	pointer-events: none;
}

.welcome-message {
	position: absolute;
	width: calc(100% - 264px); /* 240px + 12px + 12px */
	margin-top: 3.5rem;
	margin-left: var(--gap-3);
	padding: 0 var(--gap-3) var(--gap-1);
	display: flex;
	flex-direction: column;
	border: 1px solid var(--surface-border-light);
	z-index: 10;
	background: linear-gradient(to right, var(--yellow-100), var(--yellow-50));
}
.welcome-message .p-message-content {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
}
.welcome-message:deep(.p-panel-title) {
	font-weight: 600;
}
.welcome-message:deep(.p-panel-header) {
	background-color: transparent;
}
.welcome-message:deep(.p-panel-content) {
	background-color: transparent;
}
.welcome-message ul {
	list-style-position: inside;
	line-height: 1.55rem;
}

.welcome-message:deep(.p-message-wrapper svg) {
	display: none;
}
</style>
