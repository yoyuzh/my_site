import React from 'react';
import { useLocation } from 'react-router-dom';
import AdminLayout from '../../components/AdminLayout';
import { Settings } from 'lucide-react';

const titleByPath: Record<string, string> = {
  group: '用户组',
  node: '存储节点',
  oauth: '第三方登录',
};

const AdminUnderConstruction: React.FC = () => {
  const location = useLocation();
  const path = location.pathname.split('/').pop() || '';
  const title = titleByPath[path] ?? '管理功能';

  return (
    <AdminLayout title={title}>
      <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
        <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
          <Settings size={32} />
        </div>
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">
          {title} 页面正在开发中
        </h3>
        <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
          该管理模块正在加紧构建中，敬请期待。
        </p>
      </div>
    </AdminLayout>
  );
};

export default AdminUnderConstruction;
