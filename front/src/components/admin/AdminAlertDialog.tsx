import * as AlertDialog from '@radix-ui/react-alert-dialog';
import { AlertTriangle } from 'lucide-react';
import { cn } from '@/src/lib/utils';

type AdminAlertDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmTone?: 'danger' | 'warning';
  busy?: boolean;
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
};

export function AdminAlertDialog({
  open,
  title,
  description,
  confirmLabel = '确认',
  cancelLabel = '取消',
  confirmTone = 'danger',
  busy = false,
  onConfirm,
  onCancel,
}: AdminAlertDialogProps) {
  const toneClasses =
    confirmTone === 'warning'
      ? 'border-amber-500/20 bg-amber-500/10 text-amber-400'
      : 'border-red-500/20 bg-red-500/10 text-red-400';

  return (
    <AlertDialog.Root
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen && !busy) {
          onCancel();
        }
      }}
    >
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 z-[100] bg-black/60 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out" />
        <AlertDialog.Content
          onEscapeKeyDown={(event) => {
            if (busy) {
              event.preventDefault();
            }
          }}
          className="fixed inset-0 z-[101] flex items-center justify-center px-4 py-6"
        >
          <div className="relative w-full max-w-lg rounded-3xl border border-white/10 bg-gray-950/95 p-6 text-gray-100 shadow-[0_30px_120px_rgba(0,0,0,0.55)]">
            <div className={cn('mb-4 flex h-12 w-12 items-center justify-center rounded-2xl border', toneClasses)}>
              <AlertTriangle className="h-5 w-5" />
            </div>

            <AlertDialog.Title className="text-xl font-black tracking-tight">{title}</AlertDialog.Title>
            <AlertDialog.Description className="mt-3 text-sm leading-6 text-gray-300">
              {description}
            </AlertDialog.Description>

            <div className="mt-8 flex flex-wrap justify-end gap-3">
              <AlertDialog.Cancel asChild>
                <button
                  type="button"
                  disabled={busy}
                  className="rounded-xl border border-white/10 bg-white/5 px-5 py-3 text-[11px] font-black uppercase tracking-[0.18em] text-gray-100 transition-all hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {cancelLabel}
                </button>
              </AlertDialog.Cancel>
              <AlertDialog.Action asChild>
                <button
                  type="button"
                  onClick={() => {
                    void onConfirm();
                  }}
                  disabled={busy}
                  className={cn(
                    'rounded-xl px-5 py-3 text-[11px] font-black uppercase tracking-[0.18em] text-white transition-all hover:scale-[1.01] disabled:cursor-not-allowed disabled:opacity-50',
                    confirmTone === 'warning' ? 'bg-amber-500 hover:bg-amber-400' : 'bg-red-600 hover:bg-red-500',
                  )}
                >
                  {busy ? '处理中' : confirmLabel}
                </button>
              </AlertDialog.Action>
            </div>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
