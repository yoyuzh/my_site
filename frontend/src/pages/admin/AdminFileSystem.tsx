import React from 'react';
import AdminLayout from '../../components/AdminLayout';
import { HardDrive, AlertTriangle, RefreshCw } from 'lucide-react';
import { useAdminFilesystem } from '../../api/queries';
import { formatBytes } from '../../lib/format';

function formatRuntimeValue(value: string) {
  const labels: Record<string, string> = {
    LOCAL: '本地存储',
    S3_COMPATIBLE: 'S3 兼容存储',
    OSS_SDK: 'OSS SDK',
    MEMORY: '内存',
    REDIS: 'Redis',
  };
  return labels[value] ?? value;
}

const AdminFileSystem: React.FC = () => {
  const { data, isLoading, isError } = useAdminFilesystem();

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
          <button className="btn-primary w-full text-sm py-2 disabled:opacity-50 disabled:cursor-not-allowed" disabled title="后端暂未提供文件索引扫描接口">开始扫描</button>
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
          <button className="bg-red-500 hover:bg-red-600 text-white font-semibold py-2 px-4 rounded-lg transition-colors w-full text-sm disabled:opacity-50 disabled:cursor-not-allowed" disabled title="后端暂未提供孤立文件清理接口">扫描孤立文件</button>
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
          <button className="admin-secondary-button text-text-primary-light dark:text-text-primary-dark font-semibold py-2 px-4 rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors w-full text-sm disabled:opacity-50 disabled:cursor-not-allowed" disabled title="后端暂未提供容量校准接口">开始校准</button>
        </div>
      </div>
      
      <div className="card-container p-8 animate-fade-in-up" style={{ animationDelay: '300ms' }}>
         <h3 className="text-lg font-bold text-text-primary-light dark:text-white mb-4">文件系统快照</h3>
         {isLoading ? (
           <div className="p-6 text-center text-text-muted-light">加载中...</div>
         ) : isError || !data ? (
           <div className="p-6 text-center text-red-500">加载失败</div>
         ) : (
           <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
             <div className="rounded-lg admin-muted-panel p-4">
               <p className="font-semibold text-text-primary-light dark:text-white mb-2">存储概览</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">存储提供方：{formatRuntimeValue(data.overview.storageProvider)}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">文件：{data.overview.totalFiles}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">Blob：{data.overview.totalBlobs}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">实体：{data.overview.totalEntities}</p>
             </div>
             <div className="rounded-lg admin-muted-panel p-4">
               <p className="font-semibold text-text-primary-light dark:text-white mb-2">上传能力</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">代理上传：{data.upload.proxyUpload ? '支持' : '不支持'}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">直传：{data.upload.directSingleUpload ? '支持' : '不支持'}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">分片直传：{data.upload.directMultipartUpload ? '支持' : '不支持'}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">最大文件：{formatBytes(data.upload.effectiveMaxFileSizeBytes)}</p>
             </div>
             <div className="rounded-lg admin-muted-panel p-4">
               <p className="font-semibold text-text-primary-light dark:text-white mb-2">媒体处理</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">元数据提取：{data.mediaProcessing.metadataExtractionEnabled ? '开启' : '关闭'}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">原生缩略图：{data.mediaProcessing.nativeThumbnailSupport ? '支持' : '不支持'}</p>
             </div>
             <div className="rounded-lg admin-muted-panel p-4">
               <p className="font-semibold text-text-primary-light dark:text-white mb-2">缓存与 WebDAV</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">缓存后端：{formatRuntimeValue(data.cache.backend)}</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">文件列表 TTL：{data.cache.filesListTtlSeconds}s</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">目录版本 TTL：{data.cache.directoryVersionTtlSeconds}s</p>
               <p className="text-text-secondary-light dark:text-text-secondary-dark">WebDAV：{data.webdav.enabled ? '开启' : '关闭'}</p>
             </div>
           </div>
         )}
      </div>
    </AdminLayout>
  );
};

export default AdminFileSystem;
