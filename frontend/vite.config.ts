import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Бэкенд в dev-профиле слушает 8081. В prod фронт лежит внутри jar,
// поэтому origin один и проксировать нечего.
const backend = 'http://localhost:8081'

// Секретный префикс, под которым приложение висит на сервере (как у панели x-ui).
// Передаётся при сборке: VITE_BASE_PATH=/k3j9x2abc. Должен совпадать с APP_BASE_PATH бэкенда.
const basePath = (process.env.VITE_BASE_PATH ?? '').replace(/\/+$/, '')

export default defineConfig({
  base: `${basePath}/`,
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
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
