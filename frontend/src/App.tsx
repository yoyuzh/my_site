import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ThemeProvider } from './hooks/useTheme';
import { canAccessAdmin, getSession } from './lib/session';
import Login from './pages/Login';
import Register from './pages/Register';
import FileShare from './pages/FileShare';

// Dashboard Pages
import Files from './pages/Files';
import Tasks from './pages/Tasks';
import Shares from './pages/Shares';
import RecycleBin from './pages/RecycleBin';
import TransferSend from './pages/TransferSend';
import TransferReceive from './pages/TransferReceive';

// Admin Pages
import AdminHome from './pages/admin/AdminHome';
import AdminUser from './pages/admin/AdminUser';
import AdminSetting from './pages/admin/AdminSetting';
import AdminGroup from './pages/admin/AdminGroup';
import AdminPolicy from './pages/admin/AdminPolicy';
import AdminNode from './pages/admin/AdminNode';
import AdminOAuth from './pages/admin/AdminOAuth';
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

function RequireAuth({ children }: { children: JSX.Element }) {
  const location = useLocation();
  const session = getSession();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const session = getSession();

  if (!session) {
    return <Navigate to="/login" replace />;
  }

  if (!canAccessAdmin(session.user.role)) {
    return <Navigate to="/dashboard/files" replace />;
  }

  return children;
}

function HomeRedirect() {
  const session = getSession();
  if (!session) return <Navigate to="/login" replace />;
  return <Navigate to="/dashboard/files" replace />;
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
            <Route path="/share/:id" element={<FileShare />} />
            <Route path="/transfer/receive" element={<TransferReceive />} />
            
          {/* Dashboard Routes */}
          <Route path="/dashboard">
            <Route index element={<Navigate to="files" replace />} />
            <Route path="files" element={<RequireAuth><Files /></RequireAuth>} />
            <Route path="tasks" element={<RequireAuth><Tasks /></RequireAuth>} />
            <Route path="shares" element={<RequireAuth><Shares /></RequireAuth>} />
            <Route path="recycle-bin" element={<RequireAuth><RecycleBin /></RequireAuth>} />
            <Route path="transfer-send" element={<RequireAuth><TransferSend /></RequireAuth>} />
          </Route>

          {/* Admin Routes */}
          <Route path="/admin">
            <Route index element={<Navigate to="home" replace />} />
            <Route path="home" element={<RequireAdmin><AdminHome /></RequireAdmin>} />
            <Route path="settings" element={<RequireAdmin><AdminSetting /></RequireAdmin>} />
            <Route path="user" element={<RequireAdmin><AdminUser /></RequireAdmin>} />
            
            {/* Cloudreve Admin Routes */}
            <Route path="group" element={<RequireAdmin><AdminGroup /></RequireAdmin>} />
            <Route path="policy" element={<RequireAdmin><AdminPolicy /></RequireAdmin>} />
            <Route path="node" element={<RequireAdmin><AdminNode /></RequireAdmin>} />
            <Route path="oauth" element={<RequireAdmin><AdminOAuth /></RequireAdmin>} />
            <Route path="file" element={<RequireAdmin><AdminFile /></RequireAdmin>} />
            <Route path="blob" element={<RequireAdmin><AdminBlob /></RequireAdmin>} />
            <Route path="task" element={<RequireAdmin><AdminTask /></RequireAdmin>} />
            <Route path="share" element={<RequireAdmin><AdminShare /></RequireAdmin>} />
            <Route path="filesystem" element={<RequireAdmin><AdminFileSystem /></RequireAdmin>} />

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
