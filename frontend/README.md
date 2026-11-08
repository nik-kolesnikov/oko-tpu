# ОКО ТПУ — frontend: панель и виджет

Зона Таира: панель (React SPA), виджет для чужих сайтов и мок-сервер по `contracts/oko-openapi.yaml`.
Правила для Claude и людей — в `CLAUDE.md` рядом.

## Быстрый старт

```bash
cd frontend
npm install
npm run demo          # мок-сервер + панель + тестовые сайты
```

- Панель: http://localhost:5173 → «Войти через ТПУ» → в заглушке выбрать пользователя (Иванов — владелец, «Администратор ЦЭУ» видит всё).
- Сайты с виджетом: http://localhost:5500 (HTML), http://localhost:5501 (PHP, нужен `php`), http://localhost:5502 (React).
- Поставил оценку на сайте → она сразу видна в панели: «Личный кабинет студента» → «Отзывы».
- Чтобы виджет показался снова, очисти localStorage сайта (DevTools → Application → Local Storage).
- Сломать сервер для проверки виджета: http://localhost:4010/__mock/mode?widget=500 (или `slow`, `403`, `ok`).

В разработке домен ОКО один — http://localhost:5173: Vite отдаёт панель, виджет из `widget/src` (`/widget/v1/*`) и проксирует `/api` в мок или backend. Так же в проде nginx отдаёт панель, `widget/v1` и `/api` с одного домена.

### С настоящим backend

```bash
# 1. PostgreSQL и backend (см. корневой README)
docker run -d --name oko-pg -e POSTGRES_DB=oko -e POSTGRES_USER=oko -e POSTGRES_PASSWORD=oko -p 5432:5432 postgres:17-alpine
cd backend
OKO_PUBLIC_ORIGIN=http://localhost:5173 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#   Windows (cmd): set "OKO_PUBLIC_ORIGIN=http://localhost:5173" && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

# 2. Панель против backend на :8080
cd frontend && npm run dev:backend
```

`OKO_PUBLIC_ORIGIN=http://localhost:5173` обязателен: backend строит по нему адрес входа и принимает оценки из iframe только с этого origin. Вход — заглушка `dev-login`: любой логин, `admin` — админ ЦЭУ.

Виджет на тестовых сайтах с настоящим ключом: создай продукт, добавь в «Разрешённые сайты» `http://localhost:5500`, `http://localhost:5501`, `http://localhost:5502`, затем
`OKO_TEST_KEY=pk_… npm run test-pages` (Windows, cmd: `set "OKO_TEST_KEY=pk_…" && npm run test-pages`).

Что backend v1 ещё не отдаёт (дашборд, статистика, отзывы, релизы), панель показывает как «раздел пока недоступен»; главная в этом случае — список продуктов со статусами сервисов. Когда эндпоинты появятся, всё заработает без изменений в панели.

### Сборка для сервера

`npm run build:deploy` → `frontend/dist-deploy/panel` и `frontend/dist-deploy/widget/v1` — ровно то, что ждёт `deploy/nginx` (`root /usr/share/nginx/html`). Локально: `OKO_STATIC_DIR=../frontend/dist-deploy docker compose -f deploy/docker-compose.yml --env-file deploy/.env up`. На VPS — скопировать содержимое `dist-deploy` в `/opt/oko/static`.

Все команды: `CLAUDE.md`, раздел «Команды».

## Что где

```
frontend/
├── src/                       панель
│   ├── api/
│   │   ├── schema.d.ts        типы из контракта (npm run gen:api), руками не правим
│   │   ├── types.ts           короткие имена типов
│   │   ├── client.ts          fetch + CSRF + ApiError
│   │   └── hooks.ts           хук на каждую операцию, имя = operationId
│   ├── lib/                   форматирование, правила подсчёта, код вставки, одноразовый ключ
│   ├── components/            лампочка, оценка, состояния «загрузка / пусто / ошибка»
│   └── pages/
│       ├── DashboardPage      дерево продукт → сервисы
│       ├── ProductCreatePage  регистрация → сразу ключ и «Интеграция»
│       └── product/           вкладки: Обзор, Отзывы, Релизы, Интеграция, Сервисы, Настройки
├── widget/src/
│   ├── loader.js              ставится на чужой сайт
│   ├── frame.js, frame.css    форма оценки внутри iframe (страницу iframe генерирует backend)
├── mock/                      мок-сервер (состояние в памяти, демо-данные)
├── test-pages/                «чужие» сайты: html, php, react
└── scripts/                   demo, сборка виджета, e2e виджета
```

## Как работает виджет

```
сайт ТПУ                              домен ОКО
────────                              ─────────
сниппет ждёт load → грузит loader.js
loader.js: смотрит localStorage сайта
  (спрашивали < 14 дней назад? → тишина)
  создаёт <div id="oko-widget-root"> + iframe ──► GET /api/v1/widget/frame?key=…
                                                  backend: <div id="oko-root" data-key …>
                                                  + <script src=/widget/v1/frame.js>
                                                  + CSP frame-ancestors <сайты продукта>
        ◄── postMessage {oko:'hello'}            frame.js запущен
        ──► postMessage {oko:'init', raterId, pagePath, version}
                                                  GET /api/v1/widget/config (2 с)
        ◄── {oko:'ready', position, days}         карточка нарисована
  делает iframe видимым, пишет дату показа
        ◄── {oko:'resize', height}
                                                  POST /api/v1/widget/ratings (2 с)
        ◄── {oko:'close'} или {oko:'hide'}        «Спасибо» / любая ошибка
  удаляет свой div
```

