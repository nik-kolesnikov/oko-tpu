import type { ReactNode } from 'react';
import { AppShell, Badge, Button, Container, Group, Menu, Text, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconLogout } from '@tabler/icons-react';
import { Link } from 'react-router';
import type { CurrentUser } from '../api/types';
import { useLogout } from '../api/hooks';

export function Layout({ user, children }: { user: CurrentUser; children: ReactNode }) {
  const logout = useLogout();

  return (
    <AppShell header={{ height: 56 }} padding="md">
      <AppShell.Header>
        <Container size="xl" h="100%">
          <Group h="100%" justify="space-between" wrap="nowrap">
            <Group gap="lg" wrap="nowrap">
              <UnstyledButton component={Link} to="/" aria-label="ОКО ТПУ, на главную">
                <Text fw={800} size="lg">ОКО <Text span c="teal.7" fw={800}>ТПУ</Text></Text>
              </UnstyledButton>
              <Button component={Link} to="/" variant="subtle" color="gray" size="compact-sm" visibleFrom="xs">Продукты</Button>
            </Group>
            <Menu position="bottom-end" withinPortal>
              <Menu.Target>
                <UnstyledButton>
                  <Group gap={6} wrap="nowrap">
                    <Text size="sm" truncate maw={220}>{user.fullName}</Text>
                    {user.isAdmin && <Badge size="xs" variant="light">админ ЦЭУ</Badge>}
                    <IconChevronDown size={14} />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{user.login}</Menu.Label>
                <Menu.Item leftSection={<IconLogout size={14} />} onClick={() => logout.mutate()}>Выйти</Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Container>
      </AppShell.Header>
      <AppShell.Main>
        <Container size="xl" px={0}>{children}</Container>
      </AppShell.Main>
    </AppShell>
  );
}
