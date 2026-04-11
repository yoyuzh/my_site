import { useEffect, useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { HardDrive, LayoutDashboard, ListTodo, LogOut, Send, Share2, Trash2 } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { ThemeToggle } from '@/src/components/ThemeToggle';
import { logout } from '@/src/lib/auth';
import { getSession, type PortalSession } from '@/src/lib/session';

export default function MobileLayout() {
  const navigate = useNavigate();
  const [session, setSession] = useState<PortalSession | null>(() => getSession());

  useEffect(() => {
    const handleSessionChange = (event: Event) => {
      const customEvent = event as CustomEvent<PortalSession | null>;
      setSession(customEvent.detail ?? getSession());
    };
    window.addEventListener('portal-session-changed', handleSessionChange);
    return () => window.removeEventListener('portal-session-changed', handleSessionChange);
  }, []);

  useEffect(() => {
    if (!session) {
      navigate('/login', { replace: true });
    }
  }, [navigate, session]);

  const navItems = [
    { to: '/overview', icon: LayoutDashboard, label: '概览' },
    { to: '/files', icon: HardDrive, label: '网盘' },
    { to: '/tasks', icon: ListTodo, label: '任务' },
    { to: '/shares', icon: Share2, label: '分享' },
    { to: '/recycle-bin', icon: Trash2, label: '回收站' },
    { to: '/transfer', icon: Send, label: '快传' },
  ];

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-screen w-full flex-col overflow-hidden bg-aurora text-gray-900 dark:text-gray-100 transition-colors"
    >
      <header className="fixed top-4 left-4 right-4 z-50 flex items-center justify-between glass-panel rounded-lg px-6 py-4 shadow-xl border-white/20">
        <div>
          <div className="text-sm font-black tracking-tight text-blue-600 dark:text-blue-400 uppercase">移动端门户</div>
          <div className="text-sm font-bold opacity-80 dark:opacity-90 uppercase tracking-[0.2em]">{session?.user.username || '游客用户'}</div>
        </div>
        <div className="flex items-center gap-3">
          <ThemeToggle />
          <button
            type="button"
            onClick={() => {
              logout();
              navigate('/login');
            }}
            className="rounded-lg p-2.5 glass-panel hover:bg-red-500/10 text-gray-700 dark:text-gray-200 hover:text-red-500 transition-all border-white/10"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </header>

      <main className="relative flex-1 overflow-y-auto pt-28 pb-28 px-4">
        <Outlet />
      </main>

      <nav className="fixed bottom-6 left-4 right-4 z-50 flex h-20 items-center justify-around glass-panel rounded-lg px-4 shadow-2xl border-white/20">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              cn(
                'flex flex-col items-center justify-center gap-1.5 transition-all duration-300 relative',
                isActive ? 'scale-105' : 'opacity-70 grayscale hover:opacity-100 hover:grayscale-0',
              )
            }
          >
            {({ isActive }) => (
              <>
                <div className={cn(
                  'p-2.5 rounded-lg transition-all',
                  isActive ? 'bg-blue-600 text-white shadow-blue-500/30 shadow-lg' : 'text-gray-900 dark:text-gray-100'
                )}>
                  <item.icon className="h-4.5 w-4.5" />
                </div>
                <span className={cn(
                  'text-xs font-black uppercase tracking-[0.1em]',
                  isActive ? 'text-blue-600 dark:text-blue-400' : 'text-gray-700 dark:text-gray-200'
                )}>
                  {item.label}
                </span>
              </>
            )}
          </NavLink>
        ))}
      </nav>
    </motion.div>
  );
}
