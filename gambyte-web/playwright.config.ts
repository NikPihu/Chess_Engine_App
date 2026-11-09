import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests against a running stack:
 *   docker compose -f deploy/docker-compose.yml up -d
 *   mvn -pl gambyte-server spring-boot:run
 *   npm run e2e            (starts the Vite dev server itself)
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  webServer: { command: 'npm run dev', url: 'http://localhost:5173', reuseExistingServer: true },
});
