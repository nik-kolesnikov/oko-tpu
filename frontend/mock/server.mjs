// Мок-сервер ОКО по contracts/oko-openapi.yaml.
// Держит состояние в памяти: созданный продукт виден в списке, оценка из виджета — в ленте.
// Это НЕ backend. Он нужен, чтобы панель и виджет работали, пока Данил пишет настоящий API.
//
// Запуск: npm run mock   (порт 4010, можно поменять через MOCK_PORT)
// Режимы сбоев для проверки виджета: http://localhost:4010/__mock/mode?widget=ok|500|slow|403

import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { buildSeed } from './data.mjs';

const PORT = Number(process.env.MOCK_PORT || 4010);
// Домен ОКО: в разработке панель, виджет и API открываются через Vite на :5173 (см. vite.config.ts).
const PUBLIC_ORIGIN = process.env.OKO_PUBLIC_ORIGIN || 'http://localhost:5173';
const SELF_ORIGINS = new Set([PUBLIC_ORIGIN, `http://localhost:${PORT}`, `http://127.0.0.1:${PORT}`]);
const ROOT = path.dirname(fileURLToPath(import.meta.url));
const WIDGET_DIR = path.resolve(ROOT, '..', 'widget', process.env.WIDGET_DIR || 'src');
const API = '/api/v1';
const DUPLICATE_WINDOW_DAYS = 14;
const LOW_SAMPLE = 20;

const db = buildSeed();
const sessions = new Map(); // token -> userId
let mode = { widget: 'ok' };

// ───────────── утилиты ─────────────
const nextId = (arr) => arr.reduce((m, x) => Math.max(m, x.id), 0) + 1;
const nowIso = () => new Date().toISOString();
const TOMSK_OFFSET_MS = 7 * 3600000; // Asia/Tomsk, без перехода на летнее время
const tomskDay = (isoStr) => new Date(new Date(isoStr).getTime() + TOMSK_OFFSET_MS).toISOString().slice(0, 10);
const todayTomsk = () => tomskDay(nowIso());
const addDays = (day, n) => {
  const d = new Date(day + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const daysBetween = (a, b) => Math.round((new Date(b + 'T00:00:00Z') - new Date(a + 'T00:00:00Z')) / 86400000);
const isDate = (s) => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s));

class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    Object.assign(this, { status, code, details });
  }
}
const notFound = () => new ApiError(404, 'NOT_FOUND', 'Не найдено');
const validation = (details) => new ApiError(400, 'VALIDATION_ERROR', 'Некорректные данные', details);

function send(res, status, body, headers = {}) {
  if (body === undefined || body === null) {
    res.writeHead(status, headers);
    return res.end();
  }
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', ...headers });
  res.end(JSON.stringify(body));
}

