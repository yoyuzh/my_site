import React from 'react';
import { ChevronDown, ChevronRight, Folder, Loader2, RefreshCw } from 'lucide-react';

export type WorkspaceFolderTreeNodeState = {
  path: string;
  name: string;
  childPaths: string[] | null;
  childrenStatus: 'unknown' | 'has-folders' | 'empty' | 'loading' | 'error';
};

interface WorkspaceFolderTreeNodeProps {
  node: WorkspaceFolderTreeNodeState;
  depth: number;
  currentPath: string | null;
  expanded: boolean;
  onSelect: (path: string) => void;
  onToggle: (path: string) => void;
  onRetry: (path: string) => void;
  children?: React.ReactNode;
}

const WorkspaceFolderTreeNode: React.FC<WorkspaceFolderTreeNodeProps> = ({
  node,
  depth,
  currentPath,
  expanded,
  onSelect,
  onToggle,
  onRetry,
  children,
}) => {
  const active = currentPath === node.path;
  const showToggle = node.childrenStatus === 'has-folders';
  const showLoading = node.childrenStatus === 'loading';
  const showRetry = node.childrenStatus === 'error';

  return (
    <div>
      <div
        className={
          active
            ? 'sidebar-tree-item-active'
            : 'sidebar-tree-item'
        }
        style={{ marginLeft: depth * 12 }}
      >
        {showToggle ? (
          <button
            type="button"
            aria-label={expanded ? 'Collapse folder' : 'Expand folder'}
            className={active ? 'sidebar-toggle-button-active h-5 w-5 rounded-md' : 'sidebar-toggle-button h-5 w-5 rounded-md'}
            onClick={(event) => {
              event.stopPropagation();
              onToggle(node.path);
            }}
          >
            {expanded ? <ChevronDown size={12} strokeWidth={2.5} /> : <ChevronRight size={12} strokeWidth={2.5} />}
          </button>
        ) : showLoading ? (
          <span className="flex h-5 w-5 items-center justify-center text-slate-400">
            <Loader2 size={12} className="animate-spin" />
          </span>
        ) : showRetry ? (
          <button
            type="button"
            aria-label="Retry loading folder"
            className="flex h-5 w-5 items-center justify-center rounded-md text-amber-500 transition hover:bg-amber-500/10"
            onClick={(event) => {
              event.stopPropagation();
              onRetry(node.path);
            }}
          >
            <RefreshCw size={12} />
          </button>
        ) : (
          <span className="block h-5 w-5" />
        )}

        <button
          type="button"
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
          onClick={() => onSelect(node.path)}
        >
          <Folder
            size={13}
            strokeWidth={2.5}
            className={active ? 'text-brand-light dark:text-brand-dark' : 'text-slate-400 dark:text-slate-500'}
          />
          <span className="truncate text-xs font-semibold">{node.name}</span>
        </button>
      </div>

      {expanded ? children : null}
    </div>
  );
};

export default WorkspaceFolderTreeNode;
