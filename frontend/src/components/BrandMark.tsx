import React from 'react';
import clsx from 'clsx';

interface BrandMarkProps {
  title?: string;
  subtitle?: string;
  size?: number;
  className?: string;
  textClassName?: string;
}

const BrandMark: React.FC<BrandMarkProps> = ({
  title = 'YOYUZH.XYZ',
  subtitle,
  size = 40,
  className,
  textClassName,
}) => {
  return (
    <div className={clsx('flex items-center gap-3', className)}>
      <img
        src="/icons/brand-mark.svg"
        alt={`${title} brand mark`}
        width={size}
        height={size}
        className="shrink-0 rounded-[22%]"
      />
      <div className={clsx('min-w-0', textClassName)}>
        <p className="truncate text-[17px] font-extrabold tracking-tight text-text-primary-light dark:text-white">
          {title}
        </p>
        {subtitle ? (
          <p className="truncate text-[11px] font-medium tracking-[0.18em] text-brand-light dark:text-brand-dark uppercase">
            {subtitle}
          </p>
        ) : null}
      </div>
    </div>
  );
};

export default BrandMark;
