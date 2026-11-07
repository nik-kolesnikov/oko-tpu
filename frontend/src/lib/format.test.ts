import { describe, expect, it } from 'vitest';
import { averageTone, formatAverage, formatIndex, periodRange, pluralRatings, shares, todayTomsk, ORIGIN_RE } from './format';

const hist = (c: number[]) => c.map((count, i) => ({ score: i + 1, count }));

describe('формат и правила подсчёта', () => {
  it('день по Томску, а не по UTC', () => {
    // 2026-11-06 20:30 UTC — в Томске уже 7 ноября, 03:30
    expect(todayTomsk(new Date('2026-11-06T20:30:00Z'))).toBe('2026-11-07');
  });

  it('период включает сегодняшний день', () => {
    expect(periodRange('7', new Date('2026-11-07T05:00:00Z'))).toEqual({ from: '2026-11-01', to: '2026-11-07' });
    expect(periodRange('all')).toEqual({});
  });

  it('средняя с одним знаком через запятую', () => {
    expect(formatAverage(4.3)).toBe('4,3');
    expect(formatAverage(4)).toBe('4,0');
    expect(formatAverage(null)).toBe('—');
  });

  it('индекс со знаком', () => {
    expect(formatIndex(25)).toBe('+25');
    expect(formatIndex(-40)).toBe('-40');
    expect(formatIndex(0)).toBe('0');
  });

  it('цвет: мало оценок всегда серый', () => {
    expect(averageTone({ average: 4.8, lowSample: true })).toBe('gray');
    expect(averageTone({ average: 4.0, lowSample: false })).toBe('green');
    expect(averageTone({ average: 3.9, lowSample: false })).toBe('yellow');
    expect(averageTone({ average: 2.9, lowSample: false })).toBe('red');
  });

  it('доли 4–5 / 3 / 1–2', () => {
    expect(shares({ count: 10, histogram: hist([1, 1, 2, 3, 3]) })).toEqual({ positive: 60, neutral: 20, negative: 20 });
    expect(shares({ count: 0, histogram: hist([0, 0, 0, 0, 0]) })).toEqual({ positive: null, neutral: null, negative: null });
  });

  it('origin без пути', () => {
    expect(ORIGIN_RE.test('https://lk.tpu.ru')).toBe(true);
    expect(ORIGIN_RE.test('http://localhost:5500')).toBe(true);
    expect(ORIGIN_RE.test('https://lk.tpu.ru/')).toBe(false);
    expect(ORIGIN_RE.test('lk.tpu.ru')).toBe(false);
  });

  it('склонение', () => {
    expect([1, 2, 5, 11, 21, 22, 112].map(pluralRatings)).toEqual(['оценка', 'оценки', 'оценок', 'оценок', 'оценка', 'оценки', 'оценок']);
  });
});
