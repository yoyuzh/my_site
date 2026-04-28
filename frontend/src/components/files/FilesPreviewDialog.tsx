import React, { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
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
  TextField,
  Typography,
  alpha,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { Download, Eye, Save, X } from 'lucide-react';
import type { FileItem } from '../../api/types';
import { formatBytes } from '../../lib/format';
import { downloadFileBlob, getFileDownloadUrl, updateFileContent } from '../../lib/files';
import CloudreveFileTypeIcon from './CloudreveFileTypeIcon';

const MarkdownDocumentEditor = lazy(() => import('./editors/MarkdownDocumentEditor'));
const ExcalidrawDocumentEditor = lazy(() => import('./editors/ExcalidrawDocumentEditor'));

type EditableKind = 'text' | 'markdown' | 'drawio' | 'excalidraw';

function getExtension(filename: string) {
  const index = filename.lastIndexOf('.');
  return index >= 0 ? filename.slice(index + 1).toLowerCase() : '';
}

function getEditableKind(file: FileItem): EditableKind | null {
  const extension = getExtension(file.filename);
  if (extension === 'txt') {
    return 'text';
  }
  if (extension === 'md' || extension === 'markdown') {
    return 'markdown';
  }
  if (extension === 'drawio' || extension === 'dio') {
    return 'drawio';
  }
  if (extension === 'excalidraw') {
    return 'excalidraw';
  }
  return null;
}

function isPreviewable(file: FileItem) {
  const contentType = file.contentType || '';
  return (
    getEditableKind(file) != null ||
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

function getEditableContentType(file: FileItem) {
  const kind = getEditableKind(file);
  if (kind === 'markdown') {
    return 'text/markdown;charset=utf-8';
  }
  if (kind === 'drawio') {
    return 'application/x-drawio;charset=utf-8';
  }
  if (kind === 'excalidraw') {
    return 'application/vnd.excalidraw+json;charset=utf-8';
  }
  return 'text/plain;charset=utf-8';
}

async function loadFileBlob(fileId: number) {
  const result = await getFileDownloadUrl(fileId);
  if (isExternalUrl(result.url)) {
    const response = await fetch(result.url);
    if (!response.ok) {
      throw new Error('文件内容加载失败');
    }
    return response.blob();
  }
  return downloadFileBlob(fileId);
}

function buildDrawioSrc(darkMode: boolean) {
  const query = new URLSearchParams({
    embed: '1',
    embedRT: '1',
    configure: '1',
    libraries: '1',
    spin: '1',
    proto: 'json',
    keepmodified: '1',
    p: 'nxtcld',
    lang: 'zh',
    dark: darkMode ? '1' : '0',
  });
  return `https://embed.diagrams.net/?${query.toString()}`;
}

interface DrawioFrameProps {
  file: FileItem;
  value: string;
  darkMode: boolean;
  onSave: (value: string) => Promise<void>;
  onClose: () => void;
}

function DrawioFrame({ file, value, darkMode, onSave, onClose }: DrawioFrameProps) {
  const frameRef = useRef<HTMLIFrameElement | null>(null);
  const src = useMemo(() => buildDrawioSrc(darkMode), [darkMode]);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (typeof event.data !== 'string') {
        return;
      }

      let message: { event?: string; xml?: string };
      try {
        message = JSON.parse(event.data) as { event?: string; xml?: string };
      } catch {
        return;
      }

      if (message.event === 'exit') {
        onClose();
        return;
      }

      if (message.event === 'configure') {
        frameRef.current?.contentWindow?.postMessage(JSON.stringify({ action: 'configure', config: {} }), '*');
        return;
      }

      if (message.event === 'init') {
        frameRef.current?.contentWindow?.postMessage(
          JSON.stringify({
            action: 'load',
            autosave: true,
            title: file.filename,
            xml: value,
            desc: {
              xml: value,
              id: file.id,
              size: file.size,
              etag: String(file.updatedAt ?? file.id),
              writeable: true,
              name: file.filename,
              versionEnabled: false,
              ver: 2,
              instanceId: window.location.host,
            },
          }),
          '*',
        );
        frameRef.current?.contentWindow?.postMessage(JSON.stringify({ action: 'remoteInvokeReady' }), '*');
        return;
      }

      if (message.event === 'save' && typeof message.xml === 'string') {
        void onSave(message.xml).then(() => {
          frameRef.current?.contentWindow?.postMessage(
            JSON.stringify({ action: 'status', message: '已保存', modified: false }),
            '*',
          );
        });
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [file, onClose, onSave, value]);

  return (
    <Box
      component="iframe"
      ref={frameRef}
      title={file.filename}
      src={src}
      sx={{ width: '100%', height: '68vh', minHeight: 520, border: 0, bgcolor: 'background.paper' }}
    />
  );
}

export interface FilesPreviewDialogProps {
  file: FileItem | null;
  onClose: () => void;
  onSaved?: (file: FileItem) => void;
}

export const FilesPreviewDialog: React.FC<FilesPreviewDialogProps> = ({ file, onClose, onSaved }) => {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('md'));
  const [activeFile, setActiveFile] = useState<FileItem | null>(file);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [documentText, setDocumentText] = useState('');
  const [initialDocumentText, setInitialDocumentText] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    setActiveFile(file);
  }, [file]);

  useEffect(() => {
    if (!activeFile || activeFile.directory || !isPreviewable(activeFile)) {
      setObjectUrl(null);
      setDocumentText('');
      setInitialDocumentText('');
      setError(null);
      return;
    }

    let disposed = false;
    let nextUrl: string | null = null;
    let shouldRevoke = false;
    setLoading(true);
    setError(null);
    setSavedMessage(null);
    setObjectUrl(null);
    setDocumentText('');
    setInitialDocumentText('');

    void loadFileBlob(activeFile.id)
      .then(async (blob) => {
        if (disposed) {
          return;
        }
        if (getEditableKind(activeFile) != null) {
          const text = await blob.text();
          if (disposed) {
            return;
          }
          setDocumentText(text);
          setInitialDocumentText(text);
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
  }, [activeFile]);

  const contentType = activeFile?.contentType ?? '';
  const previewable = activeFile != null && !activeFile.directory && isPreviewable(activeFile);
  const editableKind = activeFile ? getEditableKind(activeFile) : null;
  const dirty = editableKind != null && documentText !== initialDocumentText;
  const displayPath = activeFile ? getLogicalPath(activeFile) : '';

  const saveDocument = useCallback(
    async (nextContent = documentText) => {
      if (!activeFile || activeFile.directory || getEditableKind(activeFile) == null) {
        return;
      }

      setSaving(true);
      setError(null);
      setSavedMessage(null);
      try {
        const upload = new File([nextContent], activeFile.filename, { type: getEditableContentType(activeFile) });
        const updated = await updateFileContent(activeFile.id, upload);
        setActiveFile(updated);
        setDocumentText(nextContent);
        setInitialDocumentText(nextContent);
        setSavedMessage('已保存');
        onSaved?.(updated);
      } catch (saveError) {
        setError(saveError instanceof Error ? saveError.message : '保存失败');
      } finally {
        setSaving(false);
      }
    },
    [activeFile, documentText, onSaved],
  );

  async function handleDownload() {
    if (!activeFile || activeFile.directory) {
      return;
    }
    const blob = await downloadFileBlob(activeFile.id);
    const nextUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = nextUrl;
    link.download = activeFile.filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(nextUrl);
  }

  function renderEditableDocument() {
    if (!activeFile || editableKind == null) {
      return null;
    }

    if (editableKind === 'text') {
      return (
        <TextField
          value={documentText}
          onChange={(event) => setDocumentText(event.target.value)}
          onKeyDown={(event) => {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
              event.preventDefault();
              void saveDocument();
            }
          }}
          multiline
          fullWidth
          minRows={22}
          spellCheck={false}
          sx={{
            '& .MuiInputBase-root': {
              alignItems: 'stretch',
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              fontSize: 14,
              lineHeight: 1.65,
            },
            '& textarea': {
              minHeight: '62vh',
            },
          }}
        />
      );
    }

    if (editableKind === 'markdown') {
      return (
        <Suspense fallback={<LinearProgress />}>
          <MarkdownDocumentEditor
            value={documentText}
            initialValue={initialDocumentText}
            darkMode={theme.palette.mode === 'dark'}
            onChange={setDocumentText}
            onSaveShortcut={() => void saveDocument()}
          />
        </Suspense>
      );
    }

    if (editableKind === 'excalidraw') {
      return (
        <Suspense fallback={<LinearProgress />}>
          <ExcalidrawDocumentEditor
            value={initialDocumentText}
            darkMode={theme.palette.mode === 'dark'}
            onChange={setDocumentText}
            onSaveShortcut={() => void saveDocument()}
          />
        </Suspense>
      );
    }

    return (
      <DrawioFrame
        file={activeFile}
        value={initialDocumentText}
        darkMode={theme.palette.mode === 'dark'}
        onSave={saveDocument}
        onClose={onClose}
      />
    );
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
            {activeFile?.filename ?? '文件预览'}
          </Typography>
          {activeFile ? (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip size="small" label={activeFile.directory ? '文件夹' : editableKind ? '可编辑' : '文件'} />
              <Chip size="small" variant="outlined" label={activeFile.contentType || '未知类型'} />
              {!activeFile.directory ? (
                <Chip size="small" variant="outlined" label={formatBytes(activeFile.size)} />
              ) : null}
              {dirty ? <Chip size="small" color="warning" label="未保存" /> : null}
              {savedMessage ? <Chip size="small" color="success" label={savedMessage} /> : null}
            </Stack>
          ) : null}
        </Stack>
        <IconButton onClick={onClose} aria-label="关闭预览">
          <X size={18} />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ minHeight: 420, bgcolor: 'background.default', p: { xs: 2, md: 2.5 } }}>
        {loading || saving ? <LinearProgress sx={{ mb: 2 }} /> : null}
        {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
        {!activeFile || activeFile.directory ? null : !previewable ? (
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
                <CloudreveFileTypeIcon file={activeFile} size={30} />
              </Box>
              <Typography fontWeight={700}>当前类型暂不支持在线预览</Typography>
              <Typography variant="body2" color="text.secondary">
                这个文件已经可以正常下载，但暂时不会在页面内渲染。
              </Typography>
            </Stack>
          </Paper>
        ) : editableKind ? (
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'background.paper', overflow: 'hidden' }}>
            {loading ? (
              <Box sx={{ minHeight: 360, display: 'grid', placeItems: 'center' }}>
                <Typography variant="body2" color="text.secondary">正在加载文件内容...</Typography>
              </Box>
            ) : (
              renderEditableDocument()
            )}
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
                alt={activeFile.filename}
                sx={{ maxWidth: '100%', maxHeight: '70vh', objectFit: 'contain' }}
              />
            ) : contentType.startsWith('video/') ? (
              <Box component="video" src={objectUrl} controls sx={{ width: '100%', maxHeight: '70vh' }} />
            ) : contentType.startsWith('audio/') ? (
              <Box component="audio" src={objectUrl} controls sx={{ width: '100%' }} />
            ) : (
              <Box
                component="iframe"
                title={activeFile.filename}
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
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
          {activeFile ? (
            <Typography variant="caption" color="text.secondary" noWrap title={displayPath}>
              {displayPath}
            </Typography>
          ) : null}
        </Stack>
        <Stack direction="row" spacing={1}>
          {previewable && !editableKind ? (
            <Button size="small" color="inherit" startIcon={<Eye size={16} />} disabled>
              预览中
            </Button>
          ) : null}
          {editableKind && editableKind !== 'drawio' ? (
            <Button
              size="small"
              variant="contained"
              disableElevation
              startIcon={<Save size={16} />}
              disabled={saving || loading || !dirty}
              onClick={() => void saveDocument()}
            >
              保存
            </Button>
          ) : null}
          {activeFile && !activeFile.directory ? (
            <Button size="small" color="inherit" startIcon={<Download size={16} />} onClick={() => void handleDownload()}>
              下载原文件
            </Button>
          ) : null}
        </Stack>
      </DialogActions>
    </Dialog>
  );
};
