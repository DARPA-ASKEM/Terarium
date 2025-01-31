<template>
	<Accordion multiple :active-index="currentActiveIndicies">
		<AccordionTab>
			<template #header>
				<span class="mr-auto">
					Parameters<span class="artifact-amount">({{ numParameters }})</span>
				</span>
				<Button
					v-if="!isAddingUncertainty && !featureConfig?.isPreview"
					label="Add uncertainty"
					icon="pi pi-question"
					outlined
					severity="secondary"
					size="small"
					@click.stop="onAddUncertainty"
					class="mr-2"
				/>
				<tera-input-text v-model="filterText" placeholder="Filter" class="w-2 p-1" />
			</template>

			<!-- Adding uncertainty header -->
			<span v-if="isAddingUncertainty" class="add-uncertainty-toolbar">
				<Button size="small" text label="Unselect all" style="min-width: 4.6rem" @click="selectedParameters = []" />
				Add
				<Dropdown
					v-model="uncertaintyType"
					option-label="name"
					option-value="value"
					:options="distributionTypeOptions().filter((type) => type.value !== DistributionType.Constant)"
				>
					<template #value>
						{{ distributions[uncertaintyType].label.toLowerCase() }}
					</template>
					<template #option="{ option }">
						{{ option.name.toLowerCase() }}
					</template>
				</Dropdown>
				uncertainty with ±
				<InputNumber class="uncertainty-percentage" v-model="uncertaintyPercentage" suffix="%" :min="0" :max="100" />
				bounds on the value of the selected constant parameters.
				<Button
					outlined
					severity="secondary"
					small
					icon="pi pi-check"
					label="Apply"
					size="small"
					@click="onUpdateDistributions"
					class="ml-auto"
					style="min-width: 5.5rem"
				/>
				<Button text rounded small icon="pi pi-times" @click="isAddingUncertainty = false" class="ml-4" />
			</span>

			<ul class="pl-1">
				<li v-for="{ baseParameter, childParameters, isVirtual } in parameterList" :key="baseParameter">
					<!-- Stratified -->
					<section v-if="isVirtual" class="parameter-entry-stratified">
						<Accordion multiple>
							<AccordionTab>
								<template #header>
									<div class="flex align-items-center w-full">
										<span>{{ baseParameter }}</span>
										<!--- Select all checkbox -->
										<div
											v-if="isAddingUncertainty"
											class="mx-4 flex align-items-center gap-2"
											@click.stop="updateSelection(baseParameter, childParameters)"
										>
											<Checkbox
												:model-value="getChildrenSelectedState(childParameters).all"
												:indeterminate="getChildrenSelectedState(childParameters).some"
												:class="getChildrenSelectedState(childParameters).some ? 'p-checkbox-indeterminate' : ''"
												binary
											/>
											<label class="text-sm font-normal cursor-pointer">
												{{ getSelectionLabel(childParameters) }}
											</label>
										</div>
										<Button
											label="Open matrix"
											text
											size="small"
											@click.stop="matrixModalId = baseParameter"
											class="ml-auto"
										/>
									</div>
								</template>
								<div class="flex">
									<ul class="ml-1">
										<li v-for="{ referenceId } in childParameters" :key="referenceId">
											<div class="flex gap-4">
												<Checkbox
													v-if="
														isAddingUncertainty &&
														getParameterDistribution(modelConfiguration, referenceId).type === DistributionType.Constant
													"
													binary
													:model-value="selectedParameters.includes(referenceId)"
													@change="onSelect(referenceId)"
												/>
												<tera-parameter-entry
													:model="model"
													:model-configuration="props.modelConfiguration"
													:model-configurations="props.modelConfigurations"
													:parameter-id="referenceId"
													:featureConfig="featureConfig"
													@update-parameter="emit('update-parameters', [$event])"
													@update-source="emit('update-source', $event)"
												/>
											</div>
										</li>
									</ul>
								</div>
							</AccordionTab>
						</Accordion>
					</section>
					<!-- Unstratified -->
					<div v-else class="flex gap-4">
						<Checkbox
							v-if="
								isAddingUncertainty &&
								getParameterDistribution(modelConfiguration, baseParameter).type === DistributionType.Constant
							"
							binary
							:model-value="selectedParameters.includes(baseParameter)"
							@change="onSelect(baseParameter)"
						/>
						<tera-parameter-entry
							:model="model"
							:model-configuration="modelConfiguration"
							:modelConfigurations="modelConfigurations"
							:parameter-id="baseParameter"
							:featureConfig="featureConfig"
							@update-parameter="emit('update-parameters', [$event])"
							@update-source="emit('update-source', $event)"
						/>
					</div>
				</li>
			</ul>
		</AccordionTab>
	</Accordion>

	<tera-stratified-matrix-modal
		v-if="matrixModalId && isStratified"
		:id="matrixModalId"
		:mmt="mmt"
		:mmt-params="mmtParams"
		:stratified-matrix-type="StratifiedMatrix.Parameters"
		:open-value-config="!!matrixModalId"
		@close-modal="matrixModalId = ''"
		@update-cell-values="
			emit(
				'update-parameters',
				$event.map((e) => ({
					id: e.id,
					distribution: {
						type: DistributionType.Constant,
						parameters: { value: e.value }
					}
				}))
			)
		"
	/>
