import React from 'react';
import clsx from 'clsx';
import { ArrowLeft, ChevronRight, Menu, X } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import BackgroundEffects from '../BackgroundEffects';
import Topbar from '../Topbar';
import { adminNavGroups } from './adminNavigation';

interface AdminShellProps {
  children: React.ReactNode;
  title: string;
}

function isActivePath(pathname: string, path: string) {
  return pathname === path || pathname.startsWith(`${path}/`);
}

const AdminShell: React.FC<AdminShellProps> = ({ children, title }) => {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);

  React.useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [location.pathname]);

  const flatItems = adminNavGroups.flatMap((group) => group.items);
  const activeItem = flatItems.find((item) => isActivePath(location.pathname, item.path));
  const activeGroup = adminNavGroups.find((group) =>
    group.items.some((item) => isActivePath(location.pathname, item.path)),
  );

  return (
    <div className="min-h-screen bg-[#EEF3FB] text-text-primary-light transition-colors duration-300 dark:bg-[#090B10] dark:text-text-primary-dark">
      <Topbar meta={`管理中心 · ${activeItem?.label ?? title}`} />
      <BackgroundEffects />

      <div className="relative flex min-h-screen pt-[68px]">
        <div
          className={clsx(
            'fixed inset-0 z-40 bg-slate-950/45 backdrop-blur-sm transition-opacity lg:hidden',
            isMobileMenuOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
          )}
          onClick={() => setIsMobileMenuOpen(false)}
        />

        <aside
          className={clsx(
            'fixed inset-y-0 left-0 z-50 w-72 border-r border-slate-200/80 bg-white/92 backdrop-blur-xl transition-transform dark:border-white/10 dark:bg-[#0F131B]/92',
            'pt-[68px] lg:translate-x-0',
            isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full',
          )}
        >
          <div className="flex h-full flex-col">
            <div className="flex items-center justify-between border-b border-slate-200/70 px-5 py-4 dark:border-white/10 lg:hidden">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-400">Admin</p>
                <p className="mt-1 text-sm font-semibold text-slate-900 dark:text-white">治理导航</p>
              </div>
              <button
                type="button"
                onClick={() => setIsMobileMenuOpen(false)}
                className="flex h-10 w-10 items-center justify-center rounded-full text-slate-500 transition-colors hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-white/5"
                aria-label="Close admin navigation"
              >
                <X size={18} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-5">
              <div className="rounded-3xl border border-slate-200/70 bg-slate-50/85 px-4 py-4 dark:border-white/10 dark:bg-white/[0.03]">
                <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-400">Admin Console</p>
                <p className="mt-2 text-sm font-semibold text-slate-900 dark:text-white">
                  面向治理路径的集中入口
                </p>
              </div>

              <nav className="mt-5 space-y-5">
                {adminNavGroups.map((group) => (
                  <div key={group.label}>
                    <p className="px-3 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-400">
                      {group.label}
                    </p>
                    <div className="mt-2 space-y-1.5">
                      {group.items.map((item) => {
                        const isActive = isActivePath(location.pathname, item.path);
                        const Icon = item.icon;

                        return (
                          <Link
                            key={item.path}
                            to={item.path}
                            className={clsx(
                              'flex items-center gap-3 rounded-2xl px-3 py-3 text-sm font-medium transition-all',
                              isActive
                                ? 'bg-slate-900 text-white shadow-lg shadow-slate-900/10 dark:bg-white dark:text-slate-950'
                                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-white/5 dark:hover:text-white',
                            )}
                          >
                            <Icon size={18} />
                            <span className="flex-1">{item.label}</span>
                            <ChevronRight size={16} className={clsx(isActive ? 'opacity-100' : 'opacity-30')} />
                          </Link>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </nav>
            </div>

            <div className="border-t border-slate-200/70 p-4 dark:border-white/10">
              <Link
                to="/dashboard/files"
                className="flex items-center gap-3 rounded-2xl border border-slate-200/70 px-4 py-3 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 dark:border-white/10 dark:text-slate-200 dark:hover:bg-white/5"
              >
                <ArrowLeft size={18} />
                返回文件工作台
              </Link>
            </div>
          </div>
        </aside>

        <div className="flex w-full flex-1 flex-col lg:pl-72">
          <div className="sticky top-[68px] z-30 border-b border-slate-200/70 bg-[#EEF3FB]/92 backdrop-blur-xl dark:border-white/10 dark:bg-[#090B10]/92">
            <div className="mx-auto flex w-full max-w-[1600px] items-center justify-between gap-4 px-4 py-4 lg:px-6">
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-xs font-medium text-slate-500 dark:text-slate-400">
                  <span>管理中心</span>
                  {activeGroup ? (
                    <>
                      <ChevronRight size={14} />
                      <span>{activeGroup.label}</span>
                    </>
                  ) : null}
                </div>
                <h1 className="mt-1 truncate text-xl font-semibold text-slate-900 dark:text-white">{title}</h1>
              </div>

              <div className="flex items-center gap-3">
                <Link
                  to="/dashboard/files"
                  className="hidden rounded-full border border-slate-200/70 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-white dark:border-white/10 dark:text-slate-200 dark:hover:bg-white/5 sm:inline-flex"
                >
                  返回文件区
                </Link>
                <button
                  type="button"
                  onClick={() => setIsMobileMenuOpen(true)}
                  className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-slate-200/70 bg-white text-slate-700 shadow-sm transition-colors hover:bg-slate-50 dark:border-white/10 dark:bg-white/5 dark:text-slate-200 dark:hover:bg-white/10 lg:hidden"
                  aria-label="Open admin navigation"
                >
                  <Menu size={18} />
                </button>
              </div>
            </div>
          </div>

          <main className="mx-auto flex w-full max-w-[1600px] flex-1 flex-col px-4 py-5 lg:px-6 lg:py-6">
            <div className="min-h-[calc(100vh-180px)]">{children}</div>
          </main>
        </div>
      </div>
    </div>
  );
};

export default AdminShell;
