import React from 'react';
import { Menu, X } from 'lucide-react';
import Topbar from './Topbar';
import BackgroundEffects from './BackgroundEffects';
import WorkspaceHeader from './workspace/WorkspaceHeader';
import WorkspaceSidebar from './workspace/WorkspaceSidebar';

interface DashboardLayoutProps {
  children: React.ReactNode;
  title: string;
  hideHeader?: boolean;
}

const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children, title, hideHeader }) => {
  const [mobileOpen, setMobileOpen] = React.useState(false);

  return (
    <div className="flex h-screen flex-col bg-bg-light dark:bg-bg-dark overflow-hidden">
      <Topbar meta={title} />
      <BackgroundEffects />

      <div className="flex-1 px-4 pb-6 pt-[88px] lg:px-6 overflow-hidden">
        <div className="mx-auto flex h-full max-w-[1600px] gap-6">
          <div className="hidden h-full lg:block">
            <WorkspaceSidebar />
          </div>
          <main className="flex h-full min-w-0 flex-1 flex-col page-enter">
            {!hideHeader && (
              <WorkspaceHeader
                title={title}
                eyebrow="MY SITE WORKSPACE"
                actions={
                  <button
                    type="button"
                    aria-label="Open workspace navigation"
                    className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/50 bg-white/80 text-slate-900 dark:bg-[#0F1117]/90 dark:text-white lg:hidden"
                    onClick={() => setMobileOpen(true)}
                  >
                    <Menu size={18} />
                  </button>
                }
              />
            )}
            {hideHeader && (
              <div className="lg:hidden mb-6">
                <button
                  type="button"
                  aria-label="Open workspace navigation"
                  className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/50 bg-white/80 text-slate-900 dark:bg-[#0F1117]/90 dark:text-white"
                  onClick={() => setMobileOpen(true)}
                >
                  <Menu size={18} />
                </button>
              </div>
            )}
            <div className="flex-1 min-h-0">
              {children}
            </div>
          </main>
        </div>
      </div>

      {mobileOpen ? (
        <div
          className="fixed inset-0 z-50 bg-slate-950/35 p-4 lg:hidden"
          onClick={() => setMobileOpen(false)}
        >
          <div className="w-[272px]" onClick={(event) => event.stopPropagation()}>
            <div className="mb-3 flex justify-end">
              <button
                type="button"
                aria-label="Close workspace navigation"
                className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white/90 text-slate-900 dark:bg-[#0F1117]/90 dark:text-white"
                onClick={() => setMobileOpen(false)}
              >
                <X size={18} />
              </button>
            </div>
            <WorkspaceSidebar onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default DashboardLayout;
