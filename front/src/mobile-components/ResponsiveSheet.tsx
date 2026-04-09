import React, { ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { cn } from '@/src/lib/utils';

export interface ResponsiveSheetProps {
  isOpen: boolean;
  onClose: () => void;
  children: ReactNode;
  className?: string;
}

export function ResponsiveSheet({ isOpen, onClose, children, className }: ResponsiveSheetProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-end">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-black/60 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className={cn(
              'relative w-full max-h-[90vh] overflow-y-auto bg-[#0f172a] rounded-t-3xl border-t border-white/10 pt-4 pb-8 px-4 flex flex-col z-10 glass-panel',
              className
            )}
          >
            <div className="w-12 h-1 shrink-0 bg-white/20 rounded-full mx-auto mb-4" />
            {children}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
