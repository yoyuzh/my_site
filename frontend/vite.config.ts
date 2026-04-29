import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  envDir: '..',
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    allowedHosts: ['localhost', '127.0.0.1', 'host.docker.internal'],
    proxy: {
      '/api': {
        // The backend binds to 127.0.0.1 in local dev. Using localhost here can
        // resolve to a different loopback target on some machines and proxy to
        // the wrong service.
        target: process.env.VITE_BACKEND_URL ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
