import React from 'react';
import { Network } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';

const AdminOAuth: React.FC = () => {
  return (
    <AdminLayout title="OAuth配置">
      <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
        <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
          <Network size={28} />
        </div>
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">后端暂未提供 OAuth 应用接口</h3>
        <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
          前端已去掉原先 Cloudreve 风格的假列表。等 backend 补出 OAuth 应用管理路由后，这个页面再切到真实数据。
        </p>
      </div>
    </AdminLayout>
  );
};

export default AdminOAuth;
