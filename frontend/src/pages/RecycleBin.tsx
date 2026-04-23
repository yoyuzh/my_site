import React, { useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation } from '@tanstack/react-query';
import { useRecycleBin } from '../api/queries';
import { formatBytes, formatDateTime } from '../lib/format';
import { restoreRecycleBinItem } from '../lib/files';

const RecycleBin: React.FC = () => {
  const [page, setPage] = useState(1);
  const { data, isLoading, isError, refetch } = useRecycleBin(page, 20);
  const restoreMutation = useMutation({
    mutationFn: restoreRecycleBinItem,
    onSuccess: () => void refetch(),
  });

  return (
    <DashboardLayout title="回收站 Recycle Bin">
      {isLoading ? (
        <div className="card-container p-10 text-center">加载中...</div>
      ) : isError ? (
        <div className="card-container p-10 text-center text-red-500">回收站加载失败</div>
      ) : data && data.items.length > 0 ? (
        <div className="card-container divide-y divide-[#D9E3F2] dark:divide-[#222233]">
          {data.items.map((item) => (
            <div key={item.id} className="px-6 py-5 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <h3 className="font-bold text-text-primary-light dark:text-white">{item.filename}</h3>
                <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  删除于 {formatDateTime(item.deletedAt)} · 将在 {formatDateTime(item.expiresAt)} 自动清理
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-sm text-text-secondary-light dark:text-text-secondary-dark">{formatBytes(item.size)}</span>
                <button
                  className="btn-primary px-4 py-2 text-sm"
                  disabled={restoreMutation.isPending}
                  onClick={() => restoreMutation.mutate(item.id)}
                >
                  恢复
                </button>
              </div>
            </div>
          ))}
          <div className="px-6 py-4 flex items-center justify-between text-sm text-text-secondary-light dark:text-text-secondary-dark">
            <span>共 {data.pagination.total_items} 项</span>
            <div className="flex gap-2">
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</button>
              <button className="px-3 py-1 border rounded bg-brand-light text-white border-brand-light">{page}</button>
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page >= data.pagination.total_pages} onClick={() => setPage((current) => current + 1)}>下一页</button>
            </div>
          </div>
        </div>
      ) : (
        <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
          <div className="w-16 h-16 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/></svg>
          </div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">回收站为空</h3>
          <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
            被删除的文件会暂时保留在这里。你可以在它们被自动清除前恢复。
          </p>
        </div>
      )}
    </DashboardLayout>
  );
};

export default RecycleBin;
