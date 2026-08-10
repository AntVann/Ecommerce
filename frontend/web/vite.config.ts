import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const target = (port: number) => ({
  target: `http://localhost:${port}`,
  changeOrigin: true,
  secure: false,
  cookiePathRewrite: {
    '/api/v1/auth': '/identity/api/v1/auth',
    '/api/v1/cart': '/cart/api/v1/cart'
  }
});

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/identity': { ...target(8081), rewrite: (path) => path.replace(/^\/identity/, '') },
      '/seller': { ...target(8082), rewrite: (path) => path.replace(/^\/seller/, '') },
      '/catalog': { ...target(8083), rewrite: (path) => path.replace(/^\/catalog/, '') },
      '/inventory': { ...target(8084), rewrite: (path) => path.replace(/^\/inventory/, '') },
      '/search': { ...target(8085), rewrite: (path) => path.replace(/^\/search/, '') },
      '/cart': { ...target(8086), rewrite: (path) => path.replace(/^\/cart/, '') },
      '/order': { ...target(8087), rewrite: (path) => path.replace(/^\/order/, '') },
      '/notification': { ...target(8089), rewrite: (path) => path.replace(/^\/notification/, '') }
    }
  }
});
