import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Folder, ListTodo, Share2, Trash2, Send, Menu } from 'lucide-react';
import Topbar from './Topbar';
import BackgroundEffects from './BackgroundEffects';

interface DashboardLayoutProps {
  children: React.ReactNode;
  title: string;
}

const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children, title }) => {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);

  const navItems = [
    { name: '总览 Overview', path: '/dashboard/overview', icon: <Home size={20} /> },
    { name: '文件 Files', path: '/dashboard/files', icon: <Folder size={20} /> },
    { name: '任务 Tasks', path: '/dashboard/tasks', icon: <ListTodo size={20} /> },
    { name: '分享 Shares', path: '/dashboard/shares', icon: <Share2 size={20} /> },
    { name: '回收站 Recycle Bin', path: '/dashboard/recycle-bin', icon: <Trash2 size={20} /> },
    { name: '快传 Transfer', path: '/dashboard/transfer-send', icon: <Send size={20} /> },
  ];

  return (
    <div className="min-h-screen bg-bg-light dark:bg-bg-dark transition-colors duration-300">
      <Topbar meta={title} />
      <BackgroundEffects />
      
      <div className="pt-[72px] flex h-screen overflow-hidden">
        {/* Sidebar */}
        <aside className={`fixed lg:static inset-y-0 left-0 z-40 w-64 bg-white/50 dark:bg-[#111117]/50 backdrop-blur-xl border-r border-[#D9E3F2] dark:border-[#222233] transform ${isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0 transition-transform duration-300 pt-[72px] lg:pt-0`}>
          <nav className="p-4 space-y-1">
            {navItems.map((item) => {
              const isActive = location.pathname === item.path || location.pathname.startsWith(item.path + '/');
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  onClick={() => setIsMobileMenuOpen(false)}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 ${
                    isActive 
                      ? 'bg-brand-light text-white dark:bg-brand-dark shadow-md' 
                      : 'text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5 hover:text-text-primary-light dark:hover:text-text-primary-dark'
                  }`}
                >
                  {item.icon}
                  <span className="font-medium text-sm">{item.name}</span>
                </Link>
              );
            })}
          </nav>
          
          {/* Admin Panel Entry */}
          <div className="p-4 border-t border-[#D9E3F2] dark:border-[#222233]">
             <Link
                to="/admin/home"
                className="flex items-center gap-3 px-4 py-3 rounded-lg text-brand-light dark:text-brand-dark bg-brand-light/10 dark:bg-brand-dark/10 hover:bg-brand-light/20 dark:hover:bg-brand-dark/20 transition-all duration-200"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m12 14 4-4"/><path d="M3.34 19a10 10 0 1 1 17.32 0"/></svg>
                <span className="font-medium text-sm font-funnel">管理面板 Admin</span>
             </Link>
          </div>
        </aside>

        {/* Mobile menu toggle */}
        <button 
          className="lg:hidden fixed bottom-6 right-6 z-50 bg-brand-light dark:bg-brand-dark text-white p-4 rounded-full shadow-lg"
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
        >
          <Menu size={24} />
        </button>

        {/* Main Content */}
        <main className="flex-1 overflow-y-auto p-6 lg:p-10 animate-fade-in-up">
          <header className="mb-8">
            <h2 className="text-3xl font-bold text-text-primary-light dark:text-white leading-tight">
              {title}
            </h2>
          </header>
          {children}
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
