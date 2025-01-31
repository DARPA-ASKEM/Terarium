<template>
	<div class="intervention-card">
		<header class="card-section">
			<tera-toggleable-input
				:model-value="intervention.name"
				@update:model-value="onUpdateName($event)"
				tag="h6"
				class="nudge-left"
			/>
			<div class="flex align-items-center ml-auto">
				<RadioButton
					:model-value="interventionType"
					:input-id="uniqueId()"
					name="interventionType"
					value="static"
					@click="onInterventionTypeChange('static')"
				/>
				<label for="static" class="ml-2">Static</label>
				<RadioButton
					:model-value="interventionType"
					:input-id="uniqueId()"
					name="interventionType"
					value="dynamic"
					class="ml-3"
					@click="onInterventionTypeChange('dynamic')"
				/>
				<label for="dynamic" class="ml-2">Dynamic</label>
				<Button class="ml-3" icon="pi pi-trash" text @click="emit('delete')" />
			</div>
		</header>
		<section>
			<div v-if="interventionType === 'static'" class="card-section pb-2">
				Starting at day
				<tera-input-number
					auto-width
					invalidate-negative
					:model-value="intervention.staticInterventions[0].timestep"
					@update:model-value="(val) => onUpdateThreshold(val, 0)"
					placeholder="Timestep"
				/>
				:
			</div>
			<div class="card-setting">
				<template v-if="interventionType === 'dynamic'">
					Set
					<section>
						<Dropdown
							class="type-menu"
							:model-value="intervention.dynamicInterventions[0].type"
							@change="onSemanticChange($event, 0)"
							:options="interventionSemanticOptions"
							option-label="label"
							option-value="value"
						/>
						<Dropdown
							class="applied-to-menu"
							:model-value="intervention.dynamicInterventions[0].appliedTo"
							@change="onAppliedToParameterChange($event, 0)"
							:options="semanticOptions(intervention.dynamicInterventions[0].type)"
							option-label="label"
							option-value="value"
							placeholder="Select"
							:filter="semanticOptions(intervention.dynamicInterventions[0].type).length > 5"
							autoFilterFocus
						/>
					</section>
				</template>
				<!-- Static -->
				<template v-if="interventionType === 'static'">
					<template v-if="intervention.staticInterventions.length === 1">
						Set
						<section>
							<Dropdown
								class="type-menu"
								:model-value="intervention.staticInterventions[0].type"
								@change="onSemanticChange($event, 0)"
								:options="interventionSemanticOptions"
								option-label="label"
								option-value="value"
							/>
							<Dropdown
								class="applied-to-menu"
								:model-value="intervention.staticInterventions[0].appliedTo"
								@change="onAppliedToParameterChange($event, 0)"
								:options="semanticOptions(intervention.staticInterventions[0].type)"
								option-label="label"
								option-value="value"
								placeholder="Select"
								:filter="semanticOptions(intervention.staticInterventions[0].type).length > 5"
								autoFilterFocus
							/>
						</section>
						to
						<tera-input-number
							auto-width
							:model-value="intervention.staticInterventions[0].value"
							@update:model-value="(val) => onUpdateValue(val, 0)"
							placeholder="value"
						/>
					</template>

					<ul v-if="intervention.staticInterventions.length > 1" class="w-full">
						<li v-for="(i, index) in intervention.staticInterventions" class="flex-1" :key="index">
							<div class="flex align-items-center pt-2 pb-2 gap-2">
								Set
								<section>
									<Dropdown
										class="type-menu"
										:model-value="i.type"
										@change="onSemanticChange($event, index)"
										:options="interventionSemanticOptions"
										option-label="label"
										option-value="value"
									/>
									<Dropdown
										class="applied-to-menu"
										:model-value="i.appliedTo"
										@change="onAppliedToParameterChange($event, index)"
										:options="semanticOptions(i.type)"
										option-label="label"
										option-value="value"
										placeholder="Select"
										:filter="semanticOptions(i.type).length > 5"
										autoFilterFocus
									/>
								</section>
								to
								<tera-input-number
									auto-width
									:model-value="i.value"
									@update:model-value="(val) => onUpdateValue(val, index)"
									placeholder="value"
								/>
								<Button
									class="ml-auto"
									icon="pi pi-times"
									size="small"
									rounded
									text
									@click="onRemoveStaticIntervention(index)"
								/>
							</div>
						</li>
					</ul>
				</template>

				<!-- Dynamic -->
				<template v-else>
					to
					<tera-input-number
						auto-width
						:model-value="intervention.dynamicInterventions[0].value"
						@update:model-value="(val) => onUpdateValue(val, 0)"
						placeholder="value"
					/>
					when
					<Dropdown
						class="applied-to-menu"
						:model-value="intervention.dynamicInterventions[0].parameter"
						@change="onTargetParameterChange"
						:options="stateOptions"
						option-label="label"
						option-value="value"
						placeholder="Select a trigger"
						:filter="stateOptions.length > 5"
						autoFilterFocus
					/>
					crosses the threshold
					<tera-input-number
						auto-width
						:model-value="intervention.dynamicInterventions[0].threshold"
						@update:model-value="(val) => onUpdateThreshold(val, 0)"
						placeholder="threshold"
					/>
					{{ dynamicInterventionUnits }}.
				</template>
			</div>
		</section>
		<footer>
			<Button
				v-if="interventionType === 'static'"
				text
				icon="pi pi-plus"
				label="Add"
				@click="onAddNewStaticIntervention"
				size="small"
			/>
		</footer>
	</div>
