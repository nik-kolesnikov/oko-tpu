// Карточка продукта: шапка + вкладки. Вкладка — часть URL (/products/12/reviews), чтобы ссылкой можно было поделиться.
import { Anchor, Badge, Group, Stack, Tabs, Text, Title } from '@mantine/core';
import { Link, useNavigate, useParams } from 'react-router';
import { useProduct, useServices } from '../../api/hooks';
import type { Health } from '../../api/types';
import { ErrorState, LoadingState } from '../../components/States';
import { HealthDot } from '../../components/Indicators';
import { NotFoundPage } from '../NotFoundPage';
import { ApiError } from '../../api/client';
import { OverviewTab } from './OverviewTab';
import { ReviewsTab } from './ReviewsTab';
import { ReleasesTab } from './ReleasesTab';
import { IntegrationTab } from './IntegrationTab';
import { ServicesTab } from './ServicesTab';
import { SettingsTab } from './SettingsTab';

const TABS = [
  { value: 'overview', label: 'Обзор' },
  { value: 'reviews', label: 'Отзывы' },
  { value: 'releases', label: 'Релизы' },
  { value: 'integration', label: 'Интеграция' },
  { value: 'services', label: 'Сервисы' },
  { value: 'settings', label: 'Настройки' },
] as const;

const STATUS_BADGE = {
  active: { color: 'teal', label: 'Активен' },
  paused: { color: 'gray', label: 'Сбор приостановлен' },
  archived: { color: 'dark', label: 'В архиве' },
} as const;

/** Лампочка продукта по тому же правилу, что в контракте для дашборда: down, если упал хоть один сервис. */
function productHealth(items: { health: Health }[]): Health {
  const hs = items.map((s) => s.health);
  const status = hs.some((h) => h.status === 'down') ? 'down' : hs.some((h) => h.status === 'up') ? 'up' : 'unknown';
  const ms = hs.map((h) => h.responseMs).filter((x): x is number => x != null);
  return { status, responseMs: ms.length ? Math.max(...ms) : null, checkedAt: null };
}

export function ProductPage() {
  const params = useParams();
  const id = Number(params.productId);
  const tab = TABS.some((t) => t.value === params.tab) ? params.tab! : 'overview';
  const navigate = useNavigate();
  const product = useProduct(id);
  const services = useServices(id);

  if (!Number.isInteger(id) || id <= 0) return <NotFoundPage />;
  if (product.isPending) return <LoadingState />;
  if (product.error) {
    if (product.error instanceof ApiError && product.error.status === 404) return <NotFoundPage />;
    return <ErrorState error={product.error} onRetry={() => product.refetch()} />;
  }

  const p = product.data;
  const badge = STATUS_BADGE[p.status];

  return (
    <Stack gap="md">
      <div>
        <Anchor component={Link} to="/" size="sm">← Все продукты</Anchor>
        <Group justify="space-between" align="flex-start" mt={4}>
          <div>
            <Group gap="sm">
              <Title order={2}>{p.name}</Title>
              <Badge color={badge.color} variant="light">{badge.label}</Badge>
            </Group>
            <Group gap="md" mt={4}>
              <Anchor href={p.url} target="_blank" rel="noreferrer" size="sm">{p.url}</Anchor>
              <Text size="sm" c="dimmed">Владельцы: {p.owners.map((o) => o.fullName).join(', ')}</Text>
            </Group>
          </div>
          {services.data && services.data.items.length > 0 && (
            <Group gap="xs">
              <Text size="sm" c="dimmed">Сейчас:</Text>
              <HealthDot health={productHealth(services.data.items)} withLabel />
            </Group>
          )}
        </Group>
      </div>

      <Tabs value={tab} onChange={(v) => v && navigate(`/products/${id}/${v === 'overview' ? '' : v}`)} keepMounted={false}>
        <Tabs.List>
          {TABS.map((t) => <Tabs.Tab key={t.value} value={t.value}>{t.label}</Tabs.Tab>)}
        </Tabs.List>
        <Tabs.Panel value="overview" pt="md"><OverviewTab productId={id} /></Tabs.Panel>
        <Tabs.Panel value="reviews" pt="md"><ReviewsTab productId={id} /></Tabs.Panel>
        <Tabs.Panel value="releases" pt="md"><ReleasesTab productId={id} /></Tabs.Panel>
        <Tabs.Panel value="integration" pt="md"><IntegrationTab product={p} /></Tabs.Panel>
        <Tabs.Panel value="services" pt="md"><ServicesTab productId={id} /></Tabs.Panel>
        <Tabs.Panel value="settings" pt="md"><SettingsTab product={p} /></Tabs.Panel>
      </Tabs>
    </Stack>
  );
}
