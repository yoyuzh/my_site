import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { RotateCcw } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import type { BackgroundTask } from '@/src/lib/background-tasks';
import { formatDateTime } from './file-types';

export function formatTaskDateTime(value: string) {
  return formatDateTime(value);
}

export function getBackgroundTaskTypeLabel(type: BackgroundTask['type']) {
  switch (type) {
    case 'ARCHIVE': return '压缩任务';
    case 'EXTRACT': return '解压任务';
    case 'MEDIA_META': return '媒体信息提取任务';
  }
}

export function getBackgroundTaskStatusLabel(status: BackgroundTask['status']) {
  switch (status) {
    case 'QUEUED': return '排队中';
    case 'RUNNING': return '执行中';
    case 'COMPLETED': return '已完成';
    case 'FAILED': return '已失败';
    case 'CANCELLED': return '已取消';
  }
}

export function getBackgroundTaskStatusClassName(status: BackgroundTask['status']) {
  switch (status) {
    case 'QUEUED': return 'text-amber-300';
    case 'RUNNING': return 'text-sky-300';
    case 'COMPLETED': return 'text-emerald-300';
    case 'FAILED': return 'text-red-300';
    case 'CANCELLED': return 'text-slate-400';
  }
}

export function FilesTaskPanel({
  backgroundTasks,
  backgroundTasksLoading,
  backgroundTasksError,
  backgroundTaskNotice,
  backgroundTaskActionId,
  onRefresh,
  onCancelTask,
}: {
  backgroundTasks: BackgroundTask[];
  backgroundTasksLoading: boolean;
  backgroundTasksError: string;
  backgroundTaskNotice: { kind: 'success' | 'error'; message: string } | null;
  backgroundTaskActionId: number | null;
  onRefresh: () => void;
  onCancelTask: (taskId: number) => void;
}) {
  return (
    <Card>
      <CardHeader className="border-b border-white/10 pb-4">
        <div className="flex items-center justify-between gap-3">
          <CardTitle className="text-base">后台任务</CardTitle>
          <button
            type="button"
            className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
            onClick={onRefresh}
            aria-label="刷新后台任务"
          >
            <RotateCcw className={cn('h-4 w-4', backgroundTasksLoading ? 'animate-spin' : '')} />
          </button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3 p-4">
        {backgroundTaskNotice ? (
          <div
            className={cn(
              'rounded-xl border px-3 py-2 text-xs leading-relaxed',
              backgroundTaskNotice.kind === 'error'
                ? 'border-red-500/20 bg-red-500/10 text-red-200'
                : 'border-emerald-500/20 bg-emerald-500/10 text-emerald-200',
            )}
            aria-live="polite"
          >
            {backgroundTaskNotice.message}
          </div>
        ) : null}
        {backgroundTasksError ? (
          <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-200">
            {backgroundTasksError}
          </div>
        ) : null}
        {backgroundTasksLoading ? (
          <div className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-4 text-sm text-slate-400">
            加载最近任务中...
          </div>
        ) : backgroundTasks.length === 0 ? (
          <div className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-4 text-sm text-slate-400">
            暂无后台任务
          </div>
        ) : (
          <div className="max-h-[32rem] space-y-3 overflow-y-auto pr-1">
            {backgroundTasks.map((task) => {
              const canCancel = task.status === 'QUEUED' || task.status === 'RUNNING';
              return (
                <div key={task.id} className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-white">{getBackgroundTaskTypeLabel(task.type)}</p>
                      <p className={cn('text-xs', getBackgroundTaskStatusClassName(task.status))}>
                        {getBackgroundTaskStatusLabel(task.status)}
                      </p>
                    </div>
                    {canCancel ? (
                      <Button
                        type="button"
                        variant="outline"
                        className="shrink-0 border-white/10 bg-white/5 px-3 text-xs text-slate-200 hover:bg-white/10"
                        onClick={() => onCancelTask(task.id)}
                        disabled={backgroundTaskActionId === task.id}
                      >
                        {backgroundTaskActionId === task.id ? '取消中...' : '取消'}
                      </Button>
                    ) : null}
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                    <div className="min-w-0">
                      <p className="text-slate-500">创建时间</p>
                      <p className="truncate text-slate-300">{formatTaskDateTime(task.createdAt)}</p>
                    </div>
                    <div className="min-w-0">
                      <p className="text-slate-500">完成时间</p>
                      <p className="truncate text-slate-300">{task.finishedAt ? formatTaskDateTime(task.finishedAt) : '未完成'}</p>
                    </div>
                  </div>
                  {task.errorMessage ? (
                    <div className="mt-3 break-words rounded-lg border border-red-500/20 bg-red-500/10 px-2 py-1 text-xs leading-relaxed text-red-200">
                      {task.errorMessage}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
