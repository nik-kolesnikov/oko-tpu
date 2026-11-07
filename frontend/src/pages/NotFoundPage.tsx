import { Button } from '@mantine/core';
import { Link } from 'react-router';
import { EmptyState } from '../components/States';

export function NotFoundPage() {
  return (
    <EmptyState title="Страница не найдена" action={<Button component={Link} to="/" variant="light">К списку продуктов</Button>}>
      Возможно, ссылка устарела или у вас нет доступа к этому продукту.
    </EmptyState>
  );
}
