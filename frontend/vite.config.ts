import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Бэкенд в dev-профиле слушает 8081. В prod фронт лежит внутри jar,
// поэтому origin один и проксировать нечего.
const backend = 'http://localhost:8081'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/storage': backend,
      '/report': backend,
      '/auth': backend,
    },
  },
  build: {
    outDir: '../services/products-service/src/main/resources/static',
    emptyOutDir: true,
  },
})
