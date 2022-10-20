import { test, expect } from '@playwright/test';
import authRoute from './utils/uncloakIntercept';

test.describe('main landing page test', () => {
	test.beforeEach(async ({ page }) => {
		await authRoute(page);

		// Go to the starting (base) url before each test as defined in the config
		await page.goto('/');
	});

	test('should load the main page correctly', async ({ page }) => {
		// Expect a title "to contain" a substring.
		await expect(page).toHaveTitle(/TERArium/);

		// create a locator
		const header = page.locator('text=Recents');

		// Expect an attribute "to be strictly equal" to the value.
		await expect(header).toBeVisible();
	});
});
