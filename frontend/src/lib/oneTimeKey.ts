// Ключи, выпущенные только что (при регистрации продукта), показываем один раз.
// Держим их только в памяти вкладки: не в localStorage, не в sessionStorage и не в history.state.
// Обновили страницу — ключей больше нет, так и задумано.
import type { ApiKeyIssued, KeyType } from '../api/types';

let pending: ApiKeyIssued[] = [];

export function stashIssuedKey(key: ApiKeyIssued) {
  pending = [...pending.filter((k) => !(k.productId === key.productId && k.type === key.type)), key];
}

/** Посмотреть, не очищая (React в StrictMode вызывает инициализатор состояния дважды). */
export function peekIssuedKey(productId: number, type: KeyType = 'public'): ApiKeyIssued | null {
  return pending.find((k) => k.productId === productId && k.type === type) ?? null;
}

export function clearIssuedKeys() {
  pending = [];
}
