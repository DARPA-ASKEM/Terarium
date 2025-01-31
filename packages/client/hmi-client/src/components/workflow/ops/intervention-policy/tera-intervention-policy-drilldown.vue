<template>
	<tera-drilldown
		v-bind="$attrs"
		:node="node"
		@on-close-clicked="emit('close')"
		@update-state="(state: any) => emit('update-state', state)"
		hide-dropdown
	>
		<template #sidebar>
			<tera-slider-panel
				v-if="pdfData.length"
				v-model:is-open="isPdfSidebarOpen"
				:content-width="isSidebarOpen ? 'calc(50vw - 320px)' : '50vw'"
				header="Document viewer"
				:documentViewer="true"
			>
				<template #content>
					<tera-drilldown-section :is-loading="isFetchingPDF">
						<tera-pdf-panel :pdfs="pdfData" ref="pdfPanelRef" />
					</tera-drilldown-section>
				</template>
			</tera-slider-panel>

			<tera-slider-panel
				v-model:is-open="isSidebarOpen"
				content-width="360px"
				header="Intervention policies"
				class="input-config"
			>
				<template #content>
					<section>
						<nav class="inline-flex">
							<Button
								class="flex-1 mr-5"
								outlined
								severity="secondary"
								label="Extract from inputs"
								icon="pi pi-sparkles"
								:loading="isLoading"
								:disabled="!props.node.inputs[0]?.value && !props.node.inputs[1]?.value"
								@click="extractInterventionPolicyFromInputs"
							/>
							<Button
								class="ml-1"
								icon="pi pi-plus"
								label="Create new"
								:disabled="!model?.id"
								@click="resetToBlankIntervention"
							/>
						</nav>
						<tera-input-text v-model="filterInterventionsText" placeholder="Filter" />
						<ul v-if="!isFetchingPolicies">
							<li v-for="policy in interventionPoliciesFiltered" :key="policy.id">
								<tera-intervention-policy-card
									:interventionPolicy="policy"
									:selected="selectedPolicy?.id === policy.id"
									@click="onReplacePolicy(policy)"
									@use-intervention="onReplacePolicy(policy)"
									@delete-intervention-policy="onDeleteInterventionPolicy(policy)"
									class="mb-2"
								/>
							</li>
						</ul>
						<tera-progress-spinner v-else is-centered />
					</section>
				</template>
			</tera-slider-panel>
		</template>
		<tera-columnar-panel>
			<tera-drilldown-section class="intervention-settings-section">
				<template #header-controls-left> Add and configure intervention settings for this policy. </template>
				<template #header-controls-right>
					<Button outlined severity="secondary" label="Reset" @click="onResetPolicy" />
				</template>
				<ul class="flex flex-column gap-2">
					<li
						v-for="(intervention, index) in knobs.transientInterventionPolicy.interventions.slice(
							firstRow,
							firstRow + MAX_NUMBER_OF_ROWS
						)"
						:key="index"
						@click="selectInterventionPolicy(intervention, index)"
					>
						<tera-intervention-card
							class="intervention"
							:class="{
								selected: selectedIntervention?.name === intervention?.name && selectedIntervention?.index === index
							}"
							:intervention="intervention"
							:parameterOptions="parameterOptions"
							:stateOptions="stateOptions"
							@update="onUpdateInterventionCard($event, index)"
							@delete="onDeleteIntervention(index)"
						/>
					</li>
				</ul>
				<Button
					class="align-self-start mt-2"
					text
					label="Add intervention"
					@click="addIntervention"
					icon="pi pi-plus"
					size="small"
				/>
				<template #footer>
					<section class="paginator w-full">
						<Paginator
							v-if="knobs.transientInterventionPolicy.interventions.length > MAX_NUMBER_OF_ROWS"
							:rows="MAX_NUMBER_OF_ROWS"
							:first="firstRow"
							:total-records="knobs.transientInterventionPolicy.interventions.length"
							:template="{
								default: 'FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink JumpToPageDropdown'
							}"
							@page="firstRow = $event.first"
						/>
					</section>
				</template>
			</tera-drilldown-section>
			<tera-drilldown-section>
				<template #header-controls-left>
					<tera-toggleable-input
						v-if="typeof knobs.transientInterventionPolicy.name === 'string'"
						:model-value="knobs.transientInterventionPolicy.name"
						@update:model-value="onChangeName"
						tag="h4"
					/>
				</template>
				<template #header-controls-right>
					<Button label="Save as..." outlined severity="secondary" @click="showSaveModal = true" />
					<Button class="mr-3" label="Save" @click="onSaveInterventionPolicy" :disabled="isSaveDisabled" />
				</template>
				<Accordion v-if="knobs.transientInterventionPolicy" multiple :active-index="currentActiveIndicies">
					<AccordionTab>
						<template #header>
							<Button v-if="!isEditingDescription" class="start-edit" text @click.stop="onEditDescription">
								<h5 class="btn-content">Description</h5>
								<i class="pi pi-pencil" />
							</Button>
							<span v-else class="confirm-cancel">
								<span>Description</span>
								<Button icon="pi pi-times" size="small" text @click.stop="isEditingDescription = false" />
								<Button icon="pi pi-check" size="small" text @click.stop="onConfirmEditDescription" />
							</span>
						</template>
						<p class="description text" v-if="!isEditingDescription">
							{{ knobs.transientInterventionPolicy.description }}
						</p>
						<Textarea
							v-else
							ref="descriptionTextareaRef"
							class="w-full"
							placeholder="Enter a description"
							v-model="newDescription"
						/>
					</AccordionTab>
					<AccordionTab header="Charts">
						<ul class="flex flex-column gap-2">
							<li v-for="appliedTo in Object.keys(groupedOutputParameters)" :key="appliedTo">
								<vega-chart
									expandable
									:are-embed-actions-visible="false"
									:visualization-spec="preparedCharts[appliedTo]"
								/>
								<span class="flex pb-6">
									<label class="pr-2 text-sm">Show this chart on node thumbnail</label>
									<Checkbox
										v-model="selectedCharts"
										:input-id="appliedTo"
										:name="appliedTo"
										:value="appliedTo"
										@change="onSelectChartChange"
									/>
								</span>
								<ul>
									<li
										class="pb-2"
										v-for="intervention in getInterventionsAppliedTo(appliedTo)"
										:key="intervention.name"
									>
										<h6 class="pb-1">{{ intervention.name }}</h6>
										<ul v-if="!isEmpty(intervention.staticInterventions)">
											<li v-for="(staticIntervention, index) in intervention.staticInterventions" :key="index">
												<p>
													Set {{ staticIntervention.type }} {{ appliedTo }} to {{ staticIntervention.value }} at time
													step {{ staticIntervention.timestep }}.
												</p>
											</li>
										</ul>
										<p v-else-if="!isEmpty(intervention.dynamicInterventions)">
											Set {{ intervention.dynamicInterventions[0].type }} {{ appliedTo }} to
											{{ intervention.dynamicInterventions[0].value }} when the
											{{ intervention.dynamicInterventions[0].parameter }}
											when it crosses the threshold value
											{{ intervention.dynamicInterventions[0].threshold }}.
										</p>
									</li>
								</ul>
							</li>
						</ul>
					</AccordionTab>
				</Accordion>
				<div v-else class="flex align-items-center h-full">
					<Vue3Lottie :animationData="EmptySeed" :height="150" loop autoplay />
				</div>
			</tera-drilldown-section>
		</tera-columnar-panel>
	</tera-drilldown>
	<tera-save-asset-modal
		:initial-name="knobs.transientInterventionPolicy.name"
		:is-visible="showSaveModal"
		:asset="knobs.transientInterventionPolicy"
		:asset-type="AssetType.InterventionPolicy"
		@close-modal="showSaveModal = false"
		@on-save="onSaveAsInterventionPolicy"
		@on-update="onSaveAsInterventionPolicy"
	/>