</template>

<script setup lang="ts">
import { Model, ModelConfiguration, ModelDistribution, ParameterSemantic } from '@/types/Types';
import { getParameterDistribution, getParameters } from '@/services/model-configurations';
import { StratifiedMatrix } from '@/types/Model';
import { computed, ref } from 'vue';
import { collapseParameters, isStratifiedModel } from '@/model-representation/mira/mira';
import { MiraModel, MiraTemplateParams } from '@/model-representation/mira/mira-common';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Button from 'primevue/button';
import { DistributionType, distributions, distributionTypeOptions } from '@/services/distribution';
import InputNumber from 'primevue/inputnumber';
import Dropdown from 'primevue/dropdown';
import Checkbox from 'primevue/checkbox';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import type { FeatureConfig } from '@/types/common';
import { calculateUncertaintyRange } from '@/utils/math';
import TeraParameterEntry from './tera-parameter-entry.vue';
import TeraStratifiedMatrixModal from './model-configurations/tera-stratified-matrix-modal.vue';

const props = defineProps<{
	model: Model;
	modelConfiguration: ModelConfiguration;
	modelConfigurations: ModelConfiguration[];
	mmt: MiraModel;
	mmtParams: MiraTemplateParams;
	featureConfig?: FeatureConfig;
}>();

const currentActiveIndicies = ref([0]);

const emit = defineEmits(['update-parameters', 'update-source']);

const isStratified = isStratifiedModel(props.mmt);

const isAddingUncertainty = ref(false);
const uncertaintyType = ref(DistributionType.Uniform);
const uncertaintyPercentage = ref(10);
const selectedParameters = ref<string[]>([]);
const filterText = ref('');

const numParameters = computed(() => parameterList.value.length);
const parameterList = computed<{ baseParameter: string; childParameters: ParameterSemantic[]; isVirtual: boolean }[]>(
	() => {
		const collapsedParameters = collapseParameters(props.mmt, props.mmtParams);
		const parameters = getParameters(props.modelConfiguration);
		return Array.from(collapsedParameters.keys())
			.map((id) => {
				const childIds = collapsedParameters.get(id) ?? [];
				const childParameters = childIds
					.map((childId) => parameters.find((p) => p.referenceId === childId))
					.filter(Boolean) as ParameterSemantic[];
				const isVirtual = childIds.length > 1;
				const baseParameter = id;

				return { baseParameter, childParameters, isVirtual };
			})
			.filter(({ baseParameter }) => baseParameter.toLowerCase().includes(filterText.value.toLowerCase()));
	}
);

const matrixModalId = ref('');

const onAddUncertainty = () => {
	const selected = Object.keys(props.mmt.parameters).filter(
		(paramId) => getParameterDistribution(props.modelConfiguration, paramId).type === DistributionType.Constant
	);
	selectedParameters.value = selected;
	isAddingUncertainty.value = true;
};

const onSelect = (paramId: string) => {
	if (selectedParameters.value.includes(paramId)) {
		selectedParameters.value.splice(selectedParameters.value.indexOf(paramId), 1);
	} else {
		selectedParameters.value.push(paramId);
	}
};

