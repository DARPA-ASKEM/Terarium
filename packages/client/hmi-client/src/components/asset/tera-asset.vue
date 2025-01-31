<template>
	<header
		v-if="showHeader && !isDocument"
		:class="{
			'overview-banner': pageType === ProjectPages.OVERVIEW,
			'with-tabs': tabs.length > 1,
			shadow: applyShadow
		}"
	>
		<!--For naming asset such as model or code file-->
		<div class="row">
			<tera-toggleable-input
				v-if="[ProjectPages.OVERVIEW, AssetType.Dataset, AssetType.Model].includes(pageType) && name"
				:model-value="name"
				tag="h4"
				@update:model-value="onRename"
			/>
			<h4 v-else>{{ name }}</h4>
			<slot name="edit-buttons" />
		</div>
		<!--put model contributors here too-->
		<span v-if="authors" class="authors">
			<i :class="authors.includes(',') ? 'pi pi-users' : 'pi pi-user'" />
			<span v-html="authors" />
		</span>
		<div v-if="doi">
			DOI:
			<a :href="`https://doi.org/${doi}`" rel="noreferrer noopener" v-html="doi" />
		</div>
		<div v-if="publisher" v-html="publisher" />
		<div class="header-buttons">
			<slot name="bottom-header-buttons" />
		</div>
		<slot name="summary" />
		<TabView v-if="tabs.length > 1" :active-index="selectedTabIndex" @tab-change="(e) => emit('tab-change', e)">
			<TabPanel v-for="(tab, index) in tabs" :key="index" :header="tab.props?.tabName" />
		</TabView>
	</header>
	<main v-if="!isLoading" ref="assetElementRef" @scroll="onScroll" :class="{ 'document-asset': isDocument }">
		<template v-if="isDocument">
			<slot name="default" />
		</template>
		<section v-else :class="{ 'overflow-hidden': overflowHidden }">
			<template v-for="(tab, index) in tabs" :key="index">
				<component :is="tab" v-show="selectedTabIndex === index" />
			</template>
			<slot name="default" />
			<nav v-if="showTableOfContents">
				<a
					v-for="[id, navOption] in navIds"
					:class="{ 'chosen-item': id === chosenItem }"
					:key="id"
					@click="scrollTo(id)"
					class="nav-item"
				>
					{{ navOption }}
				</a>
			</nav>
		</section>
	</main>
	<tera-progress-spinner v-else :font-size="2" is-centered />
</template>

