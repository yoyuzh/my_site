import React from 'react';
import { FolderPlus, MoreVertical } from 'lucide-react';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { cn } from '@/src/lib/utils';
import type { UiFile } from '@/src/pages/files/file-types';

export function MobileFilesList({
  currentFiles,
  onFolderClick,
  onOpenActionSheet,
}: {
  currentFiles: UiFile[];
  onFolderClick: (file: UiFile) => void;
  onOpenActionSheet: (file: UiFile) => void;
}) {
  if (currentFiles.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-48 text-slate-500">
        <FolderPlus className="w-10 h-10 mb-3 opacity-20" />
        <p className="text-sm">文件夹是空的</p>
      </div>
    );
  }

  return (
    <>
      {currentFiles.map((file) => (
        <div key={file.id} className="glass-panel w-full rounded-xl p-3 flex flex-row items-center gap-3 active:bg-white/5 select-none" onClick={() => onFolderClick(file)}>
          <div className="shrink-0 p-1.5 rounded-xl bg-black/20 border border-white/5">
            <FileTypeIcon type={file.type} size="md" />
          </div>
          <div className="flex-1 min-w-0 flex flex-col justify-center">
            <span className="text-sm text-white truncate w-full block">{file.name}</span>
            <div className="flex items-center text-[10px] text-slate-400 mt-0.5 gap-2">
              <span className={cn('px-1.5 py-0.5 rounded text-[9px] font-medium', getFileTypeTheme(file.type).badgeClassName)}>{file.typeLabel}</span>
              <span>{file.modified}</span>
              {file.type !== 'folder' && <span>{file.size}</span>}
            </div>
          </div>
          {file.type !== 'folder' && (
            <button className="p-2 shrink-0 text-slate-400 hover:text-white" onClick={(e) => { e.stopPropagation(); onOpenActionSheet(file); }}>
              <MoreVertical className="w-5 h-5" />
            </button>
          )}
        </div>
      ))}
    </>
  );
}
