import { computed } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '@/page/tera-home.vue';
import UnauthorizedView from '@/page/Unauthorized.vue';
import ProjectView from '@/page/tera-project.vue';
import WorkflowNodeView from '@/page/WorkflowNode.vue';
import UserAdminView from '@/components/navbar/tera-useradmin.vue';

// These are test/experiment pages
import ResponsivePlayground from '@/temp/ResponsivePlayground.vue';
import SSE from '@/temp/sse.vue';
import EvaluationScenarios from '@/temp/EvaluationScenarios.vue';
import AMRPetriTest from '@/temp/AMRPetriTest.vue';
import PyodideTest from '@/temp/PyodideTest.vue';
import JupyterTest from '@/temp/JupyterTest.vue';
import CustomInputTest from '@/temp/custom-input-test.vue';
import ClipboardTest from '@/temp/Clipboard.vue';
import VegaliteTest from '@/temp/Vegalite.vue';
import EquationsTest from '@/temp/Equations.vue';
import FunmanDebugger from '@/temp/FunmanDebugger.vue';
import { RouteName } from './routes';

export enum RoutePath {
	Home = '/',
	Project = `/projects/:projectId/:pageType?/:assetId?`,
	WorkflowNode = `/${RouteName.WorkflowNode}/:projectId/:workflowId/:nodeId`,
	UserAdmin = '/user-admin',
	Unauthorized = '/unauthorized',

	// Playground and experiments, these components are testing-only
	Ta2Playground = '/ta2-playground',
	ResponsivePlaygroundPath = '/responsive-playground',
	EvaluationScenariosPath = '/evaluation-scenarios'
}

const routes = [
	{ name: 'unauthorized', path: RoutePath.Unauthorized, component: UnauthorizedView },
	{ name: RouteName.Home, path: RoutePath.Home, component: HomeView },
	{
		name: RouteName.Project,
		path: RoutePath.Project,
		component: ProjectView,
		props: true
	},
	{
		name: RouteName.WorkflowNode,
		path: RoutePath.WorkflowNode,
		component: WorkflowNodeView,
		props: true
	},
	{
		name: RouteName.UserAdmin,
		path: RoutePath.UserAdmin,
		component: UserAdminView
	},

	// Playground and experiments, these components are testing-only
	{ path: RoutePath.ResponsivePlaygroundPath, component: ResponsivePlayground },
	{ path: RoutePath.EvaluationScenariosPath, component: EvaluationScenarios },
	{ path: '/sse', component: SSE },
	{ path: '/amr-petri-test', component: AMRPetriTest },
	{ path: '/pyodide-test', component: PyodideTest },
	{ path: '/jupyter-test', component: JupyterTest },
	{ path: '/custom-input-test', component: CustomInputTest },
	{ path: '/clipboard', component: ClipboardTest },
	{ path: '/vegalite', component: VegaliteTest },
	{ path: '/funman-debugger', component: FunmanDebugger },
	{ path: '/equations', component: EquationsTest }
];

const router = createRouter({
	history: createWebHistory(),
	routes
});

export function useCurrentRoute() {
	return computed(() => router.currentRoute.value);
}

export default router;
