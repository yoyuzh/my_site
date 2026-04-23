import React from 'react';
import { Shield } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';

const AdminGroup: React.FC = () => {
  return (
    <AdminLayout title="用户组">
      <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
        <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
          <Shield size={28} />
        </div>
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">后端暂未提供用户组接口</h3>
        <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
          当前后端只提供用户、文件、分享、任务、存储策略等治理接口，用户组管理接口尚未实现，因此这里不再展示伪数据。
        </p>
      </div>
    </AdminLayout>
  );
};

export default AdminGroup;
