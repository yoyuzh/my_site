import React from 'react';

type PageSectionHeaderProps = {
  title: string;
  description?: string;
  actions?: React.ReactNode;
};

const PageSectionHeader: React.FC<PageSectionHeaderProps> = ({
  title,
  description,
  actions,
}) => {
  return (
    <div className="flex flex-col gap-4 border-b border-slate-200/70 px-6 py-5 dark:border-white/10 lg:flex-row lg:items-center lg:justify-between">
      <div className="min-w-0">
        <h3 className="text-lg font-semibold text-slate-900 dark:text-white">{title}</h3>
        {description ? (
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{description}</p>
        ) : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  );
};

export default PageSectionHeader;
