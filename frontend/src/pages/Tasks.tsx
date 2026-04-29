import React, { useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useRemoteDownloads, useTasks } from '../api/queries';
import { formatDateTime } from '../lib/format';
import {
  getRemoteDownloadStatusLabel,
  getTaskStatusLabel,
  getTaskTypeLabel,
  readTaskPublicState,
  resolveRemoteDownloadStatus,
} from '../lib/tasks';
import type { BackgroundTask, RemoteDownloadListItem } from '../api/types';
import { UnifiedList, UnifiedListRow, UnifiedPageContent, UnifiedListColumn } from '../components/common/UnifiedPageContent';
import { Box, Typography, Stack, alpha } from '@mui/material';
import { CheckCircle2 } from 'lucide-react';

const columns: UnifiedListColumn[] = [
  { label: '任务类型', flex: 1 },
  { label: '状态', width: 140 },
  { label: '创建时间', width: 180 },
  { label: '任务 ID / 信息', flex: 1.5, align: 'right' },
];

const Tasks: React.FC = () => {
  const [page, setPage] = useState(1);
  const { data, isLoading, isError } = useTasks(page, 20);
  const { data: remoteDownloads } = useRemoteDownloads();

  const remoteDownloadsByBackgroundTaskId = useMemo(() => {
    const entries = remoteDownloads?.map((item) => [item.backgroundTaskId, item] as const) ?? [];
    return new Map<number, RemoteDownloadListItem>(
      entries.filter((entry): entry is readonly [number, RemoteDownloadListItem] => entry[0] != null),
    );
  }, [remoteDownloads]);

  function getResolvedTaskStatus(task: BackgroundTask) {
    if (task.type !== 'REMOTE_DOWNLOAD') {
      return task.status;
    }

    const remoteDownload = remoteDownloadsByBackgroundTaskId.get(task.id);
    const taskState = readTaskPublicState(task.publicStateJson);
    return resolveRemoteDownloadStatus({
      remoteStatus: remoteDownload?.status,
      taskStatus: task.status,
      phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    });
  }

  const tasks = data?.items ?? [];

  return (
    <DashboardLayout title="任务 Tasks">
      <UnifiedPageContent
        isLoading={isLoading}
        isError={isError}
        isEmpty={tasks.length === 0}
        emptyText="当前没有任务"
        emptyIcon={
          <Box sx={{ width: 64, height: 64, borderRadius: '50%', bgcolor: alpha('#4F7CFF', 0.1), display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#4F7CFF' }}>
            <CheckCircle2 size={32} />
          </Box>
        }
        pagination={{
          count: data?.pagination.total_pages || 0,
          page: page,
          onChange: setPage,
        }}
      >
        <UnifiedList columns={columns}>
          {tasks.map((task: BackgroundTask) => {
            const resolvedStatus = getResolvedTaskStatus(task);
            return (
              <UnifiedListRow key={task.id} columns={columns}>
                <Typography variant="subtitle2" fontWeight={700}>
                  {getTaskTypeLabel(task.type)}
                </Typography>
                <Box>
                  <Typography variant="body2" sx={{ 
                    px: 1, 
                    py: 0.25, 
                    borderRadius: 1, 
                    bgcolor: 'action.hover', 
                    display: 'inline-block',
                    fontSize: '0.75rem',
                    fontWeight: 600
                  }}>
                    {task.type === 'REMOTE_DOWNLOAD'
                      ? getRemoteDownloadStatusLabel(resolvedStatus)
                      : getTaskStatusLabel(resolvedStatus)}
                  </Typography>
                </Box>
                <Typography variant="body2" color="text.secondary">
                  {formatDateTime(task.createdAt)}
                </Typography>
                <Stack alignItems="flex-end">
                  <Typography variant="caption" fontWeight={700} color="text.secondary">
                    ID #{task.id}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: 300 }}>
                    {task.errorMessage || task.correlationId || '无附加信息'}
                  </Typography>
                </Stack>
              </UnifiedListRow>
            );
          })}
        </UnifiedList>
      </UnifiedPageContent>
    </DashboardLayout>
  );
};

export default Tasks;
