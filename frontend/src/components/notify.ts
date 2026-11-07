import { notifications } from '@mantine/notifications';
import { errorText } from './States';

export const notifyError = (error: unknown, title = 'Не получилось') =>
  notifications.show({ color: 'red', title, message: errorText(error) });

export const notifyOk = (message: string) => notifications.show({ color: 'teal', message });
