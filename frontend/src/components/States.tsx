// Три обязательных состояния каждого экрана: загрузка, пусто, ошибка.
import type { ReactNode } from 'react';
import { Alert, Button, Center, Loader, Stack, Text } from '@mantine/core';
import { IconAlertTriangle, IconInbox } from '@tabler/icons-react';
import { ApiError } from '../api/client';

export function LoadingState({ fullHeight, label = 'Загружаем…' }: { fullHeight?: boolean; label?: string }) {
  return (
    <Center h={fullHeight ? '100vh' : 160} role="status" aria-live="polite">
      <Stack align="center" gap="xs">
        <Loader size="sm" />
        <Text size="sm" c="dimmed">{label}</Text>
      </Stack>
    </Center>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <Center py="xl">
      <Stack align="center" gap="xs" maw={420} ta="center">
        <IconInbox size={32} stroke={1.5} color="var(--mantine-color-gray-5)" />
        <Text fw={600}>{title}</Text>
        {children && <Text size="sm" c="dimmed">{children}</Text>}
        {action}
      </Stack>
    </Center>
  );
}

export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return 'Нет связи с сервером ОКО. Проверьте, что backend или мок-сервер запущен.';
    if (error.status === 404) return 'Не найдено или у вас нет доступа.';
    if (error.status === 503) return 'Сервис временно недоступен. Попробуйте позже.';
    return error.message;
  }
  return 'Что-то пошло не так.';
}

export function ErrorState({ error, onRetry, fullHeight }: { error: unknown; onRetry?: () => void; fullHeight?: boolean }) {
  const body = (
    <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />} title="Не удалось загрузить" maw={520} w="100%">
      <Stack gap="sm" align="flex-start">
        <Text size="sm">{errorText(error)}</Text>
        {onRetry && <Button size="xs" variant="white" color="red" onClick={onRetry}>Повторить</Button>}
      </Stack>
    </Alert>
  );
  return fullHeight ? <Center h="100vh" p="md">{body}</Center> : <Center py="md">{body}</Center>;
}

/**
 * Эндпоинт есть в контракте, но backend его ещё не реализовал (v1: нет dashboard, stats, ratings, releases).
 * Такие запросы отвечают 404 на уже открытом продукте — это не «нет доступа», а «раздел ещё не готов».
 */
export function isNotImplemented(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

export function NotImplementedState({ what }: { what: string }) {
  return (
    <EmptyState title={`${what} пока недоступна`}>
      Backend ещё не отдаёт эти данные (в v1 — только каталог, ключи и приём оценок). Раздел заработает без изменений в панели, когда эндпоинт появится.
    </EmptyState>
  );
}
