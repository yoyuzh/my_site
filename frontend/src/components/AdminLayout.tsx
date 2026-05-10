import React from 'react';
import AdminShell from './admin/AdminShell';

interface AdminLayoutProps {
  children: React.ReactNode;
  title: string;
}

const AdminLayout: React.FC<AdminLayoutProps> = ({ children, title }) => {
  return <AdminShell title={title}>{children}</AdminShell>;
};

export default AdminLayout;