</template>

<script setup lang="ts">
import _, { cloneDeep, groupBy, isEmpty, isEqual, omit } from 'lodash';
import { ComponentPublicInstance, computed, nextTick, onMounted, ref, watch } from 'vue';
import TeraDrilldown from '@/components/drilldown/tera-drilldown.vue';
import TeraDrilldownSection from '@/components/drilldown/tera-drilldown-section.vue';
import { WorkflowNode } from '@/types/workflow';
import TeraSliderPanel from '@/components/widgets/tera-slider-panel.vue';
import TeraColumnarPanel from '@/components/widgets/tera-columnar-panel.vue';
import Button from 'primevue/button';
import Paginator from 'primevue/paginator';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import { getInterventionPoliciesForModel, getModel } from '@/services/model';
import {
	AssetType,
	ClientEvent,
	ClientEventType,
	Intervention,
	InterventionPolicy,
	Model,
	DynamicIntervention,
	StaticIntervention,
	TaskStatus,
	type TaskResponse,
	type DocumentAsset
} from '@/types/Types';
import { logger } from '@/utils/logger';
import TeraProgressSpinner from '@/components/widgets/tera-progress-spinner.vue';
import { useConfirm } from 'primevue/useconfirm';
import { getParameters, getStates } from '@/model-representation/service';
import TeraToggleableInput from '@/components/widgets/tera-toggleable-input.vue';
import {
	blankIntervention,
	flattenInterventionData,
	updateInterventionPolicy,
	deleteInterventionPolicy
} from '@/services/intervention-policy';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import Checkbox from 'primevue/checkbox';
import Textarea from 'primevue/textarea';
import EmptySeed from '@/assets/images/lottie-empty-seed.json';
import { Vue3Lottie } from 'vue3-lottie';
import { sortDatesDesc } from '@/utils/date';
import { createInterventionChart } from '@/services/charts';
import VegaChart from '@/components/widgets/VegaChart.vue';
import TeraSaveAssetModal from '@/components/project/tera-save-asset-modal.vue';
import { useProjects } from '@/composables/project';
import { interventionPolicyFromDocument, interventionPolicyFromDataset } from '@/services/goLLM';
import { downloadDocumentAsset, getDocumentAsset, getDocumentFileAsText } from '@/services/document-assets';
import TeraPdfPanel from '@/components/widgets/tera-pdf-panel.vue';
import { useClientEvent } from '@/composables/useClientEvent';
import TeraInterventionCard from './tera-intervention-card.vue';
import {
	InterventionPolicyOperation,
	InterventionPolicyState,
	isInterventionPoliciesEqual,
	isInterventionPoliciesValuesEqual,
	isInterventionPolicyBlank
} from './intervention-policy-operation';
import TeraInterventionPolicyCard from './tera-intervention-policy-card.vue';

