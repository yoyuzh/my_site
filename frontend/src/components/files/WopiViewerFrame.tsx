import React from 'react';
import { Alert, Dialog, DialogContent, DialogTitle, IconButton, Stack, Typography } from '@mui/material';
import { X } from 'lucide-react';
import type { FileItem, FileViewerDefinition } from '../../api/types';

export interface WopiViewerFrameProps {
  file: FileItem | null;
  viewer: FileViewerDefinition | null;
  onClose: () => void;
}

export const WopiViewerFrame: React.FC<WopiViewerFrameProps> = ({ file, viewer, onClose }) => {
  return (
    <Dialog open={file != null && viewer != null} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Stack spacing={0.5} sx={{ minWidth: 0 }}>
          <Typography fontWeight={800} noWrap>{viewer?.displayName ?? 'Office 阅读器'}</Typography>
          <Typography variant="body2" color="text.secondary" noWrap title={file?.filename}>{file?.filename}</Typography>
        </Stack>
        <IconButton onClick={onClose} aria-label="关闭">
          <X size={18} />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Alert severity="info">
          已配置该打开方式。Microsoft/WOPI 会话接口接入后，这里会直接加载对应在线阅读器。
        </Alert>
      </DialogContent>
    </Dialog>
  );
};
