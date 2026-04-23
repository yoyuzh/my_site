import React from 'react';
import { Server } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';

const AdminNode: React.FC = () => {
  return (
    <AdminLayout title="存储节点">
      <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
        <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
          <Server size={28} />
        </div>
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">后端暂未提供存储节点接口</h3>
        <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
          当前系统的治理接口已支持存储策略，但没有独立的“存储节点”读写接口，因此这个页面暂时只保留为能力占位。
        </p>
      </div>
    </AdminLayout>
  );
};

export default AdminNode;