const props = defineProps<{
	node: WorkflowNode<InterventionPolicyState>;
}>();

const emit = defineEmits(['close', 'update-state', 'select-output', 'append-output']);

const selectedCharts = ref<string[] | []>([]);

const confirm = useConfirm();

interface BasicKnobs {
	transientInterventionPolicy: InterventionPolicy;
}

const knobs = ref<BasicKnobs>({
	transientInterventionPolicy: {
		name: '',
		description: '',
		modelId: '',
		interventions: []
	}
});

interface SelectedIntervention {
	index: number;
	name: string;
	extractionDocumentId?: string;
	extractionPage?: number;
	staticInterventions: StaticIntervention[];
	dynamicInterventions: DynamicIntervention[];
}

const modelId: string | undefined = props.node.inputs[0].value?.[0];
const MAX_NUMBER_OF_ROWS = 8;
const firstRow = ref(0);

const currentActiveIndicies = ref([0, 1]);
const pdfPanelRef = ref();
const selectedIntervention = ref<SelectedIntervention | null>(null);

const isPdfSidebarOpen = ref(true);
const isFetchingPDF = ref(false);
const pdfData = ref<{ document: DocumentAsset; data: string; isPdf: boolean; name: string }[]>([]);

const showSaveModal = ref(false);
const isSidebarOpen = ref(true);
const filterInterventionsText = ref('');
const model = ref<Model | null>(null);
const isFetchingPolicies = ref(false);
const isLoading = ref(false);
const interventionPoliciesList = ref<InterventionPolicy[]>([]);
const interventionPoliciesFiltered = computed(() =>
	interventionPoliciesList.value
		.filter((policy) => policy.name?.toLowerCase().includes(filterInterventionsText.value.toLowerCase()))
		.sort((a, b) => sortDatesDesc(a.createdOn, b.createdOn))
);

