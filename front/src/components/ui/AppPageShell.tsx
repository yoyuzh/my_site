import React, { ReactNode } from 'react';
import { cn } from '@/src/lib/utils';

interface AppPageShellProps {
  toolbar: ReactNode;
  rail?: ReactNode;
  inspector?: ReactNode;
  children: ReactNode;
}

export function AppPageShell({ toolbar, rail, inspector, children }: AppPageShellProps) {
  return (
    <div className="flex-1 flex flex-col min-w-0 h-full overflow-hidden relative z-10 w-full bg-[#07101D]">
      {/* Top Toolbar */}
      <header className="h-14 shrink-0 border-b border-white/10 bg-[#0f172a]/70 flex items-center px-4 w-full z-20 backdrop-blur-xl">
        {toolbar}
      </header>

      {/* 3-Zone Content Segment */}
      <div className="flex-1 flex min-h-0 w-full overflow-hidden">
        {/* Nav Rail (e.g. Directory Tree) */}
        {rail && (
          <div className="w-64 shrink-0 border-r border-white/10 bg-[#0f172a]/20 h-full overflow-y-auto">
            {rail}
          </div>
        )}

        {/* Center Main Pane */}
        <main className="flex-1 min-w-0 h-full overflow-y-auto bg-transparent relative">
          {children}
        </main>

        {/* Inspector Panel (e.g. File Details) */}
        {inspector && (
          <div className="w-72 shrink-0 border-l border-white/10 bg-[#0f172a]/20 h-full overflow-y-auto hidden lg:block">
            {inspector}
          </div>
        )}
      </div>
    </div>
  );
}
