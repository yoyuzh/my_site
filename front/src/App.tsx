import React, { Suspense } from 'react';
import { BrowserRouter, HashRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { useAuth } from './auth/AuthProvider';
import Login from './pages/Login';
import Overview from './pages/Overview';
import Files from './pages/Files';
import Transfer from './pages/Transfer';
import FileShare from './pages/FileShare';
import Games from './pages/Games';
import { FILE_SHARE_ROUTE_PREFIX } from './lib/file-share';
import {
  getTransferRouterMode,
  LEGACY_PUBLIC_TRANSFER_ROUTE,
  PUBLIC_TRANSFER_ROUTE,
} from './lib/transfer-links';

const PortalAdminApp = React.lazy(() => import('./admin/AdminApp'));

function LegacyTransferRedirect() {
  const location = useLocation();
  return <Navigate to={`${PUBLIC_TRANSFER_ROUTE}${location.search}`} replace />;
}

function AppRoutes() {
  const { ready, session } = useAuth();
  const location = useLocation();
  const isPublicTransferRoute = location.pathname === PUBLIC_TRANSFER_ROUTE || location.pathname === LEGACY_PUBLIC_TRANSFER_ROUTE;
  const isPublicFileShareRoute = location.pathname.startsWith(`${FILE_SHARE_ROUTE_PREFIX}/`);

  if (!ready && !isPublicTransferRoute && !isPublicFileShareRoute) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#07101D] text-slate-300">
        正在检查登录状态...
      </div>
    );
  }

  const isAuthenticated = Boolean(session?.token);

  return (
    <Routes>
      <Route
        path={PUBLIC_TRANSFER_ROUTE}
        element={isAuthenticated ? <Layout><Transfer /></Layout> : <Transfer />}
      />
      <Route path={`${FILE_SHARE_ROUTE_PREFIX}/:token`} element={<FileShare />} />
      <Route path={LEGACY_PUBLIC_TRANSFER_ROUTE} element={<LegacyTransferRedirect />} />
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/overview" replace /> : <Login />}
      />
      <Route
        path="/"
        element={isAuthenticated ? <Layout /> : <Navigate to="/login" replace />}
      >
        <Route index element={<Navigate to="/overview" replace />} />
        <Route path="overview" element={<Overview />} />
        <Route path="files" element={<Files />} />
        <Route path="games" element={<Games />} />
      </Route>
      <Route
        path="/admin/*"
        element={
          isAuthenticated ? (
            <Suspense
              fallback={
                <div className="min-h-screen flex items-center justify-center bg-white text-slate-700">
                  正在加载后台管理台...
                </div>
              }
            >
              <PortalAdminApp />
            </Suspense>
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="*"
        element={<Navigate to={isAuthenticated ? '/overview' : '/login'} replace />}
      />
    </Routes>
  );
}

export default function App() {
  const Router = getTransferRouterMode() === 'hash' ? HashRouter : BrowserRouter;

  return (
    <Router>
      <AppRoutes />
    </Router>
  );
}
