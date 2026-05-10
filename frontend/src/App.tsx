import { lazy, Suspense } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ThemeProvider } from './hooks/useTheme';
import { useStoredSessionValidation } from './hooks/useStoredSessionValidation';
import { canAccessAdmin, getDefaultSignedInRoute } from './lib/session';
import Login from './pages/Login';
import Register from './pages/Register';
import Files from './pages/Files';

const FileShare = lazy(() => import('./pages/FileShare'));
const TransferReceive = lazy(() => import('./pages/TransferReceive'));

const Tasks = lazy(() => import('./pages/Tasks'));
const Shares = lazy(() => import('./pages/Shares'));
const SharedWithMe = lazy(() => import('./pages/SharedWithMe'));
const RecycleBin = lazy(() => import('./pages/RecycleBin'));
const TransferSend = lazy(() => import('./pages/TransferSend'));
const AccountSettings = lazy(() => import('./pages/AccountSettings'));
const OfflineDownloads = lazy(() => import('./pages/OfflineDownloads'));
const DashboardUnderConstruction = lazy(() => import('./pages/DashboardUnderConstruction'));

const AdminHome = lazy(() => import('./pages/admin/AdminHome'));
const AdminUser = lazy(() => import('./pages/admin/AdminUser'));
const AdminSetting = lazy(() => import('./pages/admin/AdminSetting'));
const AdminPolicy = lazy(() => import('./pages/admin/AdminPolicy'));
const AdminFile = lazy(() => import('./pages/admin/AdminFile'));
const AdminBlob = lazy(() => import('./pages/admin/AdminBlob'));
const AdminTask = lazy(() => import('./pages/admin/AdminTask'));
const AdminShare = lazy(() => import('./pages/admin/AdminShare'));
const AdminFileSystem = lazy(() => import('./pages/admin/AdminFileSystem'));
const AdminAudit = lazy(() => import('./pages/admin/AdminAudit'));
const AdminUnderConstruction = lazy(() => import('./pages/admin/AdminUnderConstruction'));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

function AuthCheckFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-light px-6 text-sm font-medium text-slate-500 dark:bg-bg-dark dark:text-slate-400">
      正在校验登录状态...
    </div>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const location = useLocation();
  const { status } = useStoredSessionValidation();

  if (status === 'checking') {
    return <AuthCheckFallback />;
  }

  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }

  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const location = useLocation();
  const { status, session } = useStoredSessionValidation();

  if (status === 'checking') {
    return <AuthCheckFallback />;
  }

  if (status !== 'authenticated' || !session) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }

  if (!canAccessAdmin(session.user.role)) {
    return <Navigate to="/dashboard/files" replace />;
  }

  return children;
}

function HomeRedirect() {
  const { status, session } = useStoredSessionValidation();
  if (status === 'checking') return <AuthCheckFallback />;
  if (status !== 'authenticated' || !session) return <Navigate to="/login" replace />;
  return <Navigate to={getDefaultSignedInRoute(session.user.role)} replace />;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <Router future={{ v7_startTransition: true }}>
          <Suspense fallback={null}>
            <Routes>
              {/* 公共登录路由 */}
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />

              {/* 公开访问路由 */}
              <Route path="/share" element={<FileShare />} />
              <Route path="/share/:id" element={<FileShare />} />
              <Route path="/transfer/receive" element={<TransferReceive />} />

              {/* 用户工作台路由 */}
              <Route path="/dashboard">
                <Route index element={<Navigate to="files" replace />} />
                <Route path="files" element={<RequireAuth><Files key="files-directory" /></RequireAuth>} />
                <Route path="images" element={<RequireAuth><Files key="files-image" mediaCategory="image" /></RequireAuth>} />
                <Route path="videos" element={<RequireAuth><Files key="files-video" mediaCategory="video" /></RequireAuth>} />
                <Route path="music" element={<RequireAuth><Files key="files-audio" mediaCategory="audio" /></RequireAuth>} />
                <Route path="documents" element={<RequireAuth><Files key="files-document" mediaCategory="document" /></RequireAuth>} />
                <Route path="shared-with-me" element={<RequireAuth><SharedWithMe /></RequireAuth>} />
                <Route path="tasks" element={<RequireAuth><Tasks /></RequireAuth>} />
                <Route path="shares" element={<RequireAuth><Shares /></RequireAuth>} />
                <Route path="recycle-bin" element={<RequireAuth><RecycleBin /></RequireAuth>} />
                <Route path="connections" element={<RequireAuth><DashboardUnderConstruction /></RequireAuth>} />
                <Route path="offline-downloads" element={<RequireAuth><OfflineDownloads /></RequireAuth>} />
                <Route path="transfer-send" element={<RequireAuth><TransferSend /></RequireAuth>} />
                <Route path="settings" element={<RequireAuth><AccountSettings /></RequireAuth>} />
              </Route>

              {/* 管理后台路由 */}
              <Route path="/admin">
                <Route index element={<Navigate to="home" replace />} />
                <Route path="home" element={<RequireAdmin><AdminHome /></RequireAdmin>} />
                <Route path="system" element={<RequireAdmin><AdminFileSystem /></RequireAdmin>} />
                <Route path="config" element={<RequireAdmin><AdminSetting /></RequireAdmin>} />
                <Route path="settings" element={<Navigate to="/admin/config" replace />} />
                <Route path="users" element={<RequireAdmin><AdminUser /></RequireAdmin>} />
                <Route path="user" element={<Navigate to="/admin/users" replace />} />
                <Route path="storage-policies" element={<RequireAdmin><AdminPolicy /></RequireAdmin>} />
                <Route path="policy" element={<Navigate to="/admin/storage-policies" replace />} />
                <Route path="files" element={<RequireAdmin><AdminFile /></RequireAdmin>} />
                <Route path="file" element={<Navigate to="/admin/files" replace />} />
                <Route path="file-blobs" element={<RequireAdmin><AdminBlob /></RequireAdmin>} />
                <Route path="blob" element={<Navigate to="/admin/file-blobs" replace />} />
                <Route path="tasks" element={<RequireAdmin><AdminTask /></RequireAdmin>} />
                <Route path="task" element={<Navigate to="/admin/tasks" replace />} />
                <Route path="shares" element={<RequireAdmin><AdminShare /></RequireAdmin>} />
                <Route path="share" element={<Navigate to="/admin/shares" replace />} />
                <Route path="filesystem" element={<Navigate to="/admin/system" replace />} />
                <Route path="audits" element={<RequireAdmin><AdminAudit /></RequireAdmin>} />
                <Route path="group" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
                <Route path="node" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
                <Route path="oauth" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
                <Route path="*" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
              </Route>

              {/* 默认入口 */}
              <Route path="/" element={<HomeRedirect />} />
            </Routes>
          </Suspense>
        </Router>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export default App;
