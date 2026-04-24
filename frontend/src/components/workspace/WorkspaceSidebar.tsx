import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Activity, Folder, Home, ListTodo, Send, Share2, Trash2 } from 'lucide-react';

const items = [
  { name: '总览 Overview', path: '/dashboard/overview', icon: Home },
  { name: '文件 Files', path: '/dashboard/files', icon: Folder },
  { name: '任务 Tasks', path: '/dashboard/tasks', icon: ListTodo },
  { name: '分享 Shares', path: '/dashboard/shares', icon: Share2 },
  { name: '回收站 Recycle Bin', path: '/dashboard/recycle-bin', icon: Trash2 },
  { name: '快传 Transfer', path: '/dashboard/transfer-send', icon: Send },
];

const WorkspaceSidebar: React.FC<{ onNavigate?: () => void }> = ({ onNavigate }) => {
  const location = useLocation();

  return (
    <aside className="surface-shell flex h-full w-[272px] flex-col p-4">
      <nav className="flex flex-1 flex-col">
        <div className="space-y-1">
        {items.map((item) => {
          const Icon = item.icon;
          const active =
            location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);

          return (
            <Link
              key={item.path}
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
          );
        })}
        </div>

        <div className="mt-auto border-t border-slate-200/70 pt-4 dark:border-white/10">
          <Link
            to="/admin/home"
            onClick={onNavigate}
            className="flex items-center gap-3 rounded-2xl bg-brand-light/10 px-4 py-3 text-brand-light transition hover:bg-brand-light/20 dark:bg-brand-dark/10 dark:text-brand-dark dark:hover:bg-brand-dark/20"
          >
            <Activity size={18} />
            <span className="text-sm font-medium">管理面板 Admin</span>
          </Link>
        </div>
      </nav>
    </aside>
  );
};

export default WorkspaceSidebar;
