/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('..', import.meta.url)), 'VITE_')
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      port: 5173,
      strictPort: true,
      // Same-origin in development, exactly like production behind the Vercel rewrite:
      // auth cookies stay first-party and CORS is never needed.
      proxy: { '/api': { target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: false } },
    },
    // Maps are uploaded but not linked from the bundles, so production doesn't advertise them.
    build: { sourcemap: 'hidden' },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      include: ['src/**/*.test.{ts,tsx}'],
    },
  }
})
