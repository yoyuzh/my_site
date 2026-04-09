import React from 'react';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { cn } from '@/src/lib/utils';
import { ellipsizeFileName } from '@/src/lib/file-name';
import { FileActionMenu } from './FileActionMenu';
import type { UiFile } from './file-types';

export function FileGridView({
  files,
  selectedFileId,
  activeDropdown,
  isSearchResult = false,
  onFileClick,
  onFileDoubleClick,
  onToggleDropdown,
  onDownload,
  onShare,
  onMove,
  onCopy,
  onRename,
  onDelete,
  onCloseDropdown,
}: {
  files: (UiFile & { originalPath?: string; originalDirectory?: boolean })[];
  selectedFileId: number | null;
  activeDropdown: number | null;
  isSearchResult?: boolean;
  onFileClick: (file: any) => void;
  onFileDoubleClick: (file: any) => void;
  onToggleDropdown: (fileId: number) => void;
  onDownload: (file: UiFile) => Promise<void>;
  onShare: (file: UiFile) => Promise<void>;
  onMove: (file: UiFile) => void;
  onCopy: (file: UiFile) => void;
  onRename: (file: UiFile) => void;
  onDelete: (file: UiFile) => void;
  onCloseDropdown: () => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
      {files.map((file) => {
        const selected = selectedFileId === file.id;

        return (
          <div
            key={file.id}
            onClick={() => onFileClick(file)}
            onDoubleClick={() => onFileDoubleClick(file)}
            className={cn(
              'group relative flex cursor-pointer flex-col items-center rounded-xl border p-4 transition-all',
              selected
                ? 'border-[#336EFF]/30 bg-[#336EFF]/10'
                : 'border-white/5 bg-white/[0.02] hover:border-white/10 hover:bg-white/[0.04]',
            )}
          >
            <div className="absolute right-2 top-2">
              <FileActionMenu
                file={file}
                activeDropdown={activeDropdown}
                onToggle={onToggleDropdown}
                onDownload={onDownload}
                onShare={onShare}
                onMove={onMove}
                onCopy={onCopy}
                onRename={onRename}
                onDelete={onDelete}
                onClose={onCloseDropdown}
                allowMutatingActions={!isSearchResult}
              />
            </div>

            <FileTypeIcon type={file.type} size="lg" className="mb-3 transition-transform duration-200 group-hover:scale-[1.03]" />

            <span className={cn('w-full truncate px-2 text-center text-sm font-medium', selected ? 'text-[#336EFF]' : 'text-slate-200')}>
              {ellipsizeFileName(file.name, 24)}
            </span>
            <span className={cn('mt-1 inline-flex rounded-full px-2 py-1 text-[11px] font-medium', getFileTypeTheme(file.type).badgeClassName)}>
              {file.typeLabel}
            </span>
            <span className="mt-2 text-xs text-slate-500">
              {file.type === 'folder' ? file.modified : file.size}
            </span>
          </div>
        );
      })}
    </div>
  );
}
