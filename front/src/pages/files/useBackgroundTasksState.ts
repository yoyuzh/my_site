import { useState, useCallback } from 'react';
import {
  cancelBackgroundTask,
  createMediaMetadataTask,
  listBackgroundTasks,
  type BackgroundTask,
} from '@/src/lib/background-tasks';
import { toBackendPath } from './useFilesDirectoryState';

export function useBackgroundTasksState() {
  const [backgroundTasks, setBackgroundTasks] = useState<BackgroundTask[]>([]);
  const [backgroundTasksLoading, setBackgroundTasksLoading] = useState(false);
  const [backgroundTasksError, setBackgroundTasksError] = useState('');
  const [backgroundTaskNotice, setBackgroundTaskNotice] = useState<{ kind: 'success' | 'error'; message: string } | null>(null);
  const [backgroundTaskActionId, setBackgroundTaskActionId] = useState<number | null>(null);

  const loadBackgroundTasks = useCallback(async () => {
    setBackgroundTasksLoading(true);
    setBackgroundTasksError('');

    try {
      const response = await listBackgroundTasks({ page: 0, size: 10 });
      setBackgroundTasks(response.items);
    } catch (error) {
      setBackgroundTasksError(error instanceof Error ? error.message : '获取后台任务失败');
    } finally {
      setBackgroundTasksLoading(false);
    }
  }, []);

  const handleCreateMediaMetadataTask = async (
    fileId: number,
    fileName: string,
    isDirectory: boolean,
    currentPath: string[]
  ) => {
    if (isDirectory) return;

    const taskPath = currentPath.length === 0 ? `/${fileName}` : `${toBackendPath(currentPath)}/${fileName}`;
    const correlationId = `media-meta:${fileId}:${Date.now()}`;

    setBackgroundTaskNotice(null);
    setBackgroundTaskActionId(fileId);

    try {
      await createMediaMetadataTask({
        fileId,
        path: taskPath,
        correlationId,
      });
      setBackgroundTaskNotice({
        kind: 'success',
        message: '已创建媒体信息提取任务，可在右侧后台任务面板查看状态。',
      });
      await loadBackgroundTasks();
    } catch (error) {
      setBackgroundTaskNotice({
        kind: 'error',
        message: error instanceof Error ? error.message : '创建媒体信息提取任务失败',
      });
    } finally {
      setBackgroundTaskActionId(null);
    }
  };

  const handleCancelBackgroundTask = async (taskId: number) => {
    setBackgroundTaskNotice(null);
    setBackgroundTaskActionId(taskId);

    try {
      await cancelBackgroundTask(taskId);
      setBackgroundTaskNotice({
        kind: 'success',
        message: `已取消任务 ${taskId}，后台列表已刷新。`,
      });
      await loadBackgroundTasks();
    } catch (error) {
      setBackgroundTaskNotice({
        kind: 'error',
        message: error instanceof Error ? error.message : '取消任务失败',
      });
    } finally {
      setBackgroundTaskActionId(null);
    }
  };

  return {
    backgroundTasks,
    backgroundTasksLoading,
    backgroundTasksError,
    backgroundTaskNotice,
    backgroundTaskActionId,
    loadBackgroundTasks,
    handleCreateMediaMetadataTask,
    handleCancelBackgroundTask,
    setBackgroundTaskNotice,
  };
}
