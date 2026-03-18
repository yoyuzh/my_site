import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { useAuth } from './auth/AuthProvider';
import Login from './pages/Login';
import Overview from './pages/Overview';
import Files from './pages/Files';
import School from './pages/School';
import Games from './pages/Games';

function AppRoutes() {
  const { ready, session } = useAuth();

  if (!ready) {
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
        <Route path="school" element={<School />} />
        <Route path="games" element={<Games />} />
      </Route>
      <Route
        path="*"
        element={<Navigate to={isAuthenticated ? '/overview' : '/login'} replace />}
      />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
