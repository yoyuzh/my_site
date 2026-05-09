import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Activity, Users, Settings, Database, HardDrive, ShieldAlert, ListChecks, Menu, ArrowLeft, Network, Shield, Share2, Server, FolderKey, FileKey } from 'lucide-react';
import Topbar from './Topbar';
import BackgroundEffects from './BackgroundEffects';

interface AdminLayoutProps {
  children: React.ReactNode;
  title: string;
}

const AdminLayout: React.FC<AdminLayoutProps> = ({ children, title }) => {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);

  const navItems = [
    { name: '管理面板', path: '/admin/home', icon: <Activity size={20} /> },
    { name: '参数设置', path: '/admin/settings', icon: <Settings size={20} /> },
    { name: '用户管理', path: '/admin/user', icon: <Users size={20} /> },
    { name: '用户组', path: '/admin/group', icon: <Shield size={20} /> },
    { name: '存储策略', path: '/admin/policy', icon: <Database size={20} /> },
    { name: '存储节点', path: '/admin/node', icon: <Server size={20} /> },
    { name: 'OAuth 配置', path: '/admin/oauth', icon: <Network size={20} /> },
    { name: '物理文件', path: '/admin/file', icon: <FileKey size={20} /> },
    { name: '文件记录', path: '/admin/blob', icon: <FolderKey size={20} /> },
    { name: '离线下载', path: '/admin/task', icon: <ListChecks size={20} /> },
    { name: '分享管理', path: '/admin/share', icon: <Share2 size={20} /> },
    { name: '文件系统', path: '/admin/filesystem', icon: <HardDrive size={20} /> },
  ];

  return (
    <div className="min-h-screen bg-[#F4F7FB] dark:bg-[#0A0A0A] transition-colors duration-300">
      <Topbar meta={`管理中心 · ${title}`} />
      <BackgroundEffects />
      
      <div className="pt-[72px] flex h-screen overflow-hidden">
        {/* Sidebar */}
        <aside className={`fixed lg:static inset-y-0 left-0 z-40 w-64 bg-white/50 dark:bg-[#111117]/50 backdrop-blur-xl border-r border-[#D9E3F2] dark:border-[#222233] transform ${isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0 transition-transform duration-300 pt-[72px] lg:pt-0 flex flex-col`}>
          <nav className="p-4 space-y-1 flex-1 overflow-y-auto">
            <div className="mb-4 px-4 pb-2 border-b border-[#D9E3F2] dark:border-[#222233]">
              <span className="text-xs font-bold tracking-wider text-text-muted-light dark:text-text-muted-dark">管理功能</span>
            </div>
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
          <div className="p-4 border-t border-[#D9E3F2] dark:border-[#222233]">
             <Link
                to="/dashboard"
                className="flex items-center gap-3 px-4 py-3 rounded-lg text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5 transition-all duration-200"
              >
                <ArrowLeft size={20} />
                <span className="font-medium text-sm">返回个人面板</span>
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
          <header className="mb-8 flex justify-between items-center">
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

export default AdminLayout;
