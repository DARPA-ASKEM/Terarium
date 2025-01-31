<template>
	<div class="chart-settings-panel-anchor">
		<transition>
			<div class="chart-settings-panel" v-if="activeSettings !== null">
				<header :class="{ shadow: false }">
					<Button :icon="`pi pi-times`" @click="$emit('close')" text rounded size="large" />
					<h4 class="line-wrap">{{ activeSettings.name }}</h4>
				</header>
				<div class="content items-wrapper">
					<section v-if="chartAnnotations !== undefined" class="annotation-items">
						<h5>Annotations</h5>
						<tera-input-text
							v-model="generateAnnotationQuery"
							:icon="'pi pi-sparkles'"
							:placeholder="'What do you want to annotate?'"
							:disabled="!!isGeneratingAnnotation"
							@keyup.enter="createAnnotationDebounced"
							@keyup.esc="cancelGenerateAnnotation"
							class="annotation-input"
						/>
						<div v-for="annotation in chartAnnotations" :key="annotation.id" class="annotation-item">
							{{ annotation.description }}
							<span class="btn-wrapper">
								<Button icon="pi pi-trash" rounded text @click="$emit('delete-annotation', annotation.id)" />
							</span>
						</div>
						<Divider />
					</section>
					<section class="items-wrapper">
						<h5>Options</h5>
						<tera-checkbox label="Use log scale" :model-value="useLog" @update:model-value="toggleLogScale($event)" />
						<tera-checkbox
							label="Hide in node"
							:model-value="isHiddenInNode"
							@update:model-value="toggleHideInNode($event)"
						/>
						<Divider />
					</section>
					<section v-if="isColorPickerEnabled">
						<h5 class="mb-3">Color picker</h5>
						<input type="color" :value="activeSettings?.primaryColor ?? ''" @change="onColorChange($event)" />
						<Divider />
					</section>
					<section v-if="activeSettings?.type === ChartSettingType.VARIABLE_COMPARISON" class="items-wrapper">
						<h5>Comparison method</h5>
						<div>
							<RadioButton
								:model-value="smallMultiplesRadioValue"
								@update:model-value="onSmallMultiplesRadioButtonChange"
								inputId="all-charts"
								value="all-charts"
							/>
							<label for="all-charts" class="ml-2">All in one chart</label>
						</div>
						<div>
							<RadioButton
								:model-value="smallMultiplesRadioValue"
								@update:model-value="onSmallMultiplesRadioButtonChange"
								inputId="small-multiples"
								value="small-multiples"
							/>
							<label for="small-multiples" class="ml-2">Small multiples</label>
						</div>
						<div class="pl-5 items-wrapper">
							<tera-checkbox
								label="Same Y axis for all"
								:disabled="!comparisonSettings?.smallMultiples"
								:model-value="isShareYAxis"
								@update:model-value="toggleShareYAxis($event)"
							/>
							<tera-checkbox
								label="Show before and after"
								:disabled="!comparisonSettings?.smallMultiples"
								:model-value="showBeforeAfter"
								@update:model-value="toggleShowBeforeAfter($event)"
							/>
						</div>
						<Divider />
						<h5>Normalize</h5>
						<tera-checkbox
							label="Normalize data by total strata population"
							:model-value="normalizeData"
							@update:model-value="toggleNormalizeData($event)"
						/>
						<slot v-if="normalizeData" name="normalize-content"></slot>
						<Divider />
					</section>
				</div>
			</div>
		</transition>
	</div>
</template>

<script setup lang="ts">
import _ from 'lodash';
import { ref, computed } from 'vue';
import Button from 'primevue/button';
import RadioButton from 'primevue/radiobutton';
import { ChartSetting, ChartSettingType, ChartSettingComparison } from '@/types/common';
import { ChartAnnotation } from '@/types/Types';
import Divider from 'primevue/divider';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraCheckbox from '@/components/widgets/tera-checkbox.vue';

const props = defineProps<{
	activeSettings: ChartSetting | null;
	annotations?: ChartAnnotation[];
	/**
	 * We receives generateAnnotation as a functor from the parent to access the parent scope directly. This allows us to utilize dependencies defined in the parent component without passing them all as props, which can be cumbersome.
	 * Additionally, it enables us to handle post-generation actions (like resetting loading state or clearing input) after function completion.
	 * @param setting ChartSetting
	 * @param query llm query to generate annotation
	 */
	generateAnnotation?: (setting: ChartSetting, query: string) => Promise<ChartAnnotation | null>;
}>();

const emit = defineEmits(['close', 'update-settings', 'delete-annotation', 'create-annotation']);

// Log scale
const useLog = computed<boolean>(() => props.activeSettings?.scale === 'log');
const toggleLogScale = (useLogScale: boolean) => {
	emit('update-settings', { scale: useLogScale ? 'log' : '' });
};
// Hide in node
const isHiddenInNode = computed<boolean>(() => !!props.activeSettings?.hideInNode);
const toggleHideInNode = (hideInNode: boolean) => {
	emit('update-settings', { hideInNode: !!hideInNode });
};