function parseCookies(req) {
  const out = {};
  for (const part of (req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

async function readJson(req) {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const raw = Buffer.concat(chunks).toString('utf8');
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    throw validation([{ field: 'body', message: 'не JSON' }]);
  }
}

const isUri = (s) => {
  try {
    const u = new URL(s);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
};

function paginate(items, query) {
  const page = Math.max(1, Number(query.get('page') || 1));
  const size = Math.min(100, Math.max(1, Number(query.get('size') || 20)));
  const totalItems = items.length;
  return {
    page, size, totalItems,
    totalPages: Math.max(1, Math.ceil(totalItems / size)),
    items: items.slice((page - 1) * size, page * size),
  };
}

// ───────────── представления ─────────────
const userView = (id) => {
  const u = db.users.find((x) => x.id === id);
  return u && { id: u.id, login: u.login, fullName: u.fullName, email: u.email };
};
const productView = (p) => ({
  id: p.id, name: p.name, description: p.description, url: p.url, status: p.status,
  owners: p.ownerIds.map(userView), createdAt: p.createdAt, updatedAt: p.updatedAt,
});
function healthOf(service) {
  if (!service.monitoringRef || !db.health[service.monitoringRef]) return { status: 'unknown', responseMs: null, checkedAt: null };
  const h = db.health[service.monitoringRef];
  return { status: h.status, responseMs: h.responseMs, checkedAt: new Date(Date.now() - 20000).toISOString() };
}
const serviceView = (s) => ({
  id: s.id, productId: s.productId, name: s.name, url: s.url, monitoringRef: s.monitoringRef,
  status: s.status, health: healthOf(s), createdAt: s.createdAt,
});
const keyView = (k) => ({
  id: k.id, productId: k.productId, type: k.type, keyPrefix: k.key.slice(0, 11),
  createdAt: k.createdAt, createdBy: userView(k.createdBy), revokedAt: k.revokedAt,
});
const releaseView = (r) => ({ ...r, createdBy: userView(r.createdBy) });
const reviewView = (r) => ({
  id: r.id, score: r.score, comment: r.comment, serviceId: r.serviceId,
  serviceName: r.serviceId ? db.services.find((s) => s.id === r.serviceId)?.name ?? null : null,
  raterRole: r.raterRole, pagePath: r.pagePath, version: r.version, source: r.source, createdAt: r.createdAt,
});

// ───────────── статистика ─────────────
function stats(rows) {
  const histogram = [1, 2, 3, 4, 5].map((score) => ({ score, count: 0 }));
  let sum = 0;
  for (const r of rows) {
    histogram[r.score - 1].count++;
    sum += r.score;
  }
  const n = rows.length;
  // Формула индекса из контракта: доля 4–5 минус доля 1–2.
  const fives = histogram[3].count + histogram[4].count;
  const low = histogram[0].count + histogram[1].count;
  return {
    count: n,
    average: n ? Math.round((sum / n) * 10) / 10 : null,
    index: n ? Math.round(((fives - low) / n) * 100) : null,
    lowSample: n < LOW_SAMPLE,
    histogram,
  };
}

function ratingsFor(productId, { from, to, serviceId } = {}) {
  return db.ratings.filter((r) => {
    if (r.productId !== productId) return false;
    if (serviceId != null && r.serviceId !== serviceId) return false;
    const day = tomskDay(r.createdAt);
    if (from && day < from) return false;
    if (to && day > to) return false;
    return true;
  });
}

function periodStart(day, granularity) {
  if (granularity === 'day') return day;
  if (granularity === 'month') return day.slice(0, 8) + '01';
  const dow = new Date(day + 'T00:00:00Z').getUTCDay(); // 0 = вс
  return addDays(day, -((dow + 6) % 7));
}

// ───────────── доступ ─────────────
function currentUser(req) {
  const token = parseCookies(req).OKO_SESSION;
  const uid = token && sessions.get(token);
  return uid ? db.users.find((u) => u.id === uid) : null;
}
function requireUser(req) {
  const u = currentUser(req);
  if (!u) throw new ApiError(401, 'UNAUTHORIZED', 'Нужно войти');
  return u;
}
function visibleProduct(user, productId) {
  const p = db.products.find((x) => x.id === productId);
  if (!p) throw notFound();
  if (!user.isAdmin && !p.ownerIds.includes(user.id)) throw notFound();
  return p;
}
function checkCsrf(req) {
  const cookie = parseCookies(req)['XSRF-TOKEN'];
  if (!cookie || req.headers['x-xsrf-token'] !== cookie) {
    throw new ApiError(403, 'FORBIDDEN', 'Нет CSRF-токена');
  }
}

function findKey(req, type) {
  const raw = req.headers['x-api-key'];
  const k = raw && db.keys.find((x) => x.key === raw && !x.revokedAt);
  if (!k || k.type !== type) throw new ApiError(401, 'INVALID_KEY', 'Неверный ключ');
  return k;
}

// ───────────── валидация ─────────────
function validateRating(body, { raterMin }) {
  const d = [];
  if (!Number.isInteger(body.score) || body.score < 1 || body.score > 5) d.push({ field: 'score', message: 'должно быть от 1 до 5' });
  if (typeof body.raterId !== 'string' || body.raterId.length < raterMin || body.raterId.length > 128) d.push({ field: 'raterId', message: `строка ${raterMin}–128 символов` });
  if (body.comment != null && (typeof body.comment !== 'string' || body.comment.length > 500)) d.push({ field: 'comment', message: 'не длиннее 500 символов' });
  if (body.pagePath != null && (typeof body.pagePath !== 'string' || body.pagePath.length > 500)) d.push({ field: 'pagePath', message: 'не длиннее 500 символов' });
  if (body.version != null && (typeof body.version !== 'string' || body.version.length > 50)) d.push({ field: 'version', message: 'не длиннее 50 символов' });
  if (body.serviceId != null && !Number.isInteger(body.serviceId)) d.push({ field: 'serviceId', message: 'целое число' });
  if (d.length) throw validation(d);
}

function acceptRating(product, body, source) {
  if (product.status !== 'active') throw new ApiError(403, 'PRODUCT_INACTIVE', 'Продукт не принимает оценки');
  if (body.serviceId != null && !db.services.some((s) => s.id === body.serviceId && s.productId === product.id && s.status === 'active')) {
    throw validation([{ field: 'serviceId', message: 'сервис не принадлежит продукту' }]);
  }
  const since = Date.now() - DUPLICATE_WINDOW_DAYS * 86400000;
  if (db.ratings.some((r) => r.productId === product.id && r.raterId === body.raterId && Date.parse(r.createdAt) > since)) {
    throw new ApiError(409, 'DUPLICATE_RATING', 'Оценка уже есть');
  }
  const row = {
    id: nextId(db.ratings), productId: product.id, serviceId: body.serviceId ?? null, score: body.score,
    comment: body.comment?.trim() || null, raterId: body.raterId, raterRole: body.raterRole ?? 'unknown',
    pagePath: body.pagePath ?? null, version: body.version ?? null, source, createdAt: nowIso(),
  };
  db.ratings.push(row);
  console.log(`[mock] оценка ${row.score} по продукту ${product.id} (${source})${row.comment ? ` — «${row.comment}»` : ''}`);
  return { id: row.id };
}

// ───────────── маршруты ─────────────
const routes = [];
const route = (method, pattern, handler, opts = {}) => {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/\{(\w+)\}/g, (_, k) => (keys.push(k), '(\\d+)')) + '$');
  routes.push({ method, re, keys, handler, opts });
};

// Авторизация (заглушка IdentityProvider)
route('GET', '/auth/login', (req, res, { query }) => {
  const returnTo = query.get('returnTo') || '/';
  const users = db.users.map((u) => `<li><a href="${API}/auth/callback?code=${u.id}&state=mock&returnTo=${encodeURIComponent(returnTo)}">${u.fullName} (${u.login}${u.isAdmin ? ', админ ЦЭУ' : ''})</a></li>`).join('');
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(`<!doctype html><meta charset="utf-8"><title>Вход (заглушка SSO)</title>
<body style="font:16px system-ui;max-width:520px;margin:60px auto">
<h1>Заглушка SSO ТПУ</h1><p>Настоящий вход появится, когда ТПУ выдаст доступ. Выберите пользователя:</p><ul>${users}</ul></body>`);
}, { public: true });

route('GET', '/auth/callback', (req, res, { query }) => {
  const user = db.users.find((u) => String(u.id) === query.get('code'));
  if (!user || !query.get('state')) throw new ApiError(400, 'VALIDATION_ERROR', 'Нет code или state');
  const token = randomBytes(16).toString('hex');
  sessions.set(token, user.id);
  let returnTo = query.get('returnTo') || '/';
  if (!returnTo.startsWith('/') || returnTo.startsWith('//')) returnTo = '/';
  send(res, 302, null, { Location: returnTo, 'Set-Cookie': `OKO_SESSION=${token}; Path=/; HttpOnly; SameSite=Lax` });
}, { public: true });

route('POST', '/auth/logout', (req, res) => {
  sessions.delete(parseCookies(req).OKO_SESSION);
  send(res, 204, null, { 'Set-Cookie': 'OKO_SESSION=; Path=/; Max-Age=0' });
});

route('GET', '/auth/me', (req, res, { user }) => {
  send(res, 200, { ...userView(user.id), isAdmin: user.isAdmin });
});

// Дашборд
route('GET', '/dashboard', (req, res, { user }) => {
  const from = addDays(todayTomsk(), -29);
  const products = db.products
    .filter((p) => p.status !== 'archived' && (user.isAdmin || p.ownerIds.includes(user.id)))
    .map((p) => {
      const svcs = db.services.filter((s) => s.productId === p.id && s.status === 'active');
      const healths = svcs.map(healthOf);
      // ProductHealthStatus из контракта: известный down важнее unknown; down + up — degraded.
      const anyDown = healths.some((h) => h.status === 'down');
      const anyUp = healths.some((h) => h.status === 'up');
      const status = anyDown ? (anyUp ? 'degraded' : 'down')
        : healths.length && healths.every((h) => h.status === 'up') ? 'up' : 'unknown';
      const ms = healths.map((h) => h.responseMs).filter((x) => x != null);
      const checked = healths.map((h) => h.checkedAt).filter(Boolean).sort().at(-1) ?? null;
      return {
        id: p.id, name: p.name, status: p.status,
        health: { status, responseMs: ms.length ? Math.max(...ms) : null, checkedAt: checked },
        rating: stats(ratingsFor(p.id, { from })),
        services: svcs.map((s) => ({ id: s.id, name: s.name, health: healthOf(s), rating: stats(ratingsFor(p.id, { from, serviceId: s.id })) })),
      };
    });
  send(res, 200, { ratingPeriodDays: 30, products });
});

// Продукты
route('GET', '/products', (req, res, { user, query }) => {
  const statuses = query.getAll('status');
  const allowed = statuses.length ? statuses : ['active', 'paused'];
  const q = (query.get('q') || '').toLowerCase();
  const items = db.products
    .filter((p) => (user.isAdmin || p.ownerIds.includes(user.id)) && allowed.includes(p.status) && (!q || p.name.toLowerCase().includes(q)))
    .sort((a, b) => b.id - a.id)
    .map(productView);
  send(res, 200, paginate(items, query));
});

function validateProduct(body, partial) {
  const d = [];
  if (!partial || body.name !== undefined) {
    if (typeof body.name !== 'string' || body.name.trim().length < 2 || body.name.length > 200) d.push({ field: 'name', message: 'от 2 до 200 символов' });
  }
  if (!partial || body.url !== undefined) {
    if (typeof body.url !== 'string' || !isUri(body.url)) d.push({ field: 'url', message: 'нужен адрес вида https://…' });
  }
  if (partial && body.status !== undefined && !['active', 'paused'].includes(body.status)) d.push({ field: 'status', message: 'active или paused' });
  if (d.length) throw validation(d);
}
const urlTaken = (url, exceptId) =>
  db.products.some((p) => p.id !== exceptId && p.status !== 'archived' && p.url.toLowerCase() === url.toLowerCase());

route('POST', '/products', async (req, res, { user }) => {
  const body = await readJson(req);
  validateProduct(body, false);
  let ownerIds = [user.id];
  if (body.ownerLogins?.length) {
    if (!user.isAdmin) throw new ApiError(403, 'FORBIDDEN', 'Назначать владельцев может только админ');
    ownerIds = body.ownerLogins.map((login) => {
      const u = db.users.find((x) => x.login === login);
      if (!u) throw validation([{ field: 'ownerLogins', message: `нет пользователя ${login}` }]);
      return u.id;
    });
  }
  if (urlTaken(body.url)) throw new ApiError(409, 'DUPLICATE_URL', 'Продукт с таким URL уже зарегистрирован');
  const p = {
    id: nextId(db.products), name: body.name.trim(), description: body.description || null, url: body.url,
    status: 'active', ownerIds, createdAt: nowIso(), updatedAt: nowIso(),
  };
  db.products.push(p);
  db.origins[p.id] = [];
  db.widgetSettings[p.id] = { question: 'Как вам сервис?', askIntervalDays: 14, commentThreshold: 3, position: 'bottom-right', widgetEnabled: true, updatedAt: nowIso() };
  send(res, 201, productView(p));
});

route('GET', '/products/{productId}', (req, res, { user, params }) => {
  send(res, 200, productView(visibleProduct(user, params.productId)));
});

route('PATCH', '/products/{productId}', async (req, res, { user, params }) => {
  const p = visibleProduct(user, params.productId);
  const body = await readJson(req);
  validateProduct(body, true);
  if (p.status === 'archived') throw new ApiError(409, 'CONFLICT', 'Продукт в архиве');
  if (body.url && urlTaken(body.url, p.id)) throw new ApiError(409, 'DUPLICATE_URL', 'Продукт с таким URL уже зарегистрирован');
  for (const k of ['name', 'description', 'url', 'status']) if (body[k] !== undefined) p[k] = body[k];
  p.updatedAt = nowIso();
  send(res, 200, productView(p));
});

route('DELETE', '/products/{productId}', (req, res, { user, params }) => {
  const p = visibleProduct(user, params.productId);
  if (!user.isAdmin) throw new ApiError(403, 'FORBIDDEN', 'Архивировать может только админ ЦЭУ');
  p.status = 'archived';
  p.updatedAt = nowIso();
  for (const k of db.keys) if (k.productId === p.id && !k.revokedAt) k.revokedAt = nowIso();
  send(res, 204);
});

// Владельцы
route('GET', '/products/{productId}/owners', (req, res, { user, params }) => {
  send(res, 200, { items: visibleProduct(user, params.productId).ownerIds.map(userView) });
});
route('POST', '/products/{productId}/owners', async (req, res, { user, params }) => {
  const p = visibleProduct(user, params.productId);
  const { login } = await readJson(req);
  if (typeof login !== 'string' || !login.trim()) throw validation([{ field: 'login', message: 'обязательно' }]);
  const u = db.users.find((x) => x.login === login.trim());
  if (!u) throw new ApiError(404, 'NOT_FOUND', 'Пользователь ещё не входил в ОКО');
  if (p.ownerIds.includes(u.id)) throw new ApiError(409, 'CONFLICT', 'Уже владелец');
  p.ownerIds.push(u.id);
  send(res, 201, userView(u.id));
});
route('DELETE', '/products/{productId}/owners/{userId}', (req, res, { user, params }) => {
  const p = visibleProduct(user, params.productId);
  if (!p.ownerIds.includes(params.userId)) throw notFound();
  if (p.ownerIds.length === 1) throw new ApiError(409, 'LAST_OWNER', 'Нельзя убрать последнего владельца');
  p.ownerIds = p.ownerIds.filter((x) => x !== params.userId);
  send(res, 204);
});

// Сервисы
function validateService(body, partial) {
  const d = [];
  if (!partial || body.name !== undefined) {
    if (typeof body.name !== 'string' || !body.name.trim() || body.name.length > 200) d.push({ field: 'name', message: 'от 1 до 200 символов' });
  }
  if (body.url != null && body.url !== '' && !isUri(body.url)) d.push({ field: 'url', message: 'нужен адрес вида https://…' });
  if (d.length) throw validation(d);
}
const refTaken = (ref, exceptId) => ref && db.services.some((s) => s.id !== exceptId && s.status === 'active' && s.monitoringRef === ref);

route('GET', '/products/{productId}/services', (req, res, { user, params, query }) => {
  visibleProduct(user, params.productId);
  const all = query.get('includeArchived') === 'true';
  send(res, 200, { items: db.services.filter((s) => s.productId === params.productId && (all || s.status === 'active')).map(serviceView) });
});
route('POST', '/products/{productId}/services', async (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const body = await readJson(req);
  validateService(body, false);
  if (refTaken(body.monitoringRef)) throw new ApiError(409, 'DUPLICATE_MONITORING_REF', 'Этот идентификатор мониторинга уже привязан');
  const s = { id: nextId(db.services), productId: params.productId, name: body.name.trim(), url: body.url || null, monitoringRef: body.monitoringRef || null, status: 'active', createdAt: nowIso() };
  db.services.push(s);
  send(res, 201, serviceView(s));
});
route('PATCH', '/products/{productId}/services/{serviceId}', async (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const s = db.services.find((x) => x.id === params.serviceId && x.productId === params.productId);
  if (!s) throw notFound();
  const body = await readJson(req);
  validateService(body, true);
  if (body.monitoringRef && refTaken(body.monitoringRef, s.id)) throw new ApiError(409, 'DUPLICATE_MONITORING_REF', 'Этот идентификатор мониторинга уже привязан');
  for (const k of ['name', 'url', 'monitoringRef']) if (body[k] !== undefined) s[k] = body[k] || (k === 'name' ? s.name : null);
  send(res, 200, serviceView(s));
});
route('DELETE', '/products/{productId}/services/{serviceId}', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const s = db.services.find((x) => x.id === params.serviceId && x.productId === params.productId);
  if (!s) throw notFound();
  s.status = 'archived';
  send(res, 204);
});

