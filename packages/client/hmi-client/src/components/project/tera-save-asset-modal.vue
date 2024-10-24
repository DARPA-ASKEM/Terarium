<template>
	<tera-modal
		v-if="isVisible"
		class="w-4"
		@modal-mask-clicked="closeModal"
		@on-modal-open="initializeAsset"
		@modal-enter-press="save"
	>
		<template #header>
			<h4>{{ title }}</h4>
		</template>
		<template #default>
			<form @submit.prevent>
				<label for="new-name">What would you like to call it?</label>
				<tera-input-text id="new-name" v-model="newName" placeholder="Enter a unique name" />
			</form>
		</template>
		<template #footer>
			<Button label="Save" size="large" @click="save" />
			<Button label="Close" class="p-button-secondary" size="large" outlined @click="closeModal" />
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { ref, PropType, computed } from 'vue';
import { cloneDeep, isEmpty } from 'lodash';
import Button from 'primevue/button';
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraModal from '@/components/widgets/tera-modal.vue';
import { AssetType, ProgrammingLanguage } from '@/types/Types';
import type { InterventionPolicy, Model, Dataset, ModelConfiguration } from '@/types/Types';
import type { Workflow } from '@/types/workflow';
import { emptyWorkflow } from '@/services/workflow';
import { setFileExtension } from '@/services/code';
import { useProjects } from '@/composables/project';
import * as saveAssetService from '@/services/save-asset';

const props = defineProps({
	initialName: {
		type: String,
		default: ''
	},
	isVisible: {
		type: Boolean,
		default: false
	},
	asset: {
		type: [Object, String, null] as PropType<saveAssetService.AssetToSave | string | null>,
		default: null
	},
	assetType: {
		type: String as PropType<AssetType>,
		default: AssetType.Model
	},
	openOnSave: {
		type: Boolean,
		default: false
	},
	isUpdatingAsset: {
		type: Boolean,
		default: false
	}
});

const emit = defineEmits(['close-modal', 'on-save']);

let newAsset: any = null;
const newName = ref<string>('');

const title = computed(() => {
	if (!props.asset) return `Create new ${props.assetType}`;
	if (props.isUpdatingAsset) return `Update ${props.assetType} name`;
	return `Save as a new ${props.assetType}`;
});

function onSave(data: any) {
	emit('on-save', data);
}

function closeModal() {
	newName.value = '';
	emit('close-modal');
}

function newAMR(modelName: string = '') {
	const amr: Model = {
		header: {
			name: modelName,
			description: '',
			schema:
				'https://raw.githubusercontent.com/DARPA-ASKEM/Model-Representations/petrinet_v0.5/petrinet/petrinet_schema.json',
			schema_name: 'petrinet',
			model_version: '0.1'
		},
		model: {
			states: [],
			transitions: []
		},
		semantics: {
			ode: {
				rates: [],
				initials: [],
				parameters: []
			}
		}
	};
	return amr;
}

// Generic save function
function save() {
	// Prepare the asset with the new name
	switch (props.assetType) {
		case AssetType.Model:
			(newAsset as Model).header.name = newName.value;
			break;
		case AssetType.Dataset:
			(newAsset as Dataset).name = newName.value;
			break;
		case AssetType.Workflow:
			(newAsset as Workflow).name = newName.value;
			break;
		case AssetType.Code:
			// File needs to be created here since name is read only
			// Here newAsset comes as a string and is reassigned as a File
			newAsset = new File([newAsset], newName.value);
			break;
		case AssetType.InterventionPolicy:
			(newAsset as InterventionPolicy).name = newName.value;
			(newAsset as InterventionPolicy).temporary = false;
			break;
		case AssetType.ModelConfiguration:
			(newAsset as ModelConfiguration).name = newName.value;
			(newAsset as ModelConfiguration).temporary = false;
			break;
		default:
			throw new Error(`Unsupported asset type ${props.assetType}`);
	}

	// Save method
	if (props.isUpdatingAsset) {
		saveAssetService.updateAddToProject(newAsset, props.assetType, onSave);
	} else {
		saveAssetService.saveAs(newAsset, props.assetType, props.openOnSave, onSave);
	}

	closeModal();
}

function initializeAsset() {
	newName.value = props.initialName;

	// If an asset is passed, clone it for saving
	if (props.asset) {
		newAsset = cloneDeep(props.asset);
		if (props.assetType === AssetType.Code) {
			newName.value = setFileExtension(newName.value, ProgrammingLanguage.Python);
		}
		return;
	}

	// Creates an empty version of the asset if there is no asset passed
	switch (props.assetType) {
		case AssetType.Model:
			newAsset = newAMR() as Model;
			break;
		case AssetType.Workflow:
			if (isEmpty(newName.value)) {
				const workflows = useProjects().getActiveProjectAssets(AssetType.Workflow);
				newName.value = `workflow ${workflows.length + 1}`;
			}
			newAsset = emptyWorkflow();
			break;
		case AssetType.Code:
			newName.value = setFileExtension('untitled', ProgrammingLanguage.Python);
			newAsset = new File([''], newName.value);
			break;
		default:
			break;
	}
}
</script>

<style scoped>
form {
	margin-top: var(--gap);
}
</style>
