import { Suspense, lazy, useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'motion/react';
import { useIsMobile } from './hooks/useIsMobile';
import { useSessionRuntime } from './hooks/use-session-runtime';
import { ApiError } from './lib/api';
import { canAccessAdmin } from './lib/session';
import { getAdminSummary } from './operations-admin/api/core/admin';

const Layout = lazy(() => import('./components/layout/Layout'));
const MobileLayout = lazy(() => import('./mobile-components/MobileLayout'));
const Login = lazy(() => import('./account/pages/LoginPage'));
const Overview = lazy(() => import('./workspace/pages/OverviewPage'));
const FilesPage = lazy(() => import('./workspace/pages/FilesPage'));
const RecycleBin = lazy(() => import('./workspace/pages/RecycleBinPage'));
const Shares = lazy(() => import('./sharing/pages/SharesPage'));
const FileShare = lazy(() => import('./sharing/pages/FileSharePage'));
const Tasks = lazy(() => import('./common/pages/TasksPage'));
const Transfer = lazy(() => import('./transfer/pages/TransferPage'));
const AdminLayout = lazy(() => import('./operations-admin/AdminLayout'));
const AdminDashboard = lazy(() => import('./operations-admin/pages/overview'));
const AdminSettings = lazy(() => import('./operations-admin/pages/settings'));
const AdminFilesystem = lazy(() => import('./operations-admin/pages/settings/filesystem'));
const AdminStoragePoliciesList = lazy(() => import('./operations-admin/pages/settings/storage-policies'));
const AdminUsersList = lazy(() => import('./operations-admin/pages/governance/users'));
const AdminFilesList = lazy(() => import('./operations-admin/pages/governance/files'));
const AdminFileBlobs = lazy(() => import('./operations-admin/pages/governance/file-blobs'));
const AdminShares = lazy(() => import('./operations-admin/pages/governance/shares'));
const AdminTasks = lazy(() => import('./operations-admin/pages/monitoring/tasks'));
const AdminAudits = lazy(() => import('./operations-admin/pages/monitoring/audits'));
const AdminOAuthApps = lazy(() => import('./operations-admin/pages/settings/oauth-apps'));

type AdminGateState =
  | { status: 'checking' }
  | { status: 'allowed' }
  | { status: 'denied' }
  | { status: 'error'; message: string };

function AnimatedRoutes({ isMobile }: { isMobile: boolean }) {
  const location = useLocation();
  const AppLayout = isMobile ? MobileLayout : Layout;

  return (
    <AnimatePresence mode="wait">
      <Suspense fallback={<RouteLoadingFallback />}>
        <Routes location={location}>
          <Route path="/login" element={<Login />} />
          <Route path="/share/:token" element={<FileShare />} />
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/overview" replace />} />
            <Route path="/overview" element={<Overview />} />
            <Route path="/files" element={<FilesPage />} />
            <Route path="/tasks" element={<Tasks />} />
            <Route path="/shares" element={<Shares />} />
            <Route path="/recycle-bin" element={<RecycleBin />} />
            <Route path="/transfer" element={<Transfer />} />

            <Route path="/admin" element={<AdminRouteGate isMobile={isMobile} />}>
              <Route index element={<Navigate to="dashboard" replace />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="settings" element={<AdminSettings />} />
              <Route path="filesystem" element={<AdminFilesystem />} />
              <Route path="storage-policies" element={<AdminStoragePoliciesList />} />
              <Route path="users" element={<AdminUsersList />} />
              <Route path="files" element={<AdminFilesList />} />
              <Route path="file-blobs" element={<AdminFileBlobs />} />
              <Route path="shares" element={<AdminShares />} />
              <Route path="tasks" element={<AdminTasks />} />
              <Route path="audits" element={<AdminAudits />} />
              <Route path="oauth-apps" element={<AdminOAuthApps />} />
            </Route>

            <Route path="*" element={<Navigate to="/overview" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </AnimatePresence>
  );
}

function AdminRouteGate({ isMobile }: { isMobile: boolean }) {
  const { session } = useSessionRuntime();
  const [state, setState] = useState<AdminGateState>({ status: 'checking' });

  useEffect(() => {
    let cancelled = false;

    if (isMobile || !session || !canAccessAdmin(session.user.role)) {
      setState({ status: 'denied' });
      return () => {
        cancelled = true;
      };
    }

    setState({ status: 'checking' });

    void getAdminSummary()
      .then(() => {
        if (!cancelled) {
          setState({ status: 'allowed' });
        }
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }

        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          setState({ status: 'denied' });
          return;
        }

        setState({
          status: 'error',
          message: error instanceof Error ? error.message : '管理员权限校验失败',
        });
      });

    return () => {
      cancelled = true;
    };
  }, [isMobile, session?.accessToken, session?.user.role]);

  if (isMobile) {
    return <Navigate to="/overview" replace />;
  }

  if (!session) {
    return <Navigate to="/login" replace />;
  }

  if (!canAccessAdmin(session.user.role) || state.status === 'denied') {
    return <Navigate to="/overview" replace />;
  }

  if (state.status === 'error') {
    return <RouteErrorState message={state.message} />;
  }

  if (state.status !== 'allowed') {
    return <RouteLoadingFallback label="Checking admin access..." />;
  }

  return <AdminLayout />;
}

function RouteLoadingFallback({ label = 'Loading...' }: { label?: string }) {
  return (
    <div className="flex h-screen w-full items-center justify-center bg-aurora text-gray-900 dark:text-gray-100">
      <div className="rounded-lg border border-white/20 bg-white/40 px-4 py-3 text-sm font-black uppercase tracking-[0.2em] shadow-lg backdrop-blur-xl dark:bg-black/30">
        {label}
      </div>
    </div>
  );
}

function RouteErrorState({ message }: { message: string }) {
  return (
    <div className="flex h-screen w-full items-center justify-center bg-aurora px-6 text-gray-900 dark:text-gray-100">
      <div className="max-w-md rounded-lg border border-red-500/20 bg-white/40 px-6 py-5 text-center shadow-lg backdrop-blur-xl dark:bg-black/30">
        <div className="text-xs font-black uppercase tracking-[0.3em] text-red-500">Admin Access Check Failed</div>
        <div className="mt-3 text-sm font-bold opacity-80">{message}</div>
      </div>
    </div>
  );
}

export default function App() {
  const isMobile = useIsMobile();

  return (
    <BrowserRouter>
      <AnimatedRoutes isMobile={isMobile} />
    </BrowserRouter>
  );
}
