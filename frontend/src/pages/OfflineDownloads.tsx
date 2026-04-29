import React, { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import DashboardLayout from '../components/DashboardLayout';
import { useRemoteDownloadDetail, useRemoteDownloads, useTasks } from '../api/queries';
import type { BackgroundTask, RemoteDownloadDetail } from '../api/types';
import CreateRemoteDownloadDialog from '../components/files/CreateRemoteDownloadDialog';
import OfflineDownloadTaskList from '../components/offline-downloads/OfflineDownloadTaskList';
import OfflineDownloadDetailPanel from '../components/offline-downloads/OfflineDownloadDetailPanel';
import { readTaskPublicState, resolveRemoteDownloadStatus } from '../lib/tasks';
import { UnifiedPageContent } from '../components/common/UnifiedPageContent';
import { Button, Box, Typography, alpha } from '@mui/material';
import { Plus, Download } from 'lucide-react';
import { useTheme } from '../hooks/useTheme';

const ACTIVE_REMOTE_DOWNLOAD_STATUSES = new Set([
  'PENDING',
  'SUBMITTED',
  'FETCHING_METADATA',
  'AWAITING_FILE_SELECTION',
  'DOWNLOADING',
  'IMPORTING',
]);

const OfflineDownloads: React.FC = () => {
  const queryClient = useQueryClient();
  const { theme } = useTheme();
  const isDark = theme === 'dark';
  const [selectedRemoteDownloadId, setSelectedRemoteDownloadId] = useState<number | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const { data: tasksData, isLoading: tasksLoading, isError: tasksError } = useTasks(1, 100);
  const {
    data: remoteDownloads,
    isLoading: remoteDownloadsLoading,
    isError: remoteDownloadsError,
  } = useRemoteDownloads();

  const remoteDownloadTaskMap = useMemo(() => {
    const entries =
      tasksData?.items
        .filter((task) => task.type === 'REMOTE_DOWNLOAD')
        .map((task) => [task.id, task] as const) ?? [];
    return new Map<number, BackgroundTask>(entries);
  }, [tasksData]);

  const getEffectiveStatus = (item: { status: string; backgroundTaskId: number | null }) => {
    const task = item.backgroundTaskId == null ? null : remoteDownloadTaskMap.get(item.backgroundTaskId) ?? null;
    const taskState = task ? readTaskPublicState(task.publicStateJson) : null;
    return resolveRemoteDownloadStatus({
      remoteStatus: item.status,
      taskStatus: task?.status,
      phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    });
  };

  const activeRemoteDownloads = useMemo(
    () => (remoteDownloads ?? []).filter((item) => ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(item))),
    [remoteDownloads, remoteDownloadTaskMap],
  );

  const { data: selectedRemoteDownload } = useRemoteDownloadDetail(selectedRemoteDownloadId);

  const selectedListItem = useMemo(
    () => remoteDownloads?.find((item) => item.id === selectedRemoteDownloadId) ?? null,
    [remoteDownloads, selectedRemoteDownloadId],
  );

  const selectedTask = useMemo(() => {
    const backgroundTaskId = selectedRemoteDownload?.backgroundTaskId ?? selectedListItem?.backgroundTaskId ?? null;
    if (backgroundTaskId == null) {
      return null;
    }
    return remoteDownloadTaskMap.get(backgroundTaskId) ?? null;
  }, [remoteDownloadTaskMap, selectedListItem, selectedRemoteDownload]);

  useEffect(() => {
    if (!remoteDownloads || remoteDownloads.length === 0) {
      if (selectedRemoteDownloadId != null) {
        setSelectedRemoteDownloadId(null);
      }
      return;
    }

    if (
      selectedRemoteDownloadId != null &&
      !remoteDownloads.some((item) => item.id === selectedRemoteDownloadId)
    ) {
      setSelectedRemoteDownloadId(null);
    }
  }, [remoteDownloads, remoteDownloadTaskMap, selectedRemoteDownloadId]);

  const handleCreated = (detail: RemoteDownloadDetail) => {
    void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
    setSelectedRemoteDownloadId(detail.id);
  };

  const handleCancelled = (detail: RemoteDownloadDetail) => {
    const nextActive = (remoteDownloads ?? []).find(
      (item) => item.id !== detail.id && ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(item)),
    );
    setSelectedRemoteDownloadId(nextActive?.id ?? detail.id);
  };

  const isLoading = tasksLoading || remoteDownloadsLoading;
  const hasLoadError = tasksError || remoteDownloadsError;

  return (
    <DashboardLayout title="离线下载 Offline Downloads">
      <UnifiedPageContent
        title="离线下载"
        actions={
          <Button
            variant="contained"
            startIcon={<Plus size={18} />}
            onClick={() => setCreateDialogOpen(true)}
            sx={{ 
              borderRadius: 2, 
              textTransform: 'none', 
              fontWeight: 700,
              boxShadow: 'none',
              '&:hover': { boxShadow: '0 4px 12px rgba(79, 124, 255, 0.2)' }
            }}
          >
            新建离线下载
          </Button>
        }
        isLoading={isLoading}
        isError={hasLoadError}
        isEmpty={(remoteDownloads?.length ?? 0) === 0}
        emptyText="暂无离线下载任务"
        emptyIcon={
          <Box sx={{ 
            width: 80, 
            height: 80, 
            borderRadius: '50%', 
            bgcolor: alpha('#4F7CFF', 0.1), 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center', 
            color: '#4F7CFF',
            mb: 2
          }}>
            <Download size={40} />
          </Box>
        }
      >
        <div className="flex h-full min-h-0 overflow-hidden">
          <div className="w-80 shrink-0 border-r border-[#D4DEEC] dark:border-white/8 flex flex-col">
            <div className="px-4 py-3 border-b border-[#D4DEEC] dark:border-white/8 bg-gray-50/50 dark:bg-white/[0.02]">
              <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                任务列表 ({activeRemoteDownloads.length} 活跃 / {(remoteDownloads?.length ?? 0) - activeRemoteDownloads.length} 历史)
              </Typography>
            </div>
            <div className="flex-1 overflow-hidden">
              <OfflineDownloadTaskList
                remoteDownloads={remoteDownloads ?? []}
                taskMap={remoteDownloadTaskMap}
                selectedRemoteDownloadId={selectedRemoteDownloadId}
                onSelectTask={setSelectedRemoteDownloadId}
              />
            </div>
          </div>
          <div className="flex-1 overflow-auto bg-white/30 dark:bg-white/[0.01]">
            <OfflineDownloadDetailPanel
              task={selectedTask}
              remoteDownload={selectedRemoteDownload ?? null}
              onCancelled={handleCancelled}
            />
          </div>
        </div>
      </UnifiedPageContent>

      <CreateRemoteDownloadDialog
        open={createDialogOpen}
        defaultPath="/downloads"
        onClose={() => setCreateDialogOpen(false)}
        onCreated={handleCreated}
      />
    </DashboardLayout>
  );
};

export default OfflineDownloads;
