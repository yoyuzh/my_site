import { fetchApi } from './api';
import type { PageResponse } from './files';

export type BackgroundTask = {
  id: number;
  type: string;
  status: string;
  userId: number;
  publicStateJson: string | null;
  correlationId: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
};

export async function getTasks(page = 0, size = 50) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return fetchApi<PageResponse<BackgroundTask>>(`/v2/tasks?${params.toString()}`);
}

export async function getTaskDetails(taskId: number) {
  return fetchApi<BackgroundTask>(`/v2/tasks/${taskId}`);
}

export async function cancelTask(taskId: number) {
  return fetchApi<void>(`/v2/tasks/${taskId}`, {
    method: 'DELETE',
  });
}

export async function retryTask(taskId: number) {
  return fetchApi<BackgroundTask>(`/v2/tasks/${taskId}/retry`, {
    method: 'POST',
  });
}

export async function createMediaMetadataTask(fileId: number) {
  return fetchApi<BackgroundTask>('/v2/tasks/media-metadata', {
    method: 'POST',
    body: JSON.stringify({ fileId }),
  });
}
