// Сквозная проверка виджета в настоящем браузере (Playwright, Chromium).
// Сам поднимает мок-сервер, Vite (домен ОКО :5173: виджет + прокси API) и тестовые страницы,
// прогоняет сценарии из frontend/CLAUDE.md и печатает итог.
//
//   npm run test:widget
//
// Первый запуск на своей машине: npm i -D playwright && npx playwright install chromium
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const { chromium } = createRequire(import.meta.url)('playwright');

const MOCK = 'http://localhost:4010';
const procs = [];
const start = (args) => {
  const p = spawn(process.execPath, args, { stdio: 'ignore' });
  procs.push(p);
  return p;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, extra = '') => {
  results.push({ name, ok });
  console.log(`${ok ? '✔' : '✘'} ${name}${extra ? ` — ${extra}` : ''}`);
};
const setMode = (m) => fetch(`${MOCK}/__mock/mode?widget=${m}`);

async function waitFor(url) {
  for (let i = 0; i < 50; i++) {
    try { await fetch(url); return; } catch { await sleep(200); }
  }
  throw new Error(`не поднялся ${url}`);
}

// Открыть страницу и собрать ошибки. Сетевые сообщения браузера (не загрузился ресурс, CSP запретил фрейм)
// пишет сам браузер, а не наш код, — считаем их отдельно.
async function open(browser, url) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const errors = { page: [], console: [], browser: [] };
  page.on('pageerror', (e) => errors.page.push(e.message));
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const t = m.text();
    if (/Failed to load resource|ERR_|Refused to frame|net::|violates the following Content Security Policy/.test(t)) errors.browser.push(t);
    else errors.console.push(t);
  });
  const t0 = Date.now();
  await page.goto(url, { waitUntil: 'load' });
  const loadMs = Date.now() - t0;
  return { ctx, page, errors, loadMs };
}

const frameOf = (page) => page.frameLocator('#oko-widget-root iframe');
const widgetVisible = (page) =>
  page.waitForFunction(() => {
    const f = document.querySelector('#oko-widget-root iframe');
    return f && getComputedStyle(f).visibility === 'visible' && f.getBoundingClientRect().height > 40;
  }, null, { timeout: 6000 }).then(() => true, () => false);
const widgetGone = (page) => page.evaluate(() => !document.getElementById('oko-widget-root'));

