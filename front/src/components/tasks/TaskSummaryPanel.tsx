import { useEffect, useState } from 'react';
import { ListTodo, Loader2 } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { taskRuntime } from '@/src/lib/task-runtime';
import { type BackgroundTask } from '@/src/lib/background-tasks';

export function TaskSummaryPanel() {
  const [activeTasks, setActiveTasks] = useState<BackgroundTask[]>([]);

  useEffect(() => {
    taskRuntime.startPolling();
    const unsubscribe = taskRuntime.subscribe(() => {
      setActiveTasks(taskRuntime.getActiveTasks());
    });
    return () => {
      unsubscribe();
      // 在这个简单的单页面应用中我们可能不需要停止轮询，除非组件被销毁
      // taskRuntime.stopPolling();
    };
  }, []);

  if (activeTasks.length === 0) return null;

  return (
    <div className="flex items-center gap-3 px-4 py-2 rounded-lg bg-blue-500/10 border border-blue-500/20 shadow-sm transition-all animate-in fade-in slide-in-from-top-2">
      <div className="relative">
        <ListTodo className="h-4 w-4 text-blue-500" />
        <span className="absolute -top-1.5 -right-1.5 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-blue-600 text-[8px] font-black text-white shadow-sm">
          {activeTasks.length}
        </span>
      </div>
      <div className="flex flex-col">
        <div className="text-[9px] font-black uppercase tracking-widest text-blue-600/60 leading-none mb-0.5">
          后台任务
        </div>
        <div className="flex items-center gap-2">
          <Loader2 className="h-3 w-3 animate-spin text-blue-500/60" />
          <span className="text-[10px] font-bold truncate max-w-[120px] uppercase tracking-tight">
            {activeTasks[0].type} ...
          </span>
        </div>
      </div>
    </div>
  );
}
