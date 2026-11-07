// Лента отзывов. ФИО и идентификаторы оценивших не показываем (их и нет в API).
// Текст отзыва выводим как обычный текст: React экранирует его сам, innerHTML не используем.
import { useMemo, useState } from 'react';
import { Badge, Card, Checkbox, Group, Pagination, SegmentedControl, Select, Stack, Text } from '@mantine/core';
import { useRatings, useServices, type RatingsQuery } from '../../api/hooks';
import { EmptyState, ErrorState, isNotImplemented, LoadingState, NotImplementedState } from '../../components/States';
import { formatDateTime, PERIOD_OPTIONS, periodRange, type PeriodKey } from '../../lib/format';

const ROLE = { student: 'студент', staff: 'сотрудник', unknown: null } as const;
const SCORE_COLOR = ['', 'red', 'red', 'yellow', 'teal', 'teal'];

export function ReviewsTab({ productId }: { productId: number }) {
  const [periodKey, setPeriodKey] = useState<PeriodKey>('30');
  const [onlyText, setOnlyText] = useState(true);
  const [onlyLow, setOnlyLow] = useState(false);
  const [serviceId, setServiceId] = useState<string>('');
  const [page, setPage] = useState(1);
  const services = useServices(productId);

  const query: RatingsQuery = useMemo(() => ({
    ...periodRange(periodKey),
    serviceId: serviceId ? Number(serviceId) : undefined,
    hasComment: onlyText || undefined,
    maxScore: onlyLow ? 3 : undefined,
    page,
    size: 20,
  }), [periodKey, serviceId, onlyText, onlyLow, page]);
  const ratings = useRatings(productId, query);

  const resetPage = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(1); };

  return (
    <Stack>
      <Group gap="md">
        <SegmentedControl data={PERIOD_OPTIONS} value={periodKey} onChange={resetPage((v: string) => setPeriodKey(v as PeriodKey))} aria-label="Период" />
        <Select
          data={[{ value: '', label: 'Все сервисы' }, ...(services.data?.items ?? []).map((s) => ({ value: String(s.id), label: s.name }))]}
          value={serviceId} onChange={resetPage((v: string | null) => setServiceId(v ?? ''))} allowDeselect={false} w={220} aria-label="Сервис"
        />
        <Checkbox label="Только с текстом" checked={onlyText} onChange={(e) => { setOnlyText(e.currentTarget.checked); setPage(1); }} />
        <Checkbox label="Только 3 и ниже" checked={onlyLow} onChange={(e) => { setOnlyLow(e.currentTarget.checked); setPage(1); }} />
      </Group>

      {ratings.isPending ? <LoadingState /> : ratings.error && isNotImplemented(ratings.error) ? <Card><NotImplementedState what="Лента отзывов" /></Card> : ratings.error ? <ErrorState error={ratings.error} onRetry={() => ratings.refetch()} /> : !ratings.data.items.length ? (
        <Card><EmptyState title="Отзывов нет">За выбранный период с такими фильтрами ничего не нашлось.</EmptyState></Card>
      ) : (
        <>
          <Text size="sm" c="dimmed">Найдено: {ratings.data.totalItems.toLocaleString('ru-RU')}</Text>
          <Stack gap="xs">
            {ratings.data.items.map((r) => (
              <Card key={r.id} padding="md">
                <Group justify="space-between" align="flex-start" wrap="nowrap">
                  <Group gap="sm" wrap="wrap">
                    <Badge color={SCORE_COLOR[r.score]} variant="filled" size="lg" className="tabular">{r.score} ★</Badge>
                    {r.serviceName && <Badge variant="light" color="gray">{r.serviceName}</Badge>}
                    {r.version && <Text size="xs" c="dimmed">версия {r.version}</Text>}
                    {r.raterRole && ROLE[r.raterRole] && <Text size="xs" c="dimmed">{ROLE[r.raterRole]}</Text>}
                    <Text size="xs" c="dimmed">{r.source === 'api' ? 'через API' : 'виджет'}{r.pagePath ? ` · ${r.pagePath}` : ''}</Text>
                  </Group>
                  <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.createdAt)}</Text>
                </Group>
                {r.comment ? <Text mt="xs" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{r.comment}</Text> : <Text mt="xs" size="sm" c="dimmed">без текста</Text>}
              </Card>
            ))}
          </Stack>
          {ratings.data.totalPages > 1 && (
            <Pagination total={ratings.data.totalPages} value={page} onChange={setPage} />
          )}
        </>
      )}
    </Stack>
  );
}
