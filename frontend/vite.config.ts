/// <reference types="vitest/config" />
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const BACKEND = 'http://localhost:9000'

// Development: Vite serves the UI on :5173 and forwards server paths to Spring on :9000.
// To the browser everything is one origin, exactly like production (where Spring serves the built UI),
// so the session cookie and CSRF token behave the same in both.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': BACKEND,
      '/actuator': BACKEND,
      '/oauth2': BACKEND,
      '/.well-known': BACKEND,
      '/userinfo': BACKEND,
      '/connect': BACKEND,
      '/logout': BACKEND,
      // GET /login is our React page; POST /login is Spring Security's login processing
      '/login': {
        target: BACKEND,
        bypass: (req) => (req.method === 'GET' ? '/index.html' : undefined),
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
