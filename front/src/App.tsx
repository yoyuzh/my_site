import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import AdminDashboard from './admin/dashboard';
import AdminFilesList from './admin/files-list';
import AdminStoragePoliciesList from './admin/storage-policies-list';
import AdminUsersList from './admin/users-list';
import AdminLayout from './admin/AdminLayout';

// 新增占位页面
import AdminSettings from './admin/settings';
import AdminFilesystem from './admin/filesystem';
import AdminFileBlobs from './admin/fileblobs';
import AdminShares from './admin/shares';
import AdminTasks from './admin/tasks';
import AdminOAuthApps from './admin/oauthapps';

import Layout from './components/layout/Layout';
import MobileLayout from './mobile-components/MobileLayout';
import { useIsMobile } from './hooks/useIsMobile';
import Login from './account/pages/LoginPage';
import Overview from './workspace/pages/OverviewPage';
import FilesPage from './workspace/pages/FilesPage';
import RecycleBin from './workspace/pages/RecycleBinPage';
import Shares from './sharing/pages/SharesPage';
import FileShare from './sharing/pages/FileSharePage';
import Tasks from './common/pages/TasksPage';
import Transfer from './transfer/pages/TransferPage';

function AnimatedRoutes({ isMobile }: { isMobile: boolean }) {
  const location = useLocation();
  const AppLayout = isMobile ? MobileLayout : Layout;

  return (
    <AnimatePresence mode="wait">
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
          
          {/* 管理台路由重构 */}
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
    </AnimatePresence>
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
