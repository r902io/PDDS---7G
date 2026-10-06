import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv, type ProxyOptions } from 'vite';

/**
 * Proxy de /api hacia el backend Spring Boot.
 *
 * - VITE_BACKEND_URL indica dónde corre el backend (esta máquina u otra).
 * - El flujo SSE (/api/simulacion/eventos) no debe cortarse por tiempo ni
 *   almacenarse en búfer: se desactivan los timeouts y se fuerzan las
 *   cabeceras que impiden el búfer intermedio y la compresión.
 */
function proxyBackend(destino: string): Record<string, ProxyOptions> {
  return {
    '/api': {
      target: destino,
      changeOrigin: true,
      timeout: 0,
      proxyTimeout: 0,
      configure: (proxy) => {
        proxy.on('proxyReq', (req, origen) => {
          if (origen.url?.startsWith('/api/simulacion/eventos')) {
            req.setHeader('Accept', 'text/event-stream');
            req.setHeader('Accept-Encoding', 'identity');
          }
        });
        proxy.on('proxyRes', (res) => {
          const tipo = String(res.headers['content-type'] ?? '');
          if (tipo.includes('text/event-stream')) {
            res.headers['cache-control'] = 'no-cache, no-transform';
            res.headers['x-accel-buffering'] = 'no';
            delete res.headers['content-length'];
          }
        });
      },
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const destino = (env.VITE_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');

  return {
    // Rutas relativas: el build funciona servido desde la raíz del backend
    // o desde cualquier subcarpeta, sin depender de internet.
    base: './',
    plugins: [react(), tailwindcss()],
    server: {
      proxy: proxyBackend(destino),
    },
    preview: {
      proxy: proxyBackend(destino),
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true,
    },
  };
});
