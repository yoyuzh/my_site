import React from 'react';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { cn } from '@/src/lib/utils';
import { ellipsizeFileName } from '@/src/lib/file-name';
import { FileActionMenu } from './FileActionMenu';
import type { UiFile } from './file-types';
import type { FileMetadata } from '@/src/lib/types';
import { splitBackendPath } from './useFilesDirectoryState';

export function FileListView({
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
    <table className="w-full table-fixed text-left border-collapse">
      <thead>
        <tr className="text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-white/5">
          <th className={cn("pb-3 pl-4 font-medium", isSearchResult ? "w-[40%]" : "w-[44%]")}>名称</th>
          {isSearchResult && <th className="hidden pb-3 font-medium md:table-cell w-[26%]">位置</th>}
          <th className={cn("hidden pb-3 font-medium", isSearchResult ? "lg:table-cell w-[20%]" : "md:table-cell w-[22%]")}>修改时间</th>
          {!isSearchResult && <th className="hidden pb-3 font-medium lg:table-cell w-[14%]">类型</th>}
          <th className="pb-3 font-medium w-[10%]">大小</th>
          <th className={cn("pb-3", isSearchResult ? "w-[4%]" : "w-[10%]")}></th>
        </tr>
      </thead>
      <tbody>
        {files.map((file) => {
          const selected = selectedFileId === file.id;

          return (
            <tr
              key={file.id}
              onClick={() => onFileClick(file)}
              onDoubleClick={() => onFileDoubleClick(file)}
              className={cn(
                'group cursor-pointer transition-colors border-b border-white/5 last:border-0',
                selected ? 'bg-[#336EFF]/10' : 'hover:bg-white/[0.02]',
              )}
            >
              <td className="py-3 pl-4 max-w-0">
                <div className="flex min-w-0 items-center gap-3">
                  <FileTypeIcon type={file.type} size="sm" />
                  <div className="min-w-0">
                    <span
                      className={cn('block truncate text-sm font-medium', selected ? 'text-[#336EFF]' : 'text-slate-200')}
                      title={file.name}
                    >
                      {ellipsizeFileName(file.name, 48)}
                    </span>
                    {isSearchResult && file.originalPath && (
                      <span className="hidden truncate text-xs text-slate-500 md:block" title={file.originalPath}>
                        {file.originalPath}
                      </span>
                    )}
                  </div>
                </div>
              </td>
              {isSearchResult && (
                <td className="hidden py-3 text-sm text-slate-400 md:table-cell">{file.originalPath}</td>
              )}
              <td className={cn("hidden py-3 text-sm text-slate-400", isSearchResult ? "lg:table-cell" : "md:table-cell")}>
                {file.modified}
              </td>
              {!isSearchResult && (
                <td className="hidden py-3 text-sm text-slate-400 lg:table-cell">
                  <span
                    className={cn(
                      'inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-medium tracking-wide',
                      getFileTypeTheme(file.type).badgeClassName,
                    )}
                  >
                    {file.typeLabel}
                  </span>
                </td>
              )}
              <td className="py-3 text-sm text-slate-400 font-mono">{file.size}</td>
              <td className="py-3 pr-4 text-right">
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
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
