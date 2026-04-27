import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import DashboardLayout from '../components/DashboardLayout';
import { useRemoteDownloadDetail, useRemoteDownloads, useTasks } from '../api/queries';
import { formatDateTime, formatBytes } from '../lib/format';
import { cancelRemoteDownload, selectRemoteDownloadFiles } from '../lib/remote-downloads';
import {
  getRemoteDownloadPhaseLabel,
  getRemoteDownloadStatusLabel,
  getRemoteDownloadSourceLabel,
  getTaskProgress,
  getTaskStatusLabel,
  getTaskTypeLabel,
  readTaskProgressSnapshot,
  readTaskPublicState,
} from '../lib/tasks';
import type { BackgroundTask, TaskProgress } from '../api/types';

const Tasks: React.FC = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [progress, setProgress] = useState<TaskProgress | null>(null);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressError, setProgressError] = useState<string | null>(null);
  const [selectedFileKeys, setSelectedFileKeys] = useState<string[]>([]);
  const { data, isLoading, isError } = useTasks(page, 20);
  const { data: remoteDownloads } = useRemoteDownloads();

  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelRemoteDownload(id),
    onSuccess: () => {
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
  const remoteDownloadStatusByBackgroundTaskId = useMemo(() => {
    const entries = remoteDownloads?.map((item) => [item.backgroundTaskId, item.status] as const) ?? [];
    return new Map<number, string>(entries.filter((entry): entry is readonly [number, string] => entry[0] != null));
  }, [remoteDownloads]);
  const selectedTask = useMemo(
    () => data?.items.find((task) => task.id === selectedTaskId) ?? null,
    [data, selectedTaskId],
  );
  const selectedRemoteDownloadId = useMemo(() => {
    if (!selectedTask || selectedTask.type !== 'REMOTE_DOWNLOAD' || !remoteDownloads) {
      return null;
    }
    return remoteDownloads.find((item) => item.backgroundTaskId === selectedTask.id)?.id ?? null;
  }, [remoteDownloads, selectedTask]);
  const { data: selectedRemoteDownload } = useRemoteDownloadDetail(selectedRemoteDownloadId);

  useEffect(() => {
    if (selectedRemoteDownload?.status === 'AWAITING_FILE_SELECTION' && selectedRemoteDownload.candidateFiles) {
      setSelectedFileKeys(
        selectedRemoteDownload.candidateFiles
          .filter(f => f.selected)
          .map(f => f.fileKey)
      );
    }
  }, [selectedRemoteDownload]);

  const selectedTaskSnapshot = selectedTask ? readTaskProgressSnapshot(selectedTask.publicStateJson) : null;
  const selectedTaskState = selectedTask ? readTaskPublicState(selectedTask.publicStateJson) : null;
  const resolvedTaskStatus =
    selectedRemoteDownload?.status || progress?.status || selectedTask?.status || '';
  const resolvedProgressPercent = progress?.progressPercent ?? selectedTaskSnapshot?.progressPercent ?? 0;
  const resolvedProcessedItems = progress?.processedItems ?? selectedTaskSnapshot?.processedItems ?? 0;
  const resolvedTotalItems = progress?.totalItems ?? selectedTaskSnapshot?.totalItems ?? 0;
  const completedWithoutItemCounts =
    resolvedTaskStatus === 'COMPLETED' && resolvedTotalItems === 0;

  useEffect(() => {
    if (!data?.items.length) {
      setSelectedTaskId(null);
      return;
    }
    if (selectedTaskId == null || !data.items.some((task) => task.id === selectedTaskId)) {
      setSelectedTaskId(data.items[0].id);
    }
  }, [data, selectedTaskId]);

  useEffect(() => {
    if (selectedTaskId == null) {
      setProgress(null);
      setProgressError(null);
      return;
    }

    let cancelled = false;
    setProgressLoading(true);
    setProgressError(null);

    void getTaskProgress(selectedTaskId)
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
  }, [selectedTaskId]);

  return (
    <DashboardLayout title="任务 Tasks">
      {isLoading ? (
        <div className="card-container p-10 text-center">加载中...</div>
      ) : isError ? (
        <div className="card-container p-10 text-center text-red-500">任务列表加载失败</div>
      ) : data && data.items.length > 0 ? (
        <div className="space-y-6">
          <div className="card-container divide-y divide-[#D9E3F2] dark:divide-[#222233]">
          {data.items.map((task: BackgroundTask) => (
            <div
              key={task.id}
              className={`px-6 py-5 flex cursor-pointer flex-col gap-2 transition-colors lg:flex-row lg:items-center lg:justify-between ${
                selectedTaskId === task.id ? 'bg-brand-light/5 dark:bg-brand-dark/10' : ''
              }`}
              onClick={() => setSelectedTaskId(task.id)}
            >
              <div>
                <h3 className="font-bold text-text-primary-light dark:text-white">
                  {getTaskTypeLabel(task.type)}
                </h3>
                <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  状态 {task.type === 'REMOTE_DOWNLOAD'
                    ? getRemoteDownloadStatusLabel(remoteDownloadStatusByBackgroundTaskId.get(task.id) ?? task.status)
                    : getTaskStatusLabel(task.status)} · 创建于 {formatDateTime(task.createdAt)}
                </p>
              </div>
              <div className="text-sm text-text-muted-light dark:text-text-muted-dark lg:text-right">
                <p>ID #{task.id}</p>
                <p>{task.errorMessage || task.correlationId || '无附加信息'}</p>
              </div>
            </div>
          ))}
          <div className="px-6 py-4 flex items-center justify-between text-sm text-text-secondary-light dark:text-text-secondary-dark">
            <span>共 {data.pagination.total_items} 条任务</span>
            <div className="flex gap-2">
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</button>
              <button className="px-3 py-1 border rounded bg-brand-light text-white border-brand-light">{page}</button>
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page >= data.pagination.total_pages} onClick={() => setPage((current) => current + 1)}>下一页</button>
            </div>
          </div>
        </div>
          {selectedTask ? (
            <div className="card-container p-6">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-lg font-bold text-text-primary-light dark:text-white">
                    {getTaskTypeLabel(selectedTask.type)}
                  </h3>
                  <p className="mt-1 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                    任务 #{selectedTask.id} · 当前状态 {selectedTask.type === 'REMOTE_DOWNLOAD'
                      ? getRemoteDownloadStatusLabel(resolvedTaskStatus)
                      : getTaskStatusLabel(resolvedTaskStatus)}
                  </p>
                </div>
                <div className="text-right text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  <p>
                    {progressLoading
                      ? '进度加载中...'
                      : completedWithoutItemCounts
                        ? '已完成'
                        : `进度 ${resolvedTaskStatus === 'COMPLETED' ? 100 : resolvedProgressPercent}%`}
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
              <div className="mt-4 h-2 w-full rounded-full bg-[#E8EEF8] dark:bg-[#1D2330]">
                <div
                  className="h-2 rounded-full bg-brand-light transition-all dark:bg-brand-dark"
                  style={{ width: `${completedWithoutItemCounts ? 100 : resolvedTaskStatus === 'COMPLETED' ? 100 : resolvedProgressPercent}%` }}
                />
              </div>
              <p className="mt-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                {progressError || progress?.message || selectedTaskSnapshot?.message || selectedTask.errorMessage || '暂无详细进度信息'}
              </p>
              {selectedTask?.type === 'REMOTE_DOWNLOAD' && selectedTaskState ? (
                <div className="mt-4 space-y-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  <div className="rounded-xl border border-[#D9E3F2] bg-[#F8FBFF] p-4 dark:border-[#222233] dark:bg-[#121826]">
                    <div className="flex justify-between items-start">
                      <div className="space-y-1">
                        <p>离线下载状态：<span className="font-medium text-text-primary-light dark:text-white">{getRemoteDownloadStatusLabel(selectedRemoteDownload?.status ?? '')}</span></p>
                        <p>阶段：{getRemoteDownloadPhaseLabel(String(selectedTaskState.phase ?? ''))}</p>
                        <p>来源：{getRemoteDownloadSourceLabel(String(selectedTaskState.sourceType ?? ''))}</p>
                        <p>引擎：{String(selectedTaskState.engineType ?? '-')}</p>
                        {selectedRemoteDownload ? (
                          <>
                            <p>目标目录：{selectedRemoteDownload.targetPath}</p>
                            <p>已选文件：{selectedRemoteDownload.selectedFileCount}</p>
                            <p>已导入文件：{selectedRemoteDownload.importedFileCount}</p>
                            {selectedRemoteDownload.failureMessage ? (
                              <p className="text-red-500">失败原因：{selectedRemoteDownload.failureMessage}</p>
                            ) : null}
                          </>
                        ) : null}
                      </div>
                      
                      {selectedRemoteDownload && !['COMPLETED', 'FAILED', 'CANCELED'].includes(selectedRemoteDownload.status) && (
                        <button
                          onClick={() => cancelMutation.mutate(selectedRemoteDownload.id)}
                          disabled={cancelMutation.isPending}
                          className="px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50 dark:text-red-400 dark:border-red-900/30 dark:hover:bg-red-900/10"
                        >
                          {cancelMutation.isPending ? '取消中...' : '取消下载'}
                        </button>
                      )}
                    </div>
                  </div>

                  {selectedRemoteDownload?.status === 'AWAITING_FILE_SELECTION' && selectedRemoteDownload.candidateFiles && (
                    <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] overflow-hidden bg-white dark:bg-black/20">
                      <div className="bg-[#F8FBFF] dark:bg-[#121826] px-4 py-3 border-b border-[#D9E3F2] dark:border-[#222233] flex justify-between items-center">
                        <h4 className="font-bold text-text-primary-light dark:text-white">选择要下载的文件</h4>
                        <span className="text-xs text-text-secondary-light dark:text-text-secondary-dark">
                          已选择 {selectedFileKeys.length} 个文件
                        </span>
                      </div>
                      <div className="max-h-60 overflow-y-auto divide-y divide-[#D9E3F2] dark:divide-[#222233]">
                        {selectedRemoteDownload.candidateFiles.map((file) => (
                          <div key={file.fileKey} className="flex items-center gap-3 px-4 py-2 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                            <input
                              type="checkbox"
                              id={`file-${file.fileKey}`}
                              checked={selectedFileKeys.includes(file.fileKey)}
                              onChange={(e) => {
                                if (e.target.checked) {
                                  setSelectedFileKeys(prev => [...prev, file.fileKey]);
                                } else {
                                  setSelectedFileKeys(prev => prev.filter(k => k !== file.fileKey));
                                }
                              }}
                              className="w-4 h-4 rounded border-gray-300 text-brand-light focus:ring-brand-light"
                            />
                            <label htmlFor={`file-${file.fileKey}`} className="flex-1 min-w-0 cursor-pointer">
                              <p className="text-sm font-medium text-text-primary-light dark:text-white truncate">{file.relativePath}</p>
                              <p className="text-xs text-text-secondary-light dark:text-text-secondary-dark">{formatBytes(file.size)}</p>
                            </label>
                          </div>
                        ))}
                      </div>
                      <div className="p-4 bg-gray-50 dark:bg-white/5 border-t border-[#D9E3F2] dark:border-[#222233]">
                        <button
                          onClick={() => selectMutation.mutate({ id: selectedRemoteDownload.id, fileKeys: selectedFileKeys })}
                          disabled={selectMutation.isPending || selectedFileKeys.length === 0}
                          className="w-full py-2 bg-brand-light text-white rounded-lg font-bold hover:bg-brand-light/90 disabled:opacity-50 transition-colors"
                        >
                          {selectMutation.isPending ? '提交中...' : '开始下载已选文件'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
          <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 11 3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          </div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">当前没有进行中的任务</h3>
          <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
            所有的上传、下载、离线下载或解压任务都会显示在这里。
          </p>
        </div>
      )}
    </DashboardLayout>
  );
};

export default Tasks;
