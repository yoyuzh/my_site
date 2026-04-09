import React, { ReactNode } from 'react';

interface PageToolbarProps {
  title: ReactNode;
  actions?: ReactNode;
}

export function PageToolbar({ title, actions }: PageToolbarProps) {
  return (
    <div className="flex w-full items-center justify-between">
      <div className="flex items-center gap-3">
        {typeof title === 'string' ? (
          <h2 className="text-lg font-semibold text-white tracking-tight">{title}</h2>
        ) : (
          title
        )}
      </div>
      {actions && (
        <div className="flex items-center gap-2">
          {actions}
        </div>
      )}
    </div>
  );
}