const selectedPolicy = ref<InterventionPolicy | null>(null);

const newDescription = ref('');
const descriptionTextareaRef = ref<ComponentPublicInstance<typeof Textarea> | null>(null);
const isEditingDescription = ref(false);
const isSaveDisabled = computed(() => {
	// Extract the selected and transient policies
	const transientPolicy = knobs.value.transientInterventionPolicy;
	const transientPolicyId = transientPolicy.id;

	// Check if the selected policy exists
	const hasSelectedPolicy = !!selectedPolicy.value;

	// Check if the IDs of the transient and selected policies differ
	const isPolicyIdDifferent = transientPolicyId !== selectedPolicy.value?.id;

	// Check if the selected policy blank
	const isPolicyBlank = isInterventionPolicyBlank(selectedPolicy.value);

	// Check if the policies themselves are equal
	const arePoliciesEqual = isInterventionPoliciesEqual(transientPolicy, selectedPolicy.value);

	// Check if the policy values are equal
	const arePolicyValuesEqual = isInterventionPoliciesValuesEqual(transientPolicy, selectedPolicy.value);

	// Disable save if either the policy ID is different, the policies are equal,
	// or the policy values are not equal
	return hasSelectedPolicy && !isPolicyBlank && (isPolicyIdDifferent || arePoliciesEqual || !arePolicyValuesEqual);
});

const documentIds = computed(() =>
	props.node.inputs
		.filter((input) => input.type === 'documentId' && input.status === 'connected')
		.map((input) => input.value?.[0]?.documentId)
		.filter((id): id is string => id !== undefined)
);

const datasetIds = computed(() =>
	props.node.inputs
		.filter((input) => input.type === 'datasetId' && input.status === 'connected')
		.map((input) => input.value?.[0])
);

const parameterOptions = computed(() => {
	if (!model.value) return [];
	return getParameters(model.value).map((parameter) => ({
		label: parameter.id,
		value: parameter.id,
		units: parameter.units?.expression
	}));
});

const stateOptions = computed(() => {
	if (!model.value) return [];
	return getStates(model.value).map((state) => ({
		label: state.id,
		value: state.id,
		units: state.units?.expression
	}));
});

const groupedOutputParameters = computed(() =>
	groupBy(flattenInterventionData(knobs.value.transientInterventionPolicy.interventions), 'appliedTo')
);

const preparedCharts = computed(() =>
	_.mapValues(groupedOutputParameters.value, (interventions, key) =>
		createInterventionChart(interventions, {
			title: key,
			width: 400,
			height: 200,
			xAxisTitle: 'Time',
			yAxisTitle: 'Value'
		})
	)
);

const getInterventionsAppliedTo = (appliedTo: string) =>
	knobs.value.transientInterventionPolicy.interventions
		.map((i) => {
			const staticInterventions = i.staticInterventions.filter((s) => s.appliedTo === appliedTo);
			const dynamicInterventions = i.dynamicInterventions.filter((d) => d.appliedTo === appliedTo);
			return {
				name: i.name,
				staticInterventions,
				dynamicInterventions
			};
		})
		.filter((i) => i.dynamicInterventions.length + i.staticInterventions.length > 0);

