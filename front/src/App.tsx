import { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'motion/react';
import { useIsMobile } from './hooks/useIsMobile';

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
const AdminLayout = lazy(() => import('./admin/AdminLayout'));
const AdminDashboard = lazy(() => import('./admin/dashboard'));
const AdminSettings = lazy(() => import('./admin/settings'));
const AdminFilesystem = lazy(() => import('./admin/filesystem'));
const AdminStoragePoliciesList = lazy(() => import('./admin/storage-policies-list'));
const AdminUsersList = lazy(() => import('./admin/users-list'));
const AdminFilesList = lazy(() => import('./admin/files-list'));
const AdminFileBlobs = lazy(() => import('./admin/fileblobs'));
const AdminShares = lazy(() => import('./admin/shares'));
const AdminTasks = lazy(() => import('./admin/tasks'));
const AdminOAuthApps = lazy(() => import('./admin/oauthapps'));

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

            <Route path="/admin" element={isMobile ? <Navigate to="/overview" replace /> : <AdminLayout />}>
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
              <Route path="oauth-apps" element={<AdminOAuthApps />} />
            </Route>

            <Route path="*" element={<Navigate to="/overview" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </AnimatePresence>
  );
}

function RouteLoadingFallback() {
  return (
    <div className="flex h-screen w-full items-center justify-center bg-aurora text-gray-900 dark:text-gray-100">
      <div className="rounded-lg border border-white/20 bg-white/40 px-4 py-3 text-sm font-black uppercase tracking-[0.2em] shadow-lg backdrop-blur-xl dark:bg-black/30">
        Loading...
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
