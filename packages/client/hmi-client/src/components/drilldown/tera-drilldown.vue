<template>
	<aside class="overlay-container">
		<tera-tooltip
			:class="{ 'no-connections': isEmpty(upstreamOperatorsNav?.[0].items) }"
			:show-tooltip="!(upstreamMenu as any)?.focused"
		>
			<Button
				ref="leftChevronButton"
				icon="pi pi-chevron-left"
				outlined
				severity="secondary"
				@click="toggleNavigationMenu($event, upstreamMenu, upstreamOperatorsNav)"
			/>
			<Menu ref="upstreamMenu" class="ml-5" popup :model="upstreamOperatorsNav" :pt="menuPt" />
			<template #tooltip-content>
				<span class="operator-nav-info">
					<template v-if="upstreamOperatorsNav?.[0]?.items?.length === 1">
						<label>Upstream operator</label>
						<span>
							<i :class="upstreamOperatorsNav[0].items[0].icon" />
							<label>{{ upstreamOperatorsNav[0].items[0].label }}</label>
						</span>
					</template>
					<label v-else>Upstream operators</label>
					<span class="kbd-shortcut">
						<kbd>Shift</kbd>+<kbd><i class="pi pi-arrow-left" /></kbd>
					</span>
				</span>
			</template>
		</tera-tooltip>
		<section v-bind="$attrs" :class="spawnAnimationRef">
			<tera-drilldown-header
				:active-index="selectedViewIndex"
				:views="views"
				:tooltip="tooltip"
				:documentation-url="node.documentationUrl"
				@tab-change="handleTabChange"
				@close="emit('on-close-clicked')"
			>
				{{ title ?? node.displayName }}
				<template #top-header-actions>
					<aside class="chips">
						<Chip
							v-for="(input, index) in node.inputs.filter((input) => input.value)"
							:key="index"
							:label="useProjects().getAssetName(input.value?.[0]) || input.label"
						>
							<template #icon>
								<tera-operator-port-icon v-if="input.type" :portType="input.type" />
							</template>
						</Chip>
					</aside>
					<template v-if="!hideDropdown && outputOptions && selectedOutputId">
						<section v-if="isDraft">There are unsaved changes</section>
						<tera-output-dropdown
							:class="{ draft: isDraft }"
							:options="outputOptions"
							:output="selectedOutputId"
							@update:selection="(e) => emit('update:selection', e)"
						/>
					</template>
				</template>
				<template #actions>
					<tera-operator-annotation :state="node.state" @update-state="(state: any) => emit('update-state', state)" />
					<slot name="header-actions" />
				</template>
			</tera-drilldown-header>
			<main class="flex overflow-hidden h-full">
				<slot name="sidebar" />
				<tera-columnar-panel class="flex-1">
					<template v-for="(tab, index) in tabs" :key="index">
						<!--
							TODO: We used to use v-show here but it ruined the rendering of tera-model-diagram
							if it was in the unselected tab. For now we are using v-if but we may want to
							use css to hide the unselected tab content instead.
						-->
						<component :is="tab" v-if="selectedViewIndex === index" />
					</template>
					<section v-if="slots.preview">
						<slot name="preview" />
					</section>
				</tera-columnar-panel>
				<slot name="sidebar-right" />
			</main>
			<footer v-if="slots.footer">
				<slot name="footer" />
			</footer>
		</section>
		<tera-tooltip
			:class="{ 'no-connections': isEmpty(downstreamOperatorsNav?.[0].items) }"
			:show-tooltip="!(downstreamMenu as any)?.focused"
			position="left"
		>
			<Button
				ref="rightChevronButton"
				icon="pi pi-chevron-right"
				outlined
				severity="secondary"
				@click="toggleNavigationMenu($event, downstreamMenu, downstreamOperatorsNav)"
			/>
			<Menu ref="downstreamMenu" class="-ml-5" popup :model="downstreamOperatorsNav" :pt="menuPt" />
			<template #tooltip-content>
				<span class="operator-nav-info">
					<template v-if="downstreamOperatorsNav?.[0]?.items?.length === 1">
						<label>Downstream operator</label>
						<span>
							<i :class="downstreamOperatorsNav[0].items[0].icon" />
							<label>{{ downstreamOperatorsNav[0].items[0].label }}</label>
						</span>
					</template>
					<label v-else>Downstream operators</label>
					<span class="kbd-shortcut">
						<kbd>Shift</kbd>+<kbd><i class="pi pi-arrow-right" /></kbd>
					</span>
				</span>
			</template>
		</tera-tooltip>
	</aside>
