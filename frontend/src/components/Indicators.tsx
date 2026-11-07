// Лампочка сервиса и значение оценки — используются на всех экранах.
import { Box, Group, Text, Tooltip } from '@mantine/core';
import type { Health, RatingStats } from '../api/types';
import { averageTone, formatAverage, formatDateTime, formatMs, HEALTH_COLOR, HEALTH_LABEL } from '../lib/format';

/** Лампочка: зелёная — работает, красная — упал, серая — нет данных. Жёлтой нет, пока её нет в контракте. */
export function HealthDot({ health, withLabel = false }: { health: Health; withLabel?: boolean }) {
  const color = `var(--mantine-color-${HEALTH_COLOR[health.status]}-6)`;
  const tip = [HEALTH_LABEL[health.status], health.responseMs != null ? formatMs(health.responseMs) : null, health.checkedAt ? `проверка ${formatDateTime(health.checkedAt)}` : null]
    .filter(Boolean)
    .join(' · ');
  return (
    <Tooltip label={tip} withArrow>
      <Group gap={6} wrap="nowrap" component="span" aria-label={`Статус: ${HEALTH_LABEL[health.status]}`}>
        <Box component="span" w={10} h={10} style={{ borderRadius: '50%', background: color, display: 'inline-block', flex: 'none' }} />
        {withLabel && <Text span size="sm">{HEALTH_LABEL[health.status]}</Text>}
      </Group>
    </Tooltip>
  );
}

const TONE_COLOR = { green: 'teal.7', yellow: 'yellow.8', red: 'red.7', gray: 'gray.6' } as const;

/** Средняя оценка с n рядом. При n < 20 — серым с подписью «мало оценок». */
export function RatingValue({ stats, size = 'sm' }: { stats: Pick<RatingStats, 'average' | 'count' | 'lowSample'>; size?: string }) {
  const tone = averageTone(stats);
  if (stats.average == null) return <Text size={size} c="dimmed">нет оценок</Text>;
  return (
    <Group gap={6} wrap="nowrap" className="tabular">
      <Text size={size} fw={600} c={TONE_COLOR[tone]}>{formatAverage(stats.average)}</Text>
      <Text size="xs" c="dimmed">{stats.lowSample ? `мало оценок (n=${stats.count})` : `n=${stats.count}`}</Text>
    </Group>
  );
}

export { TONE_COLOR };
