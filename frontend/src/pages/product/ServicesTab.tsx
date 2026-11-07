// Сервисы продукта. monitoringRef — идентификатор сервиса в системе мониторинга: по нему backend берёт лампочку и отклик.
import { useState } from 'react';
import { Button, Card, Group, Modal, Stack, Switch, Table, Text, TextInput } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { ApiError } from '../../api/client';
import { useArchiveService, useCreateService, useServices, useUpdateService } from '../../api/hooks';
import type { Service } from '../../api/types';
import { EmptyState, ErrorState, LoadingState } from '../../components/States';
import { HealthDot } from '../../components/Indicators';
import { notifyError, notifyOk } from '../../components/notify';
import { formatMs } from '../../lib/format';
import { useApiForm } from '../../lib/useApiForm';

export function ServicesTab({ productId }: { productId: number }) {
  const [showArchived, setShowArchived] = useState(false);
  const services = useServices(productId, showArchived);
  const archive = useArchiveService(productId);
  const [editing, setEditing] = useState<Service | 'new' | null>(null);

  return (
    <Stack>
      <Group justify="space-between">
        <Text size="sm" c="dimmed">Сервисы — части продукта (расписание, зачётка…). Оценку можно привязать к сервису через data-service-id в коде виджета.</Text>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>Добавить сервис</Button>
      </Group>
      <Card padding={0}>
        {services.isPending ? <LoadingState /> : services.error ? <ErrorState error={services.error} onRetry={() => services.refetch()} /> : !services.data.items.length ? (
          <EmptyState title="Сервисов пока нет">Добавьте сервис и укажите его идентификатор в мониторинге — появится лампочка «работает / не работает».</EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={640}>
            <Table>
              <Table.Thead><Table.Tr><Table.Th>ID</Table.Th><Table.Th>Название</Table.Th><Table.Th>Мониторинг</Table.Th><Table.Th>Статус</Table.Th><Table.Th>Отклик</Table.Th><Table.Th /></Table.Tr></Table.Thead>
              <Table.Tbody>
                {services.data.items.map((s) => (
                  <Table.Tr key={s.id} opacity={s.status === 'archived' ? 0.55 : 1}>
                    <Table.Td className="tabular">{s.id}</Table.Td>
                    <Table.Td>
                      <Text size="sm" fw={500}>{s.name}</Text>
                      {s.url && <Text size="xs" c="dimmed">{s.url}</Text>}
                    </Table.Td>
                    <Table.Td><Text size="sm" c={s.monitoringRef ? undefined : 'dimmed'}>{s.monitoringRef ?? 'не привязан'}</Text></Table.Td>
                    <Table.Td>{s.status === 'archived' ? <Text size="sm" c="dimmed">в архиве</Text> : <HealthDot health={s.health} withLabel />}</Table.Td>
                    <Table.Td className="tabular">{formatMs(s.health.responseMs)}</Table.Td>
                    <Table.Td>
                      {s.status === 'active' && (
                        <Group gap="xs" justify="flex-end" wrap="nowrap">
                          <Button size="xs" variant="default" onClick={() => setEditing(s)}>Изменить</Button>
                          <Button size="xs" variant="subtle" color="red" onClick={() => {
                            if (!window.confirm(`Перенести сервис «${s.name}» в архив? Оценки и статистика сохранятся.`)) return;
                            archive.mutate(s.id, { onSuccess: () => notifyOk('Сервис в архиве'), onError: (e) => notifyError(e) });
                          }}>В архив</Button>
                        </Group>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
      <Switch size="xs" label="Показать архивные" checked={showArchived} onChange={(e) => setShowArchived(e.currentTarget.checked)} />
      {editing && <ServiceModal productId={productId} service={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function ServiceModal({ productId, service, onClose }: { productId: number; service: Service | null; onClose: () => void }) {
  const create = useCreateService(productId);
  const update = useUpdateService(productId);
  const form = useApiForm({ name: service?.name ?? '', url: service?.url ?? '', monitoringRef: service?.monitoringRef ?? '' });

  const submit = async () => {
    if (!form.values.name.trim()) return form.setErrors({ name: 'Обязательно' });
    const body = { name: form.values.name.trim(), url: form.values.url.trim() || null, monitoringRef: form.values.monitoringRef.trim() || null };
    try {
      if (service) await update.mutateAsync({ serviceId: service.id, body });
      else await create.mutateAsync(body);
      notifyOk(service ? 'Сервис изменён' : 'Сервис добавлен');
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DUPLICATE_MONITORING_REF') form.setErrors({ monitoringRef: 'Этот идентификатор уже привязан к другому сервису' });
      else if (!form.applyServerError(err)) notifyError(err);
    }
  };

  return (
    <Modal opened onClose={onClose} title={service ? 'Изменить сервис' : 'Новый сервис'}>
      <form onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
        <Stack>
          <TextInput label="Название" required maxLength={200} value={form.values.name} onChange={(e) => form.set('name', e.currentTarget.value)} error={form.errors.name} />
          <TextInput label="URL" placeholder="https://lk.tpu.ru/schedule" value={form.values.url} onChange={(e) => form.set('url', e.currentTarget.value)} error={form.errors.url} />
          <TextInput label="Идентификатор в мониторинге" description="Узнайте у команды мониторинга. Без него лампочка будет серой."
            value={form.values.monitoringRef} onChange={(e) => form.set('monitoringRef', e.currentTarget.value)} error={form.errors.monitoringRef} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>Отмена</Button>
            <Button type="submit" loading={create.isPending || update.isPending}>Сохранить</Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
