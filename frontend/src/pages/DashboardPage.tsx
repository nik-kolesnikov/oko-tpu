// Главный экран: дерево «продукт → сервисы» с лампочками, оценками и откликом.
// Данные — GET /dashboard (средняя за 30 дней). Поиск и фильтр работают на клиенте.
import { Fragment, useMemo, useState } from 'react';
import {
  ActionIcon, Badge, Button, Card, Checkbox, Group, SimpleGrid, Table, Text, TextInput, Title,
} from '@mantine/core';
import { IconChevronDown, IconChevronRight, IconPlus, IconSearch } from '@tabler/icons-react';
import { Link, useNavigate } from 'react-router';
import { useCurrentUser, useDashboard, useProducts, useServices } from '../api/hooks';
import type { DashboardProduct } from '../api/types';
import { EmptyState, ErrorState, isNotImplemented, LoadingState } from '../components/States';
import { HealthDot, RatingValue } from '../components/Indicators';
import { formatAverage, formatMs, shares } from '../lib/format';

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card padding="md">
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>{label}</Text>
      <Text size="xl" fw={700} className="tabular">{value}</Text>
      {hint && <Text size="xs" c="dimmed">{hint}</Text>}
    </Card>
  );
}

const pct = (v: number | null) => (v == null ? '—' : `${v}%`);

