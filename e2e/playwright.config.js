import { defineConfig } from '@playwright/test';

export default defineConfig({
  globalSetup: './auth.setup.js',
  testDir: '.',
  testMatch: '*.spec.js',
  workers: 1,
  retries: 0,
  timeout: 45000,
  use: { baseURL: 'http://frontend:8080', storageState: '/tmp/auth-state.json', viewport: { width: 1440, height: 1000 }, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  outputDir: '/reports/results',
  reporter: [['list'], ['html', { outputFolder: '/reports/html', open: 'never' }]],
});
