<template>
	<teleport to="body">
		<transition name="modal">
			<main ref="modalRef" :style="{ '--z-index': zIndex }" @keyup.enter="emit('modal-enter-press')">
				<section :class="$attrs.class">
					<header>
						<slot name="header" />
					</header>
					<section class="content"><slot /></section>
					<section><slot name="math-editor" /></section>
					<footer>
						<slot name="footer" />
					</footer>
				</section>
				<aside @click.self="emit('modal-mask-clicked')" />
			</main>
		</transition>
	</teleport>
</template>

<script setup lang="ts">
/**
 * A modal with content slots for a header, body, and footer. Use v-if to control visibility.
 * @example
 * <tera-modal v-if="isModalVisible" class="w-4">
 * 		<template #header>Header content</template>
 * 		<template #default>Body content</template>
 * 		<template #footer>Footer content</template>
 * </tera-modal>
 *
 * Clicking outside the modal will emit the event modalMaskClicked.
 * @example
 * <modal @modal-mask-clicked="closeModal"></modal>
 *
 * To easily specify modal width add a tailwind width class by passing it as a prop. eg. `class="w-4"`
 *
 * Due to this component being wrapped in a <teleport> tag, :deep() classes specified
 * in the parent's <style scoped> won't be inherited since this component spawns as a child of the <body>.
 * :deep() only effects child tags and obviously the <body> tag is not a child of the parent.
 */

import { onMounted, ref, nextTick } from 'vue';

defineOptions({
	inheritAttrs: false
});

defineProps<{
	zIndex?: number;
}>();

const emit = defineEmits(['modal-mask-clicked', 'modal-enter-press', 'on-modal-open']);

const modalRef = ref<HTMLElement | null>(null);

onMounted(async () => {
	await nextTick();
	if (!modalRef.value) return;
	/**
	 * Dispatch an event `modal-open` when the modal is opened.
	 * The `detail.id` property of the event is modal id if available.
	 * This is used by the Guided Tour to help show the user information.
	 */
	const event = new CustomEvent('modal-open', {
		bubbles: true,
		detail: { id: modalRef.value.id ?? '' }
	});
	modalRef.value.dispatchEvent(event);

	// Focus the first input element in the default slot
	const input = modalRef.value.querySelector('.content input, .content textarea');
	if (input) (input as HTMLElement).focus();

	emit('on-modal-open'); // For triggering a parent event when the modal is opened
});
</script>

<style scoped>
main {
	isolation: isolate;
	z-index: var(--z-index, var(--z-index-modal));

	& > * {
		position: absolute;
	}
}

aside {
	z-index: 1;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	background-color: rgba(0, 0, 0, 0.5);
	display: flex;
	align-items: center;
	transition: opacity 0.1s ease;
}

main > section {
	max-height: 95vh;
	max-width: 95vw;
	background-color: #fff;
	border-radius: var(--modal-border-radius);
	box-shadow: 0 2px 8px rgba(0, 0, 0, 0.33);
	margin: 0px auto;
	padding: 2rem 0;
	transition: all 0.15s ease;
	z-index: 2;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
}

.content {
	max-height: 70vh;
	padding: 0 var(--gap-8);
	overflow-y: auto;
}

header {
	margin-bottom: 1rem;
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
}

footer {
	display: flex;
	flex-direction: row-reverse;
	gap: 1rem;
	justify-content: end;
	margin-top: 2rem;
}

header,
footer {
	padding: 0 2rem;
}

.modal-enter-from,
.modal-leave-to {
	opacity: 0;
}

.modal-enter-from main > section,
.modal-leave-to main > section {
	-webkit-transform: scale(0.9);
	transform: scale(0.9);
}

.content:deep(form label) {
	display: block;
	margin-bottom: 0.5em;
}

.content:deep(.tera-input),
.content:deep(textarea) {
	display: block;
	margin-bottom: 1rem;
	width: 100%;
}
</style>
