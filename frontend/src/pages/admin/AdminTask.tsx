import React, { useMemo, useState } from 'react';
import {
  Box,
  Button,
  FormControl,
  MenuItem,
  Paper,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import { Filter, ListChecks } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import type { AdminColumn } from '../../components/admin/AdminDataTable';
import AdminDataTable from '../../components/admin/AdminDataTable';
import AdminFilterBar from '../../components/admin/AdminFilterBar';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { localizeTaskStatus, localizeTaskType } from '../../components/admin/adminDisplayText';
import { useAdminTasks } from '../../api/queries';
import type { AdminTask as AdminTaskItem } from '../../api/types';
import { formatDateTime } from '../../lib/format';
import { readTaskProgressSnapshot, rebuildSearchIndex } from '../../lib/tasks';

function getTaskStatusTone(status: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  const normalized = status.toUpperCase();
  if (normalized.includes('SUCCESS') || normalized.includes('DONE') || normalized.includes('FINISHED') || normalized.includes('COMPLETE')) {
    return 'success';
  }
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) {
    return 'danger';
  }
  if (normalized.includes('RUN') || normalized.includes('PROCESS') || normalized.includes('LEASE')) {
    return 'info';
  }
  if (normalized.includes('QUEUE') || normalized.includes('PENDING') || normalized.includes('WAIT')) {
    return 'warning';
  }
  return 'neutral';
}

