import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import AdminDashboard from './admin/dashboard';
import AdminFilesList from './admin/files-list';
import AdminStoragePoliciesList from './admin/storage-policies-list';
import AdminUsersList from './admin/users-list';
import Layout from './components/layout/Layout';
import MobileLayout from './mobile-components/MobileLayout';
import { useIsMobile } from './hooks/useIsMobile';
import Login from './pages/Login';
import Overview from './pages/Overview';
import RecycleBin from './pages/RecycleBin';
import Shares from './pages/Shares';
import Tasks from './pages/Tasks';
import Transfer from './pages/Transfer';
import FileShare from './pages/FileShare';
import FilesPage from './pages/files/FilesPage';

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
          <Route path="/admin">
            <Route index element={<Navigate to="/admin/dashboard" replace />} />
            <Route path="dashboard" element={isMobile ? <Navigate to="/overview" replace /> : <AdminDashboard />} />
            <Route path="users" element={isMobile ? <Navigate to="/overview" replace /> : <AdminUsersList />} />
            <Route path="files" element={isMobile ? <Navigate to="/overview" replace /> : <AdminFilesList />} />
            <Route path="storage-policies" element={isMobile ? <Navigate to="/overview" replace /> : <AdminStoragePoliciesList />} />
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
