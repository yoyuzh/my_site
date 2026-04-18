import { useEffect } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  HardDrive,
  LayoutDashboard,
  ListTodo,
  LogOut,
  Send,
  Settings,
  Share2,
  Trash2,
  Sun,
  Moon,
} from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { logout } from '@/src/lib/auth';
import { canAccessAdmin } from '@/src/lib/session';
import { useTheme } from '../ThemeProvider';
import { useSessionRuntime } from '@/src/hooks/use-session-runtime';
import { UploadCenter } from '../upload/UploadCenter';
import { TaskSummaryPanel } from '../tasks/TaskSummaryPanel';
import { realtimeRuntime } from '@/src/lib/realtime-runtime';





export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { session } = useSessionRuntime();
  const { theme, setTheme } = useTheme();


  useEffect(() => {
    if (!session && location.pathname !== '/transfer') {
      navigate('/login', { replace: true });
    }
    if (session) {
      realtimeRuntime.start();
    } else {
      realtimeRuntime.stop();
    }
    return () => realtimeRuntime.stop();
  }, [location.pathname, navigate, session]);


  const navItems = [
    { to: '/overview', icon: LayoutDashboard, label: '概览' },
    { to: '/files', icon: HardDrive, label: '网盘' },
    { to: '/tasks', icon: ListTodo, label: '任务' },
    { to: '/shares', icon: Share2, label: '分享' },
    { to: '/recycle-bin', icon: Trash2, label: '回收站' },
    { to: '/transfer', icon: Send, label: '快传' },
    ...(canAccessAdmin(session?.user.role)
      ? [{ to: '/admin/dashboard', icon: Settings, label: '后台' }]
      : []),
  ];

  return (
    <div className="flex h-screen w-full bg-aurora text-gray-900 dark:text-gray-100 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-68 flex-shrink-0 border-r border-white/20 dark:border-white/10 bg-white/40 dark:bg-black/40 backdrop-blur-2xl flex flex-col z-20 shadow-xl">
        <div className="h-24 flex items-center justify-between px-8 border-b border-white/10">
          <div className="flex items-center gap-6">
            <TaskSummaryPanel />
            <div className="w-10 h-10 bg-blue-600 rounded-lg flex items-center justify-center text-white font-black shadow-lg text-lg tracking-tighter">P</div>
            <span className="text-2xl font-black tracking-tight uppercase">门户</span>
          </div>
          <button 
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="p-2.5 rounded-lg glass-panel hover:bg-white/50 transition-all font-bold"
          >
            {theme === 'dark' ? <Sun className="w-5 h-5 text-yellow-300" /> : <Moon className="w-5 h-5 text-gray-700" />}
          </button>
        </div>
        
        <div className="border-b border-white/10 px-8 py-6">
          <div className="text-sm font-black uppercase tracking-[0.2em] opacity-70 mb-1">当前账号</div>
          <div className="text-sm font-black truncate">
            {session?.user.displayName || session?.user.username || '游客用户'}
          </div>
          <div className="truncate text-sm font-bold opacity-80 dark:opacity-90 flex items-center gap-1.5 mt-2 uppercase tracking-tight">
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-green-500 shadow-[0_0_10px_rgba(34,197,94,0.6)] animate-pulse"></span>
            {session?.user.email || '未登录'}
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto py-8 px-5 space-y-1.5">
          <div className="px-3 mb-2 text-xs font-black uppercase tracking-[0.3em] opacity-70">主要功能</div>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 px-4 py-3.5 rounded-lg text-sm font-black uppercase tracking-widest transition-all duration-300 group",
                  isActive 
                    ? "glass-panel-no-hover bg-white/60 dark:bg-white/10 shadow-lg text-blue-600 dark:text-blue-400 border-white/40" 
                    : "text-gray-700 dark:text-gray-200 hover:bg-white/30 dark:hover:bg-white/5 hover:translate-x-1"
                )
              }
            >
              <item.icon className={cn("h-4 w-4 transition-colors group-hover:text-blue-500")} />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-white/10 p-6">
          <button
            type="button"
            onClick={() => {
              logout();
              navigate('/login');
            }}
            className="flex w-full items-center gap-3 rounded-lg px-4 py-4 text-sm font-black uppercase tracking-[0.2em] text-gray-700 dark:text-gray-200 hover:text-red-500 transition-all hover:bg-white/20 dark:hover:bg-white/5"
          >
            <LogOut className="h-4 w-4 opacity-60" />
            退出登录
          </button>
        </div>
      </aside>
      <main className="relative flex min-w-0 flex-1 flex-col overflow-hidden z-10">
        <Outlet />
      </main>

      <UploadCenter />
    </div>
  );
}
