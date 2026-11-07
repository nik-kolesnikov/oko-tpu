// Тестовые «чужие» сайты для виджета (разные origin, как в жизни):
//   http://localhost:5500 — обычный HTML
//   http://localhost:5501 — PHP-шаблон (нужен установленный php)
//   http://localhost:5502 — React SPA
//
// Откуда грузить виджет и с каким ключом — переменные окружения:
//   OKO_ORIGIN        домен ОКО, по умолчанию http://localhost:5173 (Vite: панель + виджет + прокси API)
//   OKO_TEST_KEY      публичный ключ продукта, по умолчанию демо-ключ из мока
//   OKO_TEST_SERVICE  id сервиса (необязательно), по умолчанию 1 для мока
// С настоящим backend: создай продукт в панели, добавь в «Разрешённые сайты»
// http://localhost:5500, http://localhost:5501, http://localhost:5502 и запусти
//   OKO_TEST_KEY=pk_... npm run test-pages
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'test-pages');
const DEMO_KEY = 'pk_demo_lk_7f3a9c2e5b1d4f60';
const env = {
  OKO_ORIGIN: process.env.OKO_ORIGIN || 'http://localhost:5173',
  OKO_TEST_KEY: process.env.OKO_TEST_KEY || DEMO_KEY,
  OKO_TEST_SERVICE: process.env.OKO_TEST_SERVICE ?? (process.env.OKO_TEST_KEY ? '' : '1'),
};
const js = (v) => v.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/</g, '\\x3c');

function render(html) {
  let out = html.replaceAll('__OKO_ORIGIN__', js(env.OKO_ORIGIN)).replaceAll('__OKO_KEY__', js(env.OKO_TEST_KEY));
  out = env.OKO_TEST_SERVICE
    ? out.replaceAll('__OKO_SERVICE__', js(env.OKO_TEST_SERVICE))
    : out.replace(/^.*__OKO_SERVICE__.*\n/m, '');
  return out;
}

function staticServer(port, folder) {
  http
    .createServer(async (req, res) => {
      try {
        const body = render(await readFile(path.join(dir, folder, 'index.html'), 'utf8'));
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-cache' });
        res.end(body);
      } catch {
        res.writeHead(404);
        res.end();
      }
    })
    .listen(port, () => console.log(`[test-pages] ${folder}: http://localhost:${port}`));
}

console.log(`[test-pages] виджет с ${env.OKO_ORIGIN}, ключ ${env.OKO_TEST_KEY.slice(0, 10)}…`);
staticServer(5500, 'html');
staticServer(5502, 'react');

const php = spawn('php', ['-S', 'localhost:5501', '-t', path.join(dir, 'php')], { stdio: 'ignore', env: { ...process.env, ...env } });
php.on('spawn', () => console.log('[test-pages] php:   http://localhost:5501'));
php.on('error', () => console.log('[test-pages] php не найден — PHP-страница не запущена (установите php или откройте index.php в своём сервере)'));
process.on('exit', () => php.kill());
process.on('SIGINT', () => process.exit(0));