export function DashboardPage() {
  const dashboard = useDashboard();
  const me = useCurrentUser();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [onlyProblems, setOnlyProblems] = useState(false);
  const [open, setOpen] = useState<Record<number, boolean>>({});

  const products = useMemo(() => {
    const list = dashboard.data?.products ?? [];
    const needle = q.trim().toLowerCase();
    return list.filter((p) => {
      if (needle && !p.name.toLowerCase().includes(needle) && !p.services.some((s) => s.name.toLowerCase().includes(needle))) return false;
      if (onlyProblems && p.health.status !== 'down') return false;
      return true;
    });
  }, [dashboard.data, q, onlyProblems]);

  const totals = useMemo(() => {
    const list = dashboard.data?.products ?? [];
    const services = list.flatMap((p) => p.services);
    const count = list.reduce((s, p) => s + p.rating.count, 0);
    const sum = list.reduce((s, p) => s + (p.rating.average ?? 0) * p.rating.count, 0);
    return {
      products: list.length,
      services: services.length,
      count,
      average: count ? Math.round((sum / count) * 10) / 10 : null,
      down: services.filter((s) => s.health.status === 'down').length,
    };
  }, [dashboard.data]);

  if (dashboard.isPending) return <LoadingState />;
  if (dashboard.error && isNotImplemented(dashboard.error)) return <ProductListFallback isAdmin={!!me.data?.isAdmin} />;
  if (dashboard.error) return <ErrorState error={dashboard.error} onRetry={() => dashboard.refetch()} />;

  const days = dashboard.data.ratingPeriodDays;
  const header = (
    <Group justify="space-between" align="flex-end" mb="md">
      <div>
        <Title order={2}>{me.data?.isAdmin ? 'Все продукты' : 'Мои продукты'}</Title>
        <Text c="dimmed" size="sm">Оценки за последние {days} дней. Статус — из системы мониторинга.</Text>
      </div>
      <Button component={Link} to="/products/new" leftSection={<IconPlus size={16} />}>Добавить продукт</Button>
    </Group>
  );

  if (!dashboard.data.products.length) {
    return (
      <>
        {header}
        <Card>
          <EmptyState title="Продуктов пока нет" action={<Button component={Link} to="/products/new">Зарегистрировать продукт</Button>}>
            Зарегистрируйте продукт, чтобы получить код виджета и начать собирать оценки.
          </EmptyState>
        </Card>
      </>
    );
  }

  return (
    <>
      {header}
      <SimpleGrid cols={{ base: 2, sm: 3, md: 5 }} mb="md">
        <Stat label="Продуктов" value={String(totals.products)} />
        <Stat label="Сервисов" value={String(totals.services)} />
        <Stat label={`Оценок за ${days} дн.`} value={totals.count.toLocaleString('ru-RU')} />
        <Stat label="Средняя по всем" value={formatAverage(totals.average)} />
        <Stat label="Не работают сейчас" value={String(totals.down)} hint="сервисов" />
      </SimpleGrid>

      <Card padding={0}>
        <Group p="md" gap="md">
          <TextInput
            placeholder="Поиск по названию"
            leftSection={<IconSearch size={16} />}
            value={q}
            onChange={(e) => setQ(e.currentTarget.value)}
            w={280}
            aria-label="Поиск по названию"
          />
          <Checkbox label="Только проблемные" checked={onlyProblems} onChange={(e) => setOnlyProblems(e.currentTarget.checked)} />
        </Group>
        <Table.ScrollContainer minWidth={760}>
          <Table highlightOnHover={false}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th w={40} />
                <Table.Th>Продукт / сервис</Table.Th>
                <Table.Th>Статус</Table.Th>
                <Table.Th>Средняя</Table.Th>
                <Table.Th>Положительных</Table.Th>
                <Table.Th>Отклик</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {products.map((p) => (
                <ProductRows
                  key={p.id}
                  product={p}
                  open={!!open[p.id]}
                  onToggle={() => setOpen((o) => ({ ...o, [p.id]: !o[p.id] }))}
                  onOpen={(serviceId) => navigate(`/products/${p.id}${serviceId ? `?service=${serviceId}` : ''}`)}
                />
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
        {!products.length && <EmptyState title="Ничего не нашлось">Измените поиск или снимите фильтр.</EmptyState>}
      </Card>
    </>
  );
}

function ProductRows({ product: p, open, onToggle, onOpen }: {
  product: DashboardProduct; open: boolean; onToggle: () => void; onOpen: (serviceId?: number) => void;
}) {
  return (
    <Fragment>
      <Table.Tr className="clickable-row" onClick={() => onOpen()}>
        <Table.Td onClick={(e) => e.stopPropagation()}>
          {p.services.length > 0 && (
            <ActionIcon variant="subtle" color="gray" onClick={onToggle} aria-label={open ? 'Свернуть сервисы' : 'Показать сервисы'} aria-expanded={open}>
              {open ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
            </ActionIcon>
          )}
        </Table.Td>
        <Table.Td>
          <Group gap="xs" wrap="nowrap">
            <Text fw={600}>{p.name}</Text>
            {p.status === 'paused' && <Badge size="xs" color="gray" variant="light">сбор приостановлен</Badge>}
            <Text size="xs" c="dimmed">{p.services.length ? `${p.services.length} серв.` : ''}</Text>
          </Group>
        </Table.Td>
        <Table.Td><HealthDot health={p.health} withLabel /></Table.Td>
        <Table.Td><RatingValue stats={p.rating} /></Table.Td>
        <Table.Td className="tabular">{pct(shares(p.rating).positive)}</Table.Td>
        <Table.Td className="tabular">{formatMs(p.health.responseMs)}</Table.Td>
      </Table.Tr>
      {open && p.services.map((s) => (
        <Table.Tr key={`s${s.id}`} className="clickable-row" onClick={() => onOpen(s.id)} bg="gray.0">
          <Table.Td />
          <Table.Td pl="xl"><Text size="sm">{s.name}</Text></Table.Td>
          <Table.Td><HealthDot health={s.health} withLabel /></Table.Td>
          <Table.Td><RatingValue stats={s.rating} /></Table.Td>
          <Table.Td className="tabular">{pct(shares(s.rating).positive)}</Table.Td>
          <Table.Td className="tabular">{formatMs(s.health.responseMs)}</Table.Td>
        </Table.Tr>
      ))}
    </Fragment>
  );
}


/**
 * Пока backend не отдаёт GET /dashboard (v1), показываем список продуктов из GET /products
 * и статусы сервисов из GET /products/{id}/services. Оценок здесь нет — они придут с /dashboard.
 */
function ProductListFallback({ isAdmin }: { isAdmin: boolean }) {
  const products = useProducts({ status: ['active', 'paused'], size: 100 });
  const navigate = useNavigate();

  return (
    <>
      <Group justify="space-between" align="flex-end" mb="md">
        <div>
          <Title order={2}>{isAdmin ? 'Все продукты' : 'Мои продукты'}</Title>
          <Text c="dimmed" size="sm">Сводка с оценками появится, когда backend добавит GET /dashboard. Пока — список продуктов и статусы сервисов.</Text>
        </div>
        <Button component={Link} to="/products/new" leftSection={<IconPlus size={16} />}>Добавить продукт</Button>
      </Group>
      <Card padding={0}>
        {products.isPending ? <LoadingState /> : products.error ? <ErrorState error={products.error} onRetry={() => products.refetch()} /> : !products.data.items.length ? (
          <EmptyState title="Продуктов пока нет" action={<Button component={Link} to="/products/new">Зарегистрировать продукт</Button>}>
            Зарегистрируйте продукт, чтобы получить код виджета и начать собирать оценки.
          </EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={640}>
            <Table>
              <Table.Thead>
                <Table.Tr><Table.Th>Продукт</Table.Th><Table.Th>URL</Table.Th><Table.Th>Сервисы</Table.Th>{isAdmin && <Table.Th>Владельцы</Table.Th>}</Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {products.data.items.map((p) => (
                  <Table.Tr key={p.id} className="clickable-row" onClick={() => navigate(`/products/${p.id}`)}>
                    <Table.Td>
                      <Group gap="xs" wrap="nowrap">
                        <Text fw={600}>{p.name}</Text>
                        {p.status === 'paused' && <Badge size="xs" color="gray" variant="light">сбор приостановлен</Badge>}
                      </Group>
                    </Table.Td>
                    <Table.Td><Text size="sm" c="dimmed">{p.url}</Text></Table.Td>
                    <Table.Td><ServicesHealth productId={p.id} /></Table.Td>
                    {isAdmin && <Table.Td><Text size="sm">{p.owners.map((o) => o.fullName).join(', ')}</Text></Table.Td>}
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
    </>
  );
}

function ServicesHealth({ productId }: { productId: number }) {
  const services = useServices(productId);
  if (services.isPending) return <Text size="sm" c="dimmed">…</Text>;
  if (services.error) return <Text size="sm" c="dimmed">—</Text>;
  if (!services.data.items.length) return <Text size="sm" c="dimmed">нет</Text>;
  return (
    <Group gap="md">
      {services.data.items.map((s) => (
        <Group key={s.id} gap={6} wrap="nowrap"><HealthDot health={s.health} /><Text size="sm">{s.name}</Text></Group>
      ))}
    </Group>
  );
}
