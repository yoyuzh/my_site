import React from 'react';
import BackgroundEffects from '../BackgroundEffects';
import Topbar from '../Topbar';

type PublicPageShellProps = React.PropsWithChildren<{
  meta: string;
  className?: string;
}>;

const PublicPageShell: React.FC<PublicPageShellProps> = ({
  meta,
  className,
  children,
}) => {
  return (
    <div className="min-h-screen bg-bg-light dark:bg-bg-dark">
      <Topbar meta={meta} />
      <BackgroundEffects />
      <main className={`mx-auto min-h-screen max-w-[1280px] px-4 pb-10 pt-[88px] lg:px-6 ${className ?? ''}`}>
        {children}
      </main>
    </div>
  );
};

export default PublicPageShell;
