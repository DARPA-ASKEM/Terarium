import { defineStore } from 'pinia';
import type { User } from '@/types/Types';
import axios, { AxiosHeaders } from 'axios';
import { computed, ref } from 'vue';
import { createOidc, Oidc } from 'oidc-spa';
import { RoleType } from '@/types/Types';

/**
 * Main store used for authentication
 */
const useAuthStore = defineStore('auth', () => {
	const oidc = ref<Oidc | null>(null);

	const login = async (redirectUri: string) => {
		if (!oidc.value?.isUserLoggedIn) {
			oidc.value?.login({
				doesCurrentHrefRequiresAuth: false,
				extraQueryParams: {
					redirectUri
				}
			});
		} else {
			oidc.value?.logout({ redirectTo: 'specific url', url: redirectUri });
		}
	};
	const logout = async () => {
		if (oidc.value?.isUserLoggedIn) {
			oidc.value?.logout({ redirectTo: 'specific url', url: `${window.location.origin}/` });
		} else {
			// already logged out
		}
	};

	const getToken = () => {
		if (oidc.value?.isUserLoggedIn) {
			return oidc.value?.getTokens().accessToken;
		}
		// no token available
		return null;
	};

	// user
	const user = ref<User | null>(null);
	const updateUser = async (updatedUser: User) => {
		const response = await axios.put('/api/user/me', updatedUser, {
			headers: new AxiosHeaders().setAuthorization(`Bearer ${getToken()}`)
		});
		user.value = response.data;
	};

	const loadUserModel = async () => {
		const response = await axios.get('/api/user/me', {
			headers: new AxiosHeaders().setAuthorization(`Bearer ${getToken()}`)
		});
		user.value = response.data;
	};
	const userInitials = computed(() => `${user.value?.givenName?.charAt(0)}${user.value?.familyName?.charAt(0)}`);

	const init = async () => {
		const oidcSettings = await axios.get('/api/configuration/keycloak').then((r) => r.data);
		const newOidc = await createOidc({
			issuerUri: `${oidcSettings['auth-server-url']}/realms/${oidcSettings.realm}`,
			clientId: oidcSettings.resource,
			publicUrl: '/'
		});

		if (!newOidc.isUserLoggedIn) {
			newOidc.login({
				doesCurrentHrefRequiresAuth: false
			});
		}
		oidc.value = newOidc;
		await loadUserModel();
	};

	const isAdmin = computed(() => {
		if (!user.value) return false;
		return user.value?.roles.some((r) => r.name === RoleType.Admin);
	});

	return {
		login,
		logout,
		getToken,
		user,
		updateUser,
		loadUserModel,
		userInitials,
		init,
		isAdmin
	};
});

export default useAuthStore;
