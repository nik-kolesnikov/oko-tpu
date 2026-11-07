// Вкладка «Обзор»: сводка, динамика с линиями релизов, распределение 1–5, до/после релиза, сервисы.
import { useMemo, useState } from 'react';
import { BarChart, LineChart } from '@mantine/charts';
import { Card, Group, SegmentedControl, Select, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core';
import { useSearchParams } from 'react-router';
import { useComparison, useReleases, useServices, useStatsSummary, useStatsTimeseries } from '../../api/hooks';
import type { RatingStats, Release } from '../../api/types';
import { EmptyState, ErrorState, isNotImplemented, LoadingState, NotImplementedState } from '../../components/States';
import { HealthDot, RatingValue, TONE_COLOR } from '../../components/Indicators';
import {
  addDays, averageTone, formatAverage, formatDate, formatIndex, formatMs, formatPeriodStart, PERIOD_OPTIONS,
  periodRange, shares, todayTomsk, type PeriodKey,
} from '../../lib/format';

function Metric({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <Card padding="md">
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>{label}</Text>
      <div className="tabular">{children}</div>
      {hint && <Text size="xs" c="dimmed">{hint}</Text>}
    </Card>
  );
}

function SummaryCards({ s }: { s: RatingStats }) {
  const sh = shares(s);
  const tone = averageTone(s);
  return (
    <SimpleGrid cols={{ base: 2, md: 4 }}>
      <Metric label="Средняя оценка" hint={s.lowSample && s.count ? `мало оценок (n=${s.count})` : undefined}>
        <Text size="xl" fw={700} c={TONE_COLOR[tone]}>{formatAverage(s.average)}</Text>
      </Metric>
      <Metric label="Оценок (n)"><Text size="xl" fw={700}>{s.count.toLocaleString('ru-RU')}</Text></Metric>
      <Metric label="Положительных / отрицательных" hint="4–5 и 1–2, % от n">
        <Text size="xl" fw={700}>
          <Text span inherit c="teal.7">{sh.positive ?? '—'}%</Text>{' / '}<Text span inherit c="red.7">{sh.negative ?? '—'}%</Text>
        </Text>
      </Metric>
      <Metric label="Индекс удовлетворённости" hint="от −100 до +100">
        <Text size="xl" fw={700} c={s.lowSample ? 'gray.6' : undefined}>{formatIndex(s.index)}</Text>
      </Metric>
    </SimpleGrid>
  );
}

/** На какую точку графика попадает дата релиза при данной детализации. */
function releaseBucket(r: Release, g: 'day' | 'week' | 'month'): string {
  const day = todayTomsk(new Date(r.releasedAt));
  if (g === 'day') return day;
  if (g === 'month') return day.slice(0, 8) + '01';
  const dow = new Date(day + 'T00:00:00Z').getUTCDay();
  return addDays(day, -((dow + 6) % 7));
}

export function OverviewTab({ productId }: { productId: number }) {
  const [search, setSearch] = useSearchParams();
  const [periodKey, setPeriodKey] = useState<PeriodKey>('30');
  const serviceParam = search.get('service');
  const serviceId = serviceParam ? Number(serviceParam) : undefined;
  const period = useMemo(() => ({ ...periodRange(periodKey), serviceId }), [periodKey, serviceId]);

  const services = useServices(productId);
  const summary = useStatsSummary(productId, period);
  const series = useStatsTimeseries(productId, period);
  const releases = useReleases(productId);

  const serviceOptions = [{ value: '', label: 'Весь продукт' }, ...(services.data?.items ?? []).map((s) => ({ value: String(s.id), label: s.name }))];

  const chartData = useMemo(() => {
    if (!series.data) return [];
    return series.data.points.map((p) => ({ label: formatPeriodStart(p.periodStart, series.data.granularity), periodStart: p.periodStart, average: p.average, count: p.count }));
  }, [series.data]);

  const releaseLines = useMemo(() => {
    if (!series.data || !releases.data) return [];
    const g = series.data.granularity;
    return releases.data.items
      .filter((r) => !serviceId || r.serviceId == null || r.serviceId === serviceId)
      .map((r) => ({ r, bucket: releaseBucket(r, g) }))
      .filter(({ bucket }) => chartData.some((d) => d.periodStart === bucket))
      .map(({ r, bucket }) => ({ x: formatPeriodStart(bucket, g), label: `v${r.version}`, color: 'violet.6' }));
  }, [series.data, releases.data, chartData, serviceId]);

  return (
    <Stack>
      <Group justify="space-between">
        <SegmentedControl data={PERIOD_OPTIONS} value={periodKey} onChange={(v) => setPeriodKey(v as PeriodKey)} aria-label="Период" />
        <Select
          data={serviceOptions}
          value={serviceParam ?? ''}
          onChange={(v) => {
            const next = new URLSearchParams(search);
            if (v) next.set('service', v); else next.delete('service');
            setSearch(next, { replace: true });
          }}
          allowDeselect={false}
          w={240}
          aria-label="Сервис"
        />
      </Group>

      {summary.isPending ? <LoadingState /> : summary.error && isNotImplemented(summary.error) ? <Card><NotImplementedState what="Статистика" /></Card> : summary.error ? <ErrorState error={summary.error} onRetry={() => summary.refetch()} /> : summary.data.count === 0 ? (
        <Card><EmptyState title="Оценки по продукту пока отсутствуют">Ожидайте поступления данных или выберите другой период.</EmptyState></Card>
      ) : (
        <>
          <SummaryCards s={summary.data} />
          <SimpleGrid cols={{ base: 1, md: 3 }}>
            <Card style={{ gridColumn: 'span 2' }}>
              <Title order={4} mb="xs">Динамика средней оценки</Title>
              {series.isPending ? <LoadingState /> : series.error ? <ErrorState error={series.error} onRetry={() => series.refetch()} /> : (
                <>
                  <LineChart
                    h={260}
                    data={chartData}
                    dataKey="label"
                    series={[{ name: 'average', label: 'Средняя', color: 'teal.6' }]}
                    yAxisProps={{ domain: [1, 5], ticks: [1, 2, 3, 4, 5] }}
                    referenceLines={releaseLines}
                    connectNulls
                    curveType="monotone"
                    valueFormatter={(v) => formatAverage(v)}
                    tooltipProps={{
                      content: ({ payload }) => {
                        const d = payload?.[0]?.payload as (typeof chartData)[number] | undefined;
                        if (!d) return null;
                        return (
                          <Card padding="xs" shadow="sm">
                            <Text size="sm" fw={600}>{d.label}</Text>
                            <Text size="sm">Средняя: {formatAverage(d.average)}</Text>
                            <Text size="xs" c="dimmed">n = {d.count}</Text>
                          </Card>
                        );
                      },
                    }}
                  />
                  <Text size="xs" c="dimmed" mt={4}>
                    {series.data.granularity === 'day' ? 'По дням' : series.data.granularity === 'week' ? 'По неделям' : 'По месяцам'}
                    {releaseLines.length ? ' · фиолетовые линии — релизы' : ''}
                  </Text>
                </>
              )}
            </Card>
            <Card>
              <Title order={4} mb="xs">Распределение оценок</Title>
              <BarChart
                h={260}
                data={summary.data.histogram.map((b) => ({ score: `${b.score}★`, count: b.count }))}
                dataKey="score"
                series={[{ name: 'count', label: 'Оценок', color: 'teal.5' }]}
              />
            </Card>
          </SimpleGrid>
        </>
      )}

      {releases.error && isNotImplemented(releases.error) ? null : <ReleaseComparisonCard productId={productId} releases={releases.data?.items ?? []} />}

      <Card>
        <Title order={4} mb="xs">Сервисы внутри</Title>
        {services.isPending ? <LoadingState /> : services.error ? <ErrorState error={services.error} /> : !services.data.items.length ? (
          <EmptyState title="Сервисов нет">Добавьте их на вкладке «Сервисы», чтобы видеть статус из мониторинга.</EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={480}>
            <Table>
              <Table.Thead><Table.Tr><Table.Th>Сервис</Table.Th><Table.Th>Статус</Table.Th><Table.Th>Отклик</Table.Th><Table.Th>Оценка за период</Table.Th></Table.Tr></Table.Thead>
              <Table.Tbody>
                {services.data.items.map((s) => (
                  <ServiceRow key={s.id} productId={productId} period={period} service={s} />
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
    </Stack>
  );
}

function ServiceRow({ productId, period, service }: { productId: number; period: { from?: string; to?: string }; service: { id: number; name: string; health: import('../../api/types').Health } }) {
  const stats = useStatsSummary(productId, { from: period.from, to: period.to, serviceId: service.id });
  return (
    <Table.Tr>
      <Table.Td>{service.name}</Table.Td>
      <Table.Td><HealthDot health={service.health} withLabel /></Table.Td>
      <Table.Td className="tabular">{formatMs(service.health.responseMs)}</Table.Td>
      <Table.Td>{stats.data ? <RatingValue stats={stats.data} /> : <Text size="sm" c="dimmed">{stats.isPending ? '…' : '—'}</Text>}</Table.Td>
    </Table.Tr>
  );
}

function ReleaseComparisonCard({ productId, releases }: { productId: number; releases: Release[] }) {
  const [picked, setPicked] = useState<string | null>(null);
  const releaseId = picked ? Number(picked) : releases[0]?.id ?? null;
  const cmp = useComparison(productId, releaseId);

  return (
    <Card>
      <Group justify="space-between" mb="xs">
        <Title order={4}>До / после релиза</Title>
        {releases.length > 0 && (
          <Select
            data={releases.map((r) => ({ value: String(r.id), label: `v${r.version} — ${r.title}` }))}
            value={releaseId ? String(releaseId) : null}
            onChange={setPicked}
            allowDeselect={false}
            w={280}
            aria-label="Релиз"
          />
        )}
      </Group>
      {!releases.length ? (
        <EmptyState title="Релизов пока нет">Отметьте релиз на вкладке «Релизы» — здесь появится сравнение 14 дней до и после.</EmptyState>
      ) : cmp.isPending ? <LoadingState /> : cmp.error ? <ErrorState error={cmp.error} onRetry={() => cmp.refetch()} /> : (
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <div>
            <Text size="xs" c="dimmed">До: {formatDate(cmp.data.before.from)} — {formatDate(cmp.data.before.to)}</Text>
            <RatingValue stats={cmp.data.before.stats} size="xl" />
          </div>
          <div>
            <Text size="xs" c="dimmed">После: {formatDate(cmp.data.after.from)} — {formatDate(cmp.data.after.to)}</Text>
            <RatingValue stats={cmp.data.after.stats} size="xl" />
          </div>
          <div>
            <Text size="xs" c="dimmed">Разница</Text>
            <Text size="xl" fw={700} className="tabular" c={cmp.data.averageDelta == null ? 'dimmed' : cmp.data.averageDelta < 0 ? 'red.7' : 'teal.7'}>
              {cmp.data.averageDelta == null ? '—' : `${cmp.data.averageDelta > 0 ? '▲ +' : cmp.data.averageDelta < 0 ? '▼ ' : ''}${formatAverage(cmp.data.averageDelta)}`}
            </Text>
            {(cmp.data.before.clippedByReleaseId || cmp.data.after.clippedByReleaseId) && (
              <Text size="xs" c="dimmed">Окно обрезано соседним релизом</Text>
            )}
          </div>
        </SimpleGrid>
      )}
    </Card>
  );
}