async function main() {
  start(['mock/server.mjs']);
  start(['node_modules/vite/bin/vite.js']);
  start(['scripts/test-pages.mjs']);
  await waitFor(`${MOCK}/__mock/mode`);
  await waitFor('http://localhost:5173/widget/v1/loader.js');
  await waitFor('http://localhost:5500');
  await sleep(500);
  const browser = await chromium.launch();

  // 1. HTML: оценка 2 с текстом доходит до сервера
  {
    const { ctx, page, errors } = await open(browser, 'http://localhost:5500');
    const shown = await widgetVisible(page);
    check('HTML: карточка появилась после загрузки страницы', shown);
    const f = frameOf(page);
    await f.getByRole('button', { name: 'Оценка 2 из 5' }).click();
    await f.getByLabel('Что не понравилось? Что можно улучшить?').fill('Тест e2e: долго грузится');
    await f.getByRole('button', { name: 'Отправить' }).click();
    check('HTML: после оценки ≤ 3 спросили текст и сказали «Спасибо»', await f.getByText('Спасибо').waitFor({ timeout: 3000 }).then(() => true, () => false));
    await sleep(3000);
    check('HTML: карточка закрылась сама', await widgetGone(page));
    check('HTML: скрипты и стили сайта не сломаны', (await page.textContent('#host-check')).startsWith('да'));
    check('HTML: нет ошибок JS и console.error', !errors.page.length && !errors.console.length, [...errors.page, ...errors.console].join('; '));
    await page.reload({ waitUntil: 'load' });
    await sleep(2500);
    check('HTML: повторно в течение 14 дней не спрашивает', await widgetGone(page));
    const stored = await page.evaluate(() => Object.entries(localStorage).filter(([k]) => k.startsWith('oko:')));
    check('HTML: в localStorage сайта только id и дата показа', stored.length === 1 && Object.keys(JSON.parse(stored[0][1])).sort().join() === 'days,id,last', JSON.stringify(stored));
    await ctx.close();
  }

  // 2. PHP: оценка 5 отправляется сразу
  {
    const { ctx, page, errors } = await open(browser, 'http://localhost:5501');
    if (await widgetVisible(page)) {
      await frameOf(page).getByRole('button', { name: 'Оценка 5 из 5' }).click();
      check('PHP: оценка 5 принята без вопроса о тексте', await frameOf(page).getByText('Спасибо').waitFor({ timeout: 3000 }).then(() => true, () => false));
    } else check('PHP: карточка появилась', false, 'нет php? запусти php -S localhost:5501 -t test-pages/php');
    check('PHP: нет ошибок JS', !errors.page.length && !errors.console.length);
    await ctx.close();
  }

  // 3. React: Esc закрывает, приложение работает
  {
    const { ctx, page, errors } = await open(browser, 'http://localhost:5502');
    const shown = await widgetVisible(page);
    check('React: карточка появилась', shown, shown ? '' : 'нужен интернет для esm.sh');
    if (shown) {
      await frameOf(page).getByRole('button', { name: 'Оценка 4 из 5' }).focus();
      await page.keyboard.press('Escape');
      await sleep(300);
      check('React: Esc закрывает карточку', await widgetGone(page));
    }
    const btn = page.locator('#counter');
    if (await btn.count()) {
      await btn.click();
      check('React: приложение хоста работает', (await btn.textContent()).includes('1'));
    }
    check('React: нет ошибок JS', !errors.page.length && !errors.console.length, [...errors.page, ...errors.console].join('; '));
    await ctx.close();
  }

  // 4. Сервер отвечает 500
  {
    await setMode('500');
    const { ctx, page, errors } = await open(browser, 'http://localhost:5500');
    await sleep(3000);
    check('500: виджета нет, страница работает', (await widgetGone(page)) && (await page.textContent('#host-check')).startsWith('да'));
    check('500: нет ошибок JS', !errors.page.length && !errors.console.length, errors.browser.length ? `браузер сообщил о сети: ${errors.browser.length}` : '');
    await ctx.close();
    await setMode('ok');
  }

  // 5. Сервер отвечает 10 секунд
  {
    await setMode('slow');
    const { ctx, page, errors, loadMs } = await open(browser, 'http://localhost:5500');
    check('10 с: событие load страницы не ждёт ОКО', loadMs < 2000, `${loadMs} мс`);
    await sleep(5000);
    check('10 с: виджет скрыт по таймауту', await widgetGone(page));
    check('10 с: нет ошибок JS', !errors.page.length && !errors.console.length);
    await ctx.close();
    await setMode('ok');
  }

  // 6. Сайт не в списке разрешённых: браузер не даёт встроить фрейм
  {
    const { ctx, page, errors } = await open(browser, 'http://127.0.0.1:5502');
    await sleep(3500);
    check('Чужой сайт: виджет не показан (frame-ancestors)', await widgetGone(page));
    check('Чужой сайт: нет ошибок JS', !errors.page.length && !errors.console.length, [...errors.page, ...errors.console].join('; '));
    await ctx.close();
  }

  // 7. Backend выключен (статика виджета ещё отдаётся)
  {
    procs[0].kill();
    await sleep(500);
    const { ctx, page, errors } = await open(browser, 'http://localhost:5500');
    await sleep(3000);
    check('Backend выключен: страница грузится, виджета нет', (await widgetGone(page)) && (await page.textContent('#host-check')).startsWith('да'));
    check('Backend выключен: нет ошибок JS', !errors.page.length && !errors.console.length);
    await ctx.close();
  }

  // 8. Домен ОКО недоступен совсем
  {
    procs[1].kill();
    await sleep(500);
    const { ctx, page, errors, loadMs } = await open(browser, 'http://localhost:5500');
    await sleep(2500);
    check('Сервер выключен: страница грузится, виджета нет', (await widgetGone(page)) && (await page.textContent('#host-check')).startsWith('да'), `load за ${loadMs} мс`);
    check('Сервер выключен: нет ошибок JS', !errors.page.length && !errors.console.length, errors.browser.length ? `браузер сообщил о сети: ${errors.browser.length}` : '');
    await ctx.close();
  }

  await browser.close();
  const failed = results.filter((r) => !r.ok).length;
  console.log(`\nИтого: ${results.length - failed} из ${results.length} проверок пройдено`);
  return failed;
}

main()
  .then((failed) => { procs.forEach((p) => p.kill()); process.exit(failed ? 1 : 0); })
  .catch((e) => { console.error(e); procs.forEach((p) => p.kill()); process.exit(1); });
