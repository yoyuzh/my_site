import React, { useEffect, useState } from 'react';
import { Alert, Dialog, DialogContent, DialogTitle, IconButton, LinearProgress, Stack, Typography } from '@mui/material';
import { X } from 'lucide-react';
import { ReactReader } from 'react-reader';
import type { FileItem } from '../../api/types';
import { getFileDownloadUrl } from '../../lib/files';

export interface EpubViewerDialogProps {
  file: FileItem | null;
  onClose: () => void;
}

export const EpubViewerDialog: React.FC<EpubViewerDialogProps> = ({ file, onClose }) => {
  const [sourceUrl, setSourceUrl] = useState<string | null>(null);
  const [location, setLocation] = useState<string | number>(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!file) {
      return;
    }
    let disposed = false;
    setSourceUrl(null);
    setError(null);
    setLocation(0);
    void getFileDownloadUrl(file.id, { viewer: true })
      .then((result) => {
        if (!disposed) {
          setSourceUrl(new URL(result.url, window.location.origin).toString());
        }
      })
      .catch((loadError) => {
        if (!disposed) {
          setError(loadError instanceof Error ? loadError.message : 'EPUB 加载失败');
        }
      });
    return () => {
      disposed = true;
    };
  }, [file]);

  return (
    <Dialog open={file != null} onClose={onClose} fullWidth maxWidth="xl">
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Stack spacing={0.5} sx={{ minWidth: 0 }}>
          <Typography fontWeight={800} noWrap>EPUB 阅读器</Typography>
          <Typography variant="body2" color="text.secondary" noWrap title={file?.filename}>{file?.filename}</Typography>
        </Stack>
        <IconButton onClick={onClose} aria-label="关闭">
          <X size={18} />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ height: '78vh', p: 0 }}>
        {!sourceUrl && !error ? <LinearProgress /> : null}
        {error ? <Alert severity="error" sx={{ m: 2 }}>{error}</Alert> : null}
        {sourceUrl ? (
          <ReactReader
            url={sourceUrl}
            location={location}
            locationChanged={(nextLocation) => setLocation(nextLocation)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
};
