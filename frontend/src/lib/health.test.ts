import { describe, expect, it } from 'vitest';
import { productHealth, productHealthStatus } from './health';

describe('productHealthStatus — лампочка продукта', () => {
  it.each([
    [['up', 'up'], 'up'],
    [['up', 'down'], 'degraded'],
    [['down', 'down'], 'down'],
    // Известное падение важнее нехватки данных.
    [['down', 'unknown'], 'down'],
    [['up', 'down', 'unknown'], 'degraded'],
    [['up', 'unknown'], 'unknown'],
    [['unknown'], 'unknown'],
    [[], 'unknown'],
  ] as const)('%j → %s', (statuses, expected) => {
    expect(productHealthStatus([...statuses])).toBe(expected);
  });
});

describe('productHealth', () => {
  it('берёт максимум времени ответа и самую свежую проверку', () => {
    const h = productHealth([
      { status: 'up', responseMs: 120, checkedAt: '2026-11-08T05:00:00Z' },
      { status: 'up', responseMs: 340, checkedAt: '2026-11-08T05:02:00Z' },
      { status: 'unknown', responseMs: null, checkedAt: null },
    ]);
    expect(h).toEqual({ status: 'unknown', responseMs: 340, checkedAt: '2026-11-08T05:02:00Z' });
  });
});
