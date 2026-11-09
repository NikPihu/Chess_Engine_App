/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

/**
 * Vite dev server + build config.
 *
 * In development the browser only talks to Vite (localhost:5173). Requests to /api and the
 * WebSocket at /ws are forwarded to the Spring Boot server on :8080, so cookies and CSRF work
 * exactly as in production, where Caddy serves both from one domain.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // "@/lib/api" instead of "../../lib/api"
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: {
    sourcemap: true,
    // Split big libraries into their own files: they change rarely, so browsers keep them cached
    // across deploys while our own code updates.
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/react-chessboard') || id.includes('node_modules/chess.js'))
            return 'chess';
          if (id.includes('node_modules/react') || id.includes('node_modules/scheduler')) return 'react';
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true, // lets Testing Library clean up the DOM after each test
    setupFiles: ['./src/test-setup.ts'],
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