</template>

<script setup lang="ts">
import { isEmpty } from 'lodash';
import { ref, computed, onMounted, onUnmounted, useSlots, ComponentPublicInstance } from 'vue';
import Button from 'primevue/button';
import Chip from 'primevue/chip';
import Menu from 'primevue/menu';
import { MenuItem, MenuItemCommandEvent } from 'primevue/menuitem';
import type { TabViewChangeEvent } from 'primevue/tabview';
import { type WorkflowNode } from '@/types/workflow';
import { isAssetOperator } from '@/services/workflow';
import TeraDrilldownHeader from '@/components/drilldown/tera-drilldown-header.vue';
import TeraColumnarPanel from '@/components/widgets/tera-columnar-panel.vue';
import TeraOperatorAnnotation from '@/components/operator/tera-operator-annotation.vue';
import TeraOperatorPortIcon from '@/components/operator/tera-operator-port-icon.vue';
import TeraOutputDropdown from '@/components/drilldown/tera-output-dropdown.vue';
import TeraTooltip from '@/components/widgets/tera-tooltip.vue';
import { useProjects } from '@/composables/project';

const props = defineProps<{
	node: WorkflowNode<any>;
	title?: string;
	tooltip?: string;
	isDraft?: boolean;
	// Applied in dynamic compoenent in tera-workflow.vue
	upstreamOperatorsNav?: MenuItem[];
	downstreamOperatorsNav?: MenuItem[];
	spawnAnimation?: 'left' | 'right' | 'scale';
	hideDropdown?: boolean;
}>();

const emit = defineEmits(['on-close-clicked', 'update-state', 'update:selection']);

const slots = useSlots();

/**
 * This will retrieve and filter all top level components in the default slot if they have the tabName prop.
 */
const tabs = computed(() => {
	if (slots.default?.()) {
		if (slots.default().length === 1) {
			// if there is only 1 component we don't need to know the tab name and we can render it.
			return slots.default();
		}
		return slots.default().filter((vnode) => vnode.props?.tabName);
	}
	return [];
});

const views = computed(() => tabs.value.map((vnode) => vnode.props?.tabName));

const selectedViewIndex = ref<number>(0);
const handleTabChange = (event: TabViewChangeEvent) => {
	selectedViewIndex.value = event.index;
};

const selectedTab = computed(() => views.value[selectedViewIndex.value]);
defineExpose({ selectedTab });

const selectedOutputId = computed(() => props.node.active ?? null);

const outputOptions = computed(() => {
	// We do not display output selection for Asset operators
	if (isAssetOperator(props.node.operationType)) {
		return null;
	}

	if (isEmpty(props.node.outputs)) return [];

	return [
		{
			label: 'Select an output',
			items: props.node.outputs
		}
	];
});

// Drilldown navigation and animations
const leftChevronButton = ref<ComponentPublicInstance<typeof Button> | null>(null);
const rightChevronButton = ref<ComponentPublicInstance<typeof Button> | null>(null);
const upstreamMenu = ref<Menu | null>(null);
const downstreamMenu = ref<Menu | null>(null);
const menuPt = {
	root: {
		style: 'margin-top: -6rem; width: auto; height: auto;'
	},
	submenuHeader: {
		style: 'color: var(--text-color-subdued); font-weight: var(--font-weight); padding-top: 0.2rem; '
	},
	icon: {
		style: 'color: var(--text-color);'
	}
};
const toggleNavigationMenu = (
	event: MouseEvent | KeyboardEvent,
	menu: Menu | null,
	operatorsNav?: MenuItem[],
	button?: ComponentPublicInstance<typeof Button> | null
) => {
	const navItems = operatorsNav?.[0]?.items;
	if (!navItems || isEmpty(navItems)) return; // Prevents keyboard shortcut from toggling hidden button and empty menu

	// If there is only one item in the menu, just navigate to that one
	if (navItems.length === 1 && navItems[0]?.command) {
		const dummyEvent: MenuItemCommandEvent = { originalEvent: event, item: navItems[0] };
		navItems[0].command(dummyEvent);
	}
	// Keyboard event will mimic clicking the navigation button to open the menu where expected
	else if (event instanceof KeyboardEvent && button) {
		button.$el.dispatchEvent(new MouseEvent('click'));
	}
	// Regular @click event
	else menu?.toggle(event);
};

