import React from 'react';
import { Folder } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { FilesSearchPanel } from './FilesSearchPanel';
import { FileListView } from './FileListView';
import { FileGridView } from './FileGridView';
import type { UiFile } from './file-types';
import type { FileMetadata } from '@/src/lib/types';

export function FilesMainPane({
  currentPath,
  currentFiles,
  shareStatus,
  viewMode,
  isSearchActive,
  searchQuery,
  searchLoading,
  searchError,
  searchResults,
  selectedSearchFile,
  selectedFile,
  activeDropdown,
  onViewModeChange,
  onSearchQueryChange,
  onSearchSubmit,
  onClearSearch,
  onFileClick,
  onFileDoubleClick,
  onSearchFileClick,
  onSearchFileDoubleClick,
  onToggleDropdown,
  onDownload,
  onShare,
  onMove,
  onCopy,
  onRename,
  onDelete,
  onCloseDropdown,
}: {
  currentPath: string[];
  currentFiles: UiFile[];
  shareStatus: string;
  viewMode: 'list' | 'grid';
  isSearchActive: boolean;
  searchQuery: string;
  searchLoading: boolean;
  searchError: string;
  searchResults: FileMetadata[] | null;
  selectedSearchFile: FileMetadata | null;
  selectedFile: UiFile | null;
  activeDropdown: number | null;
  onViewModeChange: (mode: 'list' | 'grid') => void;
  onSearchQueryChange: (query: string) => void;
  onSearchSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onClearSearch: () => void;
  onFileClick: (file: UiFile) => void;
  onFileDoubleClick: (file: UiFile) => void;
  onSearchFileClick: (file: FileMetadata) => void;
  onSearchFileDoubleClick: (file: FileMetadata) => void;
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
    <div className="flex-1 flex flex-col h-full overflow-hidden bg-transparent">
      <FilesSearchPanel
        searchQuery={searchQuery}
        searchLoading={searchLoading}
        isSearchActive={isSearchActive}
        searchError={searchError}
        onSearchQueryChange={onSearchQueryChange}
        onSearchSubmit={onSearchSubmit}
        onClearSearch={onClearSearch}
      />

      {isSearchActive ? (
        <div className="flex-1 overflow-y-auto p-0">
          {searchLoading ? (
            <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
              <Folder className="h-12 w-12 opacity-20" />
              <p className="text-sm">搜索中...</p>
            </div>
          ) : (searchResults?.length ?? 0) === 0 ? (
            <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
              <Folder className="h-12 w-12 opacity-20" />
              <p className="text-sm">未找到匹配项</p>
            </div>
          ) : viewMode === 'list' ? (
            <FileListView
              files={searchResults!.map(f => ({ ...f, typeLabel: f.contentType || 'unknown', originalPath: f.path, modified: f.createdAt } as any))}
              selectedFileId={selectedSearchFile?.id ?? null}
              activeDropdown={activeDropdown}
              isSearchResult={true}
              onFileClick={onSearchFileClick}
              onFileDoubleClick={onSearchFileDoubleClick}
              onToggleDropdown={onToggleDropdown}
              onDownload={onDownload}
              onShare={onShare}
              onMove={onMove}
              onCopy={onCopy}
              onRename={onRename}
              onDelete={onDelete}
              onCloseDropdown={onCloseDropdown}
            />
          ) : (
            <FileGridView
              files={searchResults!.map(f => ({ ...f, typeLabel: f.contentType || 'unknown', originalPath: f.path, modified: f.createdAt } as any))}
              selectedFileId={selectedSearchFile?.id ?? null}
              activeDropdown={activeDropdown}
              isSearchResult={true}
              onFileClick={onSearchFileClick}
              onFileDoubleClick={onSearchFileDoubleClick}
              onToggleDropdown={onToggleDropdown}
              onDownload={onDownload}
              onShare={onShare}
              onMove={onMove}
              onCopy={onCopy}
              onRename={onRename}
              onDelete={onDelete}
              onCloseDropdown={onCloseDropdown}
            />
          )}
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto p-0 md:p-4">
          {currentFiles.length === 0 ? (
            <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
              <Folder className="w-12 h-12 opacity-20" />
              <p className="text-sm">此文件夹为空</p>
            </div>
          ) : viewMode === 'list' ? (
            <FileListView
              files={currentFiles}
              selectedFileId={selectedFile?.id ?? null}
              activeDropdown={activeDropdown}
              isSearchResult={false}
              onFileClick={onFileClick}
              onFileDoubleClick={onFileDoubleClick}
              onToggleDropdown={onToggleDropdown}
              onDownload={onDownload}
              onShare={onShare}
              onMove={onMove}
              onCopy={onCopy}
              onRename={onRename}
              onDelete={onDelete}
              onCloseDropdown={onCloseDropdown}
            />
          ) : (
            <FileGridView
              files={currentFiles}
              selectedFileId={selectedFile?.id ?? null}
              activeDropdown={activeDropdown}
              isSearchResult={false}
              onFileClick={onFileClick}
              onFileDoubleClick={onFileDoubleClick}
              onToggleDropdown={onToggleDropdown}
              onDownload={onDownload}
              onShare={onShare}
              onMove={onMove}
              onCopy={onCopy}
              onRename={onRename}
              onDelete={onDelete}
              onCloseDropdown={onCloseDropdown}
            />
          )}
        </div>
      )}
    </div>
  );
}
