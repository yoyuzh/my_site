import React from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';

export type AdminConfirmDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  isSubmitting?: boolean;
  onConfirm: () => void;
  onClose: () => void;
};

const AdminConfirmDialog: React.FC<AdminConfirmDialogProps> = ({
  open,
  title,
  description,
  confirmLabel,
  danger = false,
  isSubmitting = false,
  onConfirm,
  onClose,
}) => {
  const handleClose = () => {
    if (!isSubmitting) {
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1.5, fontWeight: 700 }}>{title}</DialogTitle>
      <DialogContent sx={{ pt: '8px !important' }}>
        <DialogContentText sx={{ color: 'text.primary' }}>{description}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={handleClose} disabled={isSubmitting}>
          取消
        </Button>
        <Button
          onClick={onConfirm}
          variant="contained"
          color={danger ? 'error' : 'primary'}
          disabled={isSubmitting}
        >
          {isSubmitting ? '处理中...' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AdminConfirmDialog;
