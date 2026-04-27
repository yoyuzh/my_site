import React, { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Moon, Sun, User, Settings, Shield, LogOut, ChevronDown } from 'lucide-react';
import clsx from 'clsx';
import { useTheme } from '../hooks/useTheme';
import { canAccessAdmin, getSession, type PortalSession } from '../lib/session';
import { logout } from '../lib/auth';
import BrandMark from './BrandMark';

interface TopbarProps {
  meta?: string;
}

const Topbar: React.FC<TopbarProps> = ({ meta }) => {
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [session, setSessionState] = useState<PortalSession | null>(() => getSession());
  const menuRef = useRef<HTMLDivElement>(null);
  const user = session?.user;

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setShowUserMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    const handleSessionChanged = (event: Event) => {
      const detail = (event as CustomEvent<PortalSession | null>).detail;
      setSessionState(detail ?? getSession());
    };

    window.addEventListener('portal-session-changed', handleSessionChanged as EventListener);
    return () => {
      window.removeEventListener('portal-session-changed', handleSessionChanged as EventListener);
    };
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
    setShowUserMenu(false);
  };

  return (
    <header className="topbar-shell fixed inset-x-0 top-0 z-50">
      <div className="mx-auto flex h-[68px] max-w-[1600px] items-center justify-between px-4 lg:px-6">
        <Link to="/" className="min-w-0">
          <BrandMark
            size={38}
            subtitle="Personal Cloud"
            textClassName="hidden sm:block"
          />
        </Link>

        <div className="flex items-center gap-4">
          {meta && (
            <span className="text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark font-funnel">
              {meta}
            </span>
          )}

          <button
            type="button"
            aria-label="Toggle color theme"
            onClick={toggleTheme}
            className="flex h-10 w-10 items-center justify-center rounded-full text-text-secondary-light transition-colors hover:bg-black/5 dark:text-text-secondary-dark dark:hover:bg-white/5"
          >
            {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
          </button>

          {user && (
            <div className="relative" ref={menuRef}>
              <button
                type="button"
                onClick={() => setShowUserMenu(!showUserMenu)}
                className="flex items-center gap-2 rounded-full border border-white/10 bg-white/5 p-1 pl-1 pr-3 transition-all hover:bg-white/10 dark:border-white/5 dark:bg-white/5 dark:hover:bg-white/10"
              >
                <div className="h-8 w-8 overflow-hidden rounded-full bg-blue-500/10 text-blue-600 dark:bg-blue-400/10 dark:text-blue-400">
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt={user.displayName || user.username} className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center">
                      <User size={16} />
                    </div>
                  )}
                </div>
                <span className="hidden max-w-[100px] truncate text-sm font-medium text-text-primary-light dark:text-text-primary-dark sm:block">
                  {user.displayName || user.username}
                </span>
                <ChevronDown size={14} className={clsx("text-text-secondary-light transition-transform dark:text-text-secondary-dark", showUserMenu && "rotate-180")} />
              </button>

              {showUserMenu && (
                <div className="absolute right-0 mt-2 w-64 origin-top-right rounded-3xl border border-white/50 bg-white/95 p-2 shadow-2xl backdrop-blur-xl animate-in fade-in zoom-in-95 duration-200 dark:border-white/5 dark:bg-[#161922]/95">
                  <div className="px-4 py-4">
                    <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">当前账户</p>
                    <div className="mt-2 flex items-center gap-3">
                      <div className="h-10 w-10 overflow-hidden rounded-full bg-blue-500/10 text-blue-600">
                        {user.avatarUrl ? (
                          <img src={user.avatarUrl} alt={user.displayName || user.username} className="h-full w-full object-cover" />
                        ) : (
                          <div className="flex h-full w-full items-center justify-center">
                            <User size={20} />
                          </div>
                        )}
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-slate-900 dark:text-white">{user.displayName || user.username}</p>
                        <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user.email || `@${user.username}`}</p>
                      </div>
                    </div>
                  </div>

                  <div className="my-1 h-px bg-slate-100 dark:bg-white/5" />

                  <div className="space-y-1">
                    <Link
                      to="/dashboard/settings"
                      onClick={() => setShowUserMenu(false)}
                      className="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-white/5"
                    >
                      <Settings size={18} />
                      个人设置
                    </Link>

                    {canAccessAdmin(user.role) && (
                      <Link
                        to="/admin"
                        onClick={() => setShowUserMenu(false)}
                        className="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-white/5"
                      >
                        <Shield size={18} />
                        管理中心
                      </Link>
                    )}

                    <button
                      onClick={handleLogout}
                      className="flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10"
                    >
                      <LogOut size={18} />
                      安全退出
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default Topbar;
