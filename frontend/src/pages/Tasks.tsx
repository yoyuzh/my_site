import React, { useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useRemoteDownloads, useTasks } from '../api/queries';
import { formatDateTime } from '../lib/format';
import {
  getRemoteDownloadStatusLabel,
  getTaskStatusLabel,
  getTaskTypeLabel,
  readTaskPublicState,
  resolveRemoteDownloadStatus,
} from '../lib/tasks';
import type { BackgroundTask, RemoteDownloadListItem } from '../api/types';

const Tasks: React.FC = () => {
  const [page, setPage] = useState(1);
  const { data, isLoading, isError } = useTasks(page, 20);
  const { data: remoteDownloads } = useRemoteDownloads();

  const remoteDownloadsByBackgroundTaskId = useMemo(() => {
    const entries = remoteDownloads?.map((item) => [item.backgroundTaskId, item] as const) ?? [];
    return new Map<number, RemoteDownloadListItem>(
      entries.filter((entry): entry is readonly [number, RemoteDownloadListItem] => entry[0] != null),
    );
  }, [remoteDownloads]);

  function getResolvedTaskStatus(task: BackgroundTask) {
    if (task.type !== 'REMOTE_DOWNLOAD') {
      return task.status;
    }

    const remoteDownload = remoteDownloadsByBackgroundTaskId.get(task.id);
    const taskState = readTaskPublicState(task.publicStateJson);
    return resolveRemoteDownloadStatus({
      remoteStatus: remoteDownload?.status,
      taskStatus: task.status,
      phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    });
  }

  return (
    <DashboardLayout title="任务 Tasks">
      <div className="sidebar-glass flex h-full min-h-0 flex-col overflow-hidden p-4">
        {isLoading ? (
          <div className="flex flex-1 items-center justify-center p-10 text-center">
            <p className="text-text-secondary-light dark:text-text-secondary-dark">加载中...</p>
          </div>
        ) : isError ? (
          <div className="flex flex-1 items-center justify-center p-10 text-center text-red-500">
            任务列表加载失败
          </div>
        ) : data && data.items.length > 0 ? (
          <div className="flex flex-1 min-h-0 flex-col overflow-hidden">
            <div className="min-h-0 flex-1 overflow-y-auto divide-y divide-white/60 dark:divide-white/10">
              {data.items.map((task: BackgroundTask) => {
                const resolvedStatus = getResolvedTaskStatus(task);
                return (
                  <div
                    key={task.id}
                    className="flex flex-col gap-1.5 px-6 py-4 transition-colors hover:bg-white/35 dark:hover:bg-white/[0.04] lg:flex-row lg:items-center lg:justify-between"
                  >
                    <div>
                      <h3 className="font-bold text-text-primary-light dark:text-white">
                        {getTaskTypeLabel(task.type)}
                      </h3>
                      <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        状态 {task.type === 'REMOTE_DOWNLOAD'
                          ? getRemoteDownloadStatusLabel(resolvedStatus)
                          : getTaskStatusLabel(resolvedStatus)} · 创建于 {formatDateTime(task.createdAt)}
                      </p>
                    </div>
                    <div className="text-sm text-text-muted-light dark:text-text-muted-dark lg:text-right">
                      <p>ID #{task.id}</p>
                      <p>{task.errorMessage || task.correlationId || '无附加信息'}</p>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="flex items-center justify-between border-t border-white/45 px-6 py-4 text-sm text-text-secondary-light dark:border-white/8 dark:text-text-secondary-dark">
              <span>共 {data.pagination.total_items} 条任务</span>
              <div className="flex gap-2">
                <button
                  className="rounded-lg border border-white/45 bg-white/30 px-3 py-1 disabled:opacity-50 dark:border-white/10 dark:bg-white/[0.03]"
                  disabled={page <= 1}
                  onClick={() => setPage((current) => current - 1)}
                >
                  上一页
                </button>
                <button className="rounded-lg border border-brand-light bg-brand-light px-3 py-1 text-white dark:border-brand-dark dark:bg-brand-dark">
                  {page}
                </button>
                <button
                  className="rounded-lg border border-white/45 bg-white/30 px-3 py-1 disabled:opacity-50 dark:border-white/10 dark:bg-white/[0.03]"
                  disabled={page >= data.pagination.total_pages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center p-10 text-center">
            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-brand-light/10 text-brand-light">
              <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 11 3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
            </div>
            <h3 className="mb-2 text-xl font-bold text-text-primary-light dark:text-white">当前没有任务</h3>
            <p className="max-w-md text-text-secondary-light dark:text-text-secondary-dark font-geist">
              所有的上传、下载、离线下载或解压任务都会显示在这里。
            </p>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};

export default Tasks;
