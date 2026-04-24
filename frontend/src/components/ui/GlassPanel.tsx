import React from 'react';
import { clsx } from 'clsx';

type GlassPanelProps = React.PropsWithChildren<{
  className?: string;
}>;

const GlassPanel: React.FC<GlassPanelProps> = ({ children, className }) => {
  return (
    <section
      className={clsx(
        'rounded-[24px] border border-white/50 bg-white/75 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-xl dark:border-white/10 dark:bg-[#0F1117]/78 dark:shadow-[0_24px_80px_rgba(0,0,0,0.35)]',
        className,
      )}
    >
      {children}
    </section>
  );
};

export default GlassPanel;
