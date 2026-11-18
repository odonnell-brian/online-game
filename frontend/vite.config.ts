import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api to Spring Boot so there are no CORS issues in dev.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': 'http://127.0.0.1:8080' } },
});
