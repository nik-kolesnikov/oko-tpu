// Сборка виджета: widget/src → widget/dist (минификация, ES2017) + проверка веса.
// Лимиты из frontend/CLAUDE.md: loader.js ≤ 10 КБ gzip, весь виджет ≤ 30 КБ gzip.
import { build, transform } from 'esbuild';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { gzipSync } from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'widget');
const src = path.join(root, 'src');
const dist = path.join(root, 'dist');
await mkdir(dist, { recursive: true });

for (const f of ['loader.js', 'frame.js']) {
  await build({
    entryPoints: [path.join(src, f)],
    outfile: path.join(dist, f),
    minify: true,
    target: 'es2017',
    format: 'iife',
    legalComments: 'none',
    logLevel: 'warning',
  });
}
const cssOut = await transform(await readFile(path.join(src, 'frame.css'), 'utf8'), { loader: 'css', minify: true });
await writeFile(path.join(dist, 'frame.css'), cssOut.code);

const LIMITS = { loader: 10 * 1024, total: 30 * 1024 };
let total = 0;
let failed = false;
for (const f of ['loader.js', 'frame.js', 'frame.css']) {
  const gz = gzipSync(await readFile(path.join(dist, f))).length;
  total += gz;
  console.log(`${f.padEnd(11)} ${(gz / 1024).toFixed(2)} КБ gzip`);
  if (f === 'loader.js' && gz > LIMITS.loader) {
    console.error(`loader.js больше ${LIMITS.loader / 1024} КБ gzip`);
    failed = true;
  }
}
console.log(`итого      ${(total / 1024).toFixed(2)} КБ gzip (лимит ${LIMITS.total / 1024} КБ)`);
if (total > LIMITS.total) {
  console.error('Виджет превысил лимит веса');
  failed = true;
}
process.exit(failed ? 1 : 0);