<script setup lang="ts">
import { ref, computed, watch, useSlots, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { ProjectPages } from '@/types/Project';
import { AssetType } from '@/types/Types';
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import { isEmpty } from 'lodash';

const props = defineProps({
	id: {
		type: String,
		default: ''
	},
	name: {
		type: String,
		default: ''
	},
	authors: {
		type: String,
		default: null
	},
	doi: {
		type: String,
		default: null
	},
	publisher: {
		type: String,
		default: null
	},
	showHeader: {
		type: Boolean,
		default: true
	},
	// Booleans default to false if not specified
	showTableOfContents: Boolean,
	hideIntro: Boolean,
	isLoading: Boolean,
	overflowHidden: Boolean,
	isDocument: Boolean,
	selectedTabIndex: {
		type: Number,
		default: 0
	}
});

const emit = defineEmits(['tab-change', 'rename']);

const slots = useSlots();
const pageType = useRoute().params.pageType as ProjectPages | AssetType;

const assetElementRef = ref<HTMLElement | null>(null);
const scrollPosition = ref(0);
const navIds = ref<Map<string, string>>(new Map());
const chosenItem = ref<string | null>(null);

const tabs = computed(() => {
	if (slots.tabs?.()) {
		if (slots.tabs().length === 1) {
			// if there is only 1 component we don't need to know the tab name and we can render it.
			return slots.tabs();
		}
		return slots.tabs().filter((vnode) => vnode.props?.tabName);
	}
	return [];
});

function scrollTo(id: string) {
	const element = assetElementRef.value?.querySelector(`#${id}`);
	if (!element) return;
	element.scrollIntoView({ behavior: 'smooth' });
}

function onScroll(event: Event) {
	// Update scroll position
	scrollPosition.value = (event?.currentTarget as HTMLElement).scrollTop;

	// Update current nav item
	if (!assetElementRef.value) return;
	let closestItem: string | null = null;
	let smallestDistance = assetElementRef.value.scrollHeight;
	const containerTop = assetElementRef.value.getBoundingClientRect().top;

	navIds.value.forEach((_, id) => {
		const element = assetElementRef.value?.querySelector(`#${id}`)?.parentElement?.parentElement; // Gets accordion panel
		if (!element) return;

		const elementTop = Math.abs(element.getBoundingClientRect().top);
		const elementBottom = element.getBoundingClientRect().bottom - 10; // Extend the bottom slightly

		if (
			elementBottom >= containerTop && // Make sure element is below the scrollbar
			elementTop < smallestDistance // Update closestItem if this element is closer than the previous closest
		) {
			smallestDistance = elementTop;
			closestItem = id;
		}
	});
	chosenItem.value = closestItem;
}

function onRename(newName: string) {
	if (!newName || isEmpty(newName)) return;
	emit('rename', newName);
}

watch(
	() => assetElementRef.value,
	async () => {
		if (!assetElementRef.value || !props.showTableOfContents) return;
		await nextTick();

		// Find all the headers to navigate to and assign them an id
		const headers = assetElementRef.value.querySelectorAll('.p-accordion-header > a');
		if (!headers) return;

		headers.forEach((header) => {
			// Extract header name
			const textNodes = Array.from(header.childNodes).filter((node) => node.nodeType === Node.TEXT_NODE);
			let text = textNodes.map((node) => node.textContent).join('');
			if (!text) {
				const span = header.querySelector('span');
				if (span?.textContent) text = span.textContent;
			}
			if (!text) return;
			// Inject id into header based on header name
			const id = `header-nav-${text.replaceAll(' ', '-').trim()}`;
			header.setAttribute('id', id);
			// Add to map (HTML id -> navigation option/header name)
			navIds.value.set(id, text);
		});
	}
);

// Reset the scroll position to the top on asset change
watch(
	() => props.id,
	() => assetElementRef.value?.scrollIntoView()
);

const applyShadow = computed(() => scrollPosition.value > 8);
</script>

<style scoped>
main {
	display: flex;
	flex-direction: column;
	flex: 1;
	background-color: var(--surface-section);
	overflow-y: auto;
	overflow-x: hidden;
}

.document-asset {
	overflow: hidden;
}

main > section {
	display: flex;
	flex: 1;
	& > :deep(*:not(nav, i)) {
		flex: 1;
		max-width: 100%;
		overflow-x: auto;
		overflow-y: hidden;
	}
}

nav {
	display: flex;
	flex-direction: column;
	width: fit-content;
	gap: 1rem;
	padding: var(--gap-4) var(--gap-8) 0 var(--gap-2);
	/* Responsible for stickiness */
	position: sticky;
	top: 0;
	height: fit-content;

	& a.chosen-item {
		font-weight: var(--font-weight-semibold);
		color: var(--primary-color);
	}
}

.nav-item {
	min-width: 9.5rem;
}
header {
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	height: fit-content;
	padding: var(--gap-2) var(--gap-4);
	gap: var(--gap-2);
	background-color: var(--surface-0);
	backdrop-filter: blur(6px);
	overflow: hidden;
	z-index: 2;
	box-shadow: 0 0 0 0 rgba(0, 0, 0, 0);
	transition: box-shadow 0.3s;
}

header h4 {
	align-self: center;
	overflow: hidden;
	text-align: left;
}

header h4.shrink {
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	flex-grow: 1;
	flex-shrink: 1;
	max-width: fit-content;
}

header > button {
	align-self: flex-start;
}

header.overview-banner section {
	width: 100%;
	max-width: 100%;
}

header.with-tabs {
	padding: var(--gap-2) var(--gap-4) 0;
}

.overview-banner {
	background:
		url('@/assets/svg/terarium-icon-transparent.svg') no-repeat right 20% center,
		linear-gradient(45deg, #8bd4af1a, #d5e8e5 100%) no-repeat;
	background-size: 25%, 100%;
}

.row {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: var(--gap-2);
}

.close {
	margin-left: auto;
}

main:deep(.p-inputtext.p-inputtext-sm) {
	padding: 0.65rem 0.65rem 0.65rem 3rem;
}

/* Input asset name */
header section:deep(> input) {
	width: var(--constrain-width);
	font-size: var(--font-body-medium);
}

.authors i {
	margin-right: var(--gap-2);
}

.authors ~ * {
	color: var(--text-color-subdued);
}

.header-buttons:empty {
	display: none;
}

.header-buttons,
header aside {
	display: flex;
	flex-direction: row;
	gap: var(--gap-2);
}

.shadow {
	box-shadow:
		0 4px 6px -1px rgba(0, 0, 0, 0.1),
		0 2px 4px -2px rgba(0, 0, 0, 0.1);
}

/* Affects child components put in the slot*/
main:deep(.p-accordion) {
	margin: var(--gap-2);
}

main:deep(.p-accordion-content) {
	padding-bottom: var(--gap-2);
}

main:deep(.p-accordion-content ul) {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	list-style: none;
}

main:deep(.p-accordion-content > textarea) {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	padding: 5px;
	resize: none;
	overflow-y: hidden;
	width: 100%;
}

main:deep(.artifact-amount) {
	font-size: var(--font-caption);
	color: var(--text-color-subdued);
	margin-left: var(--gap-1);
}

main:deep(.p-button.p-button-outlined) {
	color: var(--text-color-primary);
	box-shadow: var(--text-color-disabled) inset 0 0 0 1px;
}

.overflow-hidden {
	overflow: hidden;
}

:deep(.p-tabview .p-tabview-panels) {
	padding: 0;
}

:deep(.p-tabview-header:not(.p-highlight) .p-tabview-nav-link) {
	background: var(--tab-backgroundcolor-unselected);
}

:deep(.p-tabview .p-tabview-nav li .p-tabview-nav-link:focus) {
	background-color: var(--surface-section);
}

:deep(.p-tabview .p-tabview-nav) {
	background-color: transparent;
}
</style>
