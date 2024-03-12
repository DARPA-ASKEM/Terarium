import { logger } from '@/utils/logger';
import { createApp } from 'vue';
import { RouteLocationNormalized } from 'vue-router';
import { createPinia } from 'pinia';
import ConfirmationService from 'primevue/confirmationservice';
import PrimeVue from 'primevue/config';
import ToastService from 'primevue/toastservice';
import Tooltip from 'primevue/tooltip';
import VueFeather from 'vue-feather';
import VueGtag from 'vue-gtag';
import { MathfieldElement } from 'mathlive';
import VueKatex from '@hsorby/vue3-katex';
import { EventType } from '@/types/Types';
import * as EventService from '@/services/event';
import API from '@/api/api';
import useAuthStore from '@/stores/auth';
import router from '@/router';
import '@node_modules/katex/dist/katex.min.css';
import App from '@/App.vue';
import { useProjects } from '@/composables/project';
import '@/assets/css/style.scss';
import Keycloak from 'keycloak-js';
import { init } from '@/services/ClientEventService';

// Extend the window object to include the Keycloak object
declare global {
	interface Window {
		keycloak_init: Promise<boolean>;
		keycloak: Keycloak;
	}
}

// if keycloak has not been initialized, reload the page
const initialized = await window.keycloak_init;
if (!initialized) {
	logger.error('Authentication Failed, reloading a the page');
	window.location.assign('/');
}

// Create the Vue application
const app = createApp(App);
// Set up the pinia store to be able to use for useAuthStore()
app.use(createPinia());

// Set up the Keycloak authentication
const authStore = useAuthStore();
authStore.setKeycloak(window.keycloak);

// Initialize user
await authStore.init();
logger.info('Authenticated');
init();
// Token Refresh
setInterval(async () => {
	await window.keycloak.updateToken(70);
}, 6000);

// Set the hash value of the window.location to null
// This is to prevent the Keycloak from redirecting to the hash value
// after the authentication
window.location.hash = '';

app
	.use(router)
	.use(ToastService)
	.use(ConfirmationService)
	.use(PrimeVue, { ripple: true })
	.use(VueKatex)
	.directive('tooltip', Tooltip)
	.directive('focus', {
		mounted(el) {
			el.focus();
		}
	});

// Configure Google Analytics
const GTAG = await API.get('/configuration/ga');
if (GTAG.data) {
	app.use(VueGtag, { config: { id: GTAG.data } });
}

app.component('math-field', MathfieldElement);
app.component(VueFeather.name, VueFeather);
app.mount('body');

let previousRoute: RouteLocationNormalized | null = null;
let routeStartedMillis = Date.now();
router.beforeEach(async (to, _from, next) => {
	if (previousRoute) {
		const nowMillis = Date.now();
		const timeSpent = nowMillis - routeStartedMillis;
		await EventService.create(
			EventType.RouteTiming,
			useProjects().activeProject.value?.id,
			JSON.stringify({
				name: previousRoute.name,
				path: previousRoute.path,
				fullPath: previousRoute.fullPath,
				timeSpent
			})
		);
	}
	previousRoute = to;
	routeStartedMillis = Date.now();
	next();
});

// Allow the use of CSS custom properties
declare module '@vue/runtime-dom' {
	export interface CSSProperties {
		[key: `--${string}`]: string | undefined;
	}
}
