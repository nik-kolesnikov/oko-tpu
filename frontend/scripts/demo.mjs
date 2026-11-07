// Всё сразу одной командой: мок-сервер + панель + тестовые страницы.
//   npm run demo
// Панель: http://localhost:5173 · сайты с виджетом: :5500 (HTML), :5501 (PHP), :5502 (React)
import { spawn } from 'node:child_process';

const run = (cmd, args) => spawn(cmd, args, { stdio: 'inherit', shell: process.platform === 'win32' });
const children = [
  run('node', ['mock/server.mjs']),
  run('node', ['scripts/test-pages.mjs']),
  run('npx', ['vite']),
];
const stop = () => {
  for (const c of children) c.kill();
  process.exit(0);
};
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
