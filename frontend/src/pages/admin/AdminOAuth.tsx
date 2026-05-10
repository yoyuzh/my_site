import React from 'react';
import { Network } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';

const AdminOAuth: React.FC = () => {
  return (
    <AdminLayout title="第三方登录配置">
      <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
        <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
          <Network size={28} />
        </div>
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">后端暂未提供第三方登录应用接口</h3>
        <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
          当前页面仅保留能力占位。等后端补出第三方登录应用管理路由后，这里再切到真实数据。
        </p>
      </div>
    </AdminLayout>
  );
};

export default AdminOAuth;
