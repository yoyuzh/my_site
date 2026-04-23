import React from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { HardDrive, Cloud, FileText, Image as ImageIcon } from 'lucide-react';
import { useFavoriteFiles, useRecentFiles, useUserCapacity } from '../api/queries';
import { formatBytes, formatDateTime } from '../lib/format';

const Overview: React.FC = () => {
  const { data: capacity } = useUserCapacity();
  const { data: recentFiles } = useRecentFiles();
  const { data: favoriteFiles } = useFavoriteFiles();

  const stats = [
    {
      label: '可用存储空间',
      value: capacity ? formatBytes(capacity.availableBytes) : '-',
      sub: capacity
        ? `已用 ${formatBytes(capacity.usedBytes)} / 共 ${formatBytes(capacity.totalBytes)}`
        : '正在加载容量信息',
      icon: <HardDrive size={24} />,
      badge: capacity ? 'Live' : 'Loading',
    },
    {
      label: '最近文件',
      value: String(recentFiles?.length ?? 0),
      sub: recentFiles?.[0] ? `最后访问文件创建于 ${formatDateTime(recentFiles[0].createdAt)}` : '暂无最近文件',
      icon: <Cloud size={24} />,
    },
    {
      label: '收藏项',
      value: String(favoriteFiles?.filter((item) => item.favorite).length ?? 0),
      sub: '来自 /api/files/favorites',
      icon: <FileText size={24} />,
    },
    {
      label: '单文件上传上限',
      value: capacity ? formatBytes(capacity.maxUploadSizeBytes) : '-',
      sub: '来自 /api/user/capacity',
      icon: <ImageIcon size={24} />,
    },
  ];

  return (
    <DashboardLayout title="总览 Overview">
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
        {stats.map((stat, i) => (
          <div key={i} className="card-container p-6 flex flex-col justify-between h-40">
            <div className="flex justify-between items-start text-brand-light dark:text-brand-dark">
              {stat.icon}
              <span className="text-xs font-geist text-text-muted-light dark:text-text-muted-dark bg-black/5 dark:bg-white/5 px-2 py-1 rounded">
                {'badge' in stat ? stat.badge : 'Today'}
              </span>
            </div>
            <div>
              <p className="text-sm font-medium text-text-secondary-light dark:text-text-secondary-dark mb-1">{stat.label}</p>
              <h3 className="text-2xl font-bold text-text-primary-light dark:text-white font-funnel">{stat.value}</h3>
              <p className="text-xs text-text-muted-light dark:text-text-muted-dark mt-2 font-geist">{stat.sub}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8">
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-4">最近使用</h3>
        {recentFiles && recentFiles.length > 0 ? (
          <div className="card-container divide-y divide-[#D9E3F2] dark:divide-[#222233]">
            {recentFiles.slice(0, 5).map((file) => (
              <div key={file.id} className="flex items-center justify-between px-6 py-4 gap-4">
                <div className="min-w-0">
                  <p className="truncate font-medium text-text-primary-light dark:text-white">{file.filename}</p>
                  <p className="text-xs text-text-muted-light dark:text-text-muted-dark">{file.path}</p>
                </div>
                <div className="text-right text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  <p>{formatBytes(file.size)}</p>
                  <p className="text-xs">{formatDateTime(file.createdAt)}</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="card-container p-8 text-center text-text-muted-light dark:text-text-muted-dark">
            <p className="font-geist">暂无最近使用的文件</p>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};

export default Overview;
