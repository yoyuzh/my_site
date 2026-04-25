import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Activity, ChevronDown, ChevronRight, Database, Folder, ListTodo, Send, Share2, Trash2 } from 'lucide-react';
import { useUserCapacity } from '../../api/queries';
import { formatBytes } from '../../lib/format';
import { WORKSPACE_FOLDER_TREE_SECTION_STORAGE_KEY } from '../../lib/workspace-folder-tree';
import WorkspaceFolderTree from './WorkspaceFolderTree';

const items = [
  { name: '文件 Files', path: '/dashboard/files', icon: Folder },
  { name: '任务 Tasks', path: '/dashboard/tasks', icon: ListTodo },
  { name: '分享 Shares', path: '/dashboard/shares', icon: Share2 },
  { name: '回收站 Recycle Bin', path: '/dashboard/recycle-bin', icon: Trash2 },
  { name: '快传 Transfer', path: '/dashboard/transfer-send', icon: Send },
];

function restoreFilesSectionCollapsed() {
  if (typeof window === 'undefined') {
    return false;
  }

  return window.localStorage.getItem(WORKSPACE_FOLDER_TREE_SECTION_STORAGE_KEY) === 'true';
}

const WorkspaceSidebar: React.FC<{ onNavigate?: () => void }> = ({ onNavigate }) => {
  const location = useLocation();
  const { data: capacity, isLoading, isError } = useUserCapacity();
  const [filesSectionCollapsed, setFilesSectionCollapsed] = useState(restoreFilesSectionCollapsed);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    window.localStorage.setItem(
      WORKSPACE_FOLDER_TREE_SECTION_STORAGE_KEY,
      filesSectionCollapsed ? 'true' : 'false',
    );
  }, [filesSectionCollapsed]);

  return (
    <aside className="surface-shell flex h-full w-[272px] flex-col p-4">
      <nav className="flex flex-1 flex-col">
        <div className="space-y-1">
          {items.map((item) => {
            const Icon = item.icon;
            const active =
              location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);
            const isFilesItem = item.path === '/dashboard/files';

            return (
              <div key={item.path}>
                {isFilesItem ? (
                  <>
                    <div
                      className={
                        active
                          ? 'flex items-center gap-2 rounded-2xl bg-slate-900 px-4 py-3 text-white dark:bg-white dark:text-slate-950'
                          : 'flex items-center gap-2 rounded-2xl px-4 py-3 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-white/5 dark:hover:text-white'
                      }
                    >
                      <Link
                        to={item.path}
                        onClick={onNavigate}
                        className="flex min-w-0 flex-1 items-center gap-3"
                      >
                        <Icon size={18} />
                        <span className="truncate text-sm font-medium">{item.name}</span>
                      </Link>
                      <button
                        type="button"
                        aria-label={filesSectionCollapsed ? 'Expand folders' : 'Collapse folders'}
                        className={
                          active
                            ? 'flex h-7 w-7 items-center justify-center rounded-xl text-white/80 transition hover:bg-white/10 hover:text-white dark:text-slate-600 dark:hover:bg-slate-200 dark:hover:text-slate-950'
                            : 'flex h-7 w-7 items-center justify-center rounded-xl text-slate-400 transition hover:bg-slate-200 hover:text-slate-700 dark:hover:bg-white/10 dark:hover:text-white'
                        }
                        onClick={() => {
                          setFilesSectionCollapsed((current) => !current);
                        }}
                      >
                        {filesSectionCollapsed ? <ChevronRight size={16} /> : <ChevronDown size={16} />}
                      </button>
                    </div>
                    {filesSectionCollapsed ? null : <WorkspaceFolderTree onNavigate={onNavigate} />}
                  </>
                ) : (
                  <Link
                    to={item.path}
                    onClick={onNavigate}
                    className={
                      active
                        ? 'flex items-center gap-3 rounded-2xl bg-slate-900 px-4 py-3 text-white dark:bg-white dark:text-slate-950'
                        : 'flex items-center gap-3 rounded-2xl px-4 py-3 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-white/5 dark:hover:text-white'
                    }
                  >
                    <Icon size={18} />
                    <span className="text-sm font-medium">{item.name}</span>
                  </Link>
                )}
              </div>
            );
          })}
        </div>

        <div className="mt-auto space-y-4">
          <div className="rounded-2xl bg-slate-50 p-4 dark:bg-white/5">
            <div className="mb-2 flex items-center gap-2 text-slate-500 dark:text-slate-400">
              <Database size={14} />
              <span className="text-xs font-medium">网盘已用</span>
            </div>
            {isLoading ? (
              <p className="text-xs text-slate-400">正在读取容量信息...</p>
            ) : isError ? (
              <p className="text-xs text-red-400">容量信息暂不可用</p>
            ) : (
              <div>
                <div className="text-sm font-semibold text-slate-900 dark:text-white">
                  {formatBytes(capacity?.usedBytes)}
                </div>
                <div className="mt-1 text-[10px] uppercase tracking-wider text-slate-400">
                  共 {formatBytes(capacity?.totalBytes)}
                </div>
              </div>
            )}
          </div>

          <div className="border-t border-slate-200/70 pt-4 dark:border-white/10">
            <Link
              to="/admin/home"
              onClick={onNavigate}
              className="flex items-center gap-3 rounded-2xl bg-brand-light/10 px-4 py-3 text-brand-light transition hover:bg-brand-light/20 dark:bg-brand-dark/10 dark:text-brand-dark dark:hover:bg-brand-dark/20"
            >
              <Activity size={18} />
              <span className="text-sm font-medium">管理面板 Admin</span>
            </Link>
          </div>
        </div>
      </nav>
    </aside>
  );
};

export default WorkspaceSidebar;
