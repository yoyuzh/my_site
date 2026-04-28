import React, { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import DashboardLayout from '../components/DashboardLayout';
import { useRemoteDownloadDetail, useRemoteDownloads, useTasks } from '../api/queries';
import type { BackgroundTask, RemoteDownloadDetail } from '../api/types';
import CreateRemoteDownloadDialog from '../components/files/CreateRemoteDownloadDialog';
import OfflineDownloadTaskList from '../components/offline-downloads/OfflineDownloadTaskList';
import OfflineDownloadDetailPanel from '../components/offline-downloads/OfflineDownloadDetailPanel';
import { readTaskPublicState, resolveRemoteDownloadStatus } from '../lib/tasks';

const ACTIVE_REMOTE_DOWNLOAD_STATUSES = new Set([
  'PENDING',
  'SUBMITTED',
  'FETCHING_METADATA',
  'AWAITING_FILE_SELECTION',
  'DOWNLOADING',
  'IMPORTING',
]);

const OfflineDownloads: React.FC = () => {
  const queryClient = useQueryClient();
  const [selectedRemoteDownloadId, setSelectedRemoteDownloadId] = useState<number | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const { data: tasksData, isLoading: tasksLoading, isError: tasksError } = useTasks(1, 100);
  const {
    data: remoteDownloads,
    isLoading: remoteDownloadsLoading,
    isError: remoteDownloadsError,
  } = useRemoteDownloads();

  const remoteDownloadTaskMap = useMemo(() => {
    const entries =
      tasksData?.items
        .filter((task) => task.type === 'REMOTE_DOWNLOAD')
        .map((task) => [task.id, task] as const) ?? [];
    return new Map<number, BackgroundTask>(entries);
  }, [tasksData]);

  const getEffectiveStatus = (item: { status: string; backgroundTaskId: number | null }) => {
    const task = item.backgroundTaskId == null ? null : remoteDownloadTaskMap.get(item.backgroundTaskId) ?? null;
    const taskState = task ? readTaskPublicState(task.publicStateJson) : null;
    return resolveRemoteDownloadStatus({
      remoteStatus: item.status,
      taskStatus: task?.status,
      phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    });
  };

  const activeRemoteDownloads = useMemo(
    () => (remoteDownloads ?? []).filter((item) => ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(item))),
    [remoteDownloads, remoteDownloadTaskMap],
  );

  const historyOnly =
    !remoteDownloadsLoading && (remoteDownloads?.length ?? 0) > 0 && activeRemoteDownloads.length === 0;

  const { data: selectedRemoteDownload } = useRemoteDownloadDetail(selectedRemoteDownloadId);

  const selectedListItem = useMemo(
    () => remoteDownloads?.find((item) => item.id === selectedRemoteDownloadId) ?? null,
    [remoteDownloads, selectedRemoteDownloadId],
  );

  const selectedTask = useMemo(() => {
    const backgroundTaskId = selectedRemoteDownload?.backgroundTaskId ?? selectedListItem?.backgroundTaskId ?? null;
    if (backgroundTaskId == null) {
      return null;
    }
    return remoteDownloadTaskMap.get(backgroundTaskId) ?? null;
  }, [remoteDownloadTaskMap, selectedListItem, selectedRemoteDownload]);

  useEffect(() => {
    if (!remoteDownloads || remoteDownloads.length === 0) {
      if (selectedRemoteDownloadId != null) {
        setSelectedRemoteDownloadId(null);
      }
      return;
    }

    if (
      selectedRemoteDownloadId != null &&
      !remoteDownloads.some((item) => item.id === selectedRemoteDownloadId)
    ) {
      setSelectedRemoteDownloadId(null);
    }
  }, [remoteDownloads, remoteDownloadTaskMap, selectedRemoteDownloadId]);

  const handleCreated = (detail: RemoteDownloadDetail) => {
    void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
    setSelectedRemoteDownloadId(detail.id);
  };

  const handleCancelled = (detail: RemoteDownloadDetail) => {
    const nextActive = (remoteDownloads ?? []).find(
      (item) => item.id !== detail.id && ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(item)),
    );
    setSelectedRemoteDownloadId(nextActive?.id ?? detail.id);
  };

  const isLoading = tasksLoading || remoteDownloadsLoading;
  const hasLoadError = tasksError || remoteDownloadsError;

  return (
    <DashboardLayout title="离线下载 Offline Downloads">
      <div className="sidebar-glass flex h-full min-h-0 flex-col overflow-hidden p-4">
        <div className="glass-tray flex items-center justify-between px-6 py-4">
          <div>
            <h2 className="text-xl font-bold text-text-primary-light dark:text-white">离线下载</h2>
            <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
              管理服务端后台下载任务，支持 HTTP、磁力链接和种子文件
            </p>
            {!isLoading && remoteDownloads ? (
              <p className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                活跃任务 {activeRemoteDownloads.length} 个，历史记录 {remoteDownloads.length - activeRemoteDownloads.length} 个
              </p>
            ) : null}
          </div>
          <button
            onClick={() => setCreateDialogOpen(true)}
            className="flex items-center gap-2 rounded-lg bg-brand-light px-4 py-2 text-sm font-bold text-white transition-all hover:bg-brand-light/90 hover:shadow-lg active:scale-95 dark:bg-brand-dark"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M5 12h14" />
              <path d="M12 5v14" />
            </svg>
            新建离线下载
          </button>
        </div>

        {isLoading ? (
          <div className="glass-tray mt-4 flex flex-1 items-center justify-center">
            <div className="text-center">
              <div className="mb-4 inline-block h-8 w-8 animate-spin rounded-full border-4 border-brand-light border-t-transparent" />
              <p className="text-text-secondary-light dark:text-text-secondary-dark">加载中...</p>
            </div>
          </div>
        ) : hasLoadError ? (
          <div className="glass-tray mt-4 flex flex-1 items-center justify-center p-10">
            <div className="text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-100 text-red-500">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="32"
                  height="32"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-bold text-text-primary-light dark:text-white">加载失败</h3>
              <p className="mb-4 text-text-secondary-light dark:text-text-secondary-dark">
                无法获取离线下载列表，请稍后重试。
              </p>
              <button
                onClick={() => {
                  void queryClient.invalidateQueries({ queryKey: ['tasks'] });
                  void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
                }}
                className="rounded-lg border border-[#D9E3F2] px-4 py-2 text-sm font-medium hover:bg-gray-50 dark:border-[#222233] dark:hover:bg-white/5"
              >
                点击重试
              </button>
            </div>
          </div>
        ) : (remoteDownloads?.length ?? 0) > 0 ? (
          <div className="glass-tray mt-4 flex flex-1 min-h-0 overflow-hidden p-0">
            <div className="w-80">
              <OfflineDownloadTaskList
                remoteDownloads={remoteDownloads ?? []}
                taskMap={remoteDownloadTaskMap}
                selectedRemoteDownloadId={selectedRemoteDownloadId}
                onSelectTask={setSelectedRemoteDownloadId}
              />
            </div>
            <div className="w-px shrink-0 bg-[#D4DEEC] dark:bg-white/10" />
            <div className="flex-1 bg-white/20 dark:bg-white/[0.02]">
              <OfflineDownloadDetailPanel
                task={selectedTask}
                remoteDownload={selectedRemoteDownload ?? null}
                onCancelled={handleCancelled}
              />
            </div>
          </div>
        ) : (
          <div className="glass-tray mt-4 flex flex-1 items-center justify-center p-10 text-center">
            <div className="max-w-md">
              <div className="mx-auto mb-6 flex h-24 w-24 items-center justify-center rounded-full bg-brand-light/10 text-brand-light">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="48"
                  height="48"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="7 10 12 15 17 10" />
                  <line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              </div>
              <h3 className="mb-3 text-2xl font-bold text-text-primary-light dark:text-white">
                暂无离线下载任务
              </h3>
              <p className="mb-8 text-text-secondary-light dark:text-text-secondary-dark font-geist">
                您可以创建离线下载任务，服务端会在后台为您下载文件并自动导入到您的存储空间。
              </p>
              <button
                onClick={() => setCreateDialogOpen(true)}
                className="rounded-xl bg-brand-light px-8 py-3 font-bold text-white shadow-lg transition-all hover:bg-brand-light/90 hover:shadow-brand-light/20 active:scale-95 dark:bg-brand-dark"
              >
                立即创建任务
              </button>
            </div>
          </div>
        )}
      </div>

      <CreateRemoteDownloadDialog
        open={createDialogOpen}
        defaultPath="/downloads"
        onClose={() => setCreateDialogOpen(false)}
        onCreated={handleCreated}
      />
    </DashboardLayout>
  );
};

export default OfflineDownloads;
