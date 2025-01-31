<template>
	<div class="intervention-card">
		<div class="content">
			<aside>{{ !isEmpty(intervention.dynamicInterventions) ? 'Dynamic' : 'Static' }} intervention</aside>
			<h6 class="pt-1 line-wrap">{{ intervention.name }}</h6>
			<ul class="text-sm">
				<li v-for="(staticIntervention, index) in intervention.staticInterventions" :key="`static-${index}`">
					Set {{ staticIntervention.type }} <strong>{{ staticIntervention.appliedTo }}</strong> to
					<strong>{{ staticIntervention.value }}</strong> starting at
					<strong>{{
						getTimePointString(staticIntervention.timestep, {
							startDate: props.startDate,
							calendarSettings: props.calendarSettings
						})
					}}</strong>
				</li>
				<li v-for="(dynamicIntervention, index) in intervention.dynamicInterventions" :key="`dynamic-${index}`">
					Set {{ dynamicIntervention.type }} <strong>{{ dynamicIntervention.appliedTo }}</strong> to
					<strong>{{ dynamicIntervention.value }}</strong> when
					<strong>{{ dynamicIntervention.parameter }}</strong> crosses the threshold&nbsp;<strong
						>{{ dynamicIntervention.threshold }} {{ getUnit(dynamicIntervention) }}</strong
					>.
				</li>
			</ul>
		</div>
	</div>
</template>

<script setup lang="ts">
import { Intervention, DynamicIntervention, InterventionSemanticType } from '@/types/Types';
import { CalendarSettings, getTimePointString } from '@/utils/date';
import { isEmpty } from 'lodash';

const props = defineProps<{
	intervention: Intervention;
	parameterUnits?: { id: string; units: string } | {};
	stateUnits?: { id: string; units: string } | {};
	startDate?: Date;
	calendarSettings?: CalendarSettings;
}>();

function getUnit(dynamicIntervention: DynamicIntervention) {
	let modelUnit = '';
	if (dynamicIntervention.type === InterventionSemanticType.State) {
		modelUnit = props.stateUnits?.[dynamicIntervention.parameter]?.units ?? '';
	} else if (dynamicIntervention.type === InterventionSemanticType.Parameter) {
		modelUnit = props.parameterUnits?.[dynamicIntervention.appliedTo]?.units ?? '';
	}
	return modelUnit;
}
</script>

<style scoped>
.intervention-card {
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	box-shadow: 0 2px 4px -1px rgba(0, 0, 0, 0.08);
	overflow: hidden;
}

.intervention-card .content {
	padding-top: var(--gap-2);
	padding-right: var(--gap-2);
	padding-bottom: var(--gap-3);
	padding-left: var(--gap-2-5);
	border-left: 4px solid var(--primary-color);
	background: var(--surface-0);
}

ul {
	list-style: none;
	margin-top: var(--gap-1);
}

li + li {
	margin-top: var(--gap-1);
}

aside {
	color: var(--text-color-subdued);
	font-size: var(--font-caption);
	font-style: italic;
}
.line-wrap {
	white-space: normal;
	overflow-wrap: break-word;
	word-break: break-word;
	max-width: 100%;
}
</style>
