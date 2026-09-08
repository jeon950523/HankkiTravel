import { defineConfig } from '@playwright/test'

const diagnostics = /실제 backend 연결 및 overflow|loading, failure, retry and slow network/
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  use: { baseURL: 'http://localhost:5175', browserName: 'chromium', channel: 'msedge', screenshot: 'only-on-failure' },
  reporter: [['list']],
  projects: [
    { name: 'development', testMatch: '**/foundation.spec.js', grep: diagnostics },
    { name: 'production', grepInvert: diagnostics },
  ],
})
