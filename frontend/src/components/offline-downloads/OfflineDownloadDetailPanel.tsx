import React, { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { formatBytes } from '../../lib/format';
import { cancelRemoteDownload, selectRemoteDownloadFiles } from '../../lib/remote-downloads';
import {
  getRemoteDownloadPhaseLabel,
  getRemoteDownloadStatusLabel,
  getRemoteDownloadSourceLabel,
  getTaskProgress,
  isRemoteDownloadTerminalStatus,
  readTaskProgressSnapshot,
  readTaskPublicState,
  resolveRemoteDownloadPhase,
  resolveRemoteDownloadStatus,
} from '../../lib/tasks';
import type { RemoteDownloadDetail, TaskProgress, BackgroundTask } from '../../api/types';

interface OfflineDownloadDetailPanelProps {
  task: BackgroundTask | null;
  remoteDownload: RemoteDownloadDetail | null;
  onCancelled?: (detail: RemoteDownloadDetail) => void;
}

const OfflineDownloadDetailPanel: React.FC<OfflineDownloadDetailPanelProps> = ({
  task,
  remoteDownload,
  onCancelled,
}) => {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<TaskProgress | null>(null);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressError, setProgressError] = useState<string | null>(null);
  const [selectedFileKeys, setSelectedFileKeys] = useState<string[]>([]);

  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelRemoteDownload(id),
    onSuccess: (detail) => {
      queryClient.setQueryData(['remoteDownloadDetail', detail.id], detail);
      queryClient.setQueryData(['remoteDownloads'], (current: Array<Record<string, unknown>> | undefined) =>
        current?.map((item) =>
          item.id === detail.id
            ? {
                ...item,
                status: detail.status,
                updatedAt: detail.updatedAt,
                finishedAt: detail.finishedAt,
              }
            : item,
        ) ?? current,
      );
      onCancelled?.(detail);
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloadDetail'] });
    },
  });

  const selectMutation = useMutation({
    mutationFn: ({ id, fileKeys }: { id: number; fileKeys: string[] }) =>
      selectRemoteDownloadFiles(id, fileKeys),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloadDetail'] });
      setSelectedFileKeys([]);
    },
  });

  useEffect(() => {
    if (remoteDownload?.status === 'AWAITING_FILE_SELECTION' && remoteDownload.candidateFiles) {
      setSelectedFileKeys(
        remoteDownload.candidateFiles
          .filter((f) => f.selected)
          .map((f) => f.fileKey)
      );
    }
  }, [remoteDownload]);

  useEffect(() => {
    if (!task) {
      setProgress(null);
      setProgressError(null);
      setProgressLoading(false);
      return;
    }

    let cancelled = false;
    setProgressLoading(true);
    setProgressError(null);

    void getTaskProgress(task.id)
      .then((result) => {
        if (!cancelled) {
          setProgress(result);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setProgress(null);
          setProgressError(error instanceof Error ? error.message : '任务进度加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setProgressLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [task]);

  if (!remoteDownload) {
    return (
      <div className="flex h-full flex-col items-center justify-center p-10 text-center">
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-brand-light/10 text-brand-light">
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
            <path d="M12 16v-4" />
            <path d="M12 8h.01" />
          </svg>
        </div>
        <h3 className="mb-2 text-xl font-bold text-text-primary-light dark:text-white">未选中任务</h3>
        <p className="max-w-xs text-text-secondary-light dark:text-text-secondary-dark font-geist">
          请从左侧列表中选择一个任务以查看详细进度和操作。
        </p>
      </div>
    );
  }

  const taskSnapshot = task ? readTaskProgressSnapshot(task.publicStateJson) : null;
  const taskState = task ? readTaskPublicState(task.publicStateJson) : null;
  const resolvedStatus = resolveRemoteDownloadStatus({
    remoteStatus: remoteDownload.status,
    progressStatus: progress?.status,
    taskStatus: task?.status,
    phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
  });
  const resolvedPhase = resolveRemoteDownloadPhase({
    phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    status: resolvedStatus,
  });
  const resolvedProgressPercent = progress?.progressPercent ?? taskSnapshot?.progressPercent ?? 0;
  const resolvedProcessedItems = progress?.processedItems ?? taskSnapshot?.processedItems ?? 0;
  const resolvedTotalItems = progress?.totalItems ?? taskSnapshot?.totalItems ?? 0;
  const completedWithoutItemCounts = resolvedStatus === 'COMPLETED' && resolvedTotalItems === 0;

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-text-primary-light dark:text-white">
            离线下载进度
          </h3>
          <p className="mt-1 text-sm text-text-secondary-light dark:text-text-secondary-dark">
            {task ? `任务 #${task.id}` : `离线下载 #${remoteDownload.id}`} · 当前状态 {getRemoteDownloadStatusLabel(resolvedStatus)}
          </p>
        </div>
        <div className="text-right text-sm text-text-secondary-light dark:text-text-secondary-dark">
          <p>
            {progressLoading
              ? '进度加载中...'
              : completedWithoutItemCounts
              ? '已完成'
              : `进度 ${resolvedStatus === 'COMPLETED' ? 100 : resolvedProgressPercent}%`}
          </p>
          <p>
            {completedWithoutItemCounts ? (
              '任务已处理完成'
            ) : (
              <>
                已处理 {resolvedProcessedItems} / {resolvedTotalItems}
              </>
            )}
          </p>
        </div>
      </div>

      <div className="mt-4 h-2 w-full rounded-full bg-white/55 dark:bg-white/[0.08]">
        <div
          className="h-2 rounded-full bg-brand-light transition-all dark:bg-brand-dark"
          style={{
            width: `${
              completedWithoutItemCounts ? 100 : resolvedStatus === 'COMPLETED' ? 100 : resolvedProgressPercent
            }%`,
          }}
        />
      </div>

      <p className="mt-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
        {progressError ||
          progress?.message ||
          taskSnapshot?.message ||
          task?.errorMessage ||
          '暂无详细进度信息'}
      </p>

      {(taskState || remoteDownload) && (
        <div className="mt-6 space-y-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
          <div className="glass-card p-4">
            <div className="flex items-start justify-between">
              <div className="space-y-1">
                <p>
                  离线下载状态：
                  <span className="font-medium text-text-primary-light dark:text-white">
                    {getRemoteDownloadStatusLabel(resolvedStatus)}
                  </span>
                </p>
                <p>阶段：{getRemoteDownloadPhaseLabel(resolvedPhase)}</p>
                <p>来源：{getRemoteDownloadSourceLabel(String(taskState?.sourceType ?? remoteDownload.sourceType ?? ''))}</p>
                <p>引擎：{String(taskState?.engineType ?? remoteDownload.engineType ?? '-')}</p>
                <p>目标目录：{remoteDownload.targetPath}</p>
                <p>已选文件：{remoteDownload.selectedFileCount}</p>
                <p>已导入文件：{remoteDownload.importedFileCount}</p>
                {remoteDownload.failureMessage ? (
                  <p className="mt-2 font-medium text-red-500">失败原因：{remoteDownload.failureMessage}</p>
                ) : null}
              </div>

              {!isRemoteDownloadTerminalStatus(resolvedStatus) && (
                <button
                  onClick={() => cancelMutation.mutate(remoteDownload.id)}
                  disabled={cancelMutation.isPending}
                  className="px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50 dark:text-red-400 dark:border-red-900/30 dark:hover:bg-red-900/10 transition-colors"
                >
                  {cancelMutation.isPending ? '取消中...' : '取消任务'}
                </button>
              )}
            </div>
          </div>

          {resolvedStatus === 'AWAITING_FILE_SELECTION' && remoteDownload.candidateFiles && (
            <div className="glass-card mt-6 overflow-hidden">
              <div className="flex items-center justify-between border-b border-white/45 bg-white/30 px-4 py-3 dark:border-white/8 dark:bg-white/[0.03]">
                <h4 className="font-bold text-text-primary-light dark:text-white">选择要下载的文件</h4>
                <span className="text-xs text-text-secondary-light dark:text-text-secondary-dark">
                  已选择 {selectedFileKeys.length} 个文件
                </span>
              </div>
              <div className="max-h-80 overflow-y-auto divide-y divide-white/45 dark:divide-white/8">
                {remoteDownload.candidateFiles.map((file) => (
                  <div
                    key={file.fileKey}
                    className="flex items-center gap-3 px-4 py-2 transition-colors hover:bg-white/35 dark:hover:bg-white/[0.04]"
                  >
                    <input
                      type="checkbox"
                      id={`file-${file.fileKey}`}
                      checked={selectedFileKeys.includes(file.fileKey)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedFileKeys((prev) => [...prev, file.fileKey]);
                        } else {
                          setSelectedFileKeys((prev) => prev.filter((k) => k !== file.fileKey));
                        }
                      }}
                      className="w-4 h-4 rounded border-gray-300 text-brand-light focus:ring-brand-light"
                    />
                    <label htmlFor={`file-${file.fileKey}`} className="flex-1 min-w-0 cursor-pointer">
                      <p className="text-sm font-medium text-text-primary-light dark:text-white truncate">
                        {file.relativePath}
                      </p>
                      <p className="text-xs text-text-secondary-light dark:text-text-secondary-dark">
                        {formatBytes(file.size)}
                      </p>
                    </label>
                  </div>
                ))}
              </div>
              <div className="border-t border-white/45 bg-white/25 p-4 dark:border-white/8 dark:bg-white/[0.02]">
                <button
                  onClick={() =>
                    selectMutation.mutate({ id: remoteDownload.id, fileKeys: selectedFileKeys })
                  }
                  disabled={selectMutation.isPending || selectedFileKeys.length === 0}
                  className="w-full py-2 bg-brand-light text-white rounded-lg font-bold hover:bg-brand-light/90 disabled:opacity-50 transition-colors"
                >
                  {selectMutation.isPending ? '提交中...' : '开始下载已选文件'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default OfflineDownloadDetailPanel;