const initialize = async (overwriteWithState: boolean = false) => {
	const state = props.node.state;
	const selectedOutput = cloneDeep(props.node.outputs.find((o) => o.id === props.node.active));
	const selectedPolicyId: string | null = selectedOutput?.value?.[0];

	await fetchInterventionPolicies();

	selectedPolicy.value =
		interventionPoliciesList.value.find(({ id }) => id === selectedPolicyId) ??
		(selectedOutput?.state as InterventionPolicy) ??
		null;

	if (overwriteWithState || !selectedPolicy.value) {
		knobs.value.transientInterventionPolicy = cloneDeep(state.interventionPolicy);
	} else {
		knobs.value.transientInterventionPolicy = cloneDeep(selectedPolicy.value);
	}
	selectedCharts.value = state.selectedCharts ?? [];
};

const applyInterventionPolicy = (interventionPolicy: InterventionPolicy) => {
	const state = cloneDeep(props.node.state);
	if (!interventionPolicy?.id) {
		logger.error('Policy not found');
		return;
	}

	const listOfPolicyIds: string[] = props.node.outputs.map((output) => output.value?.[0]);
	// Check if this output already exists
	if (listOfPolicyIds.includes(interventionPolicy.id)) {
		// Select the existing output
		const output = props.node.outputs.find((ele) => ele.value?.[0] === interventionPolicy.id);
		emit('select-output', output?.id);
	}
	// If the output does not already exist
	else {
		emit('append-output', {
			type: InterventionPolicyOperation.outputs[0].type,
			label: interventionPolicy.name,
			value: interventionPolicy.id,
			state: omit(state, ['transientInterventionPolicy'])
		});
	}
	logger.success(`Policy applied ${interventionPolicy.name}`);
};

const fetchInterventionPolicies = async () => {
	isFetchingPolicies.value = true;
	interventionPoliciesList.value = await getInterventionPoliciesForModel(modelId);
	isFetchingPolicies.value = false;
};

const selectInterventionPolicy = (intervention: Intervention, index: number) => {
	selectedIntervention.value = { ...intervention, index };
};

const onUpdateInterventionCard = (intervention: Intervention, index: number) => {
	// Clone the entire interventions array
	const updatedInterventions = [...knobs.value.transientInterventionPolicy.interventions];

	// Replace the intervention at the specified index with a deep clone of the updated intervention
	updatedInterventions[index] = cloneDeep(intervention);

	// Reassign the updated interventions array back to the transientInterventionPolicy
	// This ensures that we're not modifying the original array in place
	knobs.value.transientInterventionPolicy.interventions = updatedInterventions;
};

const onReplacePolicy = (policy: InterventionPolicy) => {
	if (selectedPolicy.value?.id === policy.id) return;
	if (isSaveDisabled.value) {
		applyInterventionPolicy(policy);
	} else {
		confirm.require({
			header: 'Are you sure you want to use this intervention policy?',
			message: `All current interventions will be replaced with those in the selected policy, “${policy.name}” This action cannot be undone.`,
			accept: () => applyInterventionPolicy(policy),
			acceptLabel: 'Confirm',
			rejectLabel: 'Cancel'
		});
	}
};

const onDeleteInterventionPolicy = (policy: InterventionPolicy) => {
	confirm.require({
		message: `Are you sure you want to delete the configuration ${policy.name}?`,
		header: 'Delete confirmation',
		acceptLabel: 'Confirm',
		rejectLabel: 'Cancel',
		accept: async () => {
			if (policy.id) {
				await deleteInterventionPolicy(policy.id);
				fetchInterventionPolicies();
			}
		}
	});
};

const addIntervention = () => {
	// by default add the first parameter with a static intervention
	knobs.value.transientInterventionPolicy.interventions.push(blankIntervention);
};

