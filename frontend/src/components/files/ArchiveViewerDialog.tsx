import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Breadcrumbs,
  Button,
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
import { ArrowLeft, Download, Folder, FolderOpen, X } from 'lucide-react';
import type { ArchiveEntry, FileItem } from '../../api/types';
import { downloadArchiveEntryBlob, getArchiveListing } from '../../lib/files';
import { createExtractTask } from '../../lib/tasks';

export interface ArchiveViewerDialogProps {
  file: FileItem | null;
  onClose: () => void;
}

interface BrowserEntry {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  contentType: string;
}

function buildFileLogicalPath(file: FileItem) {
  return file.path === '/' ? `/${file.filename}` : `${file.path}/${file.filename}`;
}

function extractLeafName(path: string) {
  const segments = path.split('/').filter(Boolean);
  return segments[segments.length - 1] ?? path;
}

function formatSize(size: number) {
  if (size <= 0) {
    return '0 B';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function launchBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function listCurrentEntries(entries: ArchiveEntry[], currentPath: string) {
  const prefix = currentPath ? `${currentPath}/` : '';
  const children = new Map<string, BrowserEntry>();

  for (const entry of entries) {
    if (currentPath && entry.relativePath === currentPath) {
      continue;
    }
    if (prefix && !entry.relativePath.startsWith(prefix)) {
      continue;
    }

    const rest = prefix ? entry.relativePath.slice(prefix.length) : entry.relativePath;
    if (!rest) {
      continue;
    }

    const slashIndex = rest.indexOf('/');
    if (slashIndex === -1) {
      children.set(rest, {
        name: rest,
        path: entry.relativePath,
        directory: entry.directory,
        size: entry.size,
        contentType: entry.contentType,
      });
      continue;
    }

    const directoryName = rest.slice(0, slashIndex);
    const directoryPath = currentPath ? `${currentPath}/${directoryName}` : directoryName;
    if (!children.has(directoryName)) {
      children.set(directoryName, {
        name: directoryName,
        path: directoryPath,
        directory: true,
        size: 0,
        contentType: 'inode/directory',
      });
    }
  }

  return Array.from(children.values()).sort((left, right) => {
    if (left.directory !== right.directory) {
      return left.directory ? -1 : 1;
    }
    return left.name.localeCompare(right.name, 'zh-CN');
  });
}

export const ArchiveViewerDialog: React.FC<ArchiveViewerDialogProps> = ({ file, onClose }) => {
  const [entries, setEntries] = useState<ArchiveEntry[]>([]);
  const [commonRootDirectoryName, setCommonRootDirectoryName] = useState<string | null>(null);
  const [currentPath, setCurrentPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [extractMessage, setExtractMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [extracting, setExtracting] = useState(false);

  useEffect(() => {
    if (!file) {
      return;
    }
    let disposed = false;
    setEntries([]);
    setCommonRootDirectoryName(null);
    setCurrentPath('');
    setError(null);
    setExtractMessage(null);
    setLoading(true);

    void getArchiveListing(file.id)
      .then((listing) => {
        if (disposed) {
          return;
        }
        setEntries(listing.entries);
        setCommonRootDirectoryName(listing.commonRootDirectoryName);
        setCurrentPath(listing.commonRootDirectoryName ?? '');
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
  }, [file]);

  const visibleEntries = useMemo(
    () => listCurrentEntries(entries, currentPath),
    [currentPath, entries],
  );

  const breadcrumbs = useMemo(() => {
    if (!currentPath) {
      return [];
    }
    const segments = currentPath.split('/').filter(Boolean);
    return segments.map((segment, index) => ({
      label: segment,
      path: segments.slice(0, index + 1).join('/'),
    }));
  }, [currentPath]);

  async function handleDownload(entry: BrowserEntry) {
    if (!file || entry.directory) {
      return;
    }
    const blob = await downloadArchiveEntryBlob(file.id, entry.path);
    launchBlobDownload(blob, extractLeafName(entry.path));
  }

  async function handleExtract() {
    if (!file) {
      return;
    }
    setExtracting(true);
    setExtractMessage(null);
    setError(null);
    try {
      await createExtractTask(file.id, buildFileLogicalPath(file));
      setExtractMessage('已创建解压任务，可以在任务列表里查看进度。');
    } catch (taskError) {
      setError(taskError instanceof Error ? taskError.message : '解压任务创建失败');
    } finally {
      setExtracting(false);
    }
  }

  function navigateUp() {
    if (!currentPath) {
      return;
    }
    const segments = currentPath.split('/').filter(Boolean);
    segments.pop();
    setCurrentPath(segments.join('/'));
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
        <Stack spacing={2} sx={{ p: 2, pb: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap" useFlexGap>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
              <Button
                size="small"
                variant="outlined"
                startIcon={<ArrowLeft size={16} />}
                disabled={!currentPath}
                onClick={navigateUp}
              >
                返回上级
              </Button>
              <Breadcrumbs aria-label="archive path" maxItems={4}>
                <Button size="small" onClick={() => setCurrentPath('')}>压缩包</Button>
                {breadcrumbs.map((breadcrumb, index) => {
                  const isLast = index === breadcrumbs.length - 1;
                  return isLast ? (
                    <Typography key={breadcrumb.path} color="text.primary" variant="body2">
                      {breadcrumb.label}
                    </Typography>
                  ) : (
                    <Button key={breadcrumb.path} size="small" onClick={() => setCurrentPath(breadcrumb.path)}>
                      {breadcrumb.label}
                    </Button>
                  );
                })}
              </Breadcrumbs>
            </Stack>
            <Button size="small" variant="contained" onClick={() => void handleExtract()} disabled={extracting || !file}>
              {extracting ? '创建中...' : '解压到所在目录'}
            </Button>
          </Stack>
          {extractMessage ? <Alert severity="success">{extractMessage}</Alert> : null}
          {error ? <Alert severity="info">{error}</Alert> : null}
        </Stack>
        {!error ? (
          <List disablePadding sx={{ py: 1 }}>
            {visibleEntries.map((entry) => (
              <ListItemButton
                key={entry.path}
                onClick={() => (entry.directory ? setCurrentPath(entry.path) : void handleDownload(entry))}
              >
                <Stack direction="row" spacing={1.5} alignItems="center" sx={{ width: '100%' }}>
                  {entry.directory ? (
                    currentPath === entry.path ? <FolderOpen size={18} /> : <Folder size={18} />
                  ) : (
                    <Download size={18} />
                  )}
                  <ListItemText
                    primary={entry.name}
                    secondary={entry.directory ? '目录' : `文件 · ${formatSize(entry.size)} · 点击下载`}
                  />
                </Stack>
              </ListItemButton>
            ))}
            {!loading && visibleEntries.length === 0 ? (
              <Stack spacing={1} alignItems="center" sx={{ py: 8 }}>
                <Typography color="text.secondary">当前目录下没有可显示的条目</Typography>
              </Stack>
            ) : null}
          </List>
        ) : null}
        {!loading && !error && entries.length === 0 ? (
          <Stack spacing={1} alignItems="center" sx={{ px: 2, pb: 4 }}>
            <Typography color="text.secondary">压缩包里没有可显示的条目</Typography>
          </Stack>
        ) : null}
        {commonRootDirectoryName && !loading && !error ? (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: 2, pb: 2 }}>
            已自动定位到归档根目录：{commonRootDirectoryName}
          </Typography>
        ) : null}
      </DialogContent>
    </Dialog>
  );
};
