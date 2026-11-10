// Короткие имена для типов из контракта. Сами типы генерируются:
//   npm run gen:api   (contracts/oko-openapi.yaml → src/api/schema.d.ts)
// Руками schema.d.ts не правим: поменялся контракт — перегенерировали.
import type { components } from './schema';

type S = components['schemas'];

export type User = S['User'];
export type CurrentUser = S['CurrentUser'];
export type Product = S['Product'];
export type ProductStatus = S['ProductStatus'];
export type ProductCreate = S['ProductCreate'];
export type ProductUpdate = S['ProductUpdate'];
export type ProductPage = S['ProductPage'];
export type Service = S['Service'];
export type ServiceCreate = S['ServiceCreate'];
export type ServiceUpdate = S['ServiceUpdate'];
export type Health = S['Health'];
export type HealthStatus = S['HealthStatus'];
export type ProductHealthStatus = S['ProductHealthStatus'];
export type ProductHealth = S['ProductHealth'];
export type ApiKey = S['ApiKey'];
export type ApiKeyIssued = S['ApiKeyIssued'];
export type KeyType = S['KeyType'];
export type OriginList = S['OriginList'];
export type WidgetSettings = S['WidgetSettings'];
export type WidgetSettingsInput = S['WidgetSettingsInput'];
export type RatingStats = S['RatingStats'];
export type Timeseries = S['Timeseries'];
export type Review = S['Review'];
export type ReviewPage = S['ReviewPage'];
export type Dashboard = S['Dashboard'];
export type DashboardProduct = S['DashboardProduct'];
export type DashboardService = S['DashboardService'];
export type Release = S['Release'];
export type ReleaseInput = S['ReleaseInput'];
export type ReleaseUpdate = S['ReleaseUpdate'];
export type ReleaseComparison = S['ReleaseComparison'];
export type Items<T> = { items: T[] };
