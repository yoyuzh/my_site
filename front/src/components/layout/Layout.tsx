import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LayoutDashboard, FolderOpen, GraduationCap, Gamepad2, LogOut } from 'lucide-react';

import { clearStoredSession } from '@/src/lib/session';
import { cn } from '@/src/lib/utils';

const NAV_ITEMS = [
  { name: '总览', path: '/overview', icon: LayoutDashboard },
  { name: '网盘', path: '/files', icon: FolderOpen },
  { name: '教务', path: '/school', icon: GraduationCap },
  { name: '游戏', path: '/games', icon: Gamepad2 },
];

export function Layout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    clearStoredSession();
    navigate('/login');
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#07101D] text-white relative overflow-hidden">
      {/* Animated Gradient Background */}
      <div className="fixed inset-0 z-0 pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] rounded-full bg-[#336EFF] opacity-20 mix-blend-screen filter blur-[120px] animate-blob" />
        <div className="absolute top-[20%] right-[-10%] w-[50%] h-[50%] rounded-full bg-purple-600 opacity-20 mix-blend-screen filter blur-[120px] animate-blob animation-delay-2000" />
        <div className="absolute bottom-[-20%] left-[20%] w-[60%] h-[60%] rounded-full bg-indigo-600 opacity-20 mix-blend-screen filter blur-[120px] animate-blob animation-delay-4000" />
      </div>

      {/* Top Navigation */}
      <header className="sticky top-0 z-50 w-full glass-panel border-b border-white/10 bg-[#07101D]/60 backdrop-blur-xl">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          {/* Brand */}
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#336EFF] to-blue-400 flex items-center justify-center shadow-lg shadow-[#336EFF]/20">
              <span className="text-white font-bold text-lg leading-none">Y</span>
            </div>
            <div className="flex flex-col">
              <span className="text-white font-bold text-sm tracking-wider">YOYUZH.XYZ</span>
              <span className="text-slate-400 text-[10px] uppercase tracking-widest">Personal Portal</span>
            </div>
          </div>

          {/* Nav Links */}
          <nav className="hidden md:flex items-center gap-2">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200 relative overflow-hidden group',
                    isActive
                      ? 'text-white shadow-md shadow-[#336EFF]/20'
                      : 'text-slate-400 hover:text-white hover:bg-white/5'
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <div className="absolute inset-0 bg-[#336EFF] opacity-100 z-0" />
                    )}
                    <item.icon className="w-4 h-4 relative z-10" />
                    <span className="relative z-10">{item.name}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          {/* User / Actions */}
          <div className="flex items-center gap-4">
            <button
              onClick={handleLogout}
              className="text-slate-400 hover:text-white transition-colors p-2 rounded-xl hover:bg-white/5 relative z-10"
              aria-label="Logout"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 container mx-auto px-4 py-8 relative z-10">
        <Outlet />
      </main>
    </div>
  );
}
