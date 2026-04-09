import React from 'react';
import { Card, CardContent } from '@/src/components/ui/card';
import { Folder, ChevronDown, ChevronRight, RotateCcw } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { getFilesSidebarFooterEntries, RECYCLE_BIN_RETENTION_DAYS, RECYCLE_BIN_ROUTE } from '../recycle-bin-state';
import type { DirectoryTreeNode } from '../files-tree';
import { useLocation, useNavigate } from 'react-router-dom';

function DirectoryTreeItem({
  node,
  onSelect,
  onToggle,
}: {
  node: DirectoryTreeNode;
  onSelect: (path: string[]) => void;
  onToggle: (path: string[]) => void;
}) {
  return (
    <div>
      <div
        className={cn(
          'group flex items-center gap-1 rounded-xl px-2 py-1.5 transition-colors',
          node.active ? 'bg-[#336EFF]/15' : 'hover:bg-white/5',
        )}
        style={{ paddingLeft: `${node.depth * 14 + 8}px` }}
      >
        <button
          type="button"
          className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-white/5 hover:text-white"
          onClick={() => onToggle(node.path)}
          aria-label={`${node.expanded ? '收起' : '展开'} ${node.name}`}
        >
          {node.expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>
        <button
          type="button"
          className={cn(
            'flex min-w-0 flex-1 items-center gap-2 rounded-lg px-2 py-1 text-left text-sm transition-colors',
            node.active ? 'text-[#336EFF]' : 'text-slate-300 hover:text-white',
          )}
          onClick={() => onSelect(node.path)}
        >
          <Folder className={cn('h-4 w-4 shrink-0', node.active ? 'text-[#336EFF]' : 'text-slate-500')} />
          <span className="truncate">{node.name}</span>
        </button>
      </div>
      {node.expanded ? node.children.map((child) => (
        <DirectoryTreeItem key={child.id} node={child} onSelect={onSelect} onToggle={onToggle} />
      )) : null}
    </div>
  );
}

export function FilesDirectoryRail({
  currentPath,
  directoryTree,
  onNavigateToPath,
  onDirectoryToggle,
}: {
  currentPath: string[];
  directoryTree: DirectoryTreeNode[];
  onNavigateToPath: (pathParts: string[]) => void;
  onDirectoryToggle: (pathParts: string[]) => void;
}) {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Card className="w-full lg:w-64 shrink-0 flex flex-col h-full overflow-hidden">
      <CardContent className="flex h-full flex-col p-4">
        <div className="min-h-0 flex-1 space-y-2">
          <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">网盘目录</p>
          <div className="flex min-h-0 flex-1 flex-col rounded-2xl border border-white/5 bg-black/20 p-2">
            <button
              type="button"
              onClick={() => onNavigateToPath([])}
              className={cn(
                'flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm font-medium transition-colors',
                currentPath.length === 0 ? 'bg-[#336EFF]/15 text-[#336EFF]' : 'text-slate-200 hover:bg-white/5 hover:text-white',
              )}
            >
              <Folder className={cn('h-4 w-4', currentPath.length === 0 ? 'text-[#336EFF]' : 'text-slate-500')} />
              <span className="truncate">网盘</span>
            </button>
            <div className="mt-1 min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
              {directoryTree.map((node) => (
                <DirectoryTreeItem
                  key={node.id}
                  node={node}
                  onSelect={onNavigateToPath}
                  onToggle={onDirectoryToggle}
                />
              ))}
            </div>
          </div>
        </div>
        <div className="mt-4 border-t border-white/10 pt-4">
          {getFilesSidebarFooterEntries().map((entry) => {
            const isActive = location.pathname === entry.path || location.pathname === RECYCLE_BIN_ROUTE;
            return (
              <button
                key={entry.path}
                type="button"
                onClick={() => navigate(entry.path)}
                className={cn(
                  'flex w-full items-center gap-3 rounded-2xl border px-3 py-3 text-left text-sm transition-colors',
                  isActive
                    ? 'border-[#336EFF]/30 bg-[#336EFF]/15 text-[#7ea6ff]'
                    : 'border-white/10 bg-white/5 text-slate-300 hover:bg-white/10 hover:text-white',
                )}
              >
                <RotateCcw className={cn('h-4 w-4', isActive ? 'text-[#7ea6ff]' : 'text-slate-400')} />
                <div className="min-w-0">
                  <p className="font-medium">{entry.label}</p>
                  <p className="truncate text-xs text-slate-500">删除后保留 {RECYCLE_BIN_RETENTION_DAYS} 天</p>
                </div>
              </button>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
