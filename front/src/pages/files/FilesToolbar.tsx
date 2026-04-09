import React from 'react';
import { ChevronRight, List, LayoutGrid, Upload, FolderUp, Plus } from 'lucide-react';
import { Button } from '@/src/components/ui/button';
import { cn } from '@/src/lib/utils';

export function FilesToolbar({
  currentPath,
  shareStatus,
  viewMode,
  onNavigateToRoot,
  onBreadcrumbClick,
  onViewModeChange,
  onUploadClick,
  onUploadFolderClick,
  onCreateFolder,
  fileInputRef,
  directoryInputRef,
  onFileChange,
  onFolderChange,
}: {
  currentPath: string[];
  shareStatus: string;
  viewMode: 'list' | 'grid';
  onNavigateToRoot: () => void;
  onBreadcrumbClick: (index: number) => void;
  onViewModeChange: (mode: 'list' | 'grid') => void;
  onUploadClick: () => void;
  onUploadFolderClick: () => void;
  onCreateFolder: () => void;
  fileInputRef: React.RefObject<HTMLInputElement>;
  directoryInputRef: React.RefObject<HTMLInputElement>;
  onFileChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onFolderChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <>
      <div className="p-4 border-b border-white/10 flex items-center justify-between shrink-0">
        <div className="flex items-center text-sm text-slate-400">
          <button className="hover:text-white transition-colors" onClick={onNavigateToRoot}>
            网盘
          </button>
          {currentPath.map((pathItem, index) => (
            <React.Fragment key={index}>
              <ChevronRight className="w-4 h-4 mx-1" />
              <button
                onClick={() => onBreadcrumbClick(index)}
                className={cn('transition-colors', index === currentPath.length - 1 ? 'text-white font-medium' : 'hover:text-white')}
              >
                {pathItem}
              </button>
            </React.Fragment>
          ))}
        </div>
        {shareStatus ? (
          <div className="hidden max-w-xs truncate text-xs text-emerald-300 md:block">{shareStatus}</div>
        ) : null}
        <div className="flex items-center gap-2 bg-black/20 p-1 rounded-lg">
          <button
            onClick={() => onViewModeChange('list')}
            className={cn(
              'p-1.5 rounded-md transition-colors',
              viewMode === 'list' ? 'bg-white/10 text-white' : 'text-slate-400 hover:text-white',
            )}
          >
            <List className="w-4 h-4" />
          </button>
          <button
            onClick={() => onViewModeChange('grid')}
            className={cn(
              'p-1.5 rounded-md transition-colors',
              viewMode === 'grid' ? 'bg-white/10 text-white' : 'text-slate-400 hover:text-white',
            )}
          >
            <LayoutGrid className="w-4 h-4" />
          </button>
        </div>
      </div>
      <div className="p-4 border-t border-white/10 flex items-center gap-3 shrink-0 bg-white/[0.01]">
        <Button variant="default" className="gap-2" onClick={onUploadClick}>
          <Upload className="w-4 h-4" /> 上传文件
        </Button>
        <Button variant="outline" className="gap-2" onClick={onUploadFolderClick}>
          <FolderUp className="w-4 h-4" /> 上传文件夹
        </Button>
        <Button variant="outline" className="gap-2" onClick={onCreateFolder}>
          <Plus className="w-4 h-4" /> 新建文件夹
        </Button>
        <input ref={fileInputRef} type="file" multiple className="hidden" onChange={onFileChange} />
        {/* @ts-ignore - directory attributes are non-standard but work */}
        <input ref={directoryInputRef} type="file" multiple directory="" webkitdirectory="" className="hidden" onChange={onFolderChange} />
      </div>
    </>
  );
}
