import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * El build se escribe directamente en los recursos estáticos del backend.
 * Así `spring-boot:run` sirve la misma aplicación que se prueba con Vite y
 * no hay un paso manual de copia entre carpetas.
 */
export default defineConfig(({ command }) => ({
  // En producción la SPA vive bajo /panel/ porque la sirve Spring Boot. En
  // desarrollo se sirve en la raíz para que /login y /panel sean rutas reales
  // del navegador y no dependan de una redirección del backend.
  base: command === 'build' ? '/panel/' : '/',
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/static/panel',
    emptyOutDir: true,
    sourcemap: false
  },
  server: {
    port: 5173,
    proxy: {
      '/panel/api': 'http://127.0.0.1:8080',
      '/panel/datos': 'http://127.0.0.1:8080',
      '/panel/csrf': 'http://127.0.0.1:8080',
      // El POST del formulario va al backend; el GET de la página lo sirve
      // Vite para no mezclar el build de producción con el servidor de recarga.
      '/login': {
        target: 'http://127.0.0.1:8080',
        bypass: (req) => (req.method === 'POST' ? undefined : '/index.html')
      },
      '/logout': 'http://127.0.0.1:8080',
      '/api': 'http://127.0.0.1:8080'
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    globals: true
  }
}));
