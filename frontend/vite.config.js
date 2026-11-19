import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev: the frontend runs on 5173 and forwards /api to the Spring Boot server on 8080.
// Build: output goes into the Spring Boot jar's static folder, so one server serves everything.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: '../api/src/main/resources/static',
    emptyOutDir: true,
  },
})
