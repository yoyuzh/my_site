import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  Activity,
  ChevronDown,
  ChevronRight,
  CloudDownload,
  Database,
  FileText,
  Home,
  Image as ImageIcon,
  Link2,
  ListTodo,
  Music2,
  Send,
  Share2,
  Trash2,
  UsersRound,
  Video,
} from 'lucide-react';
import { useUserCapacity } from '../../api/queries';
import { formatBytes } from '../../lib/format';
import { WORKSPACE_FOLDER_TREE_SECTION_STORAGE_KEY } from '../../lib/workspace-folder-tree';
import WorkspaceFolderTree from './WorkspaceFolderTree';

const items = [
  { name: '我的文件', path: '/dashboard/files', icon: Home, expandable: true },
  { name: '图片', path: '/dashboard/images', icon: ImageIcon },
  { name: '视频', path: '/dashboard/videos', icon: Video },
  { name: '音乐', path: '/dashboard/music', icon: Music2 },
  { name: '文档', path: '/dashboard/documents', icon: FileText },
  { name: '与我共享', path: '/dashboard/shared-with-me', icon: UsersRound },
  { name: '回收站', path: '/dashboard/recycle-bin', icon: Trash2 },
  { name: '我的分享', path: '/dashboard/shares', icon: Share2 },
  { name: '连接与挂载', path: '/dashboard/connections', icon: Link2 },
  { name: '后台任务', path: '/dashboard/tasks', icon: ListTodo },
  { name: '离线下载', path: '/dashboard/offline-downloads', icon: CloudDownload },
  { name: '快传', path: '/dashboard/transfer-send', icon: Send },
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
    <aside className="sidebar-glass flex h-full w-[272px] flex-col overflow-hidden p-4">
      <nav className="flex min-h-0 flex-1 flex-col">
        <div className="min-h-0 flex-1 overflow-y-auto pr-1">
          <div className="space-y-1 pb-5">
            {items.map((item) => {
              const Icon = item.icon;
              const active =
                location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);
              const isFilesItem = item.expandable === true;

              return (
                <div key={item.path}>
                  {isFilesItem ? (
                    <>
                      <div
                        className={
                          active
                            ? 'sidebar-nav-item-active gap-2'
                            : 'sidebar-nav-item gap-2'
                        }
                      >
                        <Link
                          to={item.path}
                          onClick={onNavigate}
                          className="flex min-w-0 flex-1 items-center gap-3"
                        >
                          <Icon size={16} />
                          <span className="truncate text-[13px] font-medium">{item.name}</span>
                        </Link>
                        <button
                          type="button"
                          aria-label={filesSectionCollapsed ? 'Expand folders' : 'Collapse folders'}
                          className={
                            active
                              ? 'sidebar-toggle-button-active'
                              : 'sidebar-toggle-button'
                          }
                          onClick={() => {
                            setFilesSectionCollapsed((current) => !current);
                          }}
                        >
                          {filesSectionCollapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
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
                          ? 'sidebar-nav-item-active'
                          : 'sidebar-nav-item'
                      }
                    >
                      <Icon size={16} />
                      <span className="text-[13px] font-medium">{item.name}</span>
                    </Link>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        <div className="relative z-10 mt-3 shrink-0 pt-6">
          <div className="pointer-events-none absolute inset-x-0 -top-8 h-12 bg-gradient-to-b from-transparent to-white/60 dark:to-[rgba(12,16,22,0.6)]" />
          <div className="glass-tray p-3">
            <div className="space-y-3">
              <div className="glass-card p-4">
                <div className="mb-2 flex items-center gap-2 text-slate-500/80 dark:text-slate-400/70">
                  <Database size={14} strokeWidth={2.5} />
                  <span className="text-xs font-semibold tracking-[0.08em]">网盘已用</span>
                </div>
                {isLoading ? (
                  <p className="text-xs text-slate-400 dark:text-slate-500">正在读取容量信息...</p>
                ) : isError ? (
                  <p className="text-xs text-red-400/80">容量信息暂不可用</p>
                ) : (
                  <div>
                    <div className="text-[15px] font-bold tracking-tight text-slate-900 dark:text-white/90">
                      {formatBytes(capacity?.usedBytes)}
                    </div>
                    <div className="mt-0.5 text-[10px] font-medium text-slate-400 dark:text-slate-500">
                      共 {formatBytes(capacity?.totalBytes)}
                    </div>
                  </div>
                )}
              </div>

              <div className="pt-1">
                <Link
                  to="/admin/home"
                  onClick={onNavigate}
                  className="sidebar-nav-item bg-brand-light/10 text-brand-light hover:bg-brand-light/20 dark:bg-brand-dark/15 dark:text-brand-dark dark:hover:bg-brand-dark/25"
                >
                  <Activity size={18} strokeWidth={2} />
                  <span className="text-sm font-semibold">管理面板 Admin</span>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </nav>
    </aside>
  );
};

export default WorkspaceSidebar;
