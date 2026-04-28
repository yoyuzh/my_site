import React, { useState, useEffect } from 'react';
import { Snackbar, Alert, Button, CircularProgress } from '@mui/material';

export interface ToastAction {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
}

export interface Toast {
  id: string;
  message: string;
  severity: 'info' | 'success' | 'error' | 'warning';
  loading?: boolean;
  actions?: ToastAction[];
  duration?: number | null;
}

let toastListeners: ((toasts: Toast[]) => void)[] = [];
let toasts: Toast[] = [];

const notify = () => {
  toastListeners.forEach((l) => l([...toasts]));
};

export const showToast = (toast: Omit<Toast, 'id'>) => {
  const id = Math.random().toString(36).slice(2);
  toasts = [...toasts, { ...toast, id }];
  notify();
  return id;
};

export const updateToast = (id: string, updates: Partial<Omit<Toast, 'id'>>) => {
  toasts = toasts.map((t) => (t.id === id ? { ...t, ...updates } : t));
  notify();
};

export const removeToast = (id: string) => {
  toasts = toasts.filter((t) => t.id !== id);
  notify();
};

const WorkspaceActionToastHost: React.FC = () => {
  const [currentToasts, setCurrentToasts] = useState<Toast[]>([]);
  const desktopOffset = typeof window !== 'undefined' && window.innerWidth >= 1024 ? 336 : 16;

  useEffect(() => {
    toastListeners.push(setCurrentToasts);
    return () => {
      toastListeners = toastListeners.filter((l) => l !== setCurrentToasts);
    };
  }, []);

  const handleClose = (id: string) => {
    removeToast(id);
  };

  return (
    <>
      {currentToasts.map((toast, index) => (
        <Snackbar
          key={toast.id}
          open={true}
          autoHideDuration={toast.duration === undefined ? 5000 : toast.duration}
          onClose={() => handleClose(toast.id)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
          style={{ marginBottom: index * 70, marginLeft: desktopOffset }}
        >
          <Alert
            onClose={() => handleClose(toast.id)}
            severity={toast.severity}
            variant="filled"
            sx={{ 
              width: '100%', 
              borderRadius: '16px',
              '.MuiAlert-message': { display: 'flex', alignItems: 'center', gap: 2 }
            }}
            icon={toast.loading ? <CircularProgress size={16} color="inherit" /> : undefined}
            action={
              toast.actions?.map((action, i) => (
                <Button 
                  key={i} 
                  color="inherit" 
                  size="small" 
                  startIcon={action.icon}
                  onClick={() => {
                    action.onClick();
                    handleClose(toast.id);
                  }}
                  sx={{ fontWeight: 'bold' }}
                >
                  {action.label}
                </Button>
              ))
            }
          >
            {toast.message}
          </Alert>
        </Snackbar>
      ))}
    </>
  );
};

export default WorkspaceActionToastHost;
