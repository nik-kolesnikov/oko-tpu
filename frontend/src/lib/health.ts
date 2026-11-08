// Лампочка продукта из статусов его активных сервисов — правило из контракта (ProductHealthStatus).
// Dashboard отдаёт её готовой; на карточке продукта считаем сами из listServices.
import type { Health, ProductHealth, ProductHealthStatus } from '../api/types';

/**
 * Известный статус — up или down. Есть известные и все down → down; есть и down, и up → degraded;
 * все up → up; иначе (нет сервисов или без падений, но где-то нет данных) → unknown.
 * Известное падение важнее нехватки данных: серая лампочка не должна прятать сбой.
 */
export function productHealthStatus(statuses: Health['status'][]): ProductHealthStatus {
  const down = statuses.some((s) => s === 'down');
  const up = statuses.some((s) => s === 'up');
  if (down) return up ? 'degraded' : 'down';
  if (statuses.length > 0 && statuses.every((s) => s === 'up')) return 'up';
  return 'unknown';
}

/** responseMs — максимум по сервисам, checkedAt — самая свежая проверка. */
export function productHealth(services: Health[]): ProductHealth {
  const ms = services.map((h) => h.responseMs).filter((x): x is number => x != null);
  const checked = services.map((h) => h.checkedAt).filter((x): x is string => !!x).sort().at(-1) ?? null;
  return { status: productHealthStatus(services.map((h) => h.status)), responseMs: ms.length ? Math.max(...ms) : null, checkedAt: checked };
}
