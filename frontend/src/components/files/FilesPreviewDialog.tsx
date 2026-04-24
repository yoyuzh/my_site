import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  Paper,
  Stack,
  Typography,
  alpha,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { Close, Download, InsertDriveFile, VisibilityOutlined } from '@mui/icons-material';
import type { FileItem } from '../../api/types';
import { formatBytes } from '../../lib/format';
import { downloadFileBlob, getFileDownloadUrl } from '../../lib/files';

function isPreviewable(file: FileItem) {
  const contentType = file.contentType || '';
  return (
    contentType.startsWith('image/') ||
    contentType.startsWith('video/') ||
    contentType.startsWith('audio/') ||
    contentType === 'application/pdf' ||
    contentType.startsWith('text/')
  );
}

function isExternalUrl(url: string) {
  return /^https?:\/\//i.test(url) || url.startsWith('//');
}

function getLogicalPath(file: Pick<FileItem, 'filename' | 'path'>) {
  if (!file.path) {
    return `/${file.filename}`;
  }
  if (file.path === file.filename || file.path.endsWith(`/${file.filename}`)) {
    return file.path;
  }
  return file.path === '/' ? `/${file.filename}` : `${file.path}/${file.filename}`;
}

export interface FilesPreviewDialogProps {
  file: FileItem | null;
  onClose: () => void;
}

export const FilesPreviewDialog: React.FC<FilesPreviewDialogProps> = ({ file, onClose }) => {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('md'));
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!file || file.directory || !isPreviewable(file)) {
      setObjectUrl(null);
      setError(null);
      return;
    }

    let disposed = false;
    let nextUrl: string | null = null;
    let shouldRevoke = false;
    setLoading(true);
    setError(null);
    setObjectUrl(null);

    void getFileDownloadUrl(file.id)
      .then(async (result) => {
        if (disposed) {
          return;
        }
        if (isExternalUrl(result.url)) {
          nextUrl = result.url;
          setObjectUrl(nextUrl);
          return;
        }
        const blob = await downloadFileBlob(file.id);
        if (disposed) {
          return;
        }
        nextUrl = URL.createObjectURL(blob);
        shouldRevoke = true;
        setObjectUrl(nextUrl);
      })
      .catch((previewError: unknown) => {
        if (!disposed) {
          setError(previewError instanceof Error ? previewError.message : '预览加载失败');
        }
      })
      .finally(() => {
        if (!disposed) {
          setLoading(false);
        }
      });

    return () => {
      disposed = true;
      if (nextUrl && shouldRevoke) {
        URL.revokeObjectURL(nextUrl);
      }
    };
  }, [file]);

  const contentType = file?.contentType ?? '';
  const previewable = file != null && !file.directory && isPreviewable(file);
  const displayPath = file ? getLogicalPath(file) : '';

  async function handleDownload() {
    if (!file || file.directory) {
      return;
    }
    const blob = await downloadFileBlob(file.id);
    const nextUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = nextUrl;
    link.download = file.filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(nextUrl);
  }

  return (
    <Dialog open={file != null} onClose={onClose} fullWidth fullScreen={fullScreen} maxWidth="xl">
      <DialogTitle
        sx={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 2,
          pb: 1.5,
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Stack spacing={1} sx={{ minWidth: 0 }}>
          <Typography noWrap fontWeight={700}>
            {file?.filename ?? '文件预览'}
          </Typography>
          {file ? (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip size="small" label={file.directory ? '文件夹' : '文件'} />
              <Chip size="small" variant="outlined" label={file.contentType || '未知类型'} />
              {!file.directory ? (
                <Chip size="small" variant="outlined" label={formatBytes(file.size)} />
              ) : null}
            </Stack>
          ) : null}
        </Stack>
        <IconButton onClick={onClose} aria-label="关闭预览">
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ minHeight: 420, bgcolor: 'background.default', p: { xs: 2, md: 2.5 } }}>
        {loading ? <LinearProgress sx={{ mb: 2 }} /> : null}
        {!file || file.directory ? null : !previewable ? (
          <Paper
            elevation={0}
            sx={{
              minHeight: 360,
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.paper',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: theme.palette.mode === 'dark' ? 'none' : '0 18px 40px rgba(15, 23, 42, 0.04)',
            }}
          >
            <Stack spacing={2} alignItems="center" justifyContent="center" sx={{ maxWidth: 360, textAlign: 'center', px: 3 }}>
              <Box
                sx={{
                  width: 56,
                  height: 56,
                  borderRadius: 2,
                  display: 'grid',
                  placeItems: 'center',
                  bgcolor: alpha(theme.palette.text.primary, 0.06),
                }}
              >
                <InsertDriveFile fontSize="large" color="disabled" />
              </Box>
              <Typography fontWeight={700}>当前类型暂不支持在线预览</Typography>
              <Typography variant="body2" color="text.secondary">
                这个文件已经可以正常下载，但暂时不会在页面内渲染。
              </Typography>
            </Stack>
          </Paper>
        ) : error ? (
          <Paper
            elevation={0}
            sx={{
              minHeight: 360,
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.paper',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <Typography color="error">{error}</Typography>
          </Paper>
        ) : objectUrl ? (
          <Paper
            elevation={0}
            sx={{
              minHeight: 360,
              p: { xs: 1.5, md: 2 },
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.paper',
              boxShadow: theme.palette.mode === 'dark' ? 'none' : '0 18px 40px rgba(15, 23, 42, 0.04)',
            }}
          >
            {contentType.startsWith('image/') ? (
              <Box
                component="img"
                src={objectUrl}
                alt={file.filename}
                sx={{ maxWidth: '100%', maxHeight: '70vh', objectFit: 'contain' }}
              />
            ) : contentType.startsWith('video/') ? (
              <Box component="video" src={objectUrl} controls sx={{ width: '100%', maxHeight: '70vh' }} />
            ) : contentType.startsWith('audio/') ? (
              <Box component="audio" src={objectUrl} controls sx={{ width: '100%' }} />
            ) : (
              <Box
                component="iframe"
                title={file.filename}
                src={objectUrl}
                sx={{ width: '100%', height: '70vh', border: 0, bgcolor: 'background.paper' }}
              />
            )}
          </Paper>
        ) : (
          <Paper
            elevation={0}
            sx={{
              minHeight: 360,
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.paper',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <Typography variant="body2" color="text.secondary">
              正在准备预览内容...
            </Typography>
          </Paper>
        )}
      </DialogContent>
      <DialogActions
        sx={{
          px: { xs: 2, md: 3 },
          py: 1.25,
          justifyContent: 'space-between',
          borderTop: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.paper',
        }}
      >
        <Stack direction="row" spacing={1} alignItems="center">
          {file ? (
            <Typography variant="caption" color="text.secondary" noWrap title={displayPath}>
              {displayPath}
            </Typography>
          ) : null}
        </Stack>
        <Stack direction="row" spacing={1}>
          {previewable ? (
            <Button size="small" color="inherit" startIcon={<VisibilityOutlined />} disabled>
              预览中
            </Button>
          ) : null}
          {file && !file.directory ? (
            <Button size="small" variant="contained" disableElevation startIcon={<Download />} onClick={() => void handleDownload()}>
              下载原文件
            </Button>
          ) : null}
        </Stack>
      </DialogActions>
    </Dialog>
  );
};