</template>

<script setup lang="ts">
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import Button from 'primevue/button';
import RadioButton from 'primevue/radiobutton';
import { computed } from 'vue';
import { Intervention, InterventionSemanticType } from '@/types/Types';
import Dropdown, { DropdownChangeEvent } from 'primevue/dropdown';
import TeraInputNumber from '@/components/widgets/tera-input-number.vue';
import { cloneDeep, debounce, uniqueId } from 'lodash';

const emit = defineEmits(['update', 'delete', 'add']);
const props = defineProps<{
	intervention: Intervention;
	parameterOptions: { label: string; value: string; units?: string }[];
	stateOptions: { label: string; value: string; units?: string }[];
}>();

const interventionSemanticOptions = [
	{ label: 'Parameter', value: InterventionSemanticType.Parameter },
	{ label: 'State', value: InterventionSemanticType.State }
];

const semanticOptions = (type) => {
	if (type === InterventionSemanticType.State) {
		return props.stateOptions;
	}
	return props.parameterOptions;
};

const interventionType = computed(() => {
	if (props.intervention.staticInterventions.length > 0) {
		return 'static';
	}
	if (props.intervention.dynamicInterventions.length > 0) {
		return 'dynamic';
	}
	return 'static';
});

const dynamicInterventionUnits = computed(() => {
	let units = '';
	const type = props.intervention.dynamicInterventions[0].type;
	const appliedTo = props.intervention.dynamicInterventions[0].appliedTo;

	if (type === InterventionSemanticType.Parameter) {
		units = props.parameterOptions.find((parameter) => parameter.label === appliedTo)?.units ?? '';
	} else {
		units = props.stateOptions.find((state) => state.label === appliedTo)?.units ?? '';
	}
	return units;
});

const onUpdateName = (name: string) => {
	const intervention = cloneDeep(props.intervention);
	intervention.name = name;
	debounceUpdateState(intervention);
};

const onAppliedToParameterChange = (event: DropdownChangeEvent, index: number) => {
	const intervention = cloneDeep(props.intervention);
	const item =
		interventionType.value === 'static' ? intervention.staticInterventions : intervention.dynamicInterventions;
	item[index].appliedTo = event.value;
	emit('update', intervention);
};

const onUpdateThreshold = (value: number, index: number) => {
	const intervention = cloneDeep(props.intervention);
	if (interventionType.value === 'static') {
		// The timestep value is the same only for static interventions
		intervention.staticInterventions.forEach((item) => {
			item.timestep = value;
		});
	} else {
		intervention.dynamicInterventions[index].threshold = value;
	}
	emit('update', intervention);
};

