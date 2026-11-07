// Регистрация продукта (сценарий 1 ТЗ).
// После создания сразу выпускаем публичный ключ и открываем вкладку «Интеграция» с кодом вставки.
import { Anchor, Button, Card, Group, Stack, TagsInput, Text, Textarea, TextInput, Title } from '@mantine/core';
import { Link, useNavigate } from 'react-router';
import { api, ApiError } from '../api/client';
import { useCreateProduct, useCurrentUser } from '../api/hooks';
import { notifyError } from '../components/notify';
import { useApiForm } from '../lib/useApiForm';
import { stashIssuedKey } from '../lib/oneTimeKey';
import type { ApiKeyIssued } from '../api/types';

export function ProductCreatePage() {
  const me = useCurrentUser();
  const navigate = useNavigate();
  const create = useCreateProduct();
  const form = useApiForm({ name: '', description: '', url: '', ownerLogins: [] as string[] });

  const validate = () => {
    const e: Record<string, string> = {};
    if (form.values.name.trim().length < 2) e.name = 'Минимум 2 символа';
    try {
      const u = new URL(form.values.url);
      if (!/^https?:$/.test(u.protocol)) throw new Error();
    } catch {
      e.url = 'Адрес вида https://lk.tpu.ru';
    }
    form.setErrors(e);
    return !Object.keys(e).length;
  };

  const submit = async () => {
    if (!validate()) return;
    try {
      const product = await create.mutateAsync({
        name: form.values.name.trim(),
        description: form.values.description.trim() || null,
        url: form.values.url.trim(),
        ...(me.data?.isAdmin && form.values.ownerLogins.length ? { ownerLogins: form.values.ownerLogins } : {}),
      });
      // Решение команды: при регистрации выпускаются публичный и секретный ключи.
      // По контракту createProduct ключей не возвращает, поэтому выпускаем их здесь (см. «Расхождения», п. 2).
      for (const type of ['public', 'secret'] as const) {
        try {
          stashIssuedKey(await api<ApiKeyIssued>('POST', `/products/${product.id}/keys`, { body: { type } }));
        } catch (err) {
          notifyError(err, `Продукт создан, но ${type === 'public' ? 'публичный' : 'секретный'} ключ не выпустился — выпустите его на вкладке «Интеграция»`);
        }
      }
      navigate(`/products/${product.id}/integration`, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DUPLICATE_URL') {
        form.setErrors({ url: 'Продукт с таким URL уже зарегистрирован. Укажите другой URL.' });
      } else if (!form.applyServerError(err)) {
        notifyError(err);
      }
    }
  };

  return (
    <Stack maw={640}>
      <div>
        <Anchor component={Link} to="/" size="sm">← Все продукты</Anchor>
        <Title order={2} mt={4}>Новый продукт</Title>
        <Text c="dimmed" size="sm">После сохранения выпустятся публичный и секретный ключи и откроется вкладка «Интеграция» с кодом виджета.</Text>
      </div>
      <Card>
        <form onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
          <Stack>
            <TextInput label="Название" placeholder="Личный кабинет студента" required value={form.values.name}
              onChange={(e) => form.set('name', e.currentTarget.value)} error={form.errors.name} maxLength={200} />
            <TextInput label="URL продукта" placeholder="https://lk.tpu.ru" required value={form.values.url}
              onChange={(e) => form.set('url', e.currentTarget.value)} error={form.errors.url}
              description="Должен быть уникальным среди зарегистрированных продуктов" />
            <Textarea label="Описание" autosize minRows={2} value={form.values.description}
              onChange={(e) => form.set('description', e.currentTarget.value)} error={form.errors.description} />
            {me.data?.isAdmin && (
              <TagsInput label="Владельцы (логины ТПУ)" description="Только для админа: завести продукт за команду. Пусто — владельцем станете вы."
                placeholder="ivanov_ii и Enter" value={form.values.ownerLogins}
                onChange={(v) => form.set('ownerLogins', v)} error={form.errors.ownerLogins} />
            )}
            <Group justify="flex-end">
              <Button variant="default" component={Link} to="/">Отмена</Button>
              <Button type="submit" loading={create.isPending}>Создать</Button>
            </Group>
          </Stack>
        </form>
      </Card>
    </Stack>
  );
}
