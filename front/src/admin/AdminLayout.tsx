import { NavLink, Outlet } from 'react-router-dom';
import {
  Activity,
  Database,
  FileBox,
  Files,
  HardDrive,
  Key,
  LayoutDashboard,
  ListTodo,
  Settings,
  Share2,
  Users,
} from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { motion } from 'motion/react';

export default function AdminLayout() {
  const adminNavSections = [
    {
      title: '配置控制台',
      items: [{ to: 'dashboard', icon: LayoutDashboard, label: '配置首页' }],
    },
    {
      title: '核心配置',
      items: [
        { to: 'settings', icon: Settings, label: '系统设置' },
        { to: 'storage-policies', icon: Database, label: '存储策略' },
        { to: 'users', icon: Users, label: '用户策略' },
        { to: 'filesystem', icon: HardDrive, label: '文件系统快照' },
      ],
    },
    {
      title: '治理工具',
      items: [
        { to: 'files', icon: Files, label: '文件治理' },
        { to: 'file-blobs', icon: FileBox, label: '对象治理' },
        { to: 'shares', icon: Share2, label: '分享治理' },
        { to: 'tasks', icon: ListTodo, label: '任务监控' },
        { to: 'audits', icon: Activity, label: '审计日志' },
      ],
    },
    {
      title: '规划能力',
      items: [{ to: 'oauth-apps', icon: Key, label: '三方应用' }],
    },
  ];

  return (
    <div className="flex h-full w-full overflow-hidden">
      <aside className="w-64 flex-shrink-0 border-r border-white/10 bg-white/5 dark:bg-black/20 flex flex-col z-10">
        <div className="px-8 py-8">
          <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">配置控制台</h2>
          <p className="mt-3 text-[9px] font-black uppercase tracking-[0.2em] opacity-20">先改配置，再做治理</p>
        </div>
        <nav className="flex-1 overflow-y-auto px-4 pb-8 custom-scrollbar">
          {adminNavSections.map((section) => (
            <div key={section.title} className="mb-7">
              <div className="px-4 pb-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">{section.title}</div>
              <div className="space-y-1">
                {section.items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-3 px-4 py-3 rounded-lg text-xs font-black uppercase tracking-widest transition-all duration-300',
                        isActive
                          ? 'bg-blue-600/10 text-blue-600 dark:text-blue-400 shadow-sm border border-blue-500/10'
                          : 'text-gray-700 dark:text-gray-200 hover:bg-white/10 dark:hover:bg-white/5 opacity-60 hover:opacity-100',
                      )
                    }
                  >
                    <item.icon className="h-4 w-4" />
                    {item.label}
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
        </nav>
      </aside>

      <main className="flex-1 overflow-hidden relative">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="h-full w-full overflow-y-auto custom-scrollbar"
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
