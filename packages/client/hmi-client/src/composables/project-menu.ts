import { ref } from 'vue';
import { Project } from '@/types/Types';

const isCopyDialogVisible = ref(false);
const isShareDialogVisible = ref(false);
const isRemoveDialogVisible = ref(false);
const isProjectConfigDialogVisible = ref(false);
const menuProject = ref<Project | null>(null);

export function useProjectMenu() {
	return {
		isCopyDialogVisible,
		isShareDialogVisible,
		isRemoveDialogVisible,
		isProjectConfigDialogVisible,
		menuProject
	};
}
