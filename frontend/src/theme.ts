// Тема панели. Макетов пока нет (Максим недоступен), поэтому оформление минимальное:
// один акцентный цвет, системный шрифт. Когда придут макеты по брендбуку ТПУ — правим здесь.
import { createTheme } from '@mantine/core';

export const theme = createTheme({
  primaryColor: 'teal',
  defaultRadius: 'md',
  fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif',
  headings: { fontWeight: '650' },
  components: {
    Card: { defaultProps: { withBorder: true, padding: 'lg' } },
    Table: { defaultProps: { verticalSpacing: 'sm' } },
  },
});
