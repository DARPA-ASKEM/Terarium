<template>
	<div class="initial-entry" :class="{ empty: isExpressionEmpty }">
		<header class="gap-1">
			<div class="flex">
				<strong>{{ initialId }}</strong>
				<span v-if="name" class="ml-1">{{ '| ' + name }}</span>
				<template v-if="unit">
					<label class="ml-auto">Unit:</label>
					<span class="ml-1 mr-3">{{ unit }}</span>
				</template>
				<template v-if="concept">
					<label class="ml-6">Concept:</label>
					<span class="ml-1">{{ concept }}</span>
				</template>
			</div>
			<span v-if="description" class="description">{{ description }}</span>
		</header>
		<template v-if="isEmpty(modelConfiguration.inferredParameterList) && !featureConfig?.isPreview">
			<main class="flex align-items-center">
				<span class="expression">
					<tera-input-text
						label="Expression"
						error-empty
						:model-value="getExpression()"
						@blur="onExpressionChange($event)"
					/>
				</span>
				<Button :label="getSourceLabel(initialId)" text size="small" @click="sourceOpen = !sourceOpen" />
				<Button :label="getOtherValuesLabel" text size="small" @click="showOtherConfigValueModal = true" />
			</main>
			<footer v-if="sourceOpen">
				<tera-input-text
					placeholder="Add a source"
					:model-value="getInitialSource(modelConfiguration, initialId)"
					@update:model-value="emit('update-source', { id: initialId, value: $event })"
				/>
			</footer>
		</template>
		<katex-element
			v-else
			class="expression"
			:expression="stringToLatexExpression(getExpression())"
			:throw-on-error="false"
		/>
		<tera-initial-other-value-modal
			v-if="showOtherConfigValueModal"
			:id="initialId"
			:updateEvent="'update-expression'"
			:otherValueList="otherValueList"
			:otherValuesInputTypes="DistributionType.Constant"
			@modal-mask-clicked="showOtherConfigValueModal = false"
			@update-expression="emit('update-expression', $event)"
			@update-source="emit('update-source', $event)"
			@close-modal="showOtherConfigValueModal = false"
		/>
	</div>
</template>

<script setup lang="ts">
import { isEmpty } from 'lodash';
import { computed, ref, onMounted } from 'vue';
import { DistributionType } from '@/services/distribution';
import { Model, ModelConfiguration } from '@/types/Types';
import { getInitialExpression, getInitialSource, getOtherValues } from '@/services/model-configurations';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraInitialOtherValueModal from '@/components/model/petrinet/tera-initial-other-value-modal.vue';
import Button from 'primevue/button';
import { getInitialDescription, getInitialName, getInitialUnits, getStates } from '@/model-representation/service';
import { getCurieFromGroundingIdentifier, getNameOfCurieCached } from '@/services/concept';
import { stringToLatexExpression } from '@/services/model';
import type { FeatureConfig } from '@/types/common';

const props = defineProps<{
	model: Model;
	initialId: string;
	modelConfiguration: ModelConfiguration;
	modelConfigurations: ModelConfiguration[];
	featureConfig?: FeatureConfig;
}>();

const otherValueList = computed(() =>
	getOtherValues(props.modelConfigurations, props.initialId, 'target', 'initialSemanticList', description)
);

const emit = defineEmits(['update-expression', 'update-source']);

const name = getInitialName(props.model, props.initialId);
const unit = getInitialUnits(props.model, props.initialId);
const description = getInitialDescription(props.model, props.initialId);
const isExpressionEmpty = computed(() => isEmpty(getInitialExpression(props.modelConfiguration, props.initialId)));

const concept = ref('');
const sourceOpen = ref(false);
const showOtherConfigValueModal = ref(false);
const expression = ref('');

const getOtherValuesLabel = computed(() => `Other values (${otherValueList.value?.length})`);

function onExpressionChange(value) {
	emit('update-expression', { id: props.initialId, value });
}

function getExpression() {
	if (!isExpressionEmpty.value) {
		return getInitialExpression(props.modelConfiguration, props.initialId);
	}
	return expression.value;
}

function getSourceLabel(initialId) {
	if (sourceOpen.value) return 'Hide source';
	if (!getInitialSource(props.modelConfiguration, initialId)) return 'Add source';
	return 'Show source';
}

onMounted(async () => {
	const identifiers = getStates(props.model).find((state) => state.id === props.initialId)?.grounding?.identifiers;
	if (identifiers) concept.value = await getNameOfCurieCached(getCurieFromGroundingIdentifier(identifiers));
});
</script>

<style scoped>
.initial-entry {
	border-left: 4px solid var(--surface-300);
	padding-left: var(--gap-4);
	padding-right: var(--gap-4);
	border-radius: var(--border-radius);
	border: 1px solid var(--surface-border-light);
	border-left: 4px solid var(--surface-300);
	background-color: var(--surface-0);
	transition: all 0.15s;
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}
.initial-entry:hover {
	border-left: 4px solid var(--primary-color);
	background-color: var(--surface-highlight);
}
.empty {
	border-left: 4px solid var(--error-color);
}
header {
	display: flex;
	flex-direction: column;
	padding-top: var(--gap-2);
	padding-bottom: var(--gap-2);
	gap: var(--gap-2);
}
.description {
	color: var(--text-color-subdued);
}
.expression {
	flex-grow: 1;
}
main {
	display: flex;
	justify-content: space-between;
	padding-bottom: var(--gap-1);
}

label {
	color: var(--text-color-subdued);
}
</style>