const onUpdateDistributions = () => {
	const distributionParameterMappings: { id: string; distribution: ModelDistribution }[] = [];
	if (uncertaintyType.value === DistributionType.Uniform) {
		selectedParameters.value.forEach((paramId) => {
			const distribution = getParameterDistribution(props.modelConfiguration, paramId);
			if (distribution.type !== DistributionType.Constant) return;

			const { min, max } = calculateUncertaintyRange(distribution.parameters.value, uncertaintyPercentage.value);

			const distributionParameterMapping = {
				id: paramId,
				distribution: {
					type: uncertaintyType.value,
					// A way to get around the floating point precision issue is to set a fixed number of decimal places and parse as a float
					// This will be an issue for adding uncertainty to very small numbers, but I think 8 decimal points should do
					parameters: {
						minimum: min,
						maximum: max
					}
				}
			};
			distributionParameterMappings.push(distributionParameterMapping);
		});
	}

	emit('update-parameters', distributionParameterMappings);
	isAddingUncertainty.value = false;
};

/* Handle selection */
const getSelectionLabel = (childParameters: ParameterSemantic[]) => {
	const { all, some } = getChildrenSelectedState(childParameters);
	if (all) return 'All selcted';
	if (some) return 'Some selected';
	return 'None selected';
};

const getChildrenSelectedState = (childParameters: ParameterSemantic[]) => {
	const selectableChildren = childParameters.filter(
		({ referenceId }) =>
			getParameterDistribution(props.modelConfiguration, referenceId).type === DistributionType.Constant
	);

	const all =
		selectableChildren.length > 0 &&
		selectableChildren.every(({ referenceId }) => selectedParameters.value.includes(referenceId));

	const some = selectableChildren.some(({ referenceId }) => selectedParameters.value.includes(referenceId)) && !all;

	return { all, some };
};
function updateSelection(baseParameter: string, childParameters: ParameterSemantic[]) {
	const selectableChildren = childParameters
		.filter(
			({ referenceId }) =>
				getParameterDistribution(props.modelConfiguration, referenceId).type === DistributionType.Constant
		)
		.map(({ referenceId }) => referenceId);

	if (getChildrenSelectedState(childParameters).all) {
		selectedParameters.value = selectedParameters.value.filter((id) => !selectableChildren.includes(id));
	} else {
		selectedParameters.value = [...new Set([...selectedParameters.value, ...selectableChildren])];
	}
}
</script>

<style scoped>
ul {
	flex-grow: 1;

	li {
		list-style: none;
	}

	li + li {
		margin-top: var(--gap-1-5);
	}

	li:last-child {
		margin-bottom: var(--gap-1);
	}
}

.parameter-entry-stratified {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	background: var(--surface-0);
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
	border-left: 4px solid var(--surface-300);
	padding-left: var(--gap-1);
}
.parameter-entry-stratified:hover {
	border-left-color: var(--primary-color);
	background: var(--surface-highlight);
}
/* But set a lighter hover state when hovering over child elements */
.parameter-entry-stratified:hover:has(.parameter-entry:hover) {
	border-left: 4px solid var(--primary-color-light);
	background: color-mix(in srgb, var(--surface-highlight) 30%, var(--surface-0) 70%);
}

.stratified {
	ul {
		border-left: 1px solid var(--gray-300);
		margin-left: var(--gap-2);
		padding-left: var(--gap-4);
	}

	li {
		display: flex;
		gap: var(--gap-4);
	}
}

.artifact-amount {
	font-size: var(--font-caption);
	color: var(--text-color-subdued);
	margin-left: var(--gap-1);
}

.add-uncertainty-toolbar {
	display: flex;
	align-items: center;
	gap: var(--gap-2);
	background-color: var(--surface-highlight);
	padding: var(--gap-2);
	padding-left: 0;
	margin-bottom: var(--gap-2);
	font-size: var(--font-caption);
	border-radius: var(--border-radius);
	border: 3px solid var(--primary-color);
}

:deep(.uncertainty-percentage) > input {
	width: 4rem;
}
:deep(.p-accordion-content) {
	padding-top: 0;
}

/* Checkbox: Indeterminate hackary */
:deep(.p-checkbox-indeterminate .p-checkbox-box) {
	background-color: var(--text-color-secondary);
	position: relative;
}

:deep(.p-checkbox-indeterminate .p-checkbox-box)::after {
	content: '';
	position: absolute;
	top: 50%;
	left: 50%;
	width: 10px;
	height: 2px;
	background-color: white;
	transform: translate(-50%, -50%);
}
</style>
