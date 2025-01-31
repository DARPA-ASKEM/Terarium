<template>
	<div class="policy-group">
		<div class="form-header">
			<h6 class="mr-auto">{{ config.intervention?.name ?? `Intervention` }}</h6>
			<tera-signal-bars
				v-if="!!knobs.relativeImportance"
				v-model="knobs.relativeImportance"
				@update:model-value="emit('update-self', knobs)"
				label="Relative importance"
			/>
		</div>
		<p>
			Set the {{ dynamicInterventions[0].type }}&nbsp; <strong>{{ dynamicInterventions[0].appliedTo }}</strong> to
			<strong>{{ dynamicInterventions[0].value }}</strong> when it crosses the threshold value
			<strong>{{ dynamicInterventions[0].threshold }}</strong>
		</p>
	</div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { DynamicIntervention } from '@/types/Types';
import { InterventionPolicyGroupForm } from '@/components/workflow/ops/optimize-ciemss/optimize-ciemss-operation';

const props = defineProps<{
	config: InterventionPolicyGroupForm;
}>();

const emit = defineEmits(['update-self']);

const dynamicInterventions = computed<DynamicIntervention[]>(() => props.config.intervention.dynamicInterventions);

const knobs = ref({
	relativeImportance: props.config.relativeImportance
});
</script>
<style>
.form-header {
	width: 100%;
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	align-items: center;
	gap: var(--gap-4);
	padding-bottom: 0.5rem;

	& > *:first-child {
		margin-right: auto;
	}

	& > * {
		display: flex;
		flex-direction: row;
		justify-content: space-between;
		align-items: center;
		gap: var(--gap-2);
	}
}

.policy-group {
	display: flex;
	padding: var(--gap-4);
	padding-left: var(--gap-5);
	flex-direction: column;
	justify-content: center;
	align-items: flex-start;
	gap: var(--gap-2);
	border-radius: var(--gap-1-5);
	background: var(--surface-section);
	border: 1px solid var(--surface-border-light);
	/* Shadow/medium */
	box-shadow:
		0 2px 4px -1px rgba(0, 0, 0, 0.06),
		0 4px 6px -1px rgba(0, 0, 0, 0.08);
}
.policy-group + .policy-group {
	margin-top: var(--gap-2);
}
</style>
