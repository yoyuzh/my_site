import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Box,
  Typography,
  Divider,
} from '@mui/material';
import { DeleteOutline, InfoOutlined } from '@mui/icons-material';
import type { FileDeleteMode, FileItem } from '../../api/types';

interface FileDeleteDialogProps {
  open: boolean;
  files: FileItem[];
  onClose: () => void;
  onConfirm: (mode: FileDeleteMode) => void;
  loading?: boolean;
}

export const FileDeleteDialog: React.FC<FileDeleteDialogProps> = ({
  open,
  files,
  onClose,
  onConfirm,
  loading,
}) => {
  const isSingle = files.length === 1;
  const fileName = isSingle ? files[0].filename : `${files.length} 个文件`;

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, fontWeight: 'bold' }}>
        <DeleteOutline color="error" />
        确认删除
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          确定要删除 {isSingle ? <Box component="span" sx={{ fontWeight: 'bold', color: 'text.primary' }}>{fileName}</Box> : fileName} 吗？
        </DialogContentText>
        
        <Box sx={{ 
          p: 1.5, 
          bgcolor: 'rgba(239, 68, 68, 0.05)', 
          borderRadius: 1, 
          border: '1px solid rgba(239, 68, 68, 0.1)',
          display: 'flex',
          gap: 1.5
        }}>
          <InfoOutlined color="error" sx={{ fontSize: 20, mt: 0.2 }} />
          <Typography variant="body2" color="error.main" sx={{ fontWeight: 500 }}>
            直接删除将无法从回收站找回，请谨慎操作。
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3, flexDirection: 'column', gap: 1 }}>
        <Button
          fullWidth
          variant="contained"
          color="primary"
          onClick={() => onConfirm('RECYCLE')}
          disabled={loading}
          sx={{ py: 1 }}
        >
          移至回收站
        </Button>
        <Button
          fullWidth
          variant="outlined"
          color="error"
          onClick={() => onConfirm('PERMANENT')}
          disabled={loading}
          sx={{ py: 1 }}
        >
          直接彻底删除
        </Button>
        <Button
          fullWidth
          variant="text"
          color="inherit"
          onClick={onClose}
          disabled={loading}
          sx={{ mt: 0.5 }}
        >
          取消
        </Button>
      </DialogActions>
    </Dialog>
  );
};
