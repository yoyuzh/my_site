import React, { Suspense } from 'react';
import { BrowserRouter, HashRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';

import { useAuth } from '@/src/auth/AuthProvider';
import { FILE_SHARE_ROUTE_PREFIX } from '@/src/lib/file-share';
import { getTransferRouterMode, LEGACY_PUBLIC_TRANSFER_ROUTE, PUBLIC_TRANSFER_ROUTE } from '@/src/lib/transfer-links';

import { MobileLayout } from './mobile-components/MobileLayout';
import MobileLogin from './mobile-pages/MobileLogin';
import MobileOverview from './mobile-pages/MobileOverview';
import MobileFiles from './mobile-pages/MobileFiles';
import MobileTransfer from './mobile-pages/MobileTransfer';
import MobileFileShare from './mobile-pages/MobileFileShare';
import RecycleBin from './pages/RecycleBin';

function LegacyTransferRedirect() {
  const location = useLocation();
  return <Navigate to={`${PUBLIC_TRANSFER_ROUTE}${location.search}`} replace />;
}

function MobileAppRoutes() {
  const { ready, session } = useAuth();
  const location = useLocation();
  const isPublicTransferRoute = location.pathname === PUBLIC_TRANSFER_ROUTE || location.pathname === LEGACY_PUBLIC_TRANSFER_ROUTE;
  const isPublicFileShareRoute = location.pathname.startsWith(`${FILE_SHARE_ROUTE_PREFIX}/`);

  if (!ready && !isPublicTransferRoute && !isPublicFileShareRoute) {
    return (
      <div className="min-h-[100dvh] flex items-center justify-center bg-[#07101D] text-slate-300 flex-col gap-4">
        <span className="w-6 h-6 border-2 border-white/20 border-t-white rounded-full animate-spin" />
        <span className="text-sm">正在检查登录状态...</span>
      </div>
    );
  }

  const isAuthenticated = Boolean(session?.token);

  return (
    <Routes>
      <Route
        path={PUBLIC_TRANSFER_ROUTE}
        element={isAuthenticated ? <MobileLayout><MobileTransfer /></MobileLayout> : <MobileTransfer />}
      />
      <Route path={`${FILE_SHARE_ROUTE_PREFIX}/:token`} element={<MobileFileShare />} />
      <Route path={LEGACY_PUBLIC_TRANSFER_ROUTE} element={<LegacyTransferRedirect />} />
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/overview" replace /> : <MobileLogin />}
      />
      <Route
        path="/"
        element={isAuthenticated ? <MobileLayout /> : <Navigate to="/login" replace />}
      >
        <Route index element={<Navigate to="/overview" replace />} />
        <Route path="overview" element={<MobileOverview />} />
        <Route path="files" element={<MobileFiles />} />
        <Route path="recycle-bin" element={<RecycleBin />} />
        <Route path="games" element={<Navigate to="/overview" replace />} />
      </Route>

      <Route path="/games/:gameId" element={<Navigate to={isAuthenticated ? '/overview' : '/login'} replace />} />

      {/* Admin dashboard is not mobile-optimized in this phase yet, redirect to overview or login */}
      <Route
        path="/admin/*"
        element={isAuthenticated ? <Navigate to="/overview" replace /> : <Navigate to="/login" replace />}
      />
      
      <Route
        path="*"
        element={<Navigate to={isAuthenticated ? '/overview' : '/login'} replace />}
      />
    </Routes>
  );
}

export default function MobileApp() {
  const Router = getTransferRouterMode() === 'hash' ? HashRouter : BrowserRouter;

  return (
    <Router>
      <MobileAppRoutes />
    </Router>
  );
}
