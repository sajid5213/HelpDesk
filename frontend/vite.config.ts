/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server forwards API calls to Spring Boot, so the browser sees a single
// origin (needed later for the SameSite=Strict refresh-token cookie).
const backend = 'http://localhost:8090'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': backend,
      '/auth': backend,
      '/.well-known': backend,
    },
  },
  test: {
    environment: 'jsdom',
    restoreMocks: true,
  },
})
