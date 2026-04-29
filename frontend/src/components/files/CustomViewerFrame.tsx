import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Dialog, DialogContent, DialogTitle, IconButton, LinearProgress, Stack, Typography } from '@mui/material';
import { X } from 'lucide-react';
import type { FileItem, FileViewerDefinition } from '../../api/types';
import { resolveApiUrl } from '../../api/client';
import { getFileDownloadUrl } from '../../lib/files';

function getLogicalPath(file: Pick<FileItem, 'filename' | 'path'>) {
  if (!file.path || file.path === '/') {
    return `/${file.filename}`;
  }
  return file.path.endsWith(`/${file.filename}`) ? file.path : `${file.path}/${file.filename}`;
}

function replaceTemplate(template: string, file: FileItem, sourceUrl: string) {
  const encodedSourceUrl = encodeURIComponent(sourceUrl);
  return template
    .replaceAll('{$src_urlencoded}', encodedSourceUrl)
    .replaceAll('{$src}', sourceUrl)
    .replaceAll('{$name}', encodeURIComponent(file.filename))
    .replaceAll('{$id}', encodeURIComponent(String(file.id)))
    .replaceAll('{$path}', encodeURIComponent(getLogicalPath(file)));
}

function isPubliclyReachableForThirdParty(sourceUrl: string) {
  try {
    const url = new URL(sourceUrl);
    const host = url.hostname.toLowerCase();
    if (host === 'localhost' || host === '127.0.0.1' || host === '::1') {
      return false;
    }
    if (/^\d+\.\d+\.\d+\.\d+$/.test(host)) {
      const parts = host.split('.').map((part) => Number(part));
      const [a, b] = parts;
      if (a === 10 || a === 127) {
        return false;
      }
      if (a === 172 && b >= 16 && b <= 31) {
        return false;
      }
      if (a === 192 && b === 168) {
        return false;
      }
    }
    return true;
  } catch {
    return false;
  }
}

export interface CustomViewerFrameProps {
  file: FileItem | null;
  viewer: FileViewerDefinition | null;
  onClose: () => void;
}

export const CustomViewerFrame: React.FC<CustomViewerFrameProps> = ({ file, viewer, onClose }) => {
  const [sourceUrl, setSourceUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const urlTemplate = typeof viewer?.props.urlTemplate === 'string' ? viewer.props.urlTemplate : '';
  const needsPublicSource = viewer?.id === 'google-docs' || viewer?.id === 'microsoft-office' || viewer?.id === 'photopea';
  const sourceReachable = sourceUrl ? isPubliclyReachableForThirdParty(sourceUrl) : true;
  const targetUrl = useMemo(() => {
    if (!file || !sourceUrl || !urlTemplate) {
      return null;
    }
    return replaceTemplate(urlTemplate, file, sourceUrl);
  }, [file, sourceUrl, urlTemplate]);

  useEffect(() => {
    if (!file || !viewer) {
      return;
    }
    let disposed = false;
    setError(null);
    setSourceUrl(null);
    void getFileDownloadUrl(file.id, { viewer: true })
      .then((result) => {
        if (!disposed) {
          setSourceUrl(resolveApiUrl(result.url));
        }
      })
      .catch((loadError) => {
        if (!disposed) {
          setError(loadError instanceof Error ? loadError.message : '打开方式加载失败');
        }
      });
    return () => {
      disposed = true;
    };
  }, [file, viewer]);

  useEffect(() => {
    if (viewer?.openInNew && targetUrl) {
      window.open(targetUrl, '_blank', 'noopener,noreferrer');
      onClose();
    }
  }, [onClose, targetUrl, viewer?.openInNew]);

  return (
    <Dialog open={file != null && viewer != null && !viewer?.openInNew} onClose={onClose} fullWidth maxWidth="xl">
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Stack spacing={0.5} sx={{ minWidth: 0 }}>
          <Typography fontWeight={800} noWrap>{viewer?.displayName ?? '外部阅读器'}</Typography>
          <Typography variant="body2" color="text.secondary" noWrap title={file?.filename}>{file?.filename}</Typography>
        </Stack>
        <IconButton onClick={onClose} aria-label="关闭">
          <X size={18} />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ height: '76vh', p: 0 }}>
        {!targetUrl && !error ? <LinearProgress /> : null}
        {error ? <Alert severity="error" sx={{ m: 2 }}>{error}</Alert> : null}
        {!error && sourceUrl && needsPublicSource && !sourceReachable ? (
          <Alert severity="warning" sx={{ m: 2 }}>
            当前阅读器依赖公网可访问的文件地址。现在返回的是本地或内网地址，第三方服务无法直接读取。
          </Alert>
        ) : null}
        {targetUrl && (!needsPublicSource || sourceReachable) ? (
          <iframe title={viewer?.displayName ?? file?.filename} src={targetUrl} style={{ width: '100%', height: '100%', border: 0 }} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
};
