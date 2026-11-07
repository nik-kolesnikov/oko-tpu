// Настройки продукта: основные поля, приостановка сбора, владельцы, архивация (только админ ЦЭУ).
import { useState } from 'react';
import { ActionIcon, Alert, Button, Card, Group, Stack, Table, Text, Textarea, TextInput, Title, Tooltip } from '@mantine/core';
import { IconTrash } from '@tabler/icons-react';
import { useNavigate } from 'react-router';
import { ApiError } from '../../api/client';
import { useAddOwner, useArchiveProduct, useCurrentUser, useRemoveOwner, useUpdateProduct } from '../../api/hooks';
import type { Product } from '../../api/types';
import { notifyError, notifyOk } from '../../components/notify';
import { useApiForm } from '../../lib/useApiForm';

export function SettingsTab({ product }: { product: Product }) {
  const me = useCurrentUser();
  const archived = product.status === 'archived';
  return (
    <Stack gap="lg" maw={760}>
      {archived && <Alert color="gray" title="Продукт в архиве">Изменения недоступны. Данные и статистика сохранены.</Alert>}
      {!archived && <MainForm product={product} />}
      {!archived && <CollectionCard product={product} />}
      <OwnersCard product={product} readOnly={archived} />
      {me.data?.isAdmin && !archived && <ArchiveCard product={product} />}
    </Stack>
  );
}

function MainForm({ product }: { product: Product }) {
  const update = useUpdateProduct(product.id);
  const form = useApiForm({ name: product.name, description: product.description ?? '', url: product.url });
  const dirty = form.values.name !== product.name || form.values.description !== (product.description ?? '') || form.values.url !== product.url;

  const submit = async () => {
    try {
      await update.mutateAsync({ name: form.values.name.trim(), description: form.values.description.trim() || null, url: form.values.url.trim() });
      notifyOk('Сохранено');
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DUPLICATE_URL') form.setErrors({ url: 'Продукт с таким URL уже зарегистрирован' });
      else if (!form.applyServerError(err)) notifyError(err);
    }
  };

  return (
    <Card>
      <Title order={4} mb="sm">Основное</Title>
      <form onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
        <Stack>
          <TextInput label="Название" required maxLength={200} value={form.values.name} onChange={(e) => form.set('name', e.currentTarget.value)} error={form.errors.name} />
          <TextInput label="URL продукта" required value={form.values.url} onChange={(e) => form.set('url', e.currentTarget.value)} error={form.errors.url} />
          <Textarea label="Описание" autosize minRows={2} value={form.values.description} onChange={(e) => form.set('description', e.currentTarget.value)} />
          <Group justify="flex-end"><Button type="submit" disabled={!dirty} loading={update.isPending}>Сохранить</Button></Group>
        </Stack>
      </form>
    </Card>
  );
}

function CollectionCard({ product }: { product: Product }) {
  const update = useUpdateProduct(product.id);
  const paused = product.status === 'paused';
  return (
    <Card>
      <Group justify="space-between">
        <div>
          <Title order={4}>Сбор оценок</Title>
          <Text size="sm" c="dimmed">{paused ? 'Приостановлен: виджет скрыт, API не принимает оценки. Данные сохранены.' : 'Идёт: виджет показывается, API принимает оценки.'}</Text>
        </div>
        <Button variant={paused ? 'filled' : 'default'} loading={update.isPending}
          onClick={() => update.mutate({ status: paused ? 'active' : 'paused' }, { onSuccess: () => notifyOk(paused ? 'Сбор возобновлён' : 'Сбор приостановлен'), onError: (e) => notifyError(e) })}>
          {paused ? 'Возобновить' : 'Приостановить'}
        </Button>
      </Group>
    </Card>
  );
}

function OwnersCard({ product, readOnly }: { product: Product; readOnly: boolean }) {
  const add = useAddOwner(product.id);
  const remove = useRemoveOwner(product.id);
  const [login, setLogin] = useState('');
  const [error, setError] = useState<string | undefined>();
  const last = product.owners.length <= 1;

  const submit = () => {
    if (!login.trim()) return;
    add.mutate(login.trim(), {
      onSuccess: () => { setLogin(''); notifyOk('Владелец добавлен'); },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) setError('Такой пользователь ещё не входил в ОКО. Попросите его войти один раз.');
        else if (e instanceof ApiError && e.code === 'CONFLICT') setError('Уже владелец');
        else notifyError(e);
      },
    });
  };

  return (
    <Card>
      <Title order={4} mb="sm">Владельцы</Title>
      <Table>
        <Table.Tbody>
          {product.owners.map((o) => (
            <Table.Tr key={o.id}>
              <Table.Td><Text size="sm">{o.fullName}</Text><Text size="xs" c="dimmed">{o.login}</Text></Table.Td>
              <Table.Td w={48}>
                {!readOnly && (
                  <Tooltip label={last ? 'Нельзя убрать последнего владельца' : 'Убрать'} withArrow>
                    <ActionIcon variant="subtle" color="red" disabled={last} aria-label={`Убрать ${o.fullName}`}
                      onClick={() => remove.mutate(o.id, { onSuccess: () => notifyOk('Владелец убран'), onError: (e) => notifyError(e) })}>
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Tooltip>
                )}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!readOnly && (
        <form onSubmit={(e) => { e.preventDefault(); submit(); }}>
          <Group mt="sm" align="flex-start">
            <TextInput placeholder="Логин ТПУ, например ivanov_ii" value={login} onChange={(e) => { setLogin(e.currentTarget.value); setError(undefined); }} error={error} style={{ flex: 1 }} aria-label="Логин нового владельца" />
            <Button type="submit" variant="light" loading={add.isPending}>Добавить</Button>
          </Group>
        </form>
      )}
    </Card>
  );
}

function ArchiveCard({ product }: { product: Product }) {
  const archive = useArchiveProduct(product.id);
  const navigate = useNavigate();
  return (
    <Card style={{ borderColor: 'var(--mantine-color-red-3)' }}>
      <Group justify="space-between">
        <div>
          <Title order={4}>Архивировать продукт</Title>
          <Text size="sm" c="dimmed">Виджет и приём оценок отключатся, ключи будут отозваны. Данные останутся. Доступно только админу ЦЭУ.</Text>
        </div>
        <Button color="red" variant="light" loading={archive.isPending} onClick={() => {
          if (!window.confirm(`Архивировать «${product.name}»?`)) return;
          archive.mutate(undefined, { onSuccess: () => { notifyOk('Продукт в архиве'); navigate('/'); }, onError: (e) => notifyError(e) });
        }}>Архивировать</Button>
      </Group>
    </Card>
  );
}
