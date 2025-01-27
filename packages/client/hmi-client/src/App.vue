<template>
	<Toast position="top-center" />
	<tera-navbar :active="displayNavBar" />
	<router-view v-slot="{ Component }">
		<component class="page" :is="Component" />
	</router-view>
	<tera-footer />
	<tera-common-modal-dialogs />
	<ConfirmDialog class="w-4" />
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Toast from 'primevue/toast';
import API from '@/api/api';
import TeraNavbar from '@/components/navbar/tera-navbar.vue';
import TeraFooter from '@/components/navbar/tera-footer.vue';
import TeraCommonModalDialogs from '@/components/widgets/tera-common-modal-dialogs.vue';
import { useProjects } from '@/composables/project';
import { useCurrentRoute } from '@/router';
import { ToastSeverity, ToastSummaries, useToastService } from '@/services/toast';
import { Project } from '@/types/Types';
import ConfirmDialog from 'primevue/confirmdialog';

const toast = useToastService();

/* Router */
const route = useRoute();
const router = useRouter();
const currentRoute = (useCurrentRoute().value.name ?? '').toString();
const displayNavBar = computed(() => !['unauthorized', RouteName.WorkflowNode.toString()].includes(currentRoute));

/* Project */
API.interceptors.response.use(
	(response) => response,
	(error) => {
		const status = error.response.status;
		toast.showToast(ToastSeverity.error, `${ToastSummaries.NETWORK_ERROR} (${status})`, 'Unauthorized', 5000);
		if (status === 401 || status === 403) {
			router.push({ name: 'unauthorized' });
		}
	}
);

onMounted(() => useProjects().getAll());

// Update the project when the projectId changes
watch(
	() => route.params.projectId,
	(projectId, oldProjectId) => {
		if (projectId !== oldProjectId) {
			useProjects().get(projectId as Project['id']);
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
.page {
	display: flex;
	flex: 1;
	grid-area: main;
	isolation: isolate;
	overflow: hidden;
	position: relative;
	z-index: 1;
}
</style>
