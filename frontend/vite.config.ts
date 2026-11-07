import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

// Откуда панель берёт данные в разработке (OKO_MODE):
//   mock     — npm run dev          → мок-сервер (npm run mock) на :4010
//   backend  — npm run dev:backend  → настоящий backend на :8080 (профиль dev, OKO_PUBLIC_ORIGIN=http://localhost:5173)
//   prism    — npm run dev:prism    → Prism (npm run mock:prism): строго по контракту, без состояния.
//                                     Prism отдаёт пути без /api/v1, поэтому префикс срезаем.
// В любом режиме виджет (/widget/v1/*) отдаёт сам Vite из widget/src — как nginx в проде отдаёт widget/dist.
// Поэтому домен ОКО в разработке один: http://localhost:5173 (панель, виджет и API через прокси).
const mode = process.env.OKO_MODE || 'mock';
const TARGETS: Record<string, string> = {
  mock: 'http://localhost:4010',
  backend: 'http://localhost:8080',
  prism: 'http://localhost:4010',
};
const target = process.env.OKO_API || TARGETS[mode] || TARGETS.mock;

const WIDGET_DIR = path.resolve(__dirname, 'widget/src');
const MIME: Record<string, string> = { '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };

/** /widget/v1/<файл> → widget/src/<файл>, как location /widget/ в deploy/nginx. */
function serveWidget(): Plugin {
  return {
    name: 'oko-widget-static',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const m = req.url?.match(/^\/widget\/v1\/([\w.-]+)(?:\?.*)?$/);
        if (!m || !MIME[path.extname(m[1])]) return next();
        try {
          const body = await readFile(path.join(WIDGET_DIR, m[1]));
          res.setHeader('Content-Type', MIME[path.extname(m[1])]);
          res.setHeader('Cache-Control', 'no-cache');
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.end(body);
        } catch {
          res.statusCode = 404;
          res.end();
        }
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), serveWidget()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': mode === 'prism'
        ? { target, changeOrigin: false, rewrite: (p) => p.replace(/^\/api\/v1/, '') }
        : { target, changeOrigin: false, xfwd: true },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // графики (recharts) и Mantine — отдельными файлами, чтобы основной бандл был меньше
        manualChunks: { charts: ['recharts', '@mantine/charts'], mantine: ['@mantine/core', '@mantine/hooks', '@mantine/dates', '@mantine/notifications'] },
      },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
