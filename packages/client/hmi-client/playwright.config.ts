// eslint-disable-next-line import/no-extraneous-dependencies
import { devices, type PlaywrightTestConfig } from '@playwright/test';

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// require('dotenv').config();

/**
 * See https://playwright.dev/docs/test-configuration.
 */
const config: PlaywrightTestConfig = {
	testDir: './tests/e2e',
	/* Maximum time one test can run for. */
	timeout: 30 * 1000,
	expect: {
		/**
		 * Maximum time expect() should wait for the condition to be met.
		 * For example in `await expect(locator).toHaveText();`
		 */
		timeout: 5000
	},
	/* Run tests in files in parallel */
	fullyParallel: true,
	/* Fail the build on CI if you accidentally left test.only in the source code. */
	forbidOnly: !!process.env.CI,
	/* Retry on CI only */
	retries: process.env.CI ? 2 : 0,
	/* Opt out of parallel tests on CI. */
	workers: process.env.CI ? 1 : undefined,
	/* Reporter to use. See https://playwright.dev/docs/test-reporters */
	reporter: [['html', { outputFolder: 'tests/e2e/test-report' }]],
	/* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
	use: {
		/* Maximum time each action such as `click()` can take. Defaults to 0 (no limit). */
		actionTimeout: 0,
		/* Base URL to use in actions like `await page.goto('/')`. */
		baseURL: 'http://localhost:8080',

		/* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
		trace: 'on-first-retry',
		screenshot: 'only-on-failure',
		video: 'retain-on-failure'
	},

	/* Configure projects for major browsers */
	projects: [
		{
			name: 'chromium',
			use: {
				...devices['Desktop Chrome']
			}
		},

		{
			name: 'firefox',
			use: {
				...devices['Desktop Firefox']
			}
		},

		{
			name: 'webkit',
			use: {
				...devices['Desktop Safari']
			}
		}
	],

	/* Folder for test artifacts such as screenshots, videos, traces, etc. */
	outputDir: './tests/e2e/test-results/',

	/* Run your local dev server before starting the tests */
	webServer: {
		command: 'yarn dev',
		port: 8080,
		reuseExistingServer: !process.env.CI
	}
};

export default config;
