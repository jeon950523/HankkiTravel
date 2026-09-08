import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'prompt',
      manifest: {
        name: '한끼여행', short_name: '한끼여행',
        description: '좋은 한 끼가, 좋은 여행을 만든다.',
        lang: 'ko', start_url: '/', scope: '/', display: 'standalone',
        theme_color: '#414A3C', background_color: '#F7F3EB',
        icons: [{ src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
      workbox: {
        navigateFallbackDenylist: [/^\/actuator/, /^\/api/],
        // Health and all API responses remain network-only.
        runtimeCaching: [],
      },
    }),
  ],
  server: {
    port: 5175, strictPort: true,
    proxy: { '/actuator/health': 'http://localhost:8300' },
  },
  preview: {
    port: 5175, strictPort: true,
    proxy: { '/actuator/health': 'http://localhost:8300' },
  },
  test: { environment: 'node', include: ['src/**/*.test.js'], restoreMocks: true },
})
