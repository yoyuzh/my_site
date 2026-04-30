import React, { useEffect, useMemo, useState } from 'react';
import { X, CheckCircle2, AlertCircle, Clock, Trash2, StopCircle } from 'lucide-react';
import { useUploadQueue } from '../../hooks/useUploadQueue';
import clsx from 'clsx';

interface UploadTaskPanelProps {
  onClose: () => void;
}

const MAX_VISIBLE_TASKS = 8;

function formatBytes(bytes: number) {
  if (bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** exponent;
  return `${value >= 100 || exponent === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[exponent]}`;
}

const UploadTaskPanel: React.FC<UploadTaskPanelProps> = ({ onClose }) => {
  const { tasks, cancelTask, cancelAllTasks } = useUploadQueue();
  const [showAllTasks, setShowAllTasks] = useState(false);

  useEffect(() => {
    if (tasks.length <= MAX_VISIBLE_TASKS && showAllTasks) {
      setShowAllTasks(false);
    }
  }, [showAllTasks, tasks.length]);

  if (tasks.length === 0) return null;

  const activeCount = tasks.filter((t) => t.status === 'uploading' || t.status === 'waiting').length;
  const totalSpeedBytesPerSecond = tasks
    .filter((task) => task.status === 'uploading')
    .reduce((sum, task) => sum + task.speedBytesPerSecond, 0);
  const overallProgress = Math.round(
    tasks.reduce((sum, task) => sum + task.progress, 0) / Math.max(tasks.length, 1),
  );
  const orderedTasks = useMemo(() => {
    const statusPriority: Record<string, number> = {
      uploading: 0,
      waiting: 1,
      error: 2,
      cancelled: 3,
      success: 4,
    };

    return tasks
      .map((task, index) => ({ task, index }))
      .sort((left, right) => {
        const priorityGap = statusPriority[left.task.status] - statusPriority[right.task.status];
        if (priorityGap !== 0) {
          return priorityGap;
        }
        return left.index - right.index;
      })
      .map(({ task }) => task);
  }, [tasks]);
  const visibleTasks = showAllTasks ? orderedTasks : orderedTasks.slice(0, MAX_VISIBLE_TASKS);
  const hiddenTaskCount = Math.max(0, orderedTasks.length - visibleTasks.length);

  return (
    <div className="fixed bottom-6 right-6 z-[60] w-96 overflow-hidden rounded-3xl border border-white/50 bg-white/95 shadow-2xl backdrop-blur-xl dark:border-white/5 dark:bg-[#161922]/95">
      <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-white/5">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-bold text-slate-900 dark:text-white">上传任务</span>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500 dark:bg-white/5 dark:text-slate-400">
              {activeCount > 0 ? `进行中 ${activeCount}` : '已完成'}
            </span>
          </div>
          <p className="mt-1 text-xs font-medium text-slate-500 dark:text-slate-400">
            上传速度 {formatBytes(totalSpeedBytesPerSecond)}/s
          </p>
        </div>
        <div className="flex items-center gap-1">
          {activeCount > 0 ? (
            <button
              onClick={cancelAllTasks}
              className="inline-flex h-8 items-center gap-1 rounded-xl px-2 text-xs font-semibold text-slate-500 transition-colors hover:bg-red-50 hover:text-red-500 dark:text-slate-400 dark:hover:bg-red-500/10"
              title="全部取消"
            >
              <Trash2 size={14} />
              全部取消
            </button>
          ) : null}
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-slate-100 dark:text-slate-500 dark:hover:bg-white/5"
          >
            <X size={16} />
          </button>
        </div>
      </div>

      <div className="max-h-[400px] overflow-y-auto p-2">
        <div className="space-y-1">
          {visibleTasks.map((task) => (
            <div
              key={task.id}
              className="group relative flex items-center gap-3 rounded-2xl p-3 transition-colors hover:bg-slate-50 dark:hover:bg-white/5"
            >
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-500 dark:bg-white/5 dark:text-slate-400">
                {task.status === 'uploading' && <Clock size={20} className="animate-pulse text-blue-500" />}
                {task.status === 'waiting' && <Clock size={20} />}
                {task.status === 'success' && <CheckCircle2 size={20} className="text-green-500" />}
                {task.status === 'error' && <AlertCircle size={20} className="text-red-500" />}
                {task.status === 'cancelled' && <StopCircle size={20} className="text-slate-400" />}
              </div>

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900 dark:text-white">
                  {task.file.name}
                </p>
                <div className="mt-1 flex items-center gap-2 text-[10px] font-medium uppercase tracking-wider text-slate-400">
                  <span>{formatBytes(task.file.size)}</span>
                  <span>•</span>
                  <span>{task.progress}%</span>
                  <span>•</span>
                  <span
                    className={clsx(
                      task.status === 'uploading' && 'text-blue-500',
                      task.status === 'success' && 'text-green-500',
                      task.status === 'error' && 'text-red-500',
                    )}
                  >
                    {task.status === 'uploading' && '正在上传...'}
                    {task.status === 'waiting' && '等待中'}
                    {task.status === 'success' && '上传成功'}
                    {task.status === 'error' && (task.error || '上传失败')}
                    {task.status === 'cancelled' && '已取消'}
                  </span>
                </div>
                <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-white/5">
                  <div
                    className={clsx(
                      'h-full transition-all duration-300',
                      task.status === 'success' && 'bg-green-500',
                      task.status === 'error' && 'bg-red-500',
                      task.status === 'cancelled' && 'bg-slate-300 dark:bg-slate-600',
                      (task.status === 'waiting' || task.status === 'uploading') && 'bg-blue-500',
                    )}
                    style={{ width: `${task.progress}%` }}
                  />
                </div>
                <div className="mt-1 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400">
                  <span>
                    {formatBytes(task.uploadedBytes)} / {formatBytes(task.file.size)}
                  </span>
                  <span>
                    {task.status === 'uploading' ? `${formatBytes(task.speedBytesPerSecond)}/s` : ''}
                  </span>
                </div>
              </div>

              {(task.status === 'uploading' || task.status === 'waiting') && (
                <button
                  onClick={() => cancelTask(task.id)}
                  className="flex h-8 w-8 items-center justify-center rounded-xl text-slate-400 opacity-0 transition-all hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:text-slate-500 dark:hover:bg-red-500/10"
                >
                  <X size={16} />
                </button>
              )}
            </div>
          ))}
        </div>
        {orderedTasks.length > MAX_VISIBLE_TASKS ? (
          <div className="px-3 pb-2 pt-3">
            <button
              type="button"
              onClick={() => setShowAllTasks((current) => !current)}
              className="w-full rounded-2xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 transition-colors hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-white/5"
            >
              {showAllTasks ? '收起额外任务' : `展开其余 ${hiddenTaskCount} 个任务`}
            </button>
          </div>
        ) : null}
      </div>
      
      {activeCount > 0 && (
        <div className="border-t border-slate-100 p-4 dark:border-white/5">
          <div className="flex items-center justify-between text-xs font-bold text-slate-500">
            <span>总进度</span>
            <span>{overallProgress}%</span>
          </div>
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-white/5">
            <div 
              className="h-full bg-blue-500 transition-all duration-300" 
              style={{ width: `${overallProgress}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default UploadTaskPanel;
