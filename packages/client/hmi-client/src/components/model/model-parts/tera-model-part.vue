<template>
	<ul v-bind="$attrs">
		<li
			v-for="({ base, children, isParent }, index) in filteredItems.slice(firstRow, firstRow + MAX_NUMBER_OF_ROWS)"
			:key="base.id"
			class="model-part"
		>
			<template v-if="isParent && !isEmpty(editingState)">
				<section class="parent">
					<span>
						<Button
							:icon="getEditingState(index).showChildren ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
							text
							rounded
							size="small"
							@click="getEditingState(index).showChildren = !getEditingState(index).showChildren"
							class="mr-1"
						/>
						<h6>{{ base.id }}</h6>
					</span>
					<!--N/A if it's a transition-->
					<div class="right-side">
						<template v-if="!featureConfig.isPreview && partType !== PartType.TRANSITION">
							<Button
								:disabled="getEditingState(index).isEditingChildrenUnits"
								@click="getEditingState(index).isEditingChildrenUnits = true"
								label="Add unit to all children"
								text
								size="small"
							/>
						</template>
						<Button v-if="showMatrix" label="Open matrix" text size="small" @click="$emit('open-matrix', base.id)" />
						<template v-if="!featureConfig.isPreview">
							<Button
								:disabled="getEditingState(index).isEditingChildrenConcepts"
								@click="getEditingState(index).isEditingChildrenConcepts = true"
								label="Add concept to all children"
								text
								size="small"
							/>
						</template>
					</div>
				</section>

				<!-- Add unit to all children toolbar -->
				<div v-if="getEditingState(index).isEditingChildrenUnits" class="add-to-all-children-toolbar">
					<tera-input-text placeholder="Add a unit" v-model="getEditingState(index).childrenUnits" />
					<Button
						icon="pi pi-check"
						text
						rounded
						size="small"
						@click="
							() => {
								updateAllChildren(base.id, 'unitExpression', getEditingState(index).childrenUnits);
								getEditingState(index).isEditingChildrenUnits = false;
							}
						"
					/>
					<Button
						icon="pi pi-times"
						text
						rounded
						size="small"
						@click="getEditingState(index).isEditingChildrenUnits = false"
					/>
				</div>

				<!-- Add concept to all children toolbar -->
				<div v-if="getEditingState(index).isEditingChildrenConcepts" class="add-to-all-children-toolbar">
					<span class="concept">
						<AutoComplete
							label="Concept"
							size="small"
							placeholder="Search concepts"
							v-model="getEditingState(index).childrenConcepts.name"
							:suggestions="results"
							optionLabel="name"
							@complete="
								async () => (results = await searchCuriesEntities(getEditingState(index).childrenConcepts.name))
							"
							@item-select="
								($event) => {
									const { name, curie } = $event.value;
									getEditingState(index).childrenConcepts = { name, curie };
								}
							"
						/>
					</span>
					<Button
						icon="pi pi-check"
						text
						rounded
						size="small"
						@click="
							() => {
								updateAllChildren(base.id, 'concept', getEditingState(index).childrenConcepts.curie);
								getEditingState(index).isEditingChildrenConcepts = false;
							}
						"
					/>
					<Button
						icon="pi pi-times"
						text
						rounded
						size="small"
						@click="getEditingState(index).isEditingChildrenConcepts = false"
					/>
				</div>

				<div class="stratified" v-if="getEditingState(index).showChildren">
					<ul>
						<li
							v-for="child in children.slice(
								getEditingState(index).firstRow,
								getEditingState(index).firstRow + MAX_NUMBER_OF_ROWS
							)"
							:key="child.id"
						>
							<tera-model-part-entry
								:part-type="partType"
								:description="child.description"
								:name="child.name"
								:id="child.id"
								:grounding="child.grounding"
								:templateId="child.templateId"
								:input="child.input"
								:output="child.output"
								:unitExpression="child.unitExpression"
								:expression="child.expression"
								:feature-config="featureConfig"
								@update-item="$emit('update-item', { id: child.id, ...$event })"
							/>
						</li>
					</ul>
					<Paginator
						v-if="children.length > MAX_NUMBER_OF_ROWS"
						:rows="MAX_NUMBER_OF_ROWS"
						:first="getEditingState(index).firstRow"
						:total-records="children.length"
						:template="{
							default: 'FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink JumpToPageDropdown'
						}"
						@page="getEditingState(index).firstRow = $event.first"
					/>
				</div>
			</template>
			<tera-model-part-entry
				v-else
				:part-type="partType"
				:description="base.description"
				:name="base.name"
				:id="base.id"
				:grounding="base.grounding"
				:templateId="base.templateId"
				:input="base.input"
				:output="base.output"
				:unitExpression="base.unitExpression"
				:expression="base.expression"
				:feature-config="featureConfig"
				@update-item="$emit('update-item', { id: base.id, ...$event })"
			/>
		</li>
	</ul>
	<Paginator
		v-if="filteredItems.length > MAX_NUMBER_OF_ROWS"
		:rows="MAX_NUMBER_OF_ROWS"
		:first="firstRow"
		:total-records="filteredItems.length"
		:template="{
			default: 'FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink'
		}"
		@page="firstRow = $event.first"
	/>
