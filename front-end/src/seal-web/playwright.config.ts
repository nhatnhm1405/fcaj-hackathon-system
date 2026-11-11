import { defineConfig, devices } from '@playwright/test';

const configuredSlowMo = Number.parseInt(process.env.E2E_SLOW_MO_MS ?? '0', 10);
const slowMo = Number.isFinite(configuredSlowMo) ? configuredSlowMo : 0;
const configuredTimeout = Number.parseInt(process.env.E2E_TEST_TIMEOUT_MS ?? '180000', 10);
const testTimeout = Number.isFinite(configuredTimeout) ? configuredTimeout : 180_000;

export default defineConfig({
  testDir: './e2e',
  timeout: testTimeout,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [
    ['html', {
      outputFolder: process.env.PLAYWRIGHT_HTML_REPORT ?? 'playwright-report',
      open: process.env.PLAYWRIGHT_HTML_OPEN ?? 'never',
    }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    viewport: { width: 1366, height: 768 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    launchOptions: {
      slowMo,
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
