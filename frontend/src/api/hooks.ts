// Хуки TanStack Query: по одному на операцию из контракта, имя = operationId.
// Ищешь, кто вызывает эндпоинт, — ищи operationId (например, createKey).
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { api } from './client';
import type {
  ApiKey, ApiKeyIssued, CurrentUser, Dashboard, Items, KeyType, OriginList, Product, ProductCreate,
  ProductPage, ProductStatus, ProductUpdate, RatingStats, Release, ReleaseComparison, ReleaseInput,
  ReleaseUpdate, ReviewPage, Service, ServiceCreate, ServiceUpdate, Timeseries, User, WidgetSettings,
  WidgetSettingsInput,
} from './types';

export type Period = { from?: string; to?: string; serviceId?: number };

// Ключи кэша. Всё, что относится к продукту, начинается с ['product', id] —
// так одним invalidate можно обновить карточку целиком.
export const qk = {
  me: ['me'] as const,
  dashboard: ['dashboard'] as const,
  products: (q: object) => ['products', q] as const,
  product: (id: number) => ['product', id] as const,
  owners: (id: number) => ['product', id, 'owners'] as const,
  services: (id: number, all = false) => ['product', id, 'services', all] as const,
  keys: (id: number, revoked = false) => ['product', id, 'keys', revoked] as const,
  origins: (id: number) => ['product', id, 'origins'] as const,
  widgetSettings: (id: number) => ['product', id, 'widget-settings'] as const,
  summary: (id: number, p: Period) => ['product', id, 'summary', p] as const,
  timeseries: (id: number, p: Period) => ['product', id, 'timeseries', p] as const,
  ratings: (id: number, q: object) => ['product', id, 'ratings', q] as const,
  releases: (id: number) => ['product', id, 'releases'] as const,
  comparison: (id: number, rid: number) => ['product', id, 'comparison', rid] as const,
};

// ── Авторизация ──
export const useCurrentUser = () =>
  useQuery({ queryKey: qk.me, queryFn: () => api<CurrentUser>('GET', '/auth/me'), retry: false, staleTime: 5 * 60_000 });

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>('POST', '/auth/logout'),
    onSettled: () => qc.clear(),
  });
}

/** Вход: полный переход на backend, он редиректит на SSO ТПУ (или на заглушку). */
export function startLogin(returnTo = window.location.pathname + window.location.search) {
  window.location.href = `/api/v1/auth/login?returnTo=${encodeURIComponent(returnTo)}`;
}

// ── Дашборд ──
export const useDashboard = () => useQuery({ queryKey: qk.dashboard, queryFn: () => api<Dashboard>('GET', '/dashboard') });

// ── Продукты ──
export const useProducts = (q: { status?: ProductStatus[]; q?: string; page?: number; size?: number }) =>
  useQuery({ queryKey: qk.products(q), queryFn: () => api<ProductPage>('GET', '/products', { query: q }), placeholderData: keepPreviousData });

export const useProduct = (id: number) =>
  useQuery({ queryKey: qk.product(id), queryFn: () => api<Product>('GET', `/products/${id}`) });

export function useCreateProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProductCreate) => api<Product>('POST', '/products', { body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: qk.dashboard });
    },
  });
}

export function useUpdateProduct(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProductUpdate) => api<Product>('PATCH', `/products/${id}`, { body }),
    onSuccess: (p) => {
      qc.setQueryData(qk.product(id), p);
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: qk.dashboard });
    },
  });
}

export function useArchiveProduct(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>('DELETE', `/products/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['product', id] });
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: qk.dashboard });
    },
  });
}

// ── Владельцы ──
export const useOwners = (id: number) =>
  useQuery({ queryKey: qk.owners(id), queryFn: () => api<Items<User>>('GET', `/products/${id}/owners`) });

export function useAddOwner(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (login: string) => api<User>('POST', `/products/${id}/owners`, { body: { login } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.product(id) }),
  });
}

export function useRemoveOwner(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: number) => api<void>('DELETE', `/products/${id}/owners/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.product(id) }),
  });
}

// ── Сервисы ──
export const useServices = (id: number, includeArchived = false) =>
  useQuery({
    queryKey: qk.services(id, includeArchived),
    queryFn: () => api<Items<Service>>('GET', `/products/${id}/services`, { query: { includeArchived } }),
  });

export function useCreateService(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ServiceCreate) => api<Service>('POST', `/products/${id}/services`, { body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['product', id, 'services'] });
      qc.invalidateQueries({ queryKey: qk.dashboard });
    },
  });
}