// Ключи
function issueKey(productId, type, userId) {
  const key = (type === 'public' ? 'pk_' : 'sk_') + randomBytes(16).toString('hex');
  const k = { id: nextId(db.keys), productId, type, key, createdAt: nowIso(), createdBy: userId, revokedAt: null };
  db.keys.push(k);
  return { ...keyView(k), key };
}
route('GET', '/products/{productId}/keys', (req, res, { user, params, query }) => {
  visibleProduct(user, params.productId);
  const all = query.get('includeRevoked') === 'true';
  send(res, 200, { items: db.keys.filter((k) => k.productId === params.productId && (all || !k.revokedAt)).map(keyView) });
});
route('POST', '/products/{productId}/keys', async (req, res, { user, params }) => {
  const p = visibleProduct(user, params.productId);
  const { type } = await readJson(req);
  if (!['public', 'secret'].includes(type)) throw validation([{ field: 'type', message: 'public или secret' }]);
  if (p.status === 'archived') throw new ApiError(409, 'CONFLICT', 'Продукт в архиве');
  send(res, 201, issueKey(p.id, type, user.id));
});
route('POST', '/products/{productId}/keys/{keyId}/reissue', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const k = db.keys.find((x) => x.id === params.keyId && x.productId === params.productId);
  if (!k) throw notFound();
  if (k.revokedAt) throw new ApiError(409, 'CONFLICT', 'Ключ уже отозван');
  k.revokedAt = nowIso();
  send(res, 201, issueKey(k.productId, k.type, user.id));
});
route('POST', '/products/{productId}/keys/{keyId}/revoke', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const k = db.keys.find((x) => x.id === params.keyId && x.productId === params.productId);
  if (!k) throw notFound();
  if (!k.revokedAt) k.revokedAt = nowIso();
  send(res, 200, keyView(k));
});

