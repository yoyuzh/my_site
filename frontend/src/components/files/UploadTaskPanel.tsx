import React from 'react';
import { X, CheckCircle2, AlertCircle, Clock, Trash2, StopCircle } from 'lucide-react';
import { useUploadQueue } from '../../hooks/useUploadQueue';
import clsx from 'clsx';

interface UploadTaskPanelProps {
  onClose: () => void;
}

const UploadTaskPanel: React.FC<UploadTaskPanelProps> = ({ onClose }) => {
  const { tasks, cancelTask, cancelAllTasks } = useUploadQueue();

  if (tasks.length === 0) return null;

  const activeCount = tasks.filter((t) => t.status === 'uploading' || t.status === 'waiting').length;
  const successCount = tasks.filter((t) => t.status === 'success').length;

  return (
    <div className="fixed bottom-6 right-6 z-[60] w-96 overflow-hidden rounded-3xl border border-white/50 bg-white/95 shadow-2xl backdrop-blur-xl dark:border-white/5 dark:bg-[#161922]/95">
      <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-white/5">
        <div className="flex items-center gap-2">
          <span className="text-sm font-bold text-slate-900 dark:text-white">上传任务</span>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500 dark:bg-white/5 dark:text-slate-400">
            {activeCount > 0 ? `进行中 ${activeCount}` : '已完成'}
          </span>
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
          {tasks.map((task) => (
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
                  <span>{(task.file.size / 1024 / 1024).toFixed(2)} MB</span>
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
      </div>
      
      {activeCount > 0 && (
        <div className="border-t border-slate-100 p-4 dark:border-white/5">
          <div className="flex items-center justify-between text-xs font-bold text-slate-500">
            <span>总进度</span>
            <span>{Math.round((successCount / (tasks.length)) * 100)}%</span>
          </div>
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-white/5">
            <div 
              className="h-full bg-blue-500 transition-all duration-300" 
              style={{ width: `${(successCount / tasks.length) * 100}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default UploadTaskPanel;