const onUpdateValue = (value: number, index: number) => {
	const intervention = cloneDeep(props.intervention);
	if (interventionType.value === 'static') {
		intervention.staticInterventions[index].value = value;
	} else {
		intervention.dynamicInterventions[index].value = value;
	}
	emit('update', intervention);
};

const onRemoveStaticIntervention = (index: number) => {
	const intervention = cloneDeep(props.intervention);
	intervention.staticInterventions.splice(index, 1);
	emit('update', intervention);
};

const onAddNewStaticIntervention = () => {
	const intervention = cloneDeep(props.intervention);
	intervention.staticInterventions.push({
		timestep: intervention.staticInterventions[0].timestep,
		value: Number.NaN,
		appliedTo: '',
		type: InterventionSemanticType.Parameter
	});
	emit('update', intervention);
};

const onInterventionTypeChange = (value: string) => {
	const intervention = cloneDeep(props.intervention);
	if (value === 'static') {
		intervention.staticInterventions = [
			{
				timestep: Number.NaN,
				value: Number.NaN,
				appliedTo: '',
				type: InterventionSemanticType.Parameter
			}
		];
		intervention.dynamicInterventions = [];
	} else if (value === 'dynamic') {
		intervention.staticInterventions = [];
		intervention.dynamicInterventions = [
			{
				threshold: Number.NaN,
				value: Number.NaN,
				parameter: '',
				appliedTo: '',
				type: InterventionSemanticType.Parameter
			}
		];
	}

	emit('update', intervention);
};

const onTargetParameterChange = (event: DropdownChangeEvent) => {
	const intervention = cloneDeep(props.intervention);
	intervention.dynamicInterventions[0].parameter = event.value;
	emit('update', intervention);
};

const onSemanticChange = (event: DropdownChangeEvent, index: number) => {
	const intervention = cloneDeep(props.intervention);
	const item =
		interventionType.value === 'static' ? intervention.staticInterventions : intervention.dynamicInterventions;
	item[index].type = event.value;
	if (event.value === InterventionSemanticType.State) {
		item[index].appliedTo = '';
	} else {
		item[index].appliedTo = '';
	}
	emit('update', intervention);
};

const debounceUpdateState = debounce((intervention) => {
	emit('update', intervention);
}, 100);
</script>

<style scoped>
:deep(.p-divider) {
	&.p-divider-horizontal {
		margin-top: 0;
		margin-bottom: 0;
		color: var(--gray-300);
	}
}

.card-section {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: var(--gap-2);
}

.type-menu {
	border-radius: var(--border-radius) 0 0 var(--border-radius);
	background: var(--surface-100);
	height: 27px;
}

.applied-to-menu {
	border-radius: 0 var(--border-radius) var(--border-radius) 0;
	height: 27px;
}

.intervention-card {
	background-color: var(--surface-0);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius-medium);
	padding: var(--gap-2) var(--gap-4);
	gap: var(--gap-2);
	display: flex;
	flex-direction: column;
	cursor: pointer;
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}
.intervention-card:hover {
	background-color: var(--surface-highlight);
	border-left-color: var(--primary-color);
}
.intervention-card:hover:has(.card-setting:hover) {
	border-left-color: var(--primary-color-light);
	background-color: color-mix(in srgb, var(--surface-highlight) 30%, var(--surface-0) 70%);
}

.card-setting {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: var(--gap-2);
	background: var(--surface-50);
	padding: var(--gap-2) var(--gap-4);
	margin-bottom: var(--gap-1);
	border-radius: var(--border-radius);
	border: 1px solid var(--surface-border-light);
	border-left: 4px solid var(--surface-300);
}
.card-setting:hover {
	background: var(--surface-highlight);
	border-left-color: var(--primary-color);
}

ul {
	list-style: none;
}

/* lighten divider color */
:deep(.p-divider.p-divider-horizontal:before) {
	border-top-color: var(--surface-border-light);
}

/* Align name to the left edge even though it's a button */
.nudge-left {
	margin-left: -0.5rem;
}

/* smaller dropdown to match other inputs in this card */
</style>
