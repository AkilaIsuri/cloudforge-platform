import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// `npm run dev` serves on :5173 and forwards /api to the gateway on :8080
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
