// Правила подсчёта и отображения из «Исследование и требования» и корневого CLAUDE.md.
// Сами числа (средняя, индекс, lowSample) считает backend; здесь только показ.
import type { ProductHealthStatus, RatingStats } from '../api/types';

export const TZ = 'Asia/Tomsk';

/** Сегодня по Томску в формате YYYY-MM-DD (так ждут параметры from/to). */
export function todayTomsk(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

export function addDays(day: string, n: number): string {
  const d = new Date(day + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

export type PeriodKey = '7' | '30' | '90' | 'all';
export const PERIOD_OPTIONS: { value: PeriodKey; label: string }[] = [
  { value: '7', label: '7 дней' },
  { value: '30', label: '30 дней' },
  { value: '90', label: '90 дней' },
  { value: 'all', label: 'Всё время' },
];

/** Период «последние N дней включая сегодня» → { from, to }. Для «всё время» — пусто. */
export function periodRange(key: PeriodKey, now = new Date()): { from?: string; to?: string } {
  if (key === 'all') return {};
  const to = todayTomsk(now);
  return { from: addDays(to, -(Number(key) - 1)), to };
}

/** Средняя с одним знаком после запятой: 4.3 → «4,3». */
export function formatAverage(avg: number | null | undefined): string {
  return avg == null ? '—' : avg.toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/** Индекс удовлетворённости от −100 до +100. Слово «NPS» в интерфейсе не используем. */
export function formatIndex(index: number | null | undefined): string {
  if (index == null) return '—';
  const v = Math.round(index);
  return v > 0 ? `+${v}` : String(v);
}

export type Tone = 'green' | 'yellow' | 'red' | 'gray';

/** Цвет средней: ≥ 4,0 зелёный, 3,0–3,9 жёлтый, ниже 3,0 красный; мало оценок — серый. */
export function averageTone(stats: Pick<RatingStats, 'average' | 'lowSample'>): Tone {
  if (stats.average == null || stats.lowSample) return 'gray';
  if (stats.average >= 4) return 'green';
  if (stats.average >= 3) return 'yellow';
  return 'red';
}

/** Доли положительных (4–5), нейтральных (3) и отрицательных (1–2) в процентах от n. */
export function shares(stats: Pick<RatingStats, 'count' | 'histogram'>) {
  const c = (s: number) => stats.histogram.find((b) => b.score === s)?.count ?? 0;
  const n = stats.count;
  const pct = (x: number) => (n ? Math.round((x / n) * 100) : null);
  return { positive: pct(c(4) + c(5)), neutral: pct(c(3)), negative: pct(c(1) + c(2)) };
}

// У сервиса статусы up / down / unknown, у продукта ещё degraded («часть сервисов не работает»).
export const HEALTH_LABEL: Record<ProductHealthStatus, string> = { up: 'Работает', degraded: 'Частично не работает', down: 'Не работает', unknown: 'Нет данных' };
export const HEALTH_COLOR: Record<ProductHealthStatus, string> = { up: 'green', degraded: 'yellow', down: 'red', unknown: 'gray' };

export function formatMs(ms: number | null | undefined): string {
  return ms == null ? '—' : `${ms.toLocaleString('ru-RU')} мс`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('ru-RU', { timeZone: TZ, day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('ru-RU', { timeZone: TZ, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}

/** Подпись точки графика: «12 окт» для дня/недели, «окт 2026» для месяца. */
export function formatPeriodStart(day: string, granularity: 'day' | 'week' | 'month'): string {
  const d = new Date(day + 'T00:00:00Z');
  if (granularity === 'month') return new Intl.DateTimeFormat('ru-RU', { timeZone: 'UTC', month: 'short', year: 'numeric' }).format(d);
  return new Intl.DateTimeFormat('ru-RU', { timeZone: 'UTC', day: 'numeric', month: 'short' }).format(d);
}

/** Проверка origin для списка разрешённых сайтов — тот же шаблон, что в контракте. */
export const ORIGIN_RE = /^https?:\/\/[^/\s]+$/;

export function pluralRatings(n: number): string {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return 'оценка';
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return 'оценки';
  return 'оценок';
}