const AdminTask: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const { data, isLoading, isError, refetch } = useAdminTasks({ page, page_size: pageSize });
  const selectedTask = useMemo(
    () => data?.items.find((task) => task.id === selectedTaskId) ?? null,
    [data, selectedTaskId],
  );
  const selectedTaskProgress = selectedTask ? readTaskProgressSnapshot(selectedTask.publicStateJson) : null;
  const rebuildMutation = useMutation({
    mutationFn: rebuildSearchIndex,
    onSuccess: () => void refetch(),
  });

  const columns = useMemo<AdminColumn<AdminTaskItem>[]>(
    () => [
      {
        id: 'select',
        header: '',
        accessor: () => <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />,
      },
      {
        id: 'id',
        header: '#',
        accessor: (task) => <Typography variant="body2" color="text.secondary">#{task.id}</Typography>,
      },
      {
        id: 'type',
        header: '任务类型',
        accessor: (task) => (
          <Stack direction="row" spacing={1.5} alignItems="center">
            <ListChecks size={16} />
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {localizeTaskType(task.type)}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'status',
        header: '状态与进度',
        accessor: (task) => {
          const progress = readTaskProgressSnapshot(task.publicStateJson);

          return (
            <Stack spacing={0.75}>
              <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                <AdminStatusBadge label={localizeTaskStatus(task.status)} tone={getTaskStatusTone(task.status)} />
                {(progress?.progressPercent || 0) > 0 ? (
                  <Typography variant="caption" color="text.secondary">
                    {progress?.progressPercent ?? 0}%
                  </Typography>
                ) : null}
              </Stack>
              <Typography variant="caption" color="text.secondary">
                已处理 {progress?.processedItems ?? 0} / {progress?.totalItems ?? 0}
              </Typography>
            </Stack>
          );
        },
      },
      {
        id: 'owner',
        header: '创建者',
        accessor: (task) => <Typography variant="body2">{task.ownerUsername || task.ownerEmail || '未知用户'}</Typography>,
      },
      {
        id: 'createdAt',
        header: '创建时间',
        accessor: (task) => <Typography variant="body2">{formatDateTime(task.createdAt)}</Typography>,
      },
      {
        id: 'actions',
        header: '操作',
        accessor: (task) => (
          <Button size="small" color="inherit" onClick={() => setSelectedTaskId(task.id)}>
            详情
          </Button>
        ),
        className: 'text-right',
      },
    ],
    [],
  );

  return (
    <AdminLayout title="离线下载与系统任务">
      <AdminPage
        title="离线下载与系统任务"
        description="查看异步任务执行状态，并触发搜索索引重建。"
        isLoading={isLoading}
        isError={isError}
        errorText="任务列表加载失败。"
        toolbar={
          <Button
            variant="contained"
            disabled={rebuildMutation.isPending}
            onClick={() => rebuildMutation.mutate()}
          >
            重建搜索索引
          </Button>
        }
      >
        <Stack spacing={2}>
          <AdminFilterBar
            actions={
              <Button
                variant={showFilters ? 'contained' : 'outlined'}
                startIcon={<Filter size={16} />}
                onClick={() => setShowFilters((value) => !value)}
              >
                筛选
              </Button>
            }
            summary={`共 ${data?.pagination?.total_items || 0} 条记录`}
          >
            <Typography variant="body2" color="text.secondary">
              当前接口暂未提供额外筛选参数，以下控件保留为治理视图占位。
            </Typography>
          </AdminFilterBar>

          {showFilters ? (
            <AdminFilterBar
              actions={
                <Stack direction="row" spacing={1}>
                  <Button variant="outlined">重置</Button>
                  <Button variant="contained">应用</Button>
                </Stack>
              }
            >
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <Select value="">
                  <MenuItem value="">全部状态</MenuItem>
                  <MenuItem value="0">排队中</MenuItem>
                  <MenuItem value="1">处理中</MenuItem>
                  <MenuItem value="2">失败</MenuItem>
                  <MenuItem value="3">取消</MenuItem>
                  <MenuItem value="4">完成</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <Select value="">
                  <MenuItem value="">全部</MenuItem>
                  <MenuItem value="1">压缩</MenuItem>
                  <MenuItem value="2">解压</MenuItem>
                  <MenuItem value="3">上传</MenuItem>
                  <MenuItem value="4">下载</MenuItem>
                </Select>
              </FormControl>
            </AdminFilterBar>
          ) : null}

          <AdminDataTable
            rows={data?.items || []}
            columns={columns}
            getRowKey={(task) => task.id}
            emptyText="暂无任务记录"
          />

          {selectedTask ? (
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 3 }}>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={2}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      任务详情 #{selectedTask.id}
                    </Typography>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
                      <AdminStatusBadge label={localizeTaskType(selectedTask.type)} tone="neutral" />
                      <AdminStatusBadge label={localizeTaskStatus(selectedTask.status)} tone={getTaskStatusTone(selectedTask.status)} />
                    </Stack>
                  </Box>
                  <Stack spacing={0.5} alignItems={{ xs: 'flex-start', lg: 'flex-end' }}>
                    <Typography variant="body2" color="text.secondary">
                      进度 {selectedTaskProgress?.progressPercent ?? 0}%
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      已处理 {selectedTaskProgress?.processedItems ?? 0} / {selectedTaskProgress?.totalItems ?? 0}
                    </Typography>
                  </Stack>
                </Stack>

                <Box sx={{ width: '100%', height: 8, borderRadius: 999, bgcolor: 'action.disabledBackground', overflow: 'hidden' }}>
                  <Box
                    sx={{
                      width: `${selectedTaskProgress?.progressPercent ?? 0}%`,
                      height: '100%',
                      bgcolor: 'primary.main',
                    }}
                  />
                </Box>

                <Typography variant="body2" color="text.secondary">
                  {selectedTaskProgress?.message || selectedTask.errorMessage || selectedTask.correlationId || '暂无附加信息'}
                </Typography>
              </Stack>
            </Paper>
          ) : null}

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Stack direction="row" spacing={2} alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  共 {data?.pagination?.total_items || 0} 条记录
                </Typography>
                <FormControl size="small" sx={{ minWidth: 120 }}>
                  <Select value={String(pageSize)} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }}>
                    <MenuItem value="10">10 条/页</MenuItem>
                    <MenuItem value="20">20 条/页</MenuItem>
                    <MenuItem value="50">50 条/页</MenuItem>
                  </Select>
                </FormControl>
              </Stack>
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" disabled={page <= 1} onClick={() => setPage(page - 1)}>
                  上一页
                </Button>
                <Button variant="contained" disableElevation>
                  {page}
                </Button>
                <Button
                  variant="outlined"
                  disabled={!data?.pagination?.total_pages || page >= data.pagination.total_pages}
                  onClick={() => setPage(page + 1)}
                >
                  下一页
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </Stack>
      </AdminPage>
    </AdminLayout>
  );
};

export default AdminTask;
