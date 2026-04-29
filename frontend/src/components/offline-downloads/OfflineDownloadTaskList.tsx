import React, { useEffect, useMemo, useState } from 'react';
import { formatDateTime } from '../../lib/format';
import {
  getRemoteDownloadSourceLabel,
  getRemoteDownloadStatusLabel,
  readTaskPublicState,
  resolveRemoteDownloadStatus,
} from '../../lib/tasks';
import type { BackgroundTask, RemoteDownloadListItem } from '../../api/types';
import { Box, Typography, alpha, Collapse } from '@mui/material';
import { ChevronDown, ChevronUp, Clock, FileText, Hash } from 'lucide-react';
import { useTheme } from '../../hooks/useTheme';

interface OfflineDownloadTaskListProps {
  remoteDownloads: RemoteDownloadListItem[];
  taskMap: Map<number, BackgroundTask>;
  selectedRemoteDownloadId: number | null;
  onSelectTask: (remoteDownloadId: number) => void;
}

const ACTIVE_REMOTE_DOWNLOAD_STATUSES = new Set([
  'PENDING',
  'SUBMITTED',
  'FETCHING_METADATA',
  'AWAITING_FILE_SELECTION',
  'DOWNLOADING',
  'IMPORTING',
]);

const OfflineDownloadTaskList: React.FC<OfflineDownloadTaskListProps> = ({
  remoteDownloads,
  taskMap,
  selectedRemoteDownloadId,
  onSelectTask,
}) => {
  const { theme } = useTheme();
  const isDark = theme === 'dark';
  const [historyExpanded, setHistoryExpanded] = useState(false);

  const getEffectiveStatus = (task: RemoteDownloadListItem) => {
    const backgroundTask = task.backgroundTaskId == null ? null : taskMap.get(task.backgroundTaskId) ?? null;
    const taskState = backgroundTask ? readTaskPublicState(backgroundTask.publicStateJson) : null;
    return resolveRemoteDownloadStatus({
      remoteStatus: task.status,
      taskStatus: backgroundTask?.status,
      phase: typeof taskState?.phase === 'string' ? taskState.phase : null,
    });
  };

  const activeTasks = useMemo(
    () => remoteDownloads.filter((task) => ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(task))),
    [remoteDownloads, taskMap],
  );
  const historyTasks = useMemo(
    () => remoteDownloads.filter((task) => !ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(task))),
    [remoteDownloads, taskMap],
  );

  useEffect(() => {
    if (selectedRemoteDownloadId == null) {
      return;
    }
    const selectedTask = remoteDownloads.find((task) => task.id === selectedRemoteDownloadId);
    if (selectedTask && !ACTIVE_REMOTE_DOWNLOAD_STATUSES.has(getEffectiveStatus(selectedTask))) {
      setHistoryExpanded(true);
    }
  }, [remoteDownloads, selectedRemoteDownloadId, taskMap]);

  const renderTaskItem = (task: RemoteDownloadListItem) => {
    const backgroundTask = task.backgroundTaskId == null ? null : taskMap.get(task.backgroundTaskId) ?? null;
    const effectiveStatus = getEffectiveStatus(task);
    const isSelected = selectedRemoteDownloadId === task.id;

    return (
      <Box
        key={task.id}
        onClick={() => onSelectTask(task.id)}
        sx={{
          position: 'relative',
          cursor: 'pointer',
          p: 2,
          borderBottom: '1px solid',
          borderColor: 'divider',
          transition: 'all 0.2s ease',
          bgcolor: isSelected 
            ? (isDark ? 'rgba(79,124,255,0.15)' : 'rgba(79,124,255,0.06)')
            : 'transparent',
          '&:hover': {
            bgcolor: isSelected 
              ? (isDark ? 'rgba(79,124,255,0.2)' : 'rgba(79,124,255,0.08)')
              : (isDark ? 'rgba(255,255,255,0.03)' : 'rgba(15,23,42,0.02)'),
          },
          '&:last-child': {
            borderBottom: 'none',
          },
        }}
      >
        {isSelected && (
          <Box
            sx={{
              position: 'absolute',
              left: 0,
              top: 0,
              bottom: 0,
              width: 3,
              bgcolor: '#4F7CFF',
              borderRadius: '0 2px 2px 0',
            }}
          />
        )}
        
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Hash size={10} className={isSelected ? 'text-[#4F7CFF]' : 'text-text-muted-light dark:text-text-muted-dark'} />
            <Typography 
              variant="caption" 
              fontWeight={700} 
              sx={{ color: isSelected ? '#4F7CFF' : 'text.secondary' }}
            >
              {task.id}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Clock size={10} className="text-text-muted-light dark:text-text-muted-dark" />
            <Typography variant="caption" color="text.secondary" sx={{ fontSize: '10px' }}>
              {formatDateTime(task.createdAt)}
            </Typography>
          </Box>
        </Box>

        <Typography
          variant="body2"
          fontWeight={700}
          noWrap
          sx={{ 
            mb: 0.5,
            color: isSelected ? '#4F7CFF' : 'text.primary'
          }}
        >
          {getRemoteDownloadStatusLabel(effectiveStatus)}
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.25 }}>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: '100%' }}>
            {task.targetPath}
          </Typography>
        </Box>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="caption" color="text.disabled" sx={{ fontSize: '11px' }} noWrap>
            {getRemoteDownloadSourceLabel(task.sourceType)} · {backgroundTask?.errorMessage || backgroundTask?.correlationId || '等待更多任务信息'}
          </Typography>
        </Box>
      </Box>
    );
  };

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', bgcolor: 'transparent' }}>
      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        <Box 
          sx={{ 
            px: 2, 
            py: 1, 
            bgcolor: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(15,23,42,0.02)',
            borderBottom: '1px solid',
            borderColor: 'divider',
            display: 'flex',
            alignItems: 'center',
            gap: 1
          }}
        >
          <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            活跃任务 ({activeTasks.length})
          </Typography>
        </Box>
        {activeTasks.length > 0 ? (
          <Box>{activeTasks.map(renderTaskItem)}</Box>
        ) : (
          <Box sx={{ py: 4, textAlign: 'center' }}>
            <Typography variant="caption" color="text.disabled">暂无活跃任务</Typography>
          </Box>
        )}

        <Box
          onClick={() => setHistoryExpanded((current) => !current)}
          sx={{
            px: 2,
            py: 1,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            bgcolor: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(15,23,42,0.02)',
            borderY: '1px solid',
            borderColor: 'divider',
            transition: 'all 0.2s ease',
            '&:hover': {
              bgcolor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(15,23,42,0.04)',
            }
          }}
        >
          <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            历史记录 ({historyTasks.length})
          </Typography>
          {historyExpanded ? <ChevronUp size={14} className="text-text-muted-light dark:text-text-muted-dark" /> : <ChevronDown size={14} className="text-text-muted-light dark:text-text-muted-dark" />}
        </Box>
        <Collapse in={historyExpanded}>
          {historyTasks.length > 0 ? (
            <Box>{historyTasks.map(renderTaskItem)}</Box>
          ) : (
            <Box sx={{ py: 4, textAlign: 'center' }}>
              <Typography variant="caption" color="text.disabled">暂无历史记录</Typography>
            </Box>
          )}
        </Collapse>
      </Box>
    </Box>
  );
};

export default OfflineDownloadTaskList;
