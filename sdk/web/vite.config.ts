/// <reference types="vitest/config" />
import { defineConfig } from 'vite'

// Builds two files AuthReflector serves at /sdk/:
//   authreflector-web.js      UMD: <script src=".../sdk/authreflector-web.js"> gives window.AuthReflector
//   authreflector-web.esm.js  ES module: import { create } from '.../sdk/authreflector-web.esm.js'
export default defineConfig({
  build: {
    lib: {
      entry: 'src/index.ts',
      name: 'AuthReflector',
      formats: ['umd', 'es'],
      fileName: (format) => (format === 'es' ? 'authreflector-web.esm.js' : 'authreflector-web.js'),
    },
    sourcemap: true,
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    environmentOptions: { jsdom: { url: 'http://localhost:3000/app/page' } },
  },
})
