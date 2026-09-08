import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  use: { baseURL: 'http://localhost:5175', browserName: 'chromium', channel: 'msedge', screenshot: 'only-on-failure' },
  reporter: [['list']],
})