const onDeleteIntervention = (index: number) => {
	// Create a new array excluding the intervention at the specified index
	const updatedInterventions = knobs.value.transientInterventionPolicy.interventions.filter(
		(_intervention, i) => i !== index
	);

	// Reassign the updated interventions array back to the transientInterventionPolicy
	// This ensures that we're not modifying the original array in place and Vue's reactivity system detects the change
	knobs.value.transientInterventionPolicy.interventions = updatedInterventions;

	// If the deleted intervention was the last one, add a new empty one
	if (isEmpty(knobs.value.transientInterventionPolicy.interventions)) {
		addIntervention();
	}
};

const onChangeName = async (name: string) => {
	knobs.value.transientInterventionPolicy.name = name;
};

const onEditDescription = async () => {
	isEditingDescription.value = true;
	newDescription.value = knobs.value.transientInterventionPolicy.description ?? '';
	await nextTick();
	descriptionTextareaRef.value?.$el.focus();
};

const onConfirmEditDescription = async () => {
	knobs.value.transientInterventionPolicy.description = newDescription.value;
	isEditingDescription.value = false;
};

const onResetPolicy = () => {
	confirm.require({
		header: 'Are you sure you want to reset the policy?',
		message: 'This action cannot be undone.',
		accept: () => {
			if (selectedPolicy.value) knobs.value.transientInterventionPolicy = cloneDeep(selectedPolicy.value);
		},
		acceptLabel: 'Confirm',
		rejectLabel: 'Cancel'
	});
};

const onSaveAsInterventionPolicy = (data: InterventionPolicy) => {
	applyInterventionPolicy(data);
};

const onSaveInterventionPolicy = async () => {
	const policy = cloneDeep(knobs.value.transientInterventionPolicy);
	let data;
	if (!selectedPolicy.value) {
		// create a new policy when there is no selected policy
		showSaveModal.value = true;
	} else {
		// update the existing policy when there is a selected policy
		data = await updateInterventionPolicy(policy);
		if (!data) {
			logger.error('Failed to save intervention policy');
			return;
		}
		initialize();
		useProjects().refresh();
	}
};

const resetToBlankIntervention = () => {
	if (!modelId) return;
	emit('append-output', {
		type: InterventionPolicyOperation.outputs[0].type,
		label: InterventionPolicyOperation.outputs[0].label,
		value: null,
		state: {
			modelId,
			interventions: [_.cloneDeep(blankIntervention)]
		}
	});
};

const extractInterventionPolicyFromInputs = async () => {
	const state = cloneDeep(props.node.state);
	if (!model.value?.id) {
		return;
	}

	if (documentIds.value) {
		const promiseList = [] as Promise<TaskResponse | null>[];
		documentIds.value.forEach((documentId) => {
			promiseList.push(
				interventionPolicyFromDocument(documentId, model.value?.id as string, props.node.workflowId, props.node.id)
			);
		});
		const responsesRaw = await Promise.all(promiseList);
		responsesRaw.forEach((resp) => {
			if (resp) {
				state.taskIds.push(resp.id);
			}
		});
	}

	if (datasetIds.value) {
		const promiseList = [] as Promise<TaskResponse | null>[];
		datasetIds.value.forEach((datasetId) => {
			promiseList.push(
				interventionPolicyFromDataset(datasetId, model.value?.id as string, props.node.workflowId, props.node.id)
			);
		});
		const responsesRaw = await Promise.all(promiseList);

		responsesRaw.forEach((resp) => {
			if (resp) {
				state.taskIds.push(resp.id);
			}
		});
	}
	emit('update-state', state);
};

// Listen for the task completion event
const interventionEventHandler = async (event: ClientEvent<TaskResponse>) => {
	const state = cloneDeep(props.node.state);
	if (!state.taskIds.includes(event.data?.id)) return;
	isLoading.value = true;
	if ([TaskStatus.Success, TaskStatus.Cancelled, TaskStatus.Failed].includes(event.data.status)) {
		state.taskIds = state.taskIds.filter((id) => id !== event.data.id);
		isLoading.value = false;
		if (!modelId) return;
		await fetchInterventionPolicies();
	}
};

