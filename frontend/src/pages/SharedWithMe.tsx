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
  alpha,
  Avatar
} from '@mui/material';
import {
  Delete,
  Launch,
  Lock,
  Timer,
  Person
} from '@mui/icons-material';
import type { SavedShareItem, ShareItem } from '../api/types';

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
        return <Chip label="正常" color="success" size="small" variant="outlined" />;
      case 'EXPIRED':
        return <Chip label="已过期" color="warning" size="small" variant="outlined" />;
      case 'CONSUMED':
        return <Chip label="已失效" color="info" size="small" variant="outlined" />;
      case 'REMOVED':
        return <Chip label="已移除" color="error" size="small" variant="outlined" />;
      default:
        return <Chip label={status} size="small" variant="outlined" />;
    }
  };

  return (
    <DashboardLayout title="与我共享">
      <Stack spacing={3} sx={{ height: '100%' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h5" fontWeight={700}>保存的分享链接</Typography>
        </Box>

        <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Table>
            <TableHead sx={{ bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04) }}>
              <TableRow>
                <TableCell>名称/文件</TableCell>
                <TableCell>分享者</TableCell>
                <TableCell>状态</TableCell>
                <TableCell>有效期</TableCell>
                <TableCell>保存时间</TableCell>
                <TableCell>密码保护</TableCell>
                <TableCell>查看次数</TableCell>
                <TableCell align="right">操作</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <TableRow><TableCell colSpan={8} align="center" sx={{ py: 8 }}>加载中...</TableCell></TableRow>
              ) : isError ? (
                <TableRow><TableCell colSpan={8} align="center" sx={{ py: 8, color: 'error.main' }}>加载失败</TableCell></TableRow>
              ) : savedShares.length === 0 ? (
                <TableRow><TableCell colSpan={8} align="center" sx={{ py: 8 }}>暂无保存的分享</TableCell></TableRow>
              ) : (
                savedShares.map((item: SavedShareItem) => (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography variant="subtitle2" noWrap sx={{ maxWidth: 200 }}>
                        {item.share.shareName || item.share.file?.filename || '未命名分享'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 24, height: 24, fontSize: '0.75rem' }}>
                          <Person sx={{ fontSize: 16 }} />
                        </Avatar>
                        <Typography variant="body2">{item.share.ownerUsername}</Typography>
                      </Box>
                    </TableCell>
                    <TableCell>{getStatusChip(item.share.status)}</TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {item.share.expiresAt ? formatDateTime(item.share.expiresAt) : '永久有效'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {formatDateTime(item.savedAt)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {item.share.passwordRequired ? (
                        <Tooltip title="需要密码">
                          <Lock fontSize="small" sx={{ opacity: 0.7 }} />
                        </Tooltip>
                      ) : (
                        <Typography variant="caption" color="text.secondary">无</Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{item.share.viewCount}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="打开分享页">
                          <IconButton size="small" onClick={() => window.open(buildFullShareUrl(item.share.token), '_blank')}>
                            <Launch fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="移除记录">
                          <IconButton size="small" color="error" onClick={() => handleRemove(item.id)}>
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
    </DashboardLayout>
  );
};

export default SharedWithMe;
