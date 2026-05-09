import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ThemeProvider } from './hooks/useTheme';
import { useStoredSessionValidation } from './hooks/useStoredSessionValidation';
import { canAccessAdmin, getDefaultSignedInRoute } from './lib/session';
import Login from './pages/Login';
import Register from './pages/Register';
import FileShare from './pages/FileShare';

// Dashboard Pages
import Files from './pages/Files';
import Tasks from './pages/Tasks';
import Shares from './pages/Shares';
import SharedWithMe from './pages/SharedWithMe';
import RecycleBin from './pages/RecycleBin';
import TransferSend from './pages/TransferSend';
import TransferReceive from './pages/TransferReceive';
import AccountSettings from './pages/AccountSettings';
import OfflineDownloads from './pages/OfflineDownloads';
import DashboardUnderConstruction from './pages/DashboardUnderConstruction';

// Admin Pages
import AdminHome from './pages/admin/AdminHome';
import AdminUser from './pages/admin/AdminUser';
import AdminSetting from './pages/admin/AdminSetting';
import AdminPolicy from './pages/admin/AdminPolicy';
import AdminFile from './pages/admin/AdminFile';
import AdminBlob from './pages/admin/AdminBlob';
import AdminTask from './pages/admin/AdminTask';
import AdminShare from './pages/admin/AdminShare';
import AdminFileSystem from './pages/admin/AdminFileSystem';
import AdminUnderConstruction from './pages/admin/AdminUnderConstruction';

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
        <Router>
          <Routes>
            {/* Public Auth Routes */}
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            
            {/* Public Share Route */}
            <Route path="/share" element={<FileShare />} />
            <Route path="/share/:id" element={<FileShare />} />
            <Route path="/transfer/receive" element={<TransferReceive />} />
            
          {/* Dashboard Routes */}
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

          {/* Admin Routes */}
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
            <Route path="audits" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
            <Route path="group" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
            <Route path="node" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
            <Route path="oauth" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
            <Route path="*" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
          </Route>

          {/* Default Route */}
          <Route path="/" element={<HomeRedirect />} />
        </Routes>
      </Router>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export default App;
