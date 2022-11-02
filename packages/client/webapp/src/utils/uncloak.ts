import useAuthStore from '../stores/auth';

export const uncloak = async (url: string) => {
	const auth = useAuthStore();
	const response = await fetch(url, {
		headers: {
			Authorization: `Bearer ${auth.token}`
		}
	});

	if (!response.ok) {
		return null;
	}

	const data = await response.json();
	return data;
};
