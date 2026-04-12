import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/src/lib/utils';

export const AdminDialogRoot = Dialog.Root;
export const AdminDialogTrigger = Dialog.Trigger;
export const AdminDialogPortal = Dialog.Portal;
export const AdminDialogOverlay = Dialog.Overlay;
export const AdminDialogContent = Dialog.Content;
export const AdminDialogTitle = Dialog.Title;
export const AdminDialogDescription = Dialog.Description;
export const AdminDialogClose = Dialog.Close;

type AdminDialogLayout = 'center' | 'side-panel';
type AdminDialogAccent = 'default' | 'warning' | 'danger' | 'success';

type AdminDialogProps = {
  open: boolean;
  title: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  layout?: AdminDialogLayout;
  mode?: AdminDialogLayout;
  size?: string;
  accent?: AdminDialogAccent;
  dismissible?: boolean;
  showCloseButton?: boolean;
  footer?: ReactNode;
  children: ReactNode;
  onOpenChange: (open: boolean) => void;
  className?: string;
  overlayClassName?: string;
  panelClassName?: string;
  headerClassName?: string;
  bodyClassName?: string;
  footerClassName?: string;
  titleClassName?: string;
  descriptionClassName?: string;
  closeLabel?: string;
};

const accentClasses: Record<AdminDialogAccent, string> = {
  default: 'border-blue-500/20 bg-blue-500/10 text-blue-400',
  warning: 'border-amber-500/20 bg-amber-500/10 text-amber-400',
  danger: 'border-red-500/20 bg-red-500/10 text-red-400',
  success: 'border-green-500/20 bg-green-500/10 text-green-400',
};

export function AdminDialog({
  open,
  title,
  description,
  icon,
  layout,
  mode,
  size,
  accent = 'default',
  dismissible = true,
  showCloseButton = true,
  footer,
  children,
  onOpenChange,
  className,
  overlayClassName,
  panelClassName,
  headerClassName,
  bodyClassName,
  footerClassName,
  titleClassName,
  descriptionClassName,
  closeLabel = '关闭对话框',
}: AdminDialogProps) {
  const resolvedLayout = layout ?? mode ?? (size === 'side-panel' ? 'side-panel' : 'center');
  const isSidePanel = resolvedLayout === 'side-panel';
  const sizeClasses =
    size === 'sm'
      ? 'max-w-[min(96vw,28rem)]'
      : size === 'md'
        ? 'max-w-[min(96vw,36rem)]'
        : size === 'xl'
          ? 'max-w-[min(96vw,56rem)]'
          : size === '2xl'
            ? 'max-w-[min(96vw,72rem)]'
            : size === 'full'
              ? 'max-w-[calc(100vw-2rem)]'
              : 'max-w-[min(96vw,48rem)]';
  const shellClasses = cn(
    'glass-panel-no-hover relative flex min-h-0 w-full flex-col overflow-hidden text-gray-900 dark:text-gray-100',
    isSidePanel
      ? 'h-[100dvh] max-w-[min(100vw,40rem)] rounded-none border-l border-white/10 shadow-[0_30px_120px_rgba(0,0,0,0.55)]'
      : cn('max-h-[min(calc(100dvh-3rem),48rem)] rounded-3xl border border-white/10 shadow-[0_30px_120px_rgba(0,0,0,0.55)]', sizeClasses),
    panelClassName,
  );

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay
          className={cn(
            'fixed inset-0 z-[100] bg-black/60 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out',
            overlayClassName,
          )}
        />
        <Dialog.Content
          className={cn(
            'fixed z-[101] outline-none',
            isSidePanel
              ? 'inset-y-0 right-0 flex w-full justify-end p-0'
              : 'inset-0 flex items-center justify-center px-4 py-6 sm:px-6',
            className,
          )}
          onEscapeKeyDown={(event) => {
            if (!dismissible) {
              event.preventDefault();
            }
          }}
          onPointerDownOutside={(event) => {
            if (!dismissible) {
              event.preventDefault();
            }
          }}
          onInteractOutside={(event) => {
            if (!dismissible) {
              event.preventDefault();
            }
          }}
        >
          <div className={shellClasses}>
            <div
              className={cn(
                'flex items-start justify-between gap-4 border-b border-white/10 px-6 py-6',
                !isSidePanel && 'rounded-t-3xl',
                headerClassName,
              )}
            >
              <div className="min-w-0">
                <div className="flex items-start gap-4">
                  {icon ? (
                    <div
                      className={cn(
                        'mt-0.5 flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border',
                        accentClasses[accent],
                      )}
                    >
                      {icon}
                    </div>
                  ) : null}
                  <div className="min-w-0">
                    <Dialog.Title className={cn('text-xl font-black tracking-tight text-gray-900 dark:text-gray-100', titleClassName)}>
                      {title}
                    </Dialog.Title>
                    {description ? (
                      <Dialog.Description
                        className={cn('mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300', descriptionClassName)}
                      >
                        {description}
                      </Dialog.Description>
                    ) : null}
                  </div>
                </div>
              </div>

              {showCloseButton ? (
                <Dialog.Close asChild>
                  <button
                    type="button"
                    className="rounded-full border border-white/10 p-2 text-gray-500 transition-colors hover:bg-white/10 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white"
                    aria-label={closeLabel}
                  >
                    <X className="h-4 w-4" />
                  </button>
                </Dialog.Close>
              ) : null}
            </div>

            <div className={cn('min-h-0 flex-1 overflow-y-auto px-6 py-6', bodyClassName)}>{children}</div>

            {footer ? (
              <div className={cn('border-t border-white/10 px-6 py-5', footerClassName)}>{footer}</div>
            ) : null}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