export function useUpdateService(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ serviceId, body }: { serviceId: number; body: ServiceUpdate }) =>
      api<Service>('PATCH', `/products/${id}/services/${serviceId}`, { body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['product', id, 'services'] });
      qc.invalidateQueries({ queryKey: qk.dashboard });
    },
  });
}

export function useArchiveService(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (serviceId: number) => api<void>('DELETE', `/products/${id}/services/${serviceId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['product', id, 'services'] });
      qc.invalidateQueries({ queryKey: qk.dashboard });
    },
  });
}

// ── Ключи ──
export const useKeys = (id: number, includeRevoked = false) =>
  useQuery({
    queryKey: qk.keys(id, includeRevoked),
    queryFn: () => api<Items<ApiKey>>('GET', `/products/${id}/keys`, { query: { includeRevoked } }),
  });

export function useCreateKey(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (type: KeyType) => api<ApiKeyIssued>('POST', `/products/${id}/keys`, { body: { type } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['product', id, 'keys'] }),
  });
}

export function useReissueKey(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (keyId: number) => api<ApiKeyIssued>('POST', `/products/${id}/keys/${keyId}/reissue`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['product', id, 'keys'] }),
  });
}

export function useRevokeKey(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (keyId: number) => api<ApiKey>('POST', `/products/${id}/keys/${keyId}/revoke`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['product', id, 'keys'] }),
  });
}

// ── Разрешённые сайты ──
export const useOrigins = (id: number) =>
  useQuery({ queryKey: qk.origins(id), queryFn: () => api<OriginList>('GET', `/products/${id}/origins`) });

export function useReplaceOrigins(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (origins: string[]) => api<OriginList>('PUT', `/products/${id}/origins`, { body: { origins } }),
    onSuccess: (data) => qc.setQueryData(qk.origins(id), data),
  });
}

// ── Настройки виджета ──
export const useWidgetSettings = (id: number) =>
  useQuery({ queryKey: qk.widgetSettings(id), queryFn: () => api<WidgetSettings>('GET', `/products/${id}/widget-settings`) });

export function useUpdateWidgetSettings(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: WidgetSettingsInput) => api<WidgetSettings>('PUT', `/products/${id}/widget-settings`, { body }),
    onSuccess: (data) => qc.setQueryData(qk.widgetSettings(id), data),
  });
}

// ── Статистика ──
export const useStatsSummary = (id: number, p: Period) =>
  useQuery({
    queryKey: qk.summary(id, p),
    queryFn: () => api<RatingStats>('GET', `/products/${id}/stats/summary`, { query: p }),
    placeholderData: keepPreviousData,
  });

export const useStatsTimeseries = (id: number, p: Period) =>
  useQuery({
    queryKey: qk.timeseries(id, p),
    queryFn: () => api<Timeseries>('GET', `/products/${id}/stats/timeseries`, { query: { ...p, granularity: 'auto' } }),
    placeholderData: keepPreviousData,
  });

// ── Отзывы ──
export type RatingsQuery = Period & { minScore?: number; maxScore?: number; hasComment?: boolean; version?: string; page?: number; size?: number };
export const useRatings = (id: number, q: RatingsQuery) =>
  useQuery({
    queryKey: qk.ratings(id, q),
    queryFn: () => api<ReviewPage>('GET', `/products/${id}/ratings`, { query: q }),
    placeholderData: keepPreviousData,
  });

// ── Релизы ──
export const useReleases = (id: number) =>
  useQuery({ queryKey: qk.releases(id), queryFn: () => api<Items<Release>>('GET', `/products/${id}/releases`) });

export const useComparison = (id: number, releaseId: number | null) =>
  useQuery({
    queryKey: qk.comparison(id, releaseId ?? 0),
    queryFn: () => api<ReleaseComparison>('GET', `/products/${id}/releases/${releaseId}/comparison`),
    enabled: releaseId != null,
  });

function useReleaseMutation<V>(id: number, fn: (v: V) => Promise<unknown>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.releases(id) });
      qc.invalidateQueries({ queryKey: ['product', id, 'comparison'] });
    },
  });
}
export const useCreateRelease = (id: number) =>
  useReleaseMutation(id, (body: ReleaseInput) => api<Release>('POST', `/products/${id}/releases`, { body }));
export const useUpdateRelease = (id: number) =>
  useReleaseMutation(id, ({ releaseId, body }: { releaseId: number; body: ReleaseUpdate }) =>
    api<Release>('PATCH', `/products/${id}/releases/${releaseId}`, { body }));
export const useDeleteRelease = (id: number) =>
  useReleaseMutation(id, (releaseId: number) => api<void>('DELETE', `/products/${id}/releases/${releaseId}`));
