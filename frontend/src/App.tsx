import { Navigate, Route, Routes } from 'react-router';
import { useCurrentUser } from './api/hooks';
import { ApiError } from './api/client';
import { Layout } from './components/Layout';
import { ErrorState, LoadingState } from './components/States';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProductCreatePage } from './pages/ProductCreatePage';
import { ProductPage } from './pages/product/ProductPage';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  const me = useCurrentUser();

  if (me.isPending) return <LoadingState fullHeight />;
  if (me.error) {
    if (me.error instanceof ApiError && me.error.status === 401) return <LoginPage />;
    return <ErrorState error={me.error} onRetry={() => me.refetch()} fullHeight />;
  }

  return (
    <Layout user={me.data}>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/products" element={<Navigate to="/" replace />} />
        <Route path="/products/new" element={<ProductCreatePage />} />
        <Route path="/products/:productId/:tab?" element={<ProductPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Layout>
  );
}