// Разрешённые сайты
route('GET', '/products/{productId}/origins', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  send(res, 200, { origins: db.origins[params.productId] ?? [] });
});
route('PUT', '/products/{productId}/origins', async (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const { origins } = await readJson(req);
  if (!Array.isArray(origins) || origins.length > 50) throw validation([{ field: 'origins', message: 'массив до 50 элементов' }]);
  const d = [];
  origins.forEach((o, i) => {
    if (typeof o !== 'string' || !/^https?:\/\/[^/\s]+$/.test(o)) d.push({ field: `origins[${i}]`, message: 'схема и хост без пути, например https://lk.tpu.ru' });
  });
  if (d.length) throw validation(d);
  db.origins[params.productId] = [...new Set(origins)];
  send(res, 200, { origins: db.origins[params.productId] });
});

// Настройки виджета
route('GET', '/products/{productId}/widget-settings', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  send(res, 200, db.widgetSettings[params.productId]);
});
route('PUT', '/products/{productId}/widget-settings', async (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const b = await readJson(req);
  const d = [];
  if (typeof b.question !== 'string' || !b.question.trim() || b.question.length > 200) d.push({ field: 'question', message: 'от 1 до 200 символов' });
  if (!Number.isInteger(b.askIntervalDays) || b.askIntervalDays < 1 || b.askIntervalDays > 90) d.push({ field: 'askIntervalDays', message: 'от 1 до 90' });
  if (!Number.isInteger(b.commentThreshold) || b.commentThreshold < 1 || b.commentThreshold > 5) d.push({ field: 'commentThreshold', message: 'от 1 до 5' });
  if (!['bottom-right', 'bottom-left'].includes(b.position)) d.push({ field: 'position', message: 'bottom-right или bottom-left' });
  if (typeof b.widgetEnabled !== 'boolean') d.push({ field: 'widgetEnabled', message: 'true или false' });
  if (d.length) throw validation(d);
  db.widgetSettings[params.productId] = { question: b.question.trim(), askIntervalDays: b.askIntervalDays, commentThreshold: b.commentThreshold, position: b.position, widgetEnabled: b.widgetEnabled, updatedAt: nowIso() };
  send(res, 200, db.widgetSettings[params.productId]);
});

