import React, { useEffect, useMemo, useState } from 'react';
import { formatDateTime } from '../../lib/format';
import {
  getRemoteDownloadSourceLabel,
  getRemoteDownloadStatusLabel,
  readTaskPublicState,
  resolveRemoteDownloadStatus,
} from '../../lib/tasks';
import type { BackgroundTask, RemoteDownloadListItem } from '../../api/types';

interface OfflineDownloadTaskListProps {
  remoteDownloads: RemoteDownloadListItem[];
  taskMap: Map<number, BackgroundTask>;
  selectedRemoteDownloadId: number | null;
  onSelectTask: (remoteDownloadId: number) => void;
}

const ACTIVE_REMOTE_DOWNLOAD_STATUSES = new Set([
  'PENDING',
  'SUBMITTED',
  'FETCHING_METADATA',
  'AWAITING_FILE_SELECTION',
  'DOWNLOADING',
  'IMPORTING',
]);

const OfflineDownloadTaskList: React.FC<OfflineDownloadTaskListProps> = ({
  remoteDownloads,
  taskMap,
  selectedRemoteDownloadId,
  onSelectTask,
}) => {
  const [historyExpanded, setHistoryExpanded] = useState(false);

  const getEffectiveStatus = (task: RemoteDownloadListItem) => {
    const backgroundTask = task.backgroundTaskId == null ? null : taskMap.get(task.backgroundTaskId) ?? null;
    const taskState = backgroundTask ? readTaskPublicState(backgroundTask.publicStateJson) : null;
    return resolveRemoteDownloadStatus({
      remoteStatus: task.status,
      taskStatus: backgroundTask?.status,
      phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    });
  };

  const activeTasks = useMemo(
    () => remoteDownloads.filter((task) => ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(task))),
    [remoteDownloads, taskMap],
  );
  const historyTasks = useMemo(
    () => remoteDownloads.filter((task) => !ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(task))),
    [remoteDownloads, taskMap],
  );

  useEffect(() => {
    if (selectedRemoteDownloadId == null) {
      return;
    }
    const selectedTask = remoteDownloads.find((task) => task.id === selectedRemoteDownloadId);
    if (selectedTask && !ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(selectedTask))) {
      setHistoryExpanded(true);
    }
  }, [remoteDownloads, selectedRemoteDownloadId, taskMap]);

  const renderTaskItem = (task: RemoteDownloadListItem) => {
    const backgroundTask = task.backgroundTaskId == null ? null : taskMap.get(task.backgroundTaskId) ?? null;
    const effectiveStatus = getEffectiveStatus(task);
    const isSelected = selectedRemoteDownloadId === task.id;

    return (
      <div
        key={task.id}
        onClick={() => onSelectTask(task.id)}
        className={`group relative flex cursor-pointer flex-col gap-1 border-b border-[#D4DEEC] p-4 transition-colors last:border-b-0 dark:border-white/8 ${
          isSelected ? 'bg-brand-light/8 dark:bg-brand-dark/12' : 'hover:bg-white/35 dark:hover:bg-white/[0.04]'
        }`}
      >
        {isSelected ? <div className="absolute inset-y-0 left-0 w-1 bg-brand-light dark:bg-brand-dark" /> : null}
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold text-brand-light dark:text-brand-dark">#{task.id}</span>
          <span className="text-[10px] text-text-muted-light dark:text-text-muted-dark">
            {formatDateTime(task.createdAt)}
          </span>
        </div>
        <h4
          className={`truncate text-sm font-bold ${
            isSelected ? 'text-brand-light dark:text-brand-dark' : 'text-text-primary-light dark:text-white'
          }`}
        >
          {getRemoteDownloadStatusLabel(effectiveStatus)}
        </h4>
        <p className="truncate text-xs text-text-secondary-light dark:text-text-secondary-dark">
          {task.targetPath} · {getRemoteDownloadSourceLabel(task.sourceType)}
        </p>
        <p className="truncate text-[11px] text-text-muted-light dark:text-text-muted-dark">
          {backgroundTask?.errorMessage || backgroundTask?.correlationId || '等待更多任务信息'}
        </p>
      </div>
    );
  };

  return (
    <div className="flex h-full flex-col bg-transparent">
      <div className="flex-1 overflow-y-auto">
        <div className="border-b border-[#D4DEEC] bg-white/30 px-4 py-2 dark:border-white/8 dark:bg-white/[0.03]">
          <h3 className="text-xs font-bold uppercase tracking-wider text-text-secondary-light dark:text-text-secondary-dark">
            活跃任务 ({activeTasks.length})
          </h3>
        </div>
        {activeTasks.length > 0 ? (
          <div className="divide-y divide-[#D4DEEC] dark:divide-white/8">{activeTasks.map(renderTaskItem)}</div>
        ) : (
          <div className="p-8 text-center text-sm text-text-muted-light dark:text-text-muted-dark">暂无活跃任务</div>
        )}

        <div
          className="flex cursor-pointer items-center justify-between border-y border-[#D4DEEC] bg-white/30 px-4 py-2 transition-colors hover:bg-white/40 dark:border-white/8 dark:bg-white/[0.03] dark:hover:bg-white/[0.05]"
          onClick={() => setHistoryExpanded((current) => !current)}
        >
          <h3 className="text-xs font-bold uppercase tracking-wider text-text-secondary-light dark:text-text-secondary-dark">
            历史记录 ({historyTasks.length})
          </h3>
          <svg
            className={`h-4 w-4 text-text-muted-light transition-transform dark:text-text-muted-dark ${
              historyExpanded ? 'rotate-180' : ''
            }`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
        {historyExpanded ? (
          historyTasks.length > 0 ? (
            <div className="divide-y divide-[#D4DEEC] dark:divide-white/8">{historyTasks.map(renderTaskItem)}</div>
          ) : (
            <div className="p-8 text-center text-sm text-text-muted-light dark:text-text-muted-dark">暂无历史记录</div>
          )
        ) : null}
      </div>
    </div>
  );
};

export default OfflineDownloadTaskList;
