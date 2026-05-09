import React, { useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMyShares } from '../api/queries';
import { formatDateTime } from '../lib/format';
import { deleteShare, buildFullShareUrl } from '../lib/shares';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Stack,
  Tooltip,
  Typography,
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
} from '@mui/icons-material';
import EditSharePolicyDialog from '../components/shares/EditSharePolicyDialog';
import type { ShareItem } from '../api/types';
import { UnifiedList, UnifiedListRow, UnifiedPageContent, UnifiedListColumn } from '../components/common/UnifiedPageContent';
import { showToast } from '../components/files/WorkspaceActionToastHost';

const columns: UnifiedListColumn[] = [
  { label: '名称/文件', flex: 1.5 },
  { label: '状态', width: 100 },
  { label: '统计', width: 120 },
  { label: '有效期', width: 160 },
  { label: '属性', width: 100 },
  { label: '操作', width: 160, align: 'right' },
];

const Shares: React.FC = () => {
  const [page, setPage] = useState(1);
  const [editShare, setEditShare] = useState<ShareItem | null>(null);
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useMyShares(page, 15);

  const deleteMutation = useMutation({
    mutationFn: deleteShare,
    onSuccess: () => {
      showToast({ message: '分享已取消', severity: 'success' });
      if (shares.length <= 1 && page > 1) {
        setPage((current) => Math.max(1, current - 1));
      }
      void queryClient.invalidateQueries({ queryKey: ['myShares'] });
    },
    onError: (error) => {
      showToast({ message: error instanceof Error ? error.message : '取消分享失败', severity: 'error' });
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
        return <Chip label="正常" color="success" size="small" variant="outlined" sx={{ borderRadius: 1 }} />;
      case 'EXPIRED':
        return <Chip label="已过期" color="warning" size="small" variant="outlined" sx={{ borderRadius: 1 }} />;
      case 'CONSUMED':
        return <Chip label="已失效" color="info" size="small" variant="outlined" sx={{ borderRadius: 1 }} />;
      case 'REMOVED':
        return <Chip label="已移除" color="error" size="small" variant="outlined" sx={{ borderRadius: 1 }} />;
      default:
        return <Chip label={status} size="small" variant="outlined" sx={{ borderRadius: 1 }} />;
    }
  };

  return (
    <DashboardLayout
      title="分享管理"
      headerActions={<Button startIcon={<Launch />} onClick={() => window.open('/dashboard/files', '_blank')}>去创建分享</Button>}
    >
      <UnifiedPageContent
        isLoading={isLoading}
        isError={isError}
        isEmpty={shares.length === 0}
        emptyText="暂无分享记录"
        pagination={{
          count: data?.pagination.total_pages || 0,
          page: page,
          onChange: setPage,
        }}
      >
        <UnifiedList columns={columns}>
          {shares.map((share) => (
            <UnifiedListRow key={share.id} columns={columns}>
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ maxWidth: '100%' }}>
                  {share.shareName || share.file?.filename || '未命名分享'}
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block">
                  Token: {share.token}
                </Typography>
              </Box>
              <Box>{getStatusChip(share.status)}</Box>
              <Stack spacing={0.5}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <Visibility sx={{ fontSize: 14, opacity: 0.6 }} />
                  <Typography variant="caption">{share.viewCount} 查看</Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <FileDownload sx={{ fontSize: 14, opacity: 0.6 }} />
                  <Typography variant="caption">
                    {share.downloadCount}{share.maxDownloads ? ` / ${share.maxDownloads}` : ''} 下载
                  </Typography>
                </Box>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {share.expiresAt ? formatDateTime(share.expiresAt) : '永久有效'}
              </Typography>
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
              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                <Tooltip title="查看分享页">
                  <IconButton size="small" onClick={() => window.open(buildFullShareUrl(share.token), '_blank')}>
                    <Launch sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
                <Tooltip title="复制链接">
                  <IconButton size="small" onClick={() => handleCopyLink(share.token)}>
                    <ContentCopy sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
                <Tooltip title="编辑策略">
                  <IconButton size="small" onClick={() => setEditShare(share)}>
                    <Edit sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
                <Tooltip title="取消分享">
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleCancelShare(share.id)}
                    disabled={deleteMutation.isPending && deleteMutation.variables === share.id}
                  >
                    <Delete sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
              </Stack>
            </UnifiedListRow>
          ))}
        </UnifiedList>
      </UnifiedPageContent>

      <EditSharePolicyDialog
        open={Boolean(editShare)}
        onClose={() => setEditShare(null)}
        share={editShare}
      />
    </DashboardLayout>
  );
};

export default Shares;
