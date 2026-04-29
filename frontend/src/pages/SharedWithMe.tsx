import React, { useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useSharedWithMe } from '../api/queries';
import { formatDateTime } from '../lib/format';
import { deleteSavedShare, buildFullShareUrl } from '../lib/shares';
import {
  Box,
  Chip,
  IconButton,
  Stack,
  Tooltip,
  Typography,
  Avatar
} from '@mui/material';
import {
  Delete,
  Launch,
  Lock,
  Person
} from '@mui/icons-material';
import type { SavedShareItem, ShareItem } from '../api/types';
import { UnifiedList, UnifiedListRow, UnifiedPageContent, UnifiedListColumn } from '../components/common/UnifiedPageContent';

const columns: UnifiedListColumn[] = [
  { label: '名称/文件', flex: 1.5 },
  { label: '分享者', width: 140 },
  { label: '状态', width: 100 },
  { label: '有效期', width: 160 },
  { label: '保存时间', width: 160 },
  { label: '密码保护', width: 100, align: 'center' },
  { label: '查看次数', width: 100, align: 'center' },
  { label: '操作', width: 100, align: 'right' },
];

const SharedWithMe: React.FC = () => {
  const [page, setPage] = useState(1);
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useSharedWithMe(page, 15);

  const deleteMutation = useMutation({
    mutationFn: deleteSavedShare,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sharedWithMe'] });
    },
  });

  const savedShares = useMemo(() => data?.items ?? [], [data]);

  const handleRemove = (id: number) => {
    if (window.confirm('确定要从“与我共享”中移除此项吗？')) {
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
    <DashboardLayout title="与我共享">
      <UnifiedPageContent
        isLoading={isLoading}
        isError={isError}
        isEmpty={savedShares.length === 0}
        emptyText="暂无保存的分享"
        pagination={{
          count: data?.pagination.total_pages || 0,
          page: page,
          onChange: setPage,
        }}
      >
        <UnifiedList columns={columns}>
          {savedShares.map((item: SavedShareItem) => (
            <UnifiedListRow key={item.id} columns={columns}>
              <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ maxWidth: '100%' }}>
                {item.share.shareName || item.share.file?.filename || '未命名分享'}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Avatar sx={{ width: 24, height: 24, fontSize: '0.75rem', bgcolor: 'primary.main' }}>
                  <Person sx={{ fontSize: 16 }} />
                </Avatar>
                <Typography variant="body2" noWrap>{item.share.ownerUsername}</Typography>
              </Box>
              <Box>{getStatusChip(item.share.status)}</Box>
              <Typography variant="body2" color="text.secondary">
                {item.share.expiresAt ? formatDateTime(item.share.expiresAt) : '永久有效'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {formatDateTime(item.savedAt)}
              </Typography>
              <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                {item.share.passwordRequired ? (
                  <Tooltip title="需要密码">
                    <Lock fontSize="small" sx={{ opacity: 0.7, color: 'primary.main' }} />
                  </Tooltip>
                ) : (
                  <Typography variant="caption" color="text.secondary">-</Typography>
                )}
              </Box>
              <Typography variant="body2" align="center">{item.share.viewCount}</Typography>
              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                <Tooltip title="打开分享页">
                  <IconButton size="small" onClick={() => window.open(buildFullShareUrl(item.share.token), '_blank')}>
                    <Launch sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
                <Tooltip title="移除记录">
                  <IconButton size="small" color="error" onClick={() => handleRemove(item.id)}>
                    <Delete sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
              </Stack>
            </UnifiedListRow>
          ))}
        </UnifiedList>
      </UnifiedPageContent>
    </DashboardLayout>
  );
};

export default SharedWithMe;
