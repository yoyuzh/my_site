import React from 'react';

type WorkspaceHeaderProps = {
  title: string;
  eyebrow?: string;
  actions?: React.ReactNode;
};

const WorkspaceHeader: React.FC<WorkspaceHeaderProps> = ({ title, eyebrow, actions }) => {
  return (
    <header className="mb-6 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        {eyebrow ? (
          <p className="text-xs font-medium uppercase tracking-[0.24em] text-slate-400">
            {eyebrow}
          </p>
        ) : null}
        <h2 className="mt-1 text-3xl font-semibold tracking-tight text-slate-950 dark:text-white">
          {title}
        </h2>
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
};

export default WorkspaceHeader;
