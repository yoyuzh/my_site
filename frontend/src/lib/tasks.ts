import { apiRequest } from '../api/client';
import type { BackgroundTask, QueryPage, TaskProgress } from '../api/types';

export async function getTasks(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<BackgroundTask>>({
    url: `/v2/tasks?${params.toString()}`,
    method: 'GET',
  });
}

export async function cancelTask(taskId: number) {
  return apiRequest<BackgroundTask>({
    url: `/v2/tasks/${taskId}`,
    method: 'DELETE',
  });
}

export async function retryTask(taskId: number) {
  return apiRequest<BackgroundTask>({
    url: `/v2/tasks/${taskId}/retry`,
    method: 'POST',
  });
}

export async function getTaskProgress(taskId: number) {
  return apiRequest<TaskProgress>({
    url: `/v2/tasks/${taskId}/progress`,
    method: 'GET',
  });
}

export async function rebuildSearchIndex() {
  return apiRequest<BackgroundTask>({
    url: '/v2/tasks/search-index/rebuild',
    method: 'POST',
  });
}

export function readTaskProgressSnapshot(publicStateJson: string | null | undefined): TaskProgress | null {
  if (!publicStateJson) {
    return null;
  }

  try {
    const state = JSON.parse(publicStateJson) as Record<string, unknown>;
    const processedItems = readCount(state.processedItems) ?? sumCounts(state.processedFileCount, state.processedDirectoryCount);
    const totalItems = readCount(state.totalItems) ?? sumCounts(state.totalFileCount, state.totalDirectoryCount);
    const explicitPercent = readCount(state.progressPercent);
    const progressPercent =
      explicitPercent == null
        ? totalItems > 0
          ? Math.min(100, Math.max(0, Math.floor((processedItems * 100) / totalItems)))
          : 0
        : Math.min(100, Math.max(0, explicitPercent));

    return {
      taskId: readCount(state.taskId) ?? 0,
      status: typeof state.phase === 'string' ? state.phase : '',
      progressPercent,
      processedItems,
      totalItems,
      message: typeof state.message === 'string' ? state.message : '',
    };
  } catch {
    return null;
  }
}

function readCount(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return null;
}

function sumCounts(first: unknown, second: unknown) {
  return (readCount(first) ?? 0) + (readCount(second) ?? 0);
}
