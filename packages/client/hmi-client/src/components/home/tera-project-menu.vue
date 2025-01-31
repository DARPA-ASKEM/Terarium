<template>
	<span v-if="isCopying">Copying...</span>
	<Button
		v-else
		:class="$attrs.class"
		icon="pi pi-ellipsis-v"
		rounded
		text
		@click.stop="toggle"
		:disabled="isEmpty(projectMenuItems)"
	/>
	<Menu ref="menu" :model="projectMenuItems" :popup="true" @focus="menuProject = project" />
</template>

<script setup lang="ts">
import { useProjectMenu } from '@/composables/project-menu';
import { Project } from '@/types/Types';
import { isEmpty } from 'lodash';
import Button from 'primevue/button';
import Menu from 'primevue/menu';
import { computed, ref } from 'vue';
import { exportProjectAsFile } from '@/services/project';
import { AcceptedExtensions } from '@/types/common';
import { MenuItem } from 'primevue/menuitem';
import useAuthStore from '@/stores/auth';

const props = defineProps<{ project: Project | null }>();

// Triggers modals from tera-common-modal-dialogs.vue to open
const { isCopyDialogVisible, isShareDialogVisible, isRemoveDialogVisible, isProjectConfigDialogVisible, menuProject } =
	useProjectMenu();

const isCopying = ref(false);

const menu = ref();

const editDetailsMenuItem = {
	label: 'Edit details',
	icon: 'pi pi-pencil',
	command: () => {
		isProjectConfigDialogVisible.value = true;
	}
};

const shareMenuItem = {
	label: 'Share',
	icon: 'pi pi-user-plus',
	command: () => {
		isShareDialogVisible.value = true;
	}
};

const removeMenuItem = {
	label: 'Delete',
	icon: 'pi pi-trash',
	command: () => {
		isRemoveDialogVisible.value = true;
	}
};

const copyMenuItem = {
	label: 'Copy',
	icon: 'pi pi-clone',
	command: async () => {
		isCopyDialogVisible.value = true;
	}
};

const downloadMenuItem = {
	label: 'Download',
	icon: 'pi pi-download',
	command: async () => {
		if (props.project) {
			isCopying.value = true;
			const blob = await exportProjectAsFile(props.project.id);
			if (blob) {
				const a = document.createElement('a');
				a.href = URL.createObjectURL(blob);
				a.download = `${props.project.name}.${AcceptedExtensions.PROJECTCONFIG}`;
				a.click();
				a.remove();
			}
			isCopying.value = false;
		}
	}
};

const projectMenuItems = computed(() => {
	// Basic access to a public and reader project
	const items: MenuItem[] = [copyMenuItem, downloadMenuItem];

	// Creator/Editor of the project
	if (['creator', 'writer'].includes(props.project?.userPermission ?? '')) {
		items.push(editDetailsMenuItem, shareMenuItem);
	}

	// Creator of the project, or an admin
	if (props.project?.userPermission === 'creator' || useAuthStore().isAdmin) {
		items.push(removeMenuItem);
	}

	return items;
});

function toggle(event: any) {
	menu.value.toggle(event);
}
</script>

<style scoped>
span {
	color: var(--primary-color);
}
</style>
