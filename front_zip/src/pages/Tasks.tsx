import { useEffect, useMemo, useState } from 'react';
import { RefreshCw, RotateCcw, Square, CheckCircle2, Clock, Play, AlertCircle } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { cancelTask, getTasks, retryTask, type BackgroundTask } from '@/src/lib/background-tasks';
import { formatDateTime } from '@/src/lib/format';

function parseTaskState(task: BackgroundTask) {
  if (!task.publicStateJson) {
    return {};
  }

  try {
    return JSON.parse(task.publicStateJson) as Record<string, unknown>;
  } catch {
    return {};
  }
}

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05
    }
  }
};

const itemVariants = {
  hidden: { y: 10, opacity: 0 },
  show: { y: 0, opacity: 1 }
};

export default function Tasks() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [tasks, setTasks] = useState<BackgroundTask[]>([]);

  async function loadTasks() {
    setError('');
    try {
      const result = await getTasks(0, 100);
      setTasks(result.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载任务失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadTasks();
  }, []);

  useEffect(() => {
    if (!autoRefresh) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadTasks();
    }, 5000);
    return () => window.clearInterval(timer);
  }, [autoRefresh]);

  const rows = useMemo(
    () =>
      tasks.map((task) => {
        const state = parseTaskState(task);
        const phase = typeof state.phase === 'string' ? state.phase : '';
        const progressPercent =
          typeof state.progressPercent === 'number'
            ? state.progressPercent
            : typeof state.progressPercent === 'string'
              ? Number(state.progressPercent)
              : null;
        const name =
          (typeof state.outputFilename === 'string' && state.outputFilename) ||
          (typeof state.outputDirectoryName === 'string' && state.outputDirectoryName) ||
          (typeof state.sourceFilename === 'string' && state.sourceFilename) ||
          (typeof state.path === 'string' && state.path) ||
          '-';
        return {
          ...task,
          phase,
          progressPercent: Number.isFinite(progressPercent) ? Math.max(0, Math.min(100, Number(progressPercent))) : null,
          name,
        };
      }),
    [tasks],
  );

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="flex items-center justify-between mb-10">
        <div>
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal">异步任务</h1>
          <p className="mt-3 text-sm font-black uppercase tracking-[0.2em] opacity-70">后台处理流水线 / 队列遥测</p>
        </div>
        <div className="flex items-center gap-8">
          <label className="flex items-center gap-3 text-sm font-black uppercase tracking-widest cursor-pointer group">
            <input
              checked={autoRefresh}
              onChange={(event) => setAutoRefresh(event.target.checked)}
              type="checkbox"
              className="w-4 h-4 rounded-sm border-white/20 bg-white/10 checked:bg-blue-600 focus:ring-0 transition-all"
            />
            <span className={cn("transition-opacity", autoRefresh ? "opacity-100" : "opacity-40")}>自动刷新</span>
          </label>
          <button
            type="button"
            onClick={() => {
              setLoading(true);
              void loadTasks();
            }}
            className="flex items-center gap-2 px-5 py-3 rounded-lg glass-panel hover:bg-white/40 transition-all font-black text-sm uppercase tracking-widest"
          >
            <RefreshCw className={cn("h-3.5 w-3.5", loading && "animate-spin")} />
            刷新
          </button>
        </div>
      </div>

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 dark:text-red-400 font-bold backdrop-blur-md">{error}</div> : null}

      <div className="flex-1 min-h-0">
        {loading && rows.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-sm font-black uppercase tracking-widest opacity-70">正在查询任务调度器...</div>
        ) : rows.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-sm font-black uppercase tracking-widest opacity-70">当前无任务</div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-2xl border-white/10">
            <table className="min-w-full divide-y divide-white/10">
              <thead className="bg-white/10 dark:bg-black/40">
                <tr>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">类型</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">对象</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">状态</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">进度</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">更新时间</th>
                  <th className="px-8 py-5 text-right text-xs font-black uppercase tracking-[0.2em] opacity-70">操作</th>
                </tr>
              </thead>
              <motion.tbody 
                variants={container}
                initial="hidden"
                animate="show"
                className="divide-y divide-white/10 dark:divide-white/5"
              >
                {rows.map((task) => (
                  <motion.tr key={task.id} variants={itemVariants} className="hover:bg-white/10 dark:hover:bg-white/5 transition-colors group">
                    <td className="px-8 py-5 text-sm font-black tracking-widest uppercase opacity-90">{task.type}</td>
                    <td className="px-8 py-5 text-[12px] font-black tracking-tight uppercase truncate max-w-[150px]">{task.name}</td>
                    <td className="px-8 py-5">
                      <div className="flex items-center gap-2">
                        <span className={cn(
                          "px-2 py-0.5 rounded-sm text-xs font-black uppercase tracking-widest border",
                          task.status === 'RUNNING' ? "bg-blue-500/10 text-blue-500 border-blue-500/20 shadow-[0_0_10px_rgba(59,130,246,0.1)]" :
                          task.status === 'COMPLETED' ? "bg-green-500/10 text-green-500 border-green-500/20" :
                          task.status === 'FAILED' ? "bg-red-500/10 text-red-500 border-red-500/20" : "bg-gray-500/10 text-gray-500 border-white/10"
                        )}>
                          {task.status}
                        </span>
                      </div>
                      <div className="text-xs opacity-80 dark:opacity-90 font-black uppercase tracking-widest mt-1 ml-0.5">{task.phase || '等待中'}</div>
                      {task.errorMessage ? <div className="mt-1 text-xs text-red-500 font-bold uppercase tracking-tight">{task.errorMessage}</div> : null}
                    </td>
                    <td className="px-8 py-5">
                      <div className="mb-2 h-1 w-32 rounded-full bg-white/10 overflow-hidden">
                        <motion.div
                          initial={{ width: 0 }}
                          animate={{ width: `${task.progressPercent ?? 0}%` }}
                          className={cn(
                            "h-full transition-all duration-500",
                            task.status === 'FAILED' ? 'bg-red-500' : 'bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]'
                          )}
                        />
                      </div>
                      <div className="text-sm font-black opacity-80 dark:opacity-90">{task.progressPercent != null ? `${Math.round(task.progressPercent)}%` : '-'}</div>
                    </td>
                    <td className="px-8 py-5 text-sm font-bold opacity-80 dark:opacity-90 tracking-tighter uppercase">{formatDateTime(task.updatedAt)}</td>
                    <td className="px-8 py-5 text-right">
                      <div className="flex justify-end gap-2 opactiy-40 group-hover:opacity-100 transition-opacity">
                        {task.status === 'FAILED' ? (
                          <button
                            type="button"
                            onClick={async () => {
                              await retryTask(task.id);
                              await loadTasks();
                            }}
                            className="p-2.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white text-blue-500 transition-all border-white/10 shadow-sm"
                            title="重试任务"
                          >
                            <RotateCcw className="h-4 w-4" />
                          </button>
                        ) : null}
                        {(task.status === 'QUEUED' || task.status === 'RUNNING') ? (
                          <button
                            type="button"
                            onClick={async () => {
                              await cancelTask(task.id);
                              await loadTasks();
                            }}
                            className="p-2.5 rounded-lg glass-panel hover:bg-red-500 hover:text-white text-red-500 transition-all border-white/10 shadow-sm"
                            title="取消任务"
                          >
                            <Square className="h-4 w-4" />
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </motion.tbody>
            </table>
          </div>
        )}
      </div>
    </motion.div>
  );
}
