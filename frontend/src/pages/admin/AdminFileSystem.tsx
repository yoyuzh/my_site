import React from 'react';
import AdminLayout from '../../components/AdminLayout';
import { HardDrive, AlertTriangle, RefreshCw } from 'lucide-react';

const AdminFileSystem: React.FC = () => {
  return (
    <AdminLayout title="文件系统">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <div className="card-container p-6 animate-fade-in-up">
          <div className="flex items-center gap-4 mb-4">
            <div className="bg-brand-light/10 text-brand-light p-3 rounded-lg">
              <RefreshCw size={24} />
            </div>
            <div>
              <h3 className="font-bold text-text-primary-light dark:text-white">索引重建</h3>
              <p className="text-xs text-text-muted-light dark:text-text-muted-dark">重新扫描存储并同步数据库</p>
            </div>
          </div>
          <button className="btn-primary w-full text-sm py-2">开始扫描</button>
        </div>

        <div className="card-container p-6 animate-fade-in-up" style={{ animationDelay: '100ms' }}>
          <div className="flex items-center gap-4 mb-4">
            <div className="bg-red-500/10 text-red-500 p-3 rounded-lg">
              <AlertTriangle size={24} />
            </div>
            <div>
              <h3 className="font-bold text-text-primary-light dark:text-white">孤立文件清理</h3>
              <p className="text-xs text-text-muted-light dark:text-text-muted-dark">清理无引用的物理文件</p>
            </div>
          </div>
          <button className="bg-red-500 hover:bg-red-600 text-white font-semibold py-2 px-4 rounded-lg transition-colors w-full text-sm">扫描孤立文件</button>
        </div>

        <div className="card-container p-6 animate-fade-in-up" style={{ animationDelay: '200ms' }}>
          <div className="flex items-center gap-4 mb-4">
            <div className="bg-yellow-500/10 text-yellow-600 dark:text-yellow-500 p-3 rounded-lg">
              <HardDrive size={24} />
            </div>
            <div>
              <h3 className="font-bold text-text-primary-light dark:text-white">容量校准</h3>
              <p className="text-xs text-text-muted-light dark:text-text-muted-dark">重新计算所有用户的使用量</p>
            </div>
          </div>
          <button className="bg-white dark:bg-transparent border border-[#D9E3F2] dark:border-[#222233] text-text-primary-light dark:text-white font-semibold py-2 px-4 rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors w-full text-sm">开始校准</button>
        </div>
      </div>
      
      <div className="card-container p-8 animate-fade-in-up" style={{ animationDelay: '300ms' }}>
         <h3 className="text-lg font-bold text-text-primary-light dark:text-white mb-4">系统任务日志</h3>
         <div className="border border-dashed border-[#D9E3F2] dark:border-[#222233] rounded-lg p-6 flex flex-col items-center justify-center text-text-muted-light dark:text-text-muted-dark bg-[#F8FBFF] dark:bg-black/20">
            <p className="font-geist mb-2">没有任何运行中的文件系统任务</p>
            <p className="text-xs">点击上方按钮启动对应操作</p>
         </div>
      </div>
    </AdminLayout>
  );
};

export default AdminFileSystem;
