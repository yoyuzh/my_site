import { AnimatePresence, motion } from 'motion/react';
import { Ban, CheckCircle2, ChevronDown, ChevronUp, FileUp, TriangleAlert, UploadCloud, X } from 'lucide-react';

import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { ellipsizeFileName } from '@/src/lib/file-name';
import { cn } from '@/src/lib/utils';
import {
  cancelFilesUploadTask,
  clearFilesUploads,
  toggleFilesUploadPanelOpen,
  useFilesUploadStore,
} from '@/src/pages/files-upload-store';

export function UploadProgressPanel() {
  const { uploads, isUploadPanelOpen } = useFilesUploadStore();

  if (uploads.length === 0) {
    return null;
  }

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, y: 50, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 50, scale: 0.95 }}
        className="fixed bottom-6 right-6 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-xl border border-white/10 bg-[#0f172a]/95 shadow-2xl backdrop-blur-xl"
      >
        <div
          className="flex cursor-pointer items-center justify-between border-b border-white/10 bg-white/5 px-4 py-3 transition-colors hover:bg-white/10"
          onClick={() => toggleFilesUploadPanelOpen()}
        >
          <div className="flex items-center gap-2">
            <UploadCloud className="h-4 w-4 text-[#336EFF]" />
            <span className="text-sm font-medium text-white">
              上传进度 ({uploads.filter((task) => task.status === 'completed').length}/{uploads.length})
            </span>
          </div>
          <div className="flex items-center gap-1">
            <button type="button" className="rounded p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white">
              {isUploadPanelOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronUp className="h-4 w-4" />}
            </button>
            <button
              type="button"
              className="rounded p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
              onClick={(event) => {
                event.stopPropagation();
                clearFilesUploads();
              }}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        <AnimatePresence initial={false}>
          {isUploadPanelOpen && (
            <motion.div initial={{ height: 0 }} animate={{ height: 'auto' }} exit={{ height: 0 }} className="max-h-80 overflow-y-auto">
              <div className="space-y-1 p-2">
                {uploads.map((task) => (
                  <div
                    key={task.id}
                    className={cn(
                      'group relative overflow-hidden rounded-lg p-3 transition-colors hover:bg-white/5',
                      task.status === 'error' && 'bg-rose-500/5',
                      task.status === 'cancelled' && 'bg-amber-500/5',
                    )}
                  >
                    {task.status === 'uploading' && (
                      <div
                        className="absolute inset-y-0 left-0 bg-[#336EFF]/10 transition-all duration-300 ease-out"
                        style={{ width: `${task.progress}%` }}
                      />
                    )}

                    <div className="relative z-10 flex items-start gap-3">
                      <FileTypeIcon type={task.type} size="sm" className="mt-0.5" />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-3">
                          <p className="truncate text-sm font-medium text-slate-200" title={task.fileName}>
                            {ellipsizeFileName(task.fileName, 30)}
                          </p>
                          <div className="shrink-0 flex items-center gap-2">
                            {task.status === 'uploading' ? (
                              <button
                                type="button"
                                className="rounded-md border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[11px] font-medium text-amber-200 transition-colors hover:bg-amber-500/20"
                                onClick={() => {
                                  cancelFilesUploadTask(task.id);
                                }}
                              >
                                取消
                              </button>
                            ) : null}
                            {task.status === 'completed' ? (
                              <CheckCircle2 className="h-[18px] w-[18px] text-emerald-400" />
                            ) : task.status === 'cancelled' ? (
                              <Ban className="h-[18px] w-[18px] text-amber-300" />
                            ) : task.status === 'error' ? (
                              <TriangleAlert className="h-[18px] w-[18px] text-rose-400" />
                            ) : (
                              <FileUp className="h-[18px] w-[18px] animate-pulse text-[#78A1FF]" />
                            )}
                          </div>
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                          <span className={cn('rounded-full px-2 py-1 font-medium', getFileTypeTheme(task.type).badgeClassName)}>
                            {task.typeLabel}
                          </span>
                          <span className="truncate text-slate-500">上传至: {task.destination}</span>
                        </div>
                        {task.noticeMessage && (
                          <p className="mt-2 truncate text-xs text-amber-300">{task.noticeMessage}</p>
                        )}

                        {task.status === 'uploading' && (
                          <div className="mt-2 flex items-center justify-between text-xs">
                            <span className="font-medium text-[#336EFF]">{Math.round(task.progress)}%</span>
                            <span className="font-mono text-slate-400">{task.speed}</span>
                          </div>
                        )}
                        {task.status === 'completed' && (
                          <p className="mt-2 text-xs text-emerald-400">上传完成</p>
                        )}
                        {task.status === 'cancelled' && (
                          <p className="mt-2 text-xs text-amber-300">已取消上传</p>
                        )}
                        {task.status === 'error' && (
                          <p className="mt-2 truncate text-xs text-rose-400">{task.errorMessage ?? '上传失败，请稍后重试'}</p>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </AnimatePresence>
  );
}
