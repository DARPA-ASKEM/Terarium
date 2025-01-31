import { ref } from 'vue';
import { createWorkflow } from '@/services/workflow';
import { updateCodeAsset, uploadCodeToProject } from '@/services/code';
import { createModel, updateModel } from '@/services/model';
import { useProjects } from '@/composables/project';
import { AssetType } from '@/types/Types';
import { logger } from '@/utils/logger';
import router from '@/router';
import { RouteName } from '@/router/routes';
import type { Model, Code, InterventionPolicy, ModelConfiguration } from '@/types/Types';
import type { Workflow } from '@/types/workflow';
import { createInterventionPolicy, updateInterventionPolicy } from './intervention-policy';
import { createModelConfiguration, updateModelConfiguration } from './model-configurations';

export type AssetToSave = Model | Workflow | ModelConfiguration | InterventionPolicy | File;

// TODO: Once assets have a type property, we can remove the assetType parameter

// Saves the asset as a new asset
export async function saveAs(
	newAsset: AssetToSave,
	assetType: AssetType,
	openOnSave: boolean = false,
	onSaveFunction?: Function
) {
	let response: any = null;

	switch (assetType) {
		case AssetType.Model:
			response = await createModel(newAsset as Model);
			break;
		case AssetType.Workflow:
			response = await createWorkflow(newAsset as Workflow);
			break;
		case AssetType.Code:
			response = await uploadCodeToProject(newAsset as File, ref(0));
			break;
		case AssetType.InterventionPolicy:
			response = await createInterventionPolicy(newAsset as InterventionPolicy);
			break;
		case AssetType.ModelConfiguration:
			response = await createModelConfiguration(newAsset as ModelConfiguration);
			break;
		default:
			logger.info(`Saving for ${assetType} is not implemented.`);
			return;
	}

	if (!response?.id) {
		logger.info(`Failed to save ${assetType}.`);
		return;
	}
	const projectId = useProjects().activeProject.value?.id;

	// save to project
	if (!projectId) {
		logger.error(`Asset can't be saved since target project doesn't exist.`);
		return;
	}

	await useProjects().addAsset(assetType, response.id, projectId);

	// After saving notify the user and do any necessary actions
	logger.info(`${response.name} saved successfully in project ${useProjects().activeProject.value?.name}.`);
	await useProjects().refresh();

	// redirect to the asset page
	if (openOnSave) {
		router.push({
			name: RouteName.Project,
			params: {
				projectId,
				pageType: assetType,
				assetId: response.id
			}
		});
	}

	if (onSaveFunction) onSaveFunction(response);
}

// Overwrites/updates the asset and add to project
export async function updateAddToProject(newAsset: AssetToSave, assetType: AssetType, onSaveFunction?: Function) {
	if (!(newAsset instanceof File) && !newAsset.id) {
		logger.error(`Can't update an asset that lacks an id.`);
		return;
	}
	let response: any = null;

	switch (assetType) {
		case AssetType.Model:
			response = await updateModel(newAsset as Model);
			break;
		case AssetType.Code:
			response = await updateCodeAsset(newAsset as Code);
			break;
		case AssetType.InterventionPolicy:
			response = await updateInterventionPolicy(newAsset as InterventionPolicy);
			break;
		case AssetType.ModelConfiguration:
			response = await updateModelConfiguration(newAsset as ModelConfiguration);
			break;
		default:
			logger.info(`Update for ${assetType} is not implemented.`);
			return;
	}

	if (!response) {
		logger.error(`Failed to update ${assetType}.`);
		return;
	}

	const projectId = useProjects().activeProject.value?.id;
	if (!projectId) {
		logger.error(`Asset can't be saved since target project doesn't exist.`);
		return;
	}
	await useProjects().addAsset(assetType, response.id, projectId);

	logger.info(`Updated ${response.name}.`);
	await useProjects().refresh();
	if (onSaveFunction) onSaveFunction(response);
}