useClientEvent(ClientEventType.TaskGollmInterventionsFromDocument, interventionEventHandler);
useClientEvent(ClientEventType.TaskGollmInterventionsFromDataset, interventionEventHandler);

const onSelectChartChange = () => {
	const state = cloneDeep(props.node.state);
	state.selectedCharts = selectedCharts.value;
	emit('update-state', state);
};

watch(
	() => knobs.value,
	async () => {
		const state = cloneDeep(props.node.state);
		state.interventionPolicy = knobs.value.transientInterventionPolicy;
		emit('update-state', state);
	},
	{ deep: true }
);

watch(
	() => props.node.active,
	() => {
		if (props.node.active) {
			initialize();
		}
	}
);

watch(
	() => props.node.state.taskIds,
	async (newTaskIds, oldTaskIds) => {
		// This gets triggered on state change for some reason this helps prevent fetching the policies multiple times
		if (isEqual(newTaskIds, oldTaskIds)) {
			return;
		}
		if (props.node.state.taskIds.length > 0) {
			isLoading.value = true;
		} else {
			isLoading.value = false;
			if (!modelId) return;
			fetchInterventionPolicies();
		}
	}
);

onMounted(async () => {
	if (modelId) model.value = await getModel(modelId);

	if (props.node.active) {
		// setting true will force overwrite the intervention policy with the current state on the node
		initialize(true);
	} else {
		initialize();
	}

	if (documentIds.value.length) {
		isFetchingPDF.value = true;
		documentIds.value.forEach(async (id) => {
			const document = await getDocumentAsset(id);
			const name: string = document?.name ?? '';
			const filename = document?.fileNames?.[0];
			const isPdf = !!document?.fileNames?.[0]?.endsWith('.pdf');

			if (document?.id && filename) {
				let data: string | null;
				if (isPdf) {
					data = await downloadDocumentAsset(document.id, filename);
				} else {
					data = await getDocumentFileAsText(document.id, filename);
				}
				if (data !== null) {
					pdfData.value.push({ document, data, isPdf, name });
				}
			}
		});
	}
	isFetchingPDF.value = false;
});
</script>

<style scoped>
.intervention-settings-section {
	background-color: var(--gray-200);
	padding: 0 var(--gap-3);
	gap: var(--gap-1);
}

.input-config {
	ul {
		list-style: none;
	}
	li {
		& > * {
			border-bottom: 1px solid var(--gray-300);
			border-right: 1px solid var(--gray-300);
		}
		&:first-child > * {
			border-top: 1px solid var(--gray-300);
			border-top-left-radius: var(--border-radius);
			border-top-right-radius: var(--border-radius);
		}
		&:last-child > * {
			border-bottom-left-radius: var(--border-radius);
			border-bottom-right-radius: var(--border-radius);
		}
	}
}

section {
	display: flex;
	flex-direction: column;
	gap: var(--gap-4);
	padding: 0 var(--gap-4);
}

button.start-edit {
	display: flex;
	gap: var(--gap-3);
	width: fit-content;
	padding: 0;

	& > .btn-content {
		color: var(--text-color);
	}

	& > .pi {
		color: var(--text-color-subdued);
	}
}

.intervention {
	background-color: var(--gray-0);
	border-left: 6px solid var(--surface-300);

	&.selected {
		border-left-color: var(--primary-color);
	}

	&,
	&.selected {
		transition: border-left-color 15ms;
	}
}

.confirm-cancel {
	display: flex;
	align-items: center;
	gap: var(--gap-1);
	& > span {
		margin-left: var(--gap-2);
	}
}

.description {
	margin-bottom: var(--gap-3);
}

.paginator {
	padding: unset;
}
</style>
