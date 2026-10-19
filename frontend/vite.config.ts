import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// Em desenvolvimento, /api e /actuator vao para o backend (mesma origem para o navegador).
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
