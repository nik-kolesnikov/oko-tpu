import { Button, Card, Center, Stack, Text, Title } from '@mantine/core';
import { startLogin } from '../api/hooks';

// Вход только через SSO ТПУ: логин и пароль пользователь вводит на странице ТПУ, мы их не видим.
// Пока доступа к SSO нет, backend (и мок) показывает заглушку выбора пользователя.
export function LoginPage() {
  return (
    <Center h="100vh" p="md">
      <Card w={400} maw="100%">
        <Stack gap="md">
          <div>
            <Title order={2}>ОКО <Text span inherit c="teal.7">ТПУ</Text></Title>
            <Text c="dimmed" size="sm">Оценка качества цифровых продуктов университета</Text>
          </div>
          <Button size="md" onClick={() => startLogin()}>Войти через ТПУ</Button>
          <Text size="xs" c="dimmed">Используется единая учётная запись ТПУ.</Text>
        </Stack>
      </Card>
    </Center>
  );
}
