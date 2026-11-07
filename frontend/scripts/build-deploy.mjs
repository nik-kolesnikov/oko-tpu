// Сборка статики для nginx из deploy/: frontend/dist-deploy/{panel, widget/v1}.
// Структура совпадает с тем, что ждёт deploy/nginx/oko.conf (root /usr/share/nginx/html):
//   panel/      → location /           (SPA, try_files → index.html)
//   widget/v1/  → location /widget/    (loader.js, frame.js, frame.css)
// Локально: OKO_STATIC_DIR=../frontend/dist-deploy docker compose -f deploy/docker-compose.yml --env-file deploy/.env up
// На VPS: содержимое dist-deploy копируется в /opt/oko/static.
import { execSync } from 'node:child_process';
import { cp, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'dist-deploy');
const run = (cmd) => execSync(cmd, { cwd: root, stdio: 'inherit' });

run('npx tsc -p tsconfig.json');
run('npx vite build');
run('node scripts/build-widget.mjs');

await rm(out, { recursive: true, force: true });
await mkdir(path.join(out, 'widget'), { recursive: true });
await cp(path.join(root, 'dist'), path.join(out, 'panel'), { recursive: true });
await cp(path.join(root, 'widget', 'dist'), path.join(out, 'widget', 'v1'), { recursive: true });
console.log(`\nГотово: ${path.relative(process.cwd(), out)}/panel и ${path.relative(process.cwd(), out)}/widget/v1`);
