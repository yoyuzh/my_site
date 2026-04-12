import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '@/src/lib/utils';

export type AdminInputProps = InputHTMLAttributes<HTMLInputElement>;

export const AdminInput = forwardRef<HTMLInputElement, AdminInputProps>(function AdminInput(
  { className, type = 'text', ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      type={type}
      className={cn(
        'w-full rounded-lg border border-white/10 bg-white/10 px-4 py-3 text-[11px] font-black tracking-widest outline-none transition-colors placeholder:opacity-40 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 disabled:cursor-not-allowed disabled:opacity-60',
        className,
      )}
      {...props}
    />
  );
});
