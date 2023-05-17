import { computed } from 'vue';
import { createRouter, createWebHashHistory } from 'vue-router';
import HomeView from '@/page/Home.vue';
import DataExplorerView from '@/page/data-explorer/DataExplorer.vue';
import UnauthorizedView from '@/page/Unauthorized.vue';
import ProjectView from '@/page/project/tera-project.vue';
// These are test/experiment pages
import ModelEditorView from '@/temp/ModelEditor.vue';
import ModelRunnerView from '@/temp/ModelRunner.vue';
import TA2Playground from '@/temp/TA2Playground.vue';
import ResponsivePlayground from '@/temp/ResponsivePlayground.vue';
import TheiaView from '@/temp/theia.vue';
import SSE from '@/temp/sse.vue';
import WorkflowPlayground from '@/temp/workflow-playground/WorkflowPlayground.vue';
import JupyterTransform from '@/temp/jupyter-transform.vue';
import SimulationWorkflow from '@/components/workflow/tera-simulation-workflow.vue';
import { RouteName } from './routes';

export enum RoutePath {
	Home = '/',
	Project = '/projects/:projectId/:pageType?/:assetName?/:assetId?',
	DataExplorer = '/explorer',
	Unauthorized = '/unauthorized',

	// Playground and experiments, these components are testing-only
	Theia = '/theia',
	Ta2Playground = '/ta2-playground',
	ResponsivePlaygroundPath = '/responsive-playground',
	ModelEditor = '/model-editor',
	ModelRunner = '/model-runner'
}

const routes = [
	{ name: 'unauthorized', path: RoutePath.Unauthorized, component: UnauthorizedView },
	{ name: RouteName.HomeRoute, path: RoutePath.Home, component: HomeView },
	{
		name: RouteName.ProjectRoute,
		path: RoutePath.Project,
		component: ProjectView,
		props: true
	},
	{
		name: RouteName.DataExplorerRoute,
		path: RoutePath.DataExplorer,
		component: DataExplorerView
	},
	// Playground and experiments, these components are testing-only
	{ path: RoutePath.Theia, component: TheiaView },
	{ path: RoutePath.Ta2Playground, component: TA2Playground },
	{ path: RoutePath.ResponsivePlaygroundPath, component: ResponsivePlayground },
	{ path: RoutePath.ModelEditor, component: ModelEditorView },
	{ path: RoutePath.ModelRunner, component: ModelRunnerView },
	{ path: '/workflow-playground', component: WorkflowPlayground },
	{ path: '/simulation-workflow', component: SimulationWorkflow },
	{ path: '/transform', component: JupyterTransform },
	{ path: '/sse', component: SSE }
];

const router = createRouter({
	// 4. Provide the history implementation to use. We are using the hash history for simplicity here.
	history: createWebHashHistory(),

	// short for `routes: routes`
	routes
});

export function useCurrentRoute() {
	return computed(() => router.currentRoute.value);
}

export default router;
