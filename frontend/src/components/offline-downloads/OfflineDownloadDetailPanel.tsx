import React, { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { formatBytes } from '../../lib/format';
import { cancelRemoteDownload, retryRemoteDownload, selectRemoteDownloadFiles } from '../../lib/remote-downloads';
import {
  getRemoteDownloadPhaseLabel,
  getRemoteDownloadStatusLabel,
  getRemoteDownloadSourceLabel,
  getTaskProgress,
  isRemoteDownloadTerminalStatus,
  readTaskProgressSnapshot,
  readTaskPublicState,
  resolveRemoteDownloadPhase,
  resolveRemoteDownloadStatus,
} from '../../lib/tasks';
import type { RemoteDownloadDetail, TaskProgress, BackgroundTask } from '../../api/types';
import { Box, Typography, Paper, LinearProgress, Button, Checkbox, FormControlLabel, Stack, Divider, alpha } from '@mui/material';
import { Info, XCircle, RotateCcw, FileStack, Globe, Server, Folder, FileCheck, AlertTriangle } from 'lucide-react';
import { useTheme } from '../../hooks/useTheme';

interface OfflineDownloadDetailPanelProps {
  task: BackgroundTask | null;
  remoteDownload: RemoteDownloadDetail | null;
  onCancelled?: (detail: RemoteDownloadDetail) => void;
  onRetried?: (detail: RemoteDownloadDetail) => void;
}

const OfflineDownloadDetailPanel: React.FC<OfflineDownloadDetailPanelProps> = ({
  task,
  remoteDownload,
  onCancelled,
  onRetried,
}) => {
  const { theme } = useTheme();
  const isDark = theme === 'dark';
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<TaskProgress | null>(null);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressError, setProgressError] = useState<string | null>(null);
  const [selectedFileKeys, setSelectedFileKeys] = useState<string[]>([]);

  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelRemoteDownload(id),
    onSuccess: (detail) => {
      queryClient.setQueryData(['remoteDownloadDetail', detail.id], detail);
      queryClient.setQueryData(['remoteDownloads'], (current: Array<Record<string, unknown>> | undefined) =>
        current?.map((item) =>
          item.id === detail.id
            ? {
                ...item,
                status: detail.status,
                updatedAt: detail.updatedAt,
                finishedAt: detail.finishedAt,
              }
            : item,
        ) ?? current,
      );
      onCancelled?.(detail);
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloadDetail'] });
    },
  });

  const selectMutation = useMutation({
    mutationFn: ({ id, fileKeys }: { id: number; fileKeys: string[] }) =>
      selectRemoteDownloadFiles(id, fileKeys),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloadDetail'] });
      setSelectedFileKeys([]);
    },
  });

  const retryMutation = useMutation({
    mutationFn: (id: number) => retryRemoteDownload(id),
    onSuccess: (detail) => {
      queryClient.setQueryData(['remoteDownloadDetail', detail.id], detail);
      onRetried?.(detail);
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloads'] });
      void queryClient.invalidateQueries({ queryKey: ['remoteDownloadDetail'] });
    },
  });

  useEffect(() => {
    if (remoteDownload?.status === 'AWAITING_FILE_SELECTION' && remoteDownload.candidateFiles) {
      setSelectedFileKeys(
        remoteDownload.candidateFiles
          .filter((f) => f.selected)
          .map((f) => f.fileKey)
      );
    }
  }, [remoteDownload]);

  useEffect(() => {
    if (!task) {
      setProgress(null);
      setProgressError(null);
      setProgressLoading(false);
      return;
    }

    let cancelled = false;
    setProgressLoading(true);
    setProgressError(null);

    void getTaskProgress(task.id)
      .then((result) => {
        if (!cancelled) {
          setProgress(result);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setProgress(null);
          setProgressError(error instanceof Error ? error.message : '任务进度加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setProgressLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [task]);

  if (!remoteDownload) {
    return (
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', p: 4, textAlign: 'center' }}>
        <Box sx={{ 
          width: 64, 
          height: 64, 
          borderRadius: '50%', 
          bgcolor: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(15,23,42,0.03)', 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center', 
          color: 'text.disabled',
          mb: 2
        }}>
          <Info size={32} />
        </Box>
        <Typography variant="h6" fontWeight={700} sx={{ mb: 1 }}>未选中任务</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 300 }}>
          请从左侧列表中选择一个任务以查看详细进度和操作。
        </Typography>
      </Box>
    );
  }

  const taskSnapshot = task ? readTaskProgressSnapshot(task.publicStateJson) : null;
  const taskState = task ? readTaskPublicState(task.publicStateJson) : null;
  const resolvedStatus = resolveRemoteDownloadStatus({
    remoteStatus: remoteDownload.status,
    progressStatus: progress?.status,
    taskStatus: task?.status,
    phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
  });
  const resolvedPhase = resolveRemoteDownloadPhase({
    phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    status: resolvedStatus,
  });
  const resolvedProgressPercent = progress?.progressPercent ?? taskSnapshot?.progressPercent ?? 0;
  const resolvedProcessedItems = progress?.processedItems ?? taskSnapshot?.processedItems ?? 0;
  const resolvedTotalItems = progress?.totalItems ?? taskSnapshot?.totalItems ?? 0;
  const completedWithoutItemCounts = resolvedStatus === 'COMPLETED' && resolvedTotalItems === 0;

  return (
    <Box sx={{ p: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h6" fontWeight={700} sx={{ mb: 0.5 }}>
            离线下载进度
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="caption" sx={{ px: 1, py: 0.25, bgcolor: isDark ? 'rgba(79,124,255,0.15)' : 'rgba(79,124,255,0.08)', color: '#4F7CFF', borderRadius: 1, fontWeight: 700 }}>
              {getRemoteDownloadStatusLabel(resolvedStatus)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {task ? `任务 #${task.id}` : `离线下载 #${remoteDownload.id}`}
            </Typography>
          </Stack>
        </Box>
        <Box sx={{ textAlign: 'right' }}>
          <Typography variant="h5" fontWeight={800} color="primary">
            {progressLoading ? '-' : completedWithoutItemCounts ? '100%' : `${resolvedStatus === 'COMPLETED' ? 100 : resolvedProgressPercent}%`}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {completedWithoutItemCounts ? '任务已完成' : `已处理 ${resolvedProcessedItems} / ${resolvedTotalItems}`}
          </Typography>
        </Box>
      </Box>

      <Box sx={{ mb: 4 }}>
        <LinearProgress 
          variant="determinate" 
          value={completedWithoutItemCounts || resolvedStatus === 'COMPLETED' ? 100 : resolvedProgressPercent} 
          sx={{ 
            height: 8, 
            borderRadius: 4,
            bgcolor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(15,23,42,0.05)',
            '& .MuiLinearProgress-bar': {
              borderRadius: 4,
            }
          }}
        />
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          {progressError || progress?.message || taskSnapshot?.message || task?.errorMessage || '正在准备下载...'}
        </Typography>
      </Box>

      <Stack spacing={3}>
        <Paper 
          elevation={0} 
          sx={{ 
            p: 3, 
            border: '1px solid', 
            borderColor: 'divider', 
            borderRadius: 2,
            bgcolor: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(15,23,42,0.01)'
          }}
        >
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
            <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              任务详情
            </Typography>
            <Stack direction="row" spacing={1}>
              {isRemoteDownloadTerminalStatus(resolvedStatus) ? (
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<RotateCcw size={14} />}
                  onClick={() => retryMutation.mutate(remoteDownload.id)}
                  disabled={retryMutation.isPending}
                  sx={{ borderRadius: 1.5, textTransform: 'none', px: 2, boxShadow: 'none' }}
                >
                  {retryMutation.isPending ? '重新提交中...' : '重新下载'}
                </Button>
              ) : (
                <Button
                  variant="outlined"
                  color="error"
                  size="small"
                  startIcon={<XCircle size={14} />}
                  onClick={() => cancelMutation.mutate(remoteDownload.id)}
                  disabled={cancelMutation.isPending}
                  sx={{ borderRadius: 1.5, textTransform: 'none', px: 2 }}
                >
                  {cancelMutation.isPending ? '取消中...' : '取消任务'}
                </Button>
              )}
            </Stack>
          </Box>
          
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 2.5 }}>
            <InfoItem icon={<FileStack size={16} />} label="文件名" value={remoteDownload.filename || '-'} />
            <InfoItem icon={<FileStack size={16} />} label="当前阶段" value={getRemoteDownloadPhaseLabel(resolvedPhase)} />
            <InfoItem icon={<Globe size={16} />} label="来源类型" value={getRemoteDownloadSourceLabel(String(taskState?.sourceType ?? remoteDownload.sourceType ?? ''))} />
            <InfoItem icon={<Server size={16} />} label="下载引擎" value={String(taskState?.engineType ?? remoteDownload.engineType ?? '-')} />
            <InfoItem icon={<Folder size={16} />} label="目标目录" value={remoteDownload.targetPath} />
            <InfoItem icon={<FileCheck size={16} />} label="已选/已导入" value={`${remoteDownload.selectedFileCount} / ${remoteDownload.importedFileCount}`} />
          </Box>

          <Box sx={{ mt: 3 }}>
            <Typography variant="caption" color="text.secondary" fontWeight={600}>
              下载地址
            </Typography>
            <Typography
              variant="body2"
              title={remoteDownload.sourceValue}
              sx={{ mt: 0.5, maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
            >
              {remoteDownload.sourceValue}
            </Typography>
          </Box>

          {remoteDownload.failureMessage && (
            <Box sx={{ mt: 3, p: 2, borderRadius: 1.5, bgcolor: alpha('#EF4444', 0.1), border: '1px solid', borderColor: alpha('#EF4444', 0.2), display: 'flex', gap: 1.5 }}>
              <AlertTriangle size={18} className="text-red-500 shrink-0" />
              <Typography variant="body2" color="error.main" fontWeight={500}>
                失败原因：{remoteDownload.failureMessage}
              </Typography>
            </Box>
          )}
        </Paper>

        {resolvedStatus === 'AWAITING_FILE_SELECTION' && remoteDownload.candidateFiles && (
          <Paper 
            elevation={0} 
            sx={{ 
              border: '1px solid', 
              borderColor: 'divider', 
              borderRadius: 2,
              overflow: 'hidden'
            }}
          >
            <Box sx={{ p: 2, bgcolor: isDark ? 'rgba(255,255,255,0.035)' : 'rgba(15,23,42,0.03)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="subtitle2" fontWeight={700}>选择下载文件</Typography>
              <Typography variant="caption" color="text.secondary">已选择 {selectedFileKeys.length} 个</Typography>
            </Box>
            <Divider />
            <Box sx={{ maxH: 320, overflowY: 'auto' }}>
              {remoteDownload.candidateFiles.map((file) => (
                <Box 
                  key={file.fileKey} 
                  sx={{ 
                    px: 2, 
                    py: 1.5, 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: 1.5, 
                    borderBottom: '1px solid', 
                    borderColor: 'divider',
                    '&:last-child': { borderBottom: 'none' },
                    '&:hover': { bgcolor: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(15,23,42,0.01)' }
                  }}
                >
                  <Checkbox 
                    size="small"
                    checked={selectedFileKeys.includes(file.fileKey)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setSelectedFileKeys((prev) => [...prev, file.fileKey]);
                      } else {
                        setSelectedFileKeys((prev) => prev.filter((k) => k !== file.fileKey));
                      }
                    }}
                  />
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography variant="body2" fontWeight={500} noWrap>{file.relativePath}</Typography>
                    <Typography variant="caption" color="text.secondary">{formatBytes(file.size)}</Typography>
                  </Box>
                </Box>
              ))}
            </Box>
            <Divider />
            <Box sx={{ p: 2, bgcolor: isDark ? 'rgba(255,255,255,0.01)' : 'rgba(15,23,42,0.01)' }}>
              <Button
                fullWidth
                variant="contained"
                onClick={() => selectMutation.mutate({ id: remoteDownload.id, fileKeys: selectedFileKeys })}
                disabled={selectMutation.isPending || selectedFileKeys.length === 0}
                sx={{ borderRadius: 1.5, py: 1, fontWeight: 700, boxShadow: 'none' }}
              >
                {selectMutation.isPending ? '提交中...' : '开始下载已选文件'}
              </Button>
            </Box>
          </Paper>
        )}
      </Stack>
    </Box>
  );
};

interface InfoItemProps {
  icon: React.ReactNode;
  label: string;
  value: string;
}

const InfoItem: React.FC<InfoItemProps> = ({ icon, label, value }) => (
  <Box>
    <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
      <Box sx={{ color: 'text.disabled', display: 'flex' }}>{icon}</Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600}>
        {label}
      </Typography>
    </Stack>
    <Typography variant="body2" fontWeight={600} noWrap sx={{ pl: 3.25 }}>
      {value}
    </Typography>
  </Box>
);

export default OfflineDownloadDetailPanel;
