import { useEffect, useState } from 'react';
import { CheckCircle2, ChevronDown, ChevronUp, Loader2, X, XCircle } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { uploadRuntime, type UploadTask } from '@/src/lib/upload-runtime';
import { formatBytes } from '@/src/lib/format';
import { cn } from '@/src/lib/utils';

export function UploadCenter() {
  const [tasks, setTasks] = useState<UploadTask[]>([]);
  const [isExpanded, setIsExpanded] = useState(false);

  useEffect(() => {
    const unsubscribe = uploadRuntime.subscribe(() => {
      setTasks(uploadRuntime.getTasks());
    });
    return unsubscribe;
  }, []);

  if (tasks.length === 0) return null;

  const uploadingCount = tasks.filter(t => t.status === 'UPLOADING').length;
  const successCount = tasks.filter(t => t.status === 'SUCCESS').length;
  const errorCount = tasks.filter(t => t.status === 'ERROR').length;

  return (
    <div className="fixed bottom-24 right-8 z-[60] w-80">
      <motion.div 
        layout
        className="glass-panel-no-hover overflow-hidden rounded-xl shadow-2xl border border-white/20 bg-white/10 dark:bg-black/40 backdrop-blur-xl"
      >
        <div 
          className="flex cursor-pointer items-center justify-between px-5 py-4 hover:bg-white/5 transition-colors"
          onClick={() => setIsExpanded(!isExpanded)}
        >
          <div className="flex items-center gap-3">
            <div className="relative">
              {uploadingCount > 0 ? (
                <Loader2 className="h-5 w-5 animate-spin text-blue-500" />
              ) : (
                <div className="h-5 w-5 rounded-full bg-blue-500/20 flex items-center justify-center">
                   <div className="h-2 w-2 rounded-full bg-blue-500"></div>
                </div>
              )}
            </div>
            <span className="text-[11px] font-black uppercase tracking-widest">
              传输队列 ({tasks.length})
            </span>
          </div>
          <div className="flex items-center gap-2">
            {isExpanded ? <ChevronDown className="h-4 w-4 opacity-40" /> : <ChevronUp className="h-4 w-4 opacity-40" />}
            <button 
              onClick={(e) => { e.stopPropagation(); uploadRuntime.clearFinished(); }}
              className="p-1 hover:bg-white/10 rounded transition-colors"
            >
              <X className="h-4 w-4 opacity-40 hover:opacity-100" />
            </button>
          </div>
        </div>

        <AnimatePresence>
          {isExpanded && (
            <motion.div 
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="border-t border-white/10"
            >
              <div className="max-h-80 overflow-y-auto p-4 custom-scrollbar space-y-3">
                {tasks.map((task) => (
                  <div key={task.id} className="space-y-2">
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[11px] font-bold uppercase tracking-tight opacity-90">
                          {task.filename}
                        </div>
                        <div className="text-[9px] font-black opacity-40 uppercase tracking-widest mt-0.5">
                          {formatBytes(task.size)} • {task.status === 'SUCCESS' ? '已完成' : task.status === 'ERROR' ? '失败' : `${task.progress}%`}
                        </div>
                      </div>
                      <div>
                        {task.status === 'SUCCESS' && <CheckCircle2 className="h-4 w-4 text-green-500" />}
                        {task.status === 'ERROR' && <XCircle className="h-4 w-4 text-red-500" />}
                        {task.status === 'UPLOADING' && <span className="text-[10px] font-black tabular-nums text-blue-500">{task.progress}%</span>}
                      </div>
                    </div>
                    {task.status === 'UPLOADING' && (
                      <div className="h-1 w-full rounded-full bg-white/5 overflow-hidden">
                        <motion.div 
                          initial={{ width: 0 }}
                          animate={{ width: `${task.progress}%` }}
                          className="h-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]"
                        />
                      </div>
                    )}
                    {task.error && (
                      <div className="text-[9px] font-bold text-red-500/80 uppercase tracking-tighter">
                        {task.error}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}
