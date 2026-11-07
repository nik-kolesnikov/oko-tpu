// Обёртка над fetch для панели.
// - Все пути относительно /api/v1 (как servers в контракте).
// - Сессия — cookie OKO_SESSION, поэтому credentials: 'same-origin'.
// - Изменяющие запросы несут X-XSRF-TOKEN из cookie XSRF-TOKEN (так требует контракт).
// - Любая ошибка превращается в ApiError с полями из схемы Error.
import type { components } from './schema';

export type ErrorBody = components['schemas']['Error'];
export const API_BASE = '/api/v1';

export class ApiError extends Error {
  status: number;
  code: ErrorBody['code'] | 'NETWORK';
  details: NonNullable<ErrorBody['details']>;

  constructor(status: number, body: Partial<ErrorBody> | null, fallback: string) {
    super(body?.message || fallback);
    this.status = status;
    this.code = body?.code ?? (status === 0 ? 'NETWORK' : 'UNAVAILABLE');
    this.details = body?.details ?? [];
  }

  /** Ошибки по полям формы: { name: 'от 2 до 200 символов' } */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.details.map((d) => [d.field, d.message]));
  }
}

function readCookie(name: string): string | null {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/[-.]/g, '\\$&') + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : null;
}

type Query = Record<string, string | number | boolean | undefined | null | string[]>;

export function buildQuery(query?: Query): string {
  if (!query) return '';
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '' || v === false) continue;
    if (Array.isArray(v)) v.forEach((x) => sp.append(k, x));
    else sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export async function api<T>(
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  options: { query?: Query; body?: unknown } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const xsrf = readCookie('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
  }

  let res: Response;
  try {
    res = await fetch(API_BASE + path + buildQuery(options.query), {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, null, 'Нет связи с сервером ОКО');
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    throw new ApiError(res.status, data as ErrorBody | null, `Ошибка сервера (${res.status})`);
  }
  return data as T;
}
