import React, { useEffect, useState } from 'react';
import { Box, IconButton, Paper, Typography, Chip, Divider, Stack, Skeleton } from '@mui/material';
import { Close, Favorite, Share, InfoOutlined, AccessTime, FolderOutlined, InsertDriveFileOutlined } from '@mui/icons-material';
import type { FileDetail } from '../../api/types';
import { formatBytes, formatDateTime } from '../../lib/format';
import { downloadFileBlob, getFileDownloadUrl } from '../../lib/files';

function isExternalUrl(url: string) {
  return /^https?:\/\//i.test(url) || url.startsWith('//');
}

export interface FileDetailsRailProps {
  detail: FileDetail | null;
  loading: boolean;
  error: string | null;
  onClose: () => void;
}

const DetailItem = ({ label, value, icon: Icon }: { label: string; value: string | React.ReactNode; icon?: React.ElementType }) => (
  <Box sx={{ mb: 1.5 }}>
    <Typography
      variant="caption"
      color="text.secondary"
      sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0, fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em' }}
    >
      {Icon && <Icon sx={{ fontSize: 14 }} />}
      {label}
    </Typography>
    <Typography variant="body2" sx={{ fontWeight: 500, color: 'text.primary', wordBreak: 'break-all', lineHeight: 1.4 }}>
      {value}
    </Typography>
  </Box>
);

const FileDetailsRail: React.FC<FileDetailsRailProps> = ({ detail, loading, error, onClose }) => {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  useEffect(() => {
    if (!detail || detail.directory || !detail.contentType?.startsWith('image/')) {
      setObjectUrl(null);
      return;
    }

    let disposed = false;
    let nextUrl: string | null = null;
    let shouldRevoke = false;
    setPreviewLoading(true);

    void getFileDownloadUrl(detail.id)
      .then(async (result) => {
        if (disposed) return;
        if (isExternalUrl(result.url)) {
          nextUrl = result.url;
          setObjectUrl(nextUrl);
          return;
        }
        const blob = await downloadFileBlob(detail.id);
        if (disposed) return;
        nextUrl = URL.createObjectURL(blob);
        shouldRevoke = true;
        setObjectUrl(nextUrl);
      })
      .catch(() => {
        if (!disposed) {
          setObjectUrl(null);
        }
      })
      .finally(() => {
        if (!disposed) {
          setPreviewLoading(false);
        }
      });

    return () => {
      disposed = true;
      if (nextUrl && shouldRevoke) {
        URL.revokeObjectURL(nextUrl);
      }
    };
  }, [detail]);

  return (
    <Paper
      elevation={0}
      sx={{
        width: '100%',
        height: '100%',
        overflowY: 'auto',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: { xs: 0, lg: 2 },
        bgcolor: 'background.paper',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: (theme) => (theme.palette.mode === 'dark' ? 'none' : '0 4px 20px rgba(0, 0, 0, 0.05)'),
      }}
    >
      <Box sx={{ p: 1.5, pb: 1, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', bgcolor: 'action.hover' }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="caption" color="primary" fontWeight={700} sx={{ textTransform: 'uppercase', letterSpacing: '0.1em' }}>
            属性检查器
          </Typography>
          <Typography variant="subtitle2" fontWeight={600} noWrap title={detail?.filename}>
            {detail?.filename ?? (loading ? '加载中...' : '未选中文件')}
          </Typography>
        </Box>
        <IconButton size="small" onClick={onClose} sx={{ mt: -0.5, mr: -0.5 }}>
          <Close fontSize="small" />
        </IconButton>
      </Box>

      <Divider />

      {detail?.contentType?.startsWith('image/') && (
        <Box sx={{ p: 1.5, bgcolor: 'background.default', borderBottom: '1px solid', borderColor: 'divider' }}>
          <Box
            sx={{
              width: '100%',
              minHeight: 220,
              aspectRatio: '4/3',
              borderRadius: 1,
              overflow: 'hidden',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'action.hover',
              border: '1px solid',
              borderColor: 'divider',
            }}
          >
            {previewLoading ? (
              <Skeleton variant="rectangular" width="100%" height="100%" />
            ) : objectUrl ? (
              <Box
                component="img"
                src={objectUrl}
                alt={detail.filename}
                sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
              />
            ) : (
              <InsertDriveFileOutlined color="disabled" sx={{ fontSize: 32 }} />
            )}
          </Box>
        </Box>
      )}

      <Box sx={{ p: 1.5 }}>
        {loading && (
          <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            正在加载文件详情...
          </Typography>
        )}

        {error && (
          <Typography variant="body2" color="error" sx={{ py: 2 }}>
            {error}
          </Typography>
        )}

        {detail && !loading && (
          <Stack spacing={0}>
            <Box sx={{ mb: 1.5 }}>
              <Stack direction="row" spacing={1}>
                {detail.favorite && (
                  <Chip
                    icon={<Favorite sx={{ fontSize: '14px !important' }} />}
                    label="已收藏"
                    size="small"
                    color="primary"
                    variant="filled"
                    sx={{
                      height: 24,
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      bgcolor: (theme) =>
                        theme.palette.mode === 'dark' ? 'rgba(79, 124, 255, 0.18)' : 'rgba(79, 124, 255, 0.12)',
                      color: 'primary.main',
                    }}
                  />
                )}
                {detail.shared && (
                  <Chip
                    icon={<Share sx={{ fontSize: '14px !important' }} />}
                    label="已共享"
                    size="small"
                    color="info"
                    sx={{ height: 24, fontSize: '0.75rem', fontWeight: 600 }}
                  />
                )}
                {!detail.favorite && !detail.shared && (
                  <Chip
                    label="常规"
                    size="small"
                    variant="outlined"
                    sx={{ height: 24, fontSize: '0.75rem', color: 'text.secondary' }}
                  />
                )}
              </Stack>
            </Box>

            <Typography variant="overline" display="block" color="text.secondary" sx={{ mb: 0.5, fontWeight: 700, lineHeight: 1.5 }}>
              基本信息
            </Typography>
            <Box sx={{ p: 1, mb: 2, borderRadius: 1.5, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider' }}>
              <DetailItem
                label="类型"
                value={detail.directory ? '文件夹' : detail.contentType || '未知'}
                icon={detail.directory ? FolderOutlined : InsertDriveFileOutlined}
              />
              <DetailItem label="大小" value={detail.directory ? '--' : formatBytes(detail.size)} />
              <DetailItem label="路径" value={detail.path} />
            </Box>

            <Typography variant="overline" display="block" color="text.secondary" sx={{ mb: 0.5, fontWeight: 700, lineHeight: 1.5 }}>
              时间戳
            </Typography>
            <Box sx={{ p: 1, borderRadius: 1.5, border: '1px solid', borderColor: 'divider' }}>
              <DetailItem label="创建于" value={formatDateTime(detail.createdAt)} icon={AccessTime} />
              <DetailItem label="最后修改" value={formatDateTime(detail.updatedAt)} icon={AccessTime} />
            </Box>
          </Stack>
        )}
      </Box>
    </Paper>
  );
};

export default FileDetailsRail;
