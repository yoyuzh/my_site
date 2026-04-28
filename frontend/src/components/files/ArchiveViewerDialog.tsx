import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material';
import { X } from 'lucide-react';
import JSZip, { type JSZipObject } from 'jszip';
import type { FileItem } from '../../api/types';
import { downloadFileBlob } from '../../lib/files';
import { getFileExtension } from '../../lib/file-viewers';

export interface ArchiveViewerDialogProps {
  file: FileItem | null;
  onClose: () => void;
}

function sortEntries([leftKey, leftEntry]: [string, JSZipObject], [rightKey, rightEntry]: [string, JSZipObject]) {
  if (leftEntry.dir !== rightEntry.dir) {
    return leftEntry.dir ? -1 : 1;
  }
  return leftKey.localeCompare(rightKey);
}

export const ArchiveViewerDialog: React.FC<ArchiveViewerDialogProps> = ({ file, onClose }) => {
  const [entries, setEntries] = useState<Array<[string, JSZipObject]>>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const extension = useMemo(() => (file ? getFileExtension(file) : ''), [file]);

  useEffect(() => {
    if (!file) {
      return;
    }
    let disposed = false;
    setEntries([]);
    setError(null);

    if (extension !== 'zip') {
      setError('当前压缩包浏览器先支持 ZIP 预览，RAR、7Z、TAR 系列还需要继续补齐。');
      return;
    }

    setLoading(true);
    void downloadFileBlob(file.id)
      .then(async (blob) => {
        const zip = await JSZip.loadAsync(blob);
        if (!disposed) {
          setEntries(Object.entries(zip.files).sort(sortEntries));
        }
      })
      .catch((loadError) => {
        if (!disposed) {
          setError(loadError instanceof Error ? loadError.message : '压缩包读取失败');
        }
      })
      .finally(() => {
        if (!disposed) {
          setLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [extension, file]);

  async function downloadEntry(name: string, entry: JSZipObject) {
    if (entry.dir) {
      return;
    }
    const blob = await entry.async('blob');
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name.split('/').filter(Boolean).pop() ?? name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  return (
    <Dialog open={file != null} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Stack spacing={0.5} sx={{ minWidth: 0 }}>
          <Typography fontWeight={800} noWrap>压缩包浏览器</Typography>
          <Typography variant="body2" color="text.secondary" noWrap title={file?.filename}>{file?.filename}</Typography>
        </Stack>
        <IconButton onClick={onClose} aria-label="关闭">
          <X size={18} />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ minHeight: 420, p: 0 }}>
        {loading ? <LinearProgress /> : null}
        {error ? <Alert severity="info" sx={{ m: 2 }}>{error}</Alert> : null}
        {!error ? (
          <List disablePadding>
            {entries.map(([name, entry]) => (
              <ListItemButton key={name} onClick={() => void downloadEntry(name, entry)} disabled={entry.dir}>
                <ListItemText
                  primary={name}
                  secondary={entry.dir ? '目录' : '文件 · 可下载'}
                />
              </ListItemButton>
            ))}
            {!loading && entries.length === 0 ? (
              <Stack spacing={1} alignItems="center" sx={{ py: 8 }}>
                <Typography color="text.secondary">压缩包里没有可显示的条目</Typography>
              </Stack>
            ) : null}
          </List>
        ) : null}
      </DialogContent>
    </Dialog>
  );
};