// === Settings for comparison method ===
const comparisonSettings = computed(() => props.activeSettings as ChartSettingComparison | null);
// Small multiples
const smallMultiplesRadioValue = computed(() =>
	comparisonSettings.value?.smallMultiples ? 'small-multiples' : 'all-charts'
);
const onSmallMultiplesRadioButtonChange = (value: 'all-charts' | 'small-multiples') => {
	emit('update-settings', { smallMultiples: value === 'small-multiples' });
};
// Share Y axis
const isShareYAxis = computed(() => Boolean(comparisonSettings.value?.shareYAxis));
const toggleShareYAxis = (value: boolean) => emit('update-settings', { shareYAxis: value });
// Show before and after
const showBeforeAfter = computed(() => Boolean(comparisonSettings.value?.showBeforeAfter));
const toggleShowBeforeAfter = (value: boolean) => emit('update-settings', { showBeforeAfter: value });
// ======================================

// Normalize
const normalizeData = computed(() => Boolean(comparisonSettings.value?.normalize));
const toggleNormalizeData = (value: boolean) => emit('update-settings', { normalize: value });

// Primary color
const isColorPickerEnabled = computed(() => {
	const type = props.activeSettings?.type;
	if (type) {
		return ![ChartSettingType.ERROR_DISTRIBUTION, ChartSettingType.VARIABLE_COMPARISON].includes(type);
	}
	return false;
});
const onColorChange = (event) => {
	emit('update-settings', { primaryColor: event.target?.value });
};

// ========== Chart Annotations =========
const chartAnnotations = computed(() => {
	if (props.annotations === undefined) {
		return undefined;
	}
	return props.annotations.filter((annotation) => annotation.chartId === props.activeSettings?.id);
});
const isGeneratingAnnotation = ref(false);
const generateAnnotationQuery = ref<string>('');

const createAnnotation = async () => {
	if (props.generateAnnotation === undefined || props.activeSettings === null) {
		return;
	}
	isGeneratingAnnotation.value = true;
	const newAnnotation = await props.generateAnnotation(props.activeSettings, generateAnnotationQuery.value);
	isGeneratingAnnotation.value = false;
	generateAnnotationQuery.value = '';
	emit('create-annotation', newAnnotation);
};
// Note: For some reason, @keyup.enter event on <tera-input-text> is fired twice. Let's introduce a debounced function to make sure the function is called only once.
const createAnnotationDebounced = _.debounce(createAnnotation, 100);

const cancelGenerateAnnotation = () => {
	generateAnnotationQuery.value = '';
};
// ======================================
</script>

<style scoped>
.chart-settings-panel-anchor {
	position: fixed;
	top: 7.5rem;
	right: var(--gap-8);
	width: 360px;
	height: calc(100% - 7.5rem);
	pointer-events: none;
	overflow: hidden;
}
.chart-settings-panel {
	position: relative;
	overflow-y: auto;
	top: 0;
	z-index: 3;
	margin-top: 50px;
	height: calc(100% - 51px); /* 51px = margin-top + border */
	width: 100%;
	background: #fff;
	border-top-left-radius: var(--border-radius-bigger);
	left: 2px;
	border: solid 1px var(--surface-border-light);
	pointer-events: all;
	&.v-enter-active,
	&.v-leave-active {
		transition: left 0.15s ease-out;
		left: 30%;
	}
	&.v-enter-from,
	&.v-leave-to {
		left: 100%;
	}

	header {
		position: sticky;
		top: 0;
		z-index: 3;
		display: flex;
		align-items: center;
		flex-direction: row-reverse;
		justify-content: space-between;
		padding: var(--gap-2);
		padding-left: var(--gap-4);
		gap: var(--gap-4);
		background-color: rgba(255, 255, 255, 0.8);
		backdrop-filter: blur(3px);
		border-top-left-radius: var(--border-radius-bigger);
		&.shadow {
			box-shadow: 0 1px 4px 0 rgba(0, 0, 0, 0.1);
		}
		button {
			height: 2.5rem;
		}
		.line-wrap {
			white-space: wrap;
			word-wrap: break-all;
			overflow: hidden;
			text-overflow: ellipsis;
			width: 100%;
		}
	}
	.content {
		padding: var(--gap-4);
		background: var(--surface-0);
	}

	.items-wrapper {
		display: flex;
		flex-direction: column;
		gap: var(--gap-2);
	}

	.annotation-input:deep(main) {
		padding: var(--gap-2-5) var(--gap-2);
	}
	.annotation-items {
		display: flex;
		padding-bottom: var(--gap-1);
		flex-direction: column;
		gap: var(--gap-2);

		.annotation-item {
			position: relative;
			padding: var(--gap-3);
			padding-left: var(--gap-4);
			padding-right: var(--gap-9);
			background: var(--surface-50);
			border-left: 4px solid var(--gray-600);
		}
		.btn-wrapper {
			position: absolute;
			top: 0;
			right: var(--gap-2);
			display: flex;
			flex-direction: column;
			justify-content: center;
			height: 100%;
		}
	}
}
</style>
