import React, { useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMyShares } from '../api/queries';
import { formatBytes, formatDateTime } from '../lib/format';
import { deleteShare, buildFullShareUrl } from '../lib/shares';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  Pagination,
  alpha
} from '@mui/material';
import {
  ContentCopy,
  Delete,
  Edit,
  Launch,
  Lock,
  Timer,
  Visibility,
  FileDownload,
  InfoOutlined
} from '@mui/icons-material';
import EditSharePolicyDialog from '../components/shares/EditSharePolicyDialog';
import type { ShareItem } from '../api/types';

const Shares: React.FC = () => {
  const [page, setPage] = useState(1);
  const [editShare, setEditShare] = useState<ShareItem | null>(null);
  const queryClient = useQueryClient();
  const { data, isLoading, isError, refetch } = useMyShares(page, 15);

  const deleteMutation = useMutation({
    mutationFn: deleteShare,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myShares'] });
    },
  });

  const shares = useMemo(() => data?.items ?? [], [data]);

  const handleCopyLink = (token: string) => {
    navigator.clipboard.writeText(buildFullShareUrl(token));
  };

  const handleCancelShare = (id: number) => {
    if (window.confirm('确定要取消此分享吗？链接将立即失效。')) {
      deleteMutation.mutate(id);
    }
  };

  const getStatusChip = (status: ShareItem['status']) => {
    switch (status) {
      case 'ACTIVE':
        return <Chip label="正常" color="success" size="small" variant="outlined" />;
      case 'EXPIRED':
        return <Chip label="已过期" color="warning" size="small" variant="outlined" />;
      case 'CONSUMED':
        return <Chip label="已消费" color="info" size="small" variant="outlined" />;
      case 'REMOVED':
        return <Chip label="已移除" color="error" size="small" variant="outlined" />;
      default:
        return <Chip label={status} size="small" variant="outlined" />;
    }
  };

  return (
    <DashboardLayout title="分享管理">
      <Stack spacing={3} sx={{ height: '100%' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h5" fontWeight={700}>我创建的分享</Typography>
          <Button startIcon={<Launch />} onClick={() => window.open('/dashboard/files', '_blank')}>去创建分享</Button>
        </Box>

        <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Table>
            <TableHead sx={{ bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04) }}>
              <TableRow>
                <TableCell>名称/文件</TableCell>
                <TableCell>状态</TableCell>
                <TableCell>统计</TableCell>
                <TableCell>有效期</TableCell>
                <TableCell>属性</TableCell>
                <TableCell align="right">操作</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <TableRow><TableCell colSpan={6} align="center" sx={{ py: 8 }}>加载中...</TableCell></TableRow>
              ) : isError ? (
                <TableRow><TableCell colSpan={6} align="center" sx={{ py: 8, color: 'error.main' }}>加载失败</TableCell></TableRow>
              ) : shares.length === 0 ? (
                <TableRow><TableCell colSpan={6} align="center" sx={{ py: 8 }}>暂无分享记录</TableCell></TableRow>
              ) : (
                shares.map((share) => (
                  <TableRow key={share.id} hover>
                    <TableCell>
                      <Typography variant="subtitle2" noWrap sx={{ maxWidth: 200 }}>
                        {share.shareName || share.file?.filename || '未命名分享'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" display="block">
                        Token: {share.token}
                      </Typography>
                    </TableCell>
                    <TableCell>{getStatusChip(share.status)}</TableCell>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                          <Visibility sx={{ fontSize: 14, opacity: 0.6 }} />
                          <Typography variant="caption">{share.viewCount}</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                          <FileDownload sx={{ fontSize: 14, opacity: 0.6 }} />
                          <Typography variant="caption">
                            {share.downloadCount}{share.maxDownloads ? ` / ${share.maxDownloads}` : ''}
                          </Typography>
                        </Box>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {share.expiresAt ? formatDateTime(share.expiresAt) : '永久有效'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1}>
                        {share.passwordRequired && (
                          <Tooltip title={`提取码: ${share.password || '******'}`}>
                            <Lock color="primary" sx={{ fontSize: 18 }} />
                          </Tooltip>
                        )}
                        {share.expireAfterConsume && (
                          <Tooltip title="成功消费后自动失效">
                            <Timer color="warning" sx={{ fontSize: 18 }} />
                          </Tooltip>
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="查看分享页">
                          <IconButton size="small" onClick={() => window.open(buildFullShareUrl(share.token), '_blank')}>
                            <Launch fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="复制链接">
                          <IconButton size="small" onClick={() => handleCopyLink(share.token)}>
                            <ContentCopy fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="编辑策略">
                          <IconButton size="small" onClick={() => setEditShare(share)}>
                            <Edit fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="取消分享">
                          <IconButton size="small" color="error" onClick={() => handleCancelShare(share.id)}>
                            <Delete fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {data && data.pagination.total_pages > 1 && (
          <Box sx={{ display: 'flex', justifyContent: 'center' }}>
            <Pagination
              count={data.pagination.total_pages}
              page={page}
              onChange={(_, p) => setPage(p)}
              color="primary"
            />
          </Box>
        )}
      </Stack>

      <EditSharePolicyDialog
        open={Boolean(editShare)}
        onClose={() => setEditShare(null)}
        share={editShare}
      />
    </DashboardLayout>
  );
};

export default Shares;