// Виджет (публичные эндпоинты)
function widgetFailure(res) {
  if (mode.widget === '500') return send(res, 500, { code: 'INTERNAL_ERROR', message: 'mock 500' }), true;
  if (mode.widget === '403') return send(res, 403), true;
  return false;
}
// Та же разметка и тот же CSP, что у backend (WidgetService.frame / contentSecurityPolicy).
const escapeHtml = (v) => String(v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
route('GET', '/widget/frame', async (req, res, { query }) => {
  if (widgetFailure(res)) return;
  const k = db.keys.find((x) => x.key === query.get('key') && x.type === 'public' && !x.revokedAt);
  const p = k && db.products.find((x) => x.id === k.productId);
  if (!k || !p || p.status !== 'active') return send(res, 403, null, { 'Cache-Control': 'no-store' });
  const sid = query.get('serviceId');
  if (sid && !db.services.some((s) => String(s.id) === sid && s.productId === p.id && s.status === 'active')) {
    return send(res, 403, null, { 'Cache-Control': 'no-store' });
  }
  const origins = db.origins[p.id] ?? [];
  const csp = `frame-ancestors ${origins.length ? origins.join(' ') : "'none'"}; default-src 'none'; script-src 'self'; connect-src 'self'; `
    + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; base-uri 'none'; form-action 'none'";
  const html = `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>ОКО</title>
</head>
<body>
<div id="oko-root" data-key="${escapeHtml(k.key)}" data-service-id="${sid ? escapeHtml(sid) : ''}"></div>
<script src="/widget/v1/frame.js" defer></script>
</body>
</html>
`;
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Content-Security-Policy': csp, 'Cache-Control': 'no-store' });
  res.end(html);
}, { public: true });

route('GET', '/widget/config', (req, res) => {
  if (widgetFailure(res)) return;
  const k = findKey(req, 'public');
  const p = db.products.find((x) => x.id === k.productId);
  const s = db.widgetSettings[p.id];
  if (p.status !== 'active' || !s.widgetEnabled) return send(res, 200, { enabled: false });
  send(res, 200, { enabled: true, question: s.question, askIntervalDays: s.askIntervalDays, commentThreshold: s.commentThreshold, position: s.position });
}, { public: true });

route('POST', '/widget/ratings', async (req, res) => {
  if (widgetFailure(res)) return;
  const k = findKey(req, 'public');
  const p = db.products.find((x) => x.id === k.productId);
  // Оценку шлёт только iframe на нашем домене, поэтому Origin должен быть нашим (контракт, submitWidgetRating).
  if (!SELF_ORIGINS.has(req.headers.origin)) throw new ApiError(403, 'ORIGIN_NOT_ALLOWED', 'Недопустимый Origin');
  const body = await readJson(req);
  validateRating(body, { raterMin: 8 });
  send(res, 201, acceptRating(p, body, 'widget'));
}, { public: true });

route('POST', '/ratings', async (req, res) => {
  const k = findKey(req, 'secret');
  const p = db.products.find((x) => x.id === k.productId);
  const body = await readJson(req);
  validateRating(body, { raterMin: 1 });
  if (body.raterRole != null && !['student', 'staff', 'unknown'].includes(body.raterRole)) throw validation([{ field: 'raterRole', message: 'student, staff или unknown' }]);
  send(res, 201, acceptRating(p, body, 'api'));
}, { public: true });

// Статистика
function periodParams(query) {
  const from = query.get('from');
  const to = query.get('to');
  const d = [];
  if (from && !isDate(from)) d.push({ field: 'from', message: 'дата YYYY-MM-DD' });
  if (to && !isDate(to)) d.push({ field: 'to', message: 'дата YYYY-MM-DD' });
  if (from && to && from > to) d.push({ field: 'from', message: 'позже, чем to' });
  if (d.length) throw validation(d);
  const sid = query.get('serviceId');
  return { from: from || undefined, to: to || undefined, serviceId: sid ? Number(sid) : undefined };
}
route('GET', '/products/{productId}/stats/summary', (req, res, { user, params, query }) => {
  visibleProduct(user, params.productId);
  send(res, 200, stats(ratingsFor(params.productId, periodParams(query))));
});
route('GET', '/products/{productId}/stats/timeseries', (req, res, { user, params, query }) => {
  visibleProduct(user, params.productId);
  const pp = periodParams(query);
  const rows = ratingsFor(params.productId, pp);
  const to = pp.to || todayTomsk();
  const firstDay = rows.map((r) => tomskDay(r.createdAt)).sort()[0];
  const from = pp.from || firstDay || addDays(to, -29);
  let g = query.get('granularity') || 'auto';
  if (g === 'auto') g = daysBetween(from, to) < 30 ? 'day' : 'week';
  const buckets = new Map();
  for (let d = periodStart(from, g); d <= to; ) {
    buckets.set(d, []);
    d = g === 'day' ? addDays(d, 1) : g === 'week' ? addDays(d, 7) : (() => { const x = new Date(d + 'T00:00:00Z'); x.setUTCMonth(x.getUTCMonth() + 1); return x.toISOString().slice(0, 10); })();
  }
  for (const r of rows) buckets.get(periodStart(tomskDay(r.createdAt), g))?.push(r);
  const points = [...buckets].map(([periodStart, rs]) => {
    const s = stats(rs);
    return { periodStart, count: s.count, average: s.average, lowSample: s.lowSample };
  });
  send(res, 200, { granularity: g, from, to, points });
});

// Отзывы
route('GET', '/products/{productId}/ratings', (req, res, { user, params, query }) => {
  visibleProduct(user, params.productId);
  const pp = periodParams(query);
  const min = Number(query.get('minScore') || 1);
  const max = Number(query.get('maxScore') || 5);
  const hasComment = query.get('hasComment') === 'true';
  const version = query.get('version');
  const items = ratingsFor(params.productId, pp)
    .filter((r) => r.score >= min && r.score <= max && (!hasComment || r.comment) && (!version || r.version === version))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(reviewView);
  send(res, 200, paginate(items, query));
});

// Релизы
function validateRelease(b, partial, productId) {
  const d = [];
  if (!partial || b.version !== undefined) if (typeof b.version !== 'string' || !b.version.trim() || b.version.length > 50) d.push({ field: 'version', message: 'от 1 до 50 символов' });
  if (!partial || b.title !== undefined) if (typeof b.title !== 'string' || !b.title.trim() || b.title.length > 200) d.push({ field: 'title', message: 'от 1 до 200 символов' });
  if (!partial || b.releasedAt !== undefined) if (typeof b.releasedAt !== 'string' || Number.isNaN(Date.parse(b.releasedAt))) d.push({ field: 'releasedAt', message: 'дата и время ISO 8601' });
  if (b.serviceId != null && !db.services.some((s) => s.id === b.serviceId && s.productId === productId)) d.push({ field: 'serviceId', message: 'сервис не из этого продукта' });
  if (d.length) throw validation(d);
}
route('GET', '/products/{productId}/releases', (req, res, { user, params, query }) => {
  visibleProduct(user, params.productId);
  const sid = query.get('serviceId');
  const items = db.releases
    .filter((r) => r.productId === params.productId && (!sid || r.serviceId === Number(sid)))
    .sort((a, b) => b.releasedAt.localeCompare(a.releasedAt))
    .map(releaseView);
  send(res, 200, { items });
});
route('POST', '/products/{productId}/releases', async (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const b = await readJson(req);
  validateRelease(b, false, params.productId);
  const r = { id: nextId(db.releases), productId: params.productId, serviceId: b.serviceId ?? null, version: b.version.trim(), title: b.title.trim(), description: b.description || null, releasedAt: new Date(b.releasedAt).toISOString(), createdBy: user.id, createdAt: nowIso() };
  db.releases.push(r);
  send(res, 201, releaseView(r));
});
route('PATCH', '/products/{productId}/releases/{releaseId}', async (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const r = db.releases.find((x) => x.id === params.releaseId && x.productId === params.productId);
  if (!r) throw notFound();
  const b = await readJson(req);
  validateRelease(b, true, params.productId);
  for (const k of ['version', 'title', 'description']) if (b[k] !== undefined) r[k] = b[k];
  if (b.releasedAt) r.releasedAt = new Date(b.releasedAt).toISOString();
  send(res, 200, releaseView(r));
});
route('DELETE', '/products/{productId}/releases/{releaseId}', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const i = db.releases.findIndex((x) => x.id === params.releaseId && x.productId === params.productId);
  if (i < 0) throw notFound();
  db.releases.splice(i, 1);
  send(res, 204);
});
route('GET', '/products/{productId}/releases/{releaseId}/comparison', (req, res, { user, params }) => {
  visibleProduct(user, params.productId);
  const r = db.releases.find((x) => x.id === params.releaseId && x.productId === params.productId);
  if (!r) throw notFound();
  const day = tomskDay(r.releasedAt);
  const others = db.releases
    .filter((x) => x.productId === r.productId && x.id !== r.id && (x.serviceId ?? null) === (r.serviceId ?? null))
    .map((x) => ({ id: x.id, day: tomskDay(x.releasedAt) }));
  const before = { from: addDays(day, -DUPLICATE_WINDOW_DAYS), to: addDays(day, -1), clippedByReleaseId: null };
  const after = { from: day, to: addDays(day, DUPLICATE_WINDOW_DAYS - 1), clippedByReleaseId: null };
  for (const o of others) {
    if (o.day < day && o.day >= before.from) { before.from = o.day; before.clippedByReleaseId = o.id; }
    if (o.day > day && o.day <= after.to) { after.to = addDays(o.day, -1); after.clippedByReleaseId = o.id; }
  }
  const sid = r.serviceId ?? undefined;
  const bs = stats(ratingsFor(r.productId, { ...before, serviceId: sid }));
  const as = stats(ratingsFor(r.productId, { ...after, serviceId: sid }));
  send(res, 200, {
    release: releaseView(r),
    before: { ...before, stats: bs },
    after: { ...after, stats: as },
    averageDelta: bs.average != null && as.average != null ? Math.round((as.average - bs.average) * 10) / 10 : null,
  });
});

