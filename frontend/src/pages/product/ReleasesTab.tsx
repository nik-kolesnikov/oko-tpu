// Релизы: метка (дата, версия, название), изменение средней «до/после» и форма добавления.
import { useState } from 'react';
import { ActionIcon, Button, Card, Group, Menu, Modal, Select, Stack, Table, Text, Textarea, TextInput } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { IconDots, IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import 'dayjs/locale/ru';
import { useComparison, useCreateRelease, useDeleteRelease, useReleases, useServices, useUpdateRelease } from '../../api/hooks';
import type { Release } from '../../api/types';
import { EmptyState, ErrorState, isNotImplemented, LoadingState, NotImplementedState } from '../../components/States';
import { notifyError, notifyOk } from '../../components/notify';
import { formatAverage, formatDate } from '../../lib/format';
import { useApiForm } from '../../lib/useApiForm';

dayjs.locale('ru');

function Delta({ productId, releaseId }: { productId: number; releaseId: number }) {
  const cmp = useComparison(productId, releaseId);
  if (cmp.isPending) return <Text size="sm" c="dimmed">…</Text>;
  if (cmp.error || cmp.data.averageDelta == null) return <Text size="sm" c="dimmed">недостаточно данных</Text>;
  const d = cmp.data.averageDelta;
  const low = cmp.data.before.stats.lowSample || cmp.data.after.stats.lowSample;
  return (
    <Group gap={6} className="tabular" wrap="nowrap">
      <Text size="sm">{formatAverage(cmp.data.before.stats.average)} → {formatAverage(cmp.data.after.stats.average)}</Text>
      <Text size="sm" fw={600} c={low ? 'gray.6' : d < 0 ? 'red.7' : 'teal.7'}>({d > 0 ? '+' : ''}{formatAverage(d)})</Text>
      {low && <Text size="xs" c="dimmed">мало оценок</Text>}
    </Group>
  );
}

export function ReleasesTab({ productId }: { productId: number }) {
  const releases = useReleases(productId);
  const services = useServices(productId);
  const del = useDeleteRelease(productId);
  const [editing, setEditing] = useState<Release | 'new' | null>(null);
  const svcName = (id?: number | null) => services.data?.items.find((s) => s.id === id)?.name;

  return (
    <Stack>
      <Group justify="space-between">
        <Text c="dimmed" size="sm">Сравнение: 14 дней до релиза и 14 дней после. Окно обрезается соседним релизом.</Text>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>Отметить релиз</Button>
      </Group>
      <Card padding={0}>
        {releases.isPending ? <LoadingState /> : releases.error && isNotImplemented(releases.error) ? <NotImplementedState what="Разметка релизов" /> : releases.error ? <ErrorState error={releases.error} onRetry={() => releases.refetch()} /> : !releases.data.items.length ? (
          <EmptyState title="Релизов пока нет">Отметьте выход новой версии, чтобы увидеть, как изменились оценки.</EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={640}>
            <Table>
              <Table.Thead>
                <Table.Tr><Table.Th>Дата</Table.Th><Table.Th>Версия</Table.Th><Table.Th>Название</Table.Th><Table.Th>Сервис</Table.Th><Table.Th>Средняя до → после</Table.Th><Table.Th w={48} /></Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {releases.data.items.map((r) => (
                  <Table.Tr key={r.id}>
                    <Table.Td>{formatDate(r.releasedAt)}</Table.Td>
                    <Table.Td><Text fw={600} size="sm">{r.version}</Text></Table.Td>
                    <Table.Td>
                      <Text size="sm">{r.title}</Text>
                      {r.description && <Text size="xs" c="dimmed">{r.description}</Text>}
                    </Table.Td>
                    <Table.Td><Text size="sm" c={r.serviceId ? undefined : 'dimmed'}>{svcName(r.serviceId) ?? 'весь продукт'}</Text></Table.Td>
                    <Table.Td><Delta productId={productId} releaseId={r.id} /></Table.Td>
                    <Table.Td>
                      <Menu position="bottom-end" withinPortal>
                        <Menu.Target><ActionIcon variant="subtle" color="gray" aria-label="Действия с релизом"><IconDots size={16} /></ActionIcon></Menu.Target>
                        <Menu.Dropdown>
                          <Menu.Item onClick={() => setEditing(r)}>Изменить</Menu.Item>
                          <Menu.Item color="red" onClick={() => {
                            if (!window.confirm(`Удалить метку релиза ${r.version}? Оценки не пострадают.`)) return;
                            del.mutate(r.id, { onSuccess: () => notifyOk('Метка удалена'), onError: (e) => notifyError(e) });
                          }}>Удалить</Menu.Item>
                        </Menu.Dropdown>
                      </Menu>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
      {editing && (
        <ReleaseModal
          productId={productId}
          release={editing === 'new' ? null : editing}
          services={services.data?.items ?? []}
          onClose={() => setEditing(null)}
        />
      )}
    </Stack>
  );
}

function ReleaseModal({ productId, release, services, onClose }: {
  productId: number; release: Release | null; services: { id: number; name: string }[]; onClose: () => void;
}) {
  const create = useCreateRelease(productId);
  const update = useUpdateRelease(productId);
  const form = useApiForm({
    version: release?.version ?? '',
    title: release?.title ?? '',
    description: release?.description ?? '',
    releasedAt: release ? new Date(release.releasedAt) : new Date(),
    serviceId: release?.serviceId ? String(release.serviceId) : '',
  });

  const submit = async () => {
    const e: Record<string, string> = {};
    if (!form.values.version.trim()) e.version = 'Обязательно';
    if (!form.values.title.trim()) e.title = 'Обязательно';
    if (!form.values.releasedAt) e.releasedAt = 'Обязательно';
    form.setErrors(e);
    if (Object.keys(e).length) return;
    const body = {
      version: form.values.version.trim(),
      title: form.values.title.trim(),
      description: form.values.description.trim() || null,
      releasedAt: new Date(form.values.releasedAt).toISOString(),
    };
    try {
      if (release) await update.mutateAsync({ releaseId: release.id, body });
      else await create.mutateAsync({ ...body, serviceId: form.values.serviceId ? Number(form.values.serviceId) : null });
      notifyOk(release ? 'Релиз изменён' : 'Релиз отмечен');
      onClose();
    } catch (err) {
      if (!form.applyServerError(err)) notifyError(err);
    }
  };

  return (
    <Modal opened onClose={onClose} title={release ? 'Изменить релиз' : 'Отметить релиз'}>
      <form onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
        <Stack>
          <Group grow>
            <TextInput label="Версия" placeholder="2.4.0" required maxLength={50} value={form.values.version} onChange={(e) => form.set('version', e.currentTarget.value)} error={form.errors.version} />
            <DateTimePicker label="Дата и время" required valueFormat="DD.MM.YYYY HH:mm" value={form.values.releasedAt}
              onChange={(v) => form.set('releasedAt', v ? new Date(v) : new Date())} error={form.errors.releasedAt} />
          </Group>
          <TextInput label="Название" placeholder="Новое расписание" required maxLength={200} value={form.values.title} onChange={(e) => form.set('title', e.currentTarget.value)} error={form.errors.title} />
          <Textarea label="Описание" autosize minRows={2} value={form.values.description} onChange={(e) => form.set('description', e.currentTarget.value)} />
          {!release && (
            <Select label="Сервис" description="Если релиз касается одного сервиса — сравнение будет только по нему"
              data={[{ value: '', label: 'Весь продукт' }, ...services.map((s) => ({ value: String(s.id), label: s.name }))]}
              value={form.values.serviceId} onChange={(v) => form.set('serviceId', v ?? '')} allowDeselect={false} error={form.errors.serviceId} />
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>Отмена</Button>
            <Button type="submit" loading={create.isPending || update.isPending}>Сохранить</Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
