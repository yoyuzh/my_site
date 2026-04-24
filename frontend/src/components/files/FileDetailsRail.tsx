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
  <Box sx={{ mb: 2 }}>
    <Typography
      variant="caption"
      color="text.secondary"
      sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5, fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em' }}
    >
      {Icon && <Icon sx={{ fontSize: 14 }} />}
      {label}
    </Typography>
    <Typography variant="body2" sx={{ fontWeight: 500, color: 'text.primary', wordBreak: 'break-all', lineHeight: 1.5 }}>
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
        position: 'sticky',
        top: 24,
        width: '100%',
        height: { xs: '100vh', lg: 'auto' },
        maxHeight: { xs: '100vh', lg: 'calc(100vh - 8rem)' },
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
      <Box sx={{ p: 2, pb: 1.5, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', bgcolor: 'action.hover' }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="caption" color="primary" fontWeight={700} sx={{ textTransform: 'uppercase', letterSpacing: '0.1em' }}>
            属性检查器
          </Typography>
          <Typography variant="subtitle1" fontWeight={600} noWrap title={detail?.filename}>
            {detail?.filename ?? (loading ? '加载中...' : '未选中文件')}
          </Typography>
        </Box>
        <IconButton size="small" onClick={onClose} sx={{ mt: -0.5, mr: -0.5 }}>
          <Close fontSize="small" />
        </IconButton>
      </Box>

      <Divider />

      {detail?.contentType?.startsWith('image/') && (
        <Box sx={{ p: 2, bgcolor: 'background.default', borderBottom: '1px solid', borderColor: 'divider' }}>
          <Box
            sx={{
              width: '100%',
              aspectRatio: '16/9',
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
              <InsertDriveFileOutlined color="disabled" sx={{ fontSize: 48 }} />
            )}
          </Box>
        </Box>
      )}

      <Box sx={{ p: 2 }}>
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
            <Box sx={{ mb: 3 }}>
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

            <Typography variant="overline" display="block" color="text.secondary" sx={{ mb: 1.5, fontWeight: 700 }}>
              基本信息
            </Typography>
            <Box sx={{ p: 1.5, mb: 3, borderRadius: 1.5, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider' }}>
              <DetailItem
                label="类型"
                value={detail.directory ? '文件夹' : detail.contentType || '未知'}
                icon={detail.directory ? FolderOutlined : InsertDriveFileOutlined}
              />
              <DetailItem label="大小" value={detail.directory ? '--' : formatBytes(detail.size)} />
              <DetailItem label="路径" value={detail.path} />
            </Box>

            <Typography variant="overline" display="block" color="text.secondary" sx={{ mb: 1.5, fontWeight: 700 }}>
              时间戳
            </Typography>
            <Box sx={{ p: 1.5, borderRadius: 1.5, border: '1px solid', borderColor: 'divider' }}>
              <DetailItem label="创建于" value={formatDateTime(detail.createdAt)} icon={AccessTime} />
              <DetailItem label="最后修改" value={formatDateTime(detail.updatedAt)} icon={AccessTime} />
            </Box>

            <Box
              sx={{
                mt: 3,
                p: 2,
                borderRadius: 1.5,
                border: '1px dashed',
                borderColor: 'divider',
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
              }}
            >
              <InfoOutlined color="disabled" fontSize="small" />
              <Typography variant="caption" color="text.secondary">
                这些属性由系统自动生成，部分属性可能取决于存储后端。
              </Typography>
            </Box>
          </Stack>
        )}
      </Box>
    </Paper>
  );
};

export default FileDetailsRail;
