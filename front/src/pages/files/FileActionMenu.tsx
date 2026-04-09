import React from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Download, Share2, Folder, Copy, Edit2, Trash2, MoreVertical } from 'lucide-react';
import type { UiFile } from './file-types';

export function FileActionMenu({
  file,
  activeDropdown,
  onToggle,
  onDownload,
  onShare,
  onMove,
  onCopy,
  onRename,
  onDelete,
  onClose,
  allowMutatingActions = true,
}: {
  file: UiFile;
  activeDropdown: number | null;
  onToggle: (fileId: number) => void;
  onDownload: (file: UiFile) => Promise<void>;
  onShare: (file: UiFile) => Promise<void>;
  onMove: (file: UiFile) => void;
  onCopy: (file: UiFile) => void;
  onRename: (file: UiFile) => void;
  onDelete: (file: UiFile) => void;
  onClose: () => void;
  allowMutatingActions?: boolean;
}) {
  return (
    <div className="relative inline-block text-left">
      <button
        onClick={(event) => {
          event.stopPropagation();
          onToggle(file.id);
        }}
        className="rounded-md p-1.5 text-slate-500 opacity-0 transition-all hover:bg-white/10 hover:text-white group-hover:opacity-100"
      >
        <MoreVertical className="w-4 h-4" />
      </button>
      {activeDropdown === file.id && (
        <div
          className="fixed inset-0 z-40"
          onClick={(event) => {
            event.stopPropagation();
            onClose();
          }}
        />
      )}
      <AnimatePresence>
        {activeDropdown === file.id && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 10 }}
            transition={{ duration: 0.15 }}
            className="absolute right-0 top-full z-50 mt-1 w-32 overflow-hidden rounded-lg border border-white/10 bg-[#1e293b] py-1 shadow-xl"
          >
            <button
              onClick={(event) => {
                event.stopPropagation();
                void onDownload(file);
                onClose();
              }}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
            >
              <Download className="w-4 h-4" /> {file.type === 'folder' ? '下载文件夹' : '下载文件'}
            </button>
            {file.type !== 'folder' ? (
              <button
                onClick={(event) => {
                  event.stopPropagation();
                  void onShare(file);
                  onClose();
                }}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
              >
                <Share2 className="w-4 h-4" /> 分享链接
              </button>
            ) : null}
            {allowMutatingActions ? (
              <>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onMove(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Folder className="w-4 h-4" /> 移动
                </button>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onCopy(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Copy className="w-4 h-4" /> 复制到
                </button>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onRename(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Edit2 className="w-4 h-4" /> 重命名
                </button>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onDelete(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-400 transition-colors hover:bg-red-500/10 hover:text-red-300"
                >
                  <Trash2 className="w-4 h-4" /> 删除
                </button>
              </>
            ) : null}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