function handleKeyNavigation(event: KeyboardEvent) {
	const target = event.target as HTMLElement;
	if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
		return; // Prevent navigation if the user is editing text
	}
	if (event.shiftKey && event.key === 'ArrowLeft') {
		toggleNavigationMenu(event, upstreamMenu.value, props.upstreamOperatorsNav, leftChevronButton.value);
	} else if (event.shiftKey && event.key === 'ArrowRight') {
		toggleNavigationMenu(event, downstreamMenu.value, props.downstreamOperatorsNav, rightChevronButton.value);
	}
}

// Animation class must be applied on mounted to avoid flickering
const spawnAnimationRef = ref('');
onMounted(() => {
	spawnAnimationRef.value = props.spawnAnimation ?? 'scale';
	window.addEventListener('keydown', handleKeyNavigation);
});

onUnmounted(() => window.removeEventListener('keydown', handleKeyNavigation));
</script>

<style scoped>
.overlay-container {
	isolation: isolate;
	z-index: var(--z-index, var(--z-index-modal));
	position: fixed;
	width: 100%;
	height: 100%;
	background-color: rgba(0, 0, 0, 0.32);
	padding: var(--gap-4) var(--gap-1);
	padding-bottom: 0;
	display: flex;
	gap: var(--gap-1);
	backdrop-filter: blur(2px);

	/* There is a performance issue with these large modals.
	When scrolling it takes time to render the content, particularly heavy content such as the LLM integrations. This will show
	us the main application behind the modal temporarily as content loads when scrolling which is a bit of an eye sore.
	An extra div here is used to alleviate the impact of these issues a little by allowing us to see the overlay container rather
	than the main application behind the modal when these render issues come, however this is still an issue regardless.
	*/
	& > section {
		flex: 1;
		background: var(--surface-0);
		border-radius: var(--modal-border-radius) var(--modal-border-radius) 0 0;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		&.left {
			animation: fadeLeft 0.15s ease-out;
		}
		&.right {
			animation: fadeRight 0.15s ease-out;
		}
		&.scale {
			animation: scaleForward 0.15s ease-out;
		}
	}

	& > div {
		align-self: center;
		&.no-connections {
			visibility: hidden;
		}

		& > button {
			height: 4rem;
			width: 20px;
			border-radius: var(--border-radius-big);
		}
	}
}

.operator-nav-info {
	display: flex;
	flex-direction: column;
	flex-wrap: nowrap;
	padding: var(--gap-1-5) var(--gap-2);
	gap: var(--gap-3);
	white-space: nowrap;

	& > span {
		display: flex;
		align-items: center;
		& > i {
			margin-right: var(--gap-2);
		}
	}

	& > label {
		color: var(--text-color-subdued);
	}
}

.chips {
	display: flex;
	gap: var(--gap-1);
	margin-right: auto;
	overflow-x: auto;
	scrollbar-width: thin;
}

footer {
	padding: 0 1.5rem 1rem 1.5rem;
	display: flex;
	justify-content: flex-end;
	gap: 0.5rem;
}

.p-chip {
	background-color: var(--surface-section);
	color: var(--text-color-primary);
}

:deep(.p-chip .p-chip-text) {
	font-size: 13px;
	margin: var(--gap-0-5);
}

.draft {
	border-color: var(--warning-color);
	background-color: var(--surface-warning);
}

@keyframes scaleForward {
	from {
		opacity: 0.5;
		scale: 0.5;
	}
	to {
		opacity: 1;
		scale: 1;
	}
}

@keyframes fadeLeft {
	from {
		opacity: 0.5;
		transform: translateX(10rem);
	}
	to {
		opacity: 1;
		transform: translateX(0);
	}
}

@keyframes fadeRight {
	from {
		opacity: 0.5;
		transform: translateX(-10rem);
	}
	to {
		opacity: 1;
		transform: translateX(0);
	}
}
</style>