</template>

<script setup lang="ts">
import { isEmpty } from 'lodash';
import { ref, computed } from 'vue';
import type { ModelPartItem } from '@/types/Model';
import type { DKG } from '@/types/Types';
import { searchCuriesEntities } from '@/services/concept';
import TeraModelPartEntry from '@/components/model/model-parts/tera-model-part-entry.vue';
import AutoComplete from 'primevue/autocomplete';
import Button from 'primevue/button';
import type { FeatureConfig } from '@/types/common';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import Paginator from 'primevue/paginator';
import { PartType } from '@/model-representation/service';

const props = defineProps<{
	items: any[];
	featureConfig: FeatureConfig;
	collapsedItems?: Map<string, string[]>;
	showMatrix?: boolean;
	partType: PartType;
	filter?: string;
}>();

const emit = defineEmits(['update-item', 'open-matrix']);

const MAX_NUMBER_OF_ROWS = 5;

const editingState = ref(
	Array.from({ length: props.items.length }, () => ({
		showChildren: false,
		isEditingChildrenUnits: false,
		isEditingChildrenConcepts: false,
		childrenUnits: '',
		childrenConcepts: { name: '', curie: '' },
		firstRow: 0
	}))
);
const results = ref<DKG[]>([]);
const firstRow = ref(0);

const filteredItems = computed(() => {
	const filterText = props.filter?.toLowerCase() ?? '';
	if (!filterText) return props.items;
	return props.items
		.map(({ base, children, isParent }) => {
			const filteredChildren = children.filter((child) => child.id.toLowerCase().includes(filterText));
			const baseMatches =
				base.id.toLowerCase().includes(filterText) || base.templateId?.toLowerCase().includes(filterText);
			const childrenMatch = filteredChildren.length > 0;
			if (baseMatches || childrenMatch) {
				return { base, children: filteredChildren, isParent };
			}
			return null;
		})
		.filter(Boolean) as { base: ModelPartItem; children: ModelPartItem[]; isParent: boolean }[];
});

// Maps filtered indices to original indices
const filteredToOriginalIndex = computed(() => {
	const indexMap = new Map();
	filteredItems.value.forEach((item, filteredIndex) => {
		const originalIndex = props.items.findIndex((original) => original.base.id === item.base.id);
		indexMap.set(filteredIndex, originalIndex);
	});
	return indexMap;
});
// Update references to parentEditingState to use the mapping
function getEditingState(filteredIndex: number) {
	const originalIndex = filteredToOriginalIndex.value.get(filteredIndex);
	return editingState.value[originalIndex];
}

function updateAllChildren(base: string, key: string, value: string) {
	if (isEmpty(value) || !props.collapsedItems) return;
	const ids = props.collapsedItems.get(base);
	ids?.forEach((id) => emit('update-item', { id, key, value }));
}
</script>

<style scoped>
ul {
	display: flex;
	flex-direction: column;
	list-style: none;
	gap: var(--gap-2);
}

.model-part {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	padding: var(--gap-3);
	border-left: 4px solid var(--surface-border);
	background: var(--surface-0);
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
	transition: all 0.15s;
	&:has(.parent) {
		padding: var(--gap-2) 0 var(--gap-2) var(--gap-1);
	}
}
/* First set the hover state for the model-part itself */
.model-part:hover {
	border-left: 4px solid var(--primary-color);
	background: var(--surface-highlight);
}
/* But set a lighter hover state when hovering over child elements */
.model-part:hover:has(.stratified > ul > li:hover) {
	border-left: 4px solid var(--primary-color-light);
	background: color-mix(in srgb, var(--surface-highlight) 30%, var(--surface-0) 70%);
}

li {
	padding-bottom: var(--gap-2);
	border-bottom: 1px solid var(--surface-border-light);
}

.parent,
.parent > span {
	display: flex;
	justify-content: space-between;
	align-items: center;
}

.stratified {
	margin: var(--gap-2) var(--gap-3) 0 var(--gap-3);
	& > ul {
		& > li {
			background: var(--surface-0);
			border: 1px solid var(--surface-border-light);
			border-radius: var(--border-radius);
			border-left: 4px solid var(--surface-border);
			padding-left: var(--gap-4);
			padding-right: var(--gap-2);
			padding-bottom: var(--gap-2);
			padding-top: var(--gap-2);
			&:hover {
				background: var(--surface-highlight);
				border-left-color: var(--primary-color);
			}
		}
	}
}

.concept {
	display: flex;
	align-items: center;
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
	gap: var(--gap-1);
}

:deep(.p-autocomplete-input) {
	padding: var(--gap-1) var(--gap-2);
}

.add-to-all-children-toolbar {
	display: flex;
	align-items: center;
	gap: var(--gap-1);
	padding: var(--gap-1) var(--gap-4);
	background-color: var(--surface-highlight);
	border-radius: var(--border-radius);
	margin-left: var(--gap-3);
	margin-right: var(--gap-3);
	margin-bottom: var(--gap-1);
}
</style>
