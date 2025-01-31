<template>
	<div class="parameter-entry flex flex-column flex-1" :class="{ empty: isParameterEmpty }">
		<header class="gap-1 pt-2 pb-2">
			<div class="flex">
				<strong>{{ parameterId }}</strong>
				<span v-if="name" class="ml-1">{{ '| ' + name }}</span>
				<template v-if="units">
					<label class="ml-auto">Unit:</label>
					<span class="ml-1 mr-3">{{ units }}</span>
				</template>

				<template v-if="concept">
					<label class="ml-6">Concept:</label>
					<span class="ml-1">{{ concept }}</span>
				</template>
			</div>

			<span class="description" v-if="description">{{ description }}</span>
		</header>
		<div v-if="inferredDistribution" class="inferred-parameter">
			<span class="type"><label>Type</label> {{ inferredDistribution.type }}</span>
			<span class="mean">
				<label>Mean</label> {{ displayNumber(inferredDistribution?.parameters?.mean?.toString()) }}
			</span>
			<span class="std">
				<label>STDDEV</label> {{ displayNumber(inferredDistribution?.parameters?.stddev?.toString()) }}
			</span>
		</div>
		<template v-else-if="!featureConfig?.isPreview">
			<main class="flex flex-wrap gap-2">
				<tera-distribution-input
					:model="model"
					:modelConfiguration="modelConfiguration"
					:parameter-id="parameterId"
					:parameter="getParameterDistribution(modelConfiguration, parameterId)"
					@update-parameter="emit('update-parameter', [$event])"
				>
				</tera-distribution-input>
				<section>
					<Button :label="getSourceLabel(parameterId)" text size="small" @click="isSourceOpen = !isSourceOpen" />
					<Button :label="getOtherValuesLabel" text size="small" @click="showOtherConfigValueModal = true" />
				</section>
			</main>
			<footer v-if="isSourceOpen" class="mb-2">
				<tera-input-text
					placeholder="Add a source"
					:model-value="getParameterSource(modelConfiguration, parameterId)"
					@update:model-value="emit('update-source', { id: parameterId, value: $event })"
				/>
			</footer>
		</template>
		<p class="flex gap-4" v-else>
			<template v-if="getParameterDistribution(modelConfiguration, parameterId).type === DistributionType.Constant">
				<span><label>Type</label> {{ distributions[DistributionType.Constant].label }}</span>
				<span>
					<label>Value</label>
					{{ getParameterDistribution(modelConfiguration, parameterId).parameters.value }}
				</span>
			</template>
			<template v-else-if="getParameterDistribution(modelConfiguration, parameterId).type === DistributionType.Uniform">
				<span><label>Type</label> {{ distributions[DistributionType.Uniform].label }}</span>
				<span>
					<label>Min</label>
					{{ getParameterDistribution(modelConfiguration, parameterId).parameters.minimum }}
				</span>
				<span>
					<label>Max</label>
					{{ getParameterDistribution(modelConfiguration, parameterId).parameters.maximum }}
				</span>
			</template>
		</p>
	</div>
	<tera-parameter-other-value-modal
		v-if="showOtherConfigValueModal"
		:id="parameterId"
		:otherValueList="otherValueList"
		@modal-mask-clicked="showOtherConfigValueModal = false"
		@update-parameter="emit('update-parameter', $event)"
		@update-source="emit('update-source', $event)"
		@close-modal="showOtherConfigValueModal = false"
	/>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { Model, ModelConfiguration } from '@/types/Types';
import {
	getParameterSource,
	getParameterDistribution,
	isNumberInputEmpty,
	getOtherValues
} from '@/services/model-configurations';
import TeraDistributionInput from '@/components/model/petrinet/tera-distribution-input.vue';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import Button from 'primevue/button';
import { DistributionType, distributions } from '@/services/distribution';
import { getParameter } from '@/model-representation/service';
import TeraParameterOtherValueModal from '@/components/model/petrinet/tera-parameter-other-value-modal.vue';
import { displayNumber } from '@/utils/number';
import { getCurieFromGroundingIdentifier, getNameOfCurieCached } from '@/services/concept';
import type { FeatureConfig } from '@/types/common';

const props = defineProps<{
	model: Model;
	modelConfiguration: ModelConfiguration;
	modelConfigurations: ModelConfiguration[];
	parameterId: string;
	featureConfig?: FeatureConfig;
}>();

const emit = defineEmits(['update-parameter', 'update-source']);

const name = getParameter(props.model, props.parameterId)?.name;
const units = getParameter(props.model, props.parameterId)?.units?.expression;
const description = getParameter(props.model, props.parameterId)?.description;
const inferredDistribution = computed(
	() =>
		props.modelConfiguration.inferredParameterList?.find((param) => param.referenceId === props.parameterId)
			?.distribution
);

const concept = ref('');
const isSourceOpen = ref(false);
const showOtherConfigValueModal = ref(false);
const isParameterEmpty = ref(false);

const otherValueList = computed(() =>
	getOtherValues(props.modelConfigurations, props.parameterId, 'referenceId', 'parameterSemanticList', description)
);

function getSourceLabel(initialId) {
	if (isSourceOpen.value) return 'Hide source';
	if (!getParameterSource(props.modelConfiguration, initialId)) return 'Add source';
	return 'Show source';
}

const getOtherValuesLabel = computed(() => `Other values (${otherValueList.value?.length})`);

function isParameterInputEmpty(parameter) {
	if (parameter.type === DistributionType.Constant) {
		return isNumberInputEmpty(parameter.parameters.value);
	}
	return isNumberInputEmpty(parameter.parameters.maximum) || isNumberInputEmpty(parameter.parameters.minimum);
}

onMounted(async () => {
	const identifiers = getParameter(props.model, props.parameterId)?.grounding?.identifiers;
	if (identifiers) concept.value = await getNameOfCurieCached(getCurieFromGroundingIdentifier(identifiers));
	const parameter = getParameterDistribution(props.modelConfiguration, props.parameterId, true);
	isParameterEmpty.value = inferredDistribution.value ? false : isParameterInputEmpty(parameter);
});
</script>

<style scoped>
.parameter-entry {
	background: var(--surface-0);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	border-left: 4px solid var(--surface-300);
	padding-left: var(--gap-4);
	padding-right: var(--gap-4);
	transition: all 0.15s;
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}
.parameter-entry:hover {
	border-left: 4px solid var(--primary-color);
	background: var(--surface-highlight);
}
.empty {
	border-left: 4px solid var(--error-color);
}

header {
	display: flex;
	flex-direction: column;
	gap: var(--gap-2);
	padding-bottom: var(--gap-2);
	padding-bottom: var(--gap-2);
}
main {
	display: flex;
	justify-content: space-between;
	padding-bottom: var(--gap-2);
}

.description {
	color: var(--text-color-subdued);
}

:deep(.p-dropdown-label) {
	min-width: 10rem;
}

label {
	color: var(--text-color-subdued);
}

.inferred-parameter {
	display: flex;
	& > span {
		width: 20%;
	}
}

:deep(.parameter-input) {
	max-height: 2.5rem;
}
</style>