// ───────────── статика виджета и служебное ─────────────
const MIME = { '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.html': 'text/html; charset=utf-8' };
async function serveWidget(res, file) {
  if (!/^[\w.-]+$/.test(file)) return send(res, 404);
  try {
    const body = await readFile(path.join(WIDGET_DIR, file));
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache', 'Access-Control-Allow-Origin': '*' });
    res.end(body);
  } catch {
    send(res, 404);
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  try {
    if (url.pathname === '/__mock/mode') {
      const w = url.searchParams.get('widget');
      if (w) mode.widget = w;
      return send(res, 200, mode);
    }
    if (url.pathname.startsWith('/widget/v1/')) {
      if (mode.widget === 'slow') await new Promise((r) => setTimeout(r, 10000));
      if (mode.widget === '500') return send(res, 500);
      return serveWidget(res, url.pathname.slice('/widget/v1/'.length));
    }
    if (!url.pathname.startsWith(API)) return send(res, 404, { code: 'NOT_FOUND', message: 'Не найдено' });
    const p = url.pathname.slice(API.length) || '/';
    if (p.startsWith('/widget/') && mode.widget === 'slow') await new Promise((r) => setTimeout(r, 10000));

    const pathMatches = routes.filter((r) => r.re.test(p));
    if (!pathMatches.length) throw notFound();
    const r = pathMatches.find((x) => x.method === req.method);
    if (!r) return send(res, 405, { code: 'METHOD_NOT_ALLOWED', message: 'Метод не поддерживается' });

    const m = p.match(r.re);
    const params = Object.fromEntries(r.keys.map((k, i) => [k, Number(m[i + 1])]));
    const ctx = { params, query: url.searchParams, user: null };

    // Spring выставляет XSRF-TOKEN cookie сам; повторяем это поведение (только для панели, не для виджета).
    if (!r.opts.public && !parseCookies(req)['XSRF-TOKEN']) {
      res.setHeader('Set-Cookie', `XSRF-TOKEN=${randomBytes(12).toString('hex')}; Path=/; SameSite=Lax`);
    }
    if (!r.opts.public) {
      ctx.user = requireUser(req);
      if (req.method !== 'GET') checkCsrf(req);
    }
    await r.handler(req, res, ctx);
  } catch (e) {
    if (e instanceof ApiError) {
      return send(res, e.status, { code: e.code, message: e.message, ...(e.details ? { details: e.details } : {}) });
    }
    console.error(e);
    send(res, 500, { code: 'INTERNAL_ERROR', message: 'Ошибка мок-сервера' });
  }
});

server.listen(PORT, () => {
  console.log(`[mock] ОКО мок-сервер: http://localhost:${PORT}${API}`);
  console.log(`[mock] виджет: http://localhost:${PORT}/widget/v1/loader.js (из widget/${process.env.WIDGET_DIR || 'src'})`);
  console.log(`[mock] режим сбоев: http://localhost:${PORT}/__mock/mode?widget=ok|500|slow|403`);
});
