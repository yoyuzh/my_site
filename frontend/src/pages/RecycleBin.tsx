import React, { useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRecycleBin } from '../api/queries';
import { formatBytes, formatDateTime, formatTimeUntil } from '../lib/format';
import { restoreRecycleBinItem, deleteRecycleBinItem } from '../lib/files';
import {
  RestoreFromTrash,
  DeleteForever,
} from '@mui/icons-material';
import {
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  CircularProgress,
  Typography,
  Box,
  Stack,
  alpha,
} from '@mui/material';
import { UnifiedList, UnifiedListRow, UnifiedPageContent, UnifiedListColumn } from '../components/common/UnifiedPageContent';
import CloudreveFileTypeIcon from '../components/files/CloudreveFileTypeIcon';
import type { RecycleBinItem } from '../api/types';
import { showToast } from '../components/files/WorkspaceActionToastHost';

const columns: UnifiedListColumn[] = [
  { label: '名称', flex: 1.5 },
  { label: '大小', width: 120 },
  { label: '过期时间', width: 180 },
  { label: '原始位置', flex: 1 },
  { label: '操作', width: 100, align: 'right' },
];

const RecycleBin: React.FC = () => {
  const [page, setPage] = useState(1);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useRecycleBin(page, 20);

  const restoreMutation = useMutation({
    mutationFn: restoreRecycleBinItem,
    onSuccess: () => {
      showToast({ message: '文件已恢复', severity: 'success' });
      if (items.length <= 1 && page > 1) {
        setPage((current) => Math.max(1, current - 1));
      }
      void queryClient.invalidateQueries({ queryKey: ['recycleBin'] });
      void queryClient.invalidateQueries({ queryKey: ['files'] });
    },
    onError: (error) => {
      showToast({ message: error instanceof Error ? error.message : '恢复失败', severity: 'error' });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteRecycleBinItem,
    onSuccess: () => {
      showToast({ message: '文件已彻底删除', severity: 'success' });
      if (items.length <= 1 && page > 1) {
        setPage((current) => Math.max(1, current - 1));
      }
      void queryClient.invalidateQueries({ queryKey: ['recycleBin'] });
      setDeleteConfirmId(null);
    },
    onError: (error) => {
      showToast({ message: error instanceof Error ? error.message : '彻底删除失败', severity: 'error' });
    },
  });

  const handleDeletePermanent = () => {
    if (deleteConfirmId !== null) {
      deleteMutation.mutate(deleteConfirmId);
    }
  };

  const items = data?.items ?? [];

  return (
    <DashboardLayout title="回收站 Recycle Bin">
      <UnifiedPageContent
        isLoading={isLoading}
        isError={isError}
        isEmpty={items.length === 0}
        emptyText="回收站为空"
        emptyIcon={
          <Box sx={{ width: 64, height: 64, borderRadius: '50%', bgcolor: alpha('#f44336', 0.1), display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#f44336' }}>
            <DeleteForever sx={{ fontSize: 32 }} />
          </Box>
        }
        pagination={{
          count: data?.pagination.total_pages || 0,
          page: page,
          onChange: setPage,
        }}
      >
        <UnifiedList columns={columns}>
          {items.map((item: RecycleBinItem) => (
            <UnifiedListRow key={item.id} columns={columns}>
              <Stack direction="row" alignItems="center" spacing={1.5} sx={{ minWidth: 0 }}>
                <CloudreveFileTypeIcon file={item} size={24} />
                <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ maxWidth: '100%' }} title={item.filename}>
                  {item.filename}
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {item.directory ? '-' : formatBytes(item.size)}
              </Typography>
              <Typography variant="body2" color="text.secondary" title={formatDateTime(item.expiresAt)}>
                {formatTimeUntil(item.expiresAt)}
              </Typography>
              <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: '100%' }} title={item.path}>
                {item.path}
              </Typography>
              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                <Tooltip title="恢复">
                  <IconButton
                    size="small"
                    color="primary"
                    onClick={() => restoreMutation.mutate(item.id)}
                    disabled={restoreMutation.isPending && restoreMutation.variables === item.id}
                  >
                    {restoreMutation.isPending && restoreMutation.variables === item.id ? (
                      <CircularProgress size={18} />
                    ) : (
                      <RestoreFromTrash sx={{ fontSize: 18 }} />
                    )}
                  </IconButton>
                </Tooltip>
                <Tooltip title="直接删除">
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => setDeleteConfirmId(item.id)}
                    disabled={deleteMutation.isPending && deleteMutation.variables === item.id}
                  >
                    <DeleteForever sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
              </Stack>
            </UnifiedListRow>
          ))}
        </UnifiedList>
      </UnifiedPageContent>

      <Dialog
        open={deleteConfirmId !== null}
        onClose={() => setDeleteConfirmId(null)}
        aria-labelledby="delete-dialog-title"
        PaperProps={{ sx: { borderRadius: 3 } }}
      >
        <DialogTitle id="delete-dialog-title" sx={{ fontWeight: 700 }}>
          确认永久删除？
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            该操作将立即彻底删除该文件，且不可恢复。请确认是否继续？
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button onClick={() => setDeleteConfirmId(null)} color="inherit" variant="outlined" sx={{ borderRadius: 2 }}>
            取消
          </Button>
          <Button
            onClick={handleDeletePermanent}
            color="error"
            variant="contained"
            autoFocus
            disabled={deleteMutation.isPending}
            startIcon={deleteMutation.isPending ? <CircularProgress size={16} color="inherit" /> : null}
            sx={{ borderRadius: 2, px: 3 }}
          >
            彻底删除
          </Button>
        </DialogActions>
      </Dialog>
    </DashboardLayout>
  );
};

export default RecycleBin;