Если hello не пришёл за 2 с или ready — ещё за 2,5 с, loader молча удаляет iframe. Поэтому выключенный, медленный или отвечающий 500 сервер для сайта незаметен. Это проверяет `npm run test:widget` (23 проверки).

Вес после сборки: loader.js ≈ 1,4 КБ gzip, весь виджет ≈ 4,2 КБ (лимиты 10 и 30 КБ).

## Связка с backend v1

Проверено на backend из `dev` (Spring Boot, профиль `dev`, PostgreSQL 16):
вход через `dev-login`, создание продукта и выпуск ключей, разрешённые сайты, сервисы, владельцы, настройки виджета, ошибки `409 DUPLICATE_URL` и `404` при добавлении незнакомого владельца; виджет на HTML, PHP и React → `201` и запись в `rating` и `rating_daily`.

Как устроено:
- страницу iframe генерирует backend (`WidgetService.frame`): `<div id="oko-root" data-key data-service-id>` и `<script src="/widget/v1/frame.js">`. `frame.js` сам подключает `frame.css` (CSP разрешает `style-src 'self'`);
- оценка из iframe приходит с `Origin` = `OKO_PUBLIC_ORIGIN`; другой `Origin` backend не принимает (контракт, `submitWidgetRating`);
- CSRF: cookie `XSRF-TOKEN` → заголовок `X-XSRF-TOKEN` (`SpaCsrfTokenRequestHandler`);
- мок (`mock/server.mjs`) отдаёт ту же разметку iframe и тот же CSP, что и backend.

## Расхождения в документах (решает Николай)

Полный список — `docs/team/Расхождения в документах.md`. Что из него касается панели:

Решено по «Предложениям по открытым вопросам» (8 ноября):
- **Ключи.** Полный ключ, в том числе публичный, виден только при выпуске и перевыпуске. При создании продукта ключи не выпускаются: на вкладке «Интеграция» — кнопка «Выпустить публичный ключ».
- **Формула индекса** — доля 4–5 минус доля 1–2, и в `CLAUDE.md`, и в контракте. Мок считает так же.
- **Лампочка продукта** — `degraded` (жёлтая), правило в `src/lib/health.ts`. Вариант «известный `down` важнее `unknown`» ждёт решения Николая.

Осталось:
1. **Код вставки** в «Плане на неделю» подразумевает `<script async>`, но такой тег задерживает событие `load` сайта, если сервер ОКО висит. Поэтому сниппет подключает loader после `load` (`src/lib/embed.ts`).

## Как поддерживать самому

- **Поменялся контракт** → `npm run gen:api` → `npm run typecheck`. TypeScript покажет все места, которые надо поправить.
- **Новый эндпоинт** → хук в `src/api/hooks.ts` по образцу соседних → использовать на странице.
- **Новый экран** → у каждого экрана три состояния: `LoadingState`, `EmptyState`, `ErrorState` из `components/States.tsx`.
- **Правка виджета** → `npm run build:widget` (покажет вес) → `npm run test:widget`. Без зависимостей, текст только через `textContent`.
- **Перед PR** → `npm run typecheck && npm test && npm run test:widget`.

## Где учиться

Порядок — от того, что нужно для правок прямо сейчас, к углублению.

**Основа панели**
- React, официальный учебник на русском: https://ru.react.dev/learn — разделы «Describing the UI», «Adding Interactivity», «Managing State». Потом «You Might Not Need an Effect».
- TypeScript Handbook: https://www.typescriptlang.org/docs/handbook/intro.html — Everyday Types, Narrowing, Generics.
- Mantine (компоненты): https://mantine.dev/getting-started/ — у каждого компонента страница с примерами и props. Графики: https://mantine.dev/charts/line-chart/
- TanStack Query (загрузка данных и кэш): https://tanstack.com/query/latest/docs/framework/react/overview — Queries, Mutations, Query Invalidation.
- React Router: https://reactrouter.com/home — режим Declarative.
- Vite: https://vite.dev/guide/ — сервер разработки, proxy, переменные окружения.

**Виджет (чистый JS)**
- learn.javascript.ru, «Общение между окнами» (postMessage): https://learn.javascript.ru/cross-window-communication
- MDN, postMessage: https://developer.mozilla.org/ru/docs/Web/API/Window/postMessage
- MDN, CSP frame-ancestors: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/frame-ancestors
- MDN, сторонние cookie (почему raterId не в cookie iframe): https://developer.mozilla.org/en-US/docs/Web/Privacy/Guides/Third-party_cookies
- MDN, AbortController (таймауты fetch): https://developer.mozilla.org/en-US/docs/Web/API/AbortController
- MDN, document.currentScript: https://developer.mozilla.org/en-US/docs/Web/API/Document/currentScript
- WAI-ARIA, паттерн диалога (фокус, Esc): https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/

**Инструменты**
- openapi-typescript (типы из контракта): https://openapi-ts.dev/introduction
- Prism (мок по OpenAPI): https://docs.stoplight.io/docs/prism/674b27b261c3c-prism-overview
- Playwright (e2e): https://playwright.dev/docs/intro

**Как читать этот код**: начни с `src/api/hooks.ts` → `pages/DashboardPage.tsx` → `pages/product/IntegrationTab.tsx`. Виджет: `widget/src/loader.js`, потом `frame.js`. Каждый файл начинается с комментария, зачем он нужен.
