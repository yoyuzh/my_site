import { apiV2Request } from './api';
import type { PageResponse } from './types';

export type BackgroundTaskType = 'ARCHIVE' | 'EXTRACT' | 'MEDIA_META';

export type BackgroundTaskStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface BackgroundTask {
  id: number;
  type: BackgroundTaskType;
  status: BackgroundTaskStatus;
  userId: number;
  publicStateJson: string;
  correlationId: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}

export type BackgroundTaskState = Record<string, unknown>;

export interface BackgroundTaskPage extends PageResponse<BackgroundTask> {}

export interface ListBackgroundTasksParams {
  page?: number;
  size?: number;
}

export interface CreateMediaMetadataTaskParams {
  fileId: number;
  path: string;
  correlationId?: string;
}

function appendNumberParam(searchParams: URLSearchParams, key: string, value?: number) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return;
  }

  searchParams.set(key, String(value));
}

export function buildBackgroundTasksPath(params: ListBackgroundTasksParams = {}) {
  const searchParams = new URLSearchParams();
  appendNumberParam(searchParams, 'page', params.page ?? 0);
  appendNumberParam(searchParams, 'size', params.size ?? 10);

  const query = searchParams.toString();
  return query ? `/tasks?${query}` : '/tasks';
}

export function listBackgroundTasks(params: ListBackgroundTasksParams = {}) {
  return apiV2Request<BackgroundTaskPage>(buildBackgroundTasksPath(params));
}

export function getBackgroundTask(id: number) {
  return apiV2Request<BackgroundTask>(`/tasks/${id}`);
}

export function cancelBackgroundTask(id: number) {
  return apiV2Request<BackgroundTask>(`/tasks/${id}`, {
    method: 'DELETE',
  });
}

export function createMediaMetadataTask(params: CreateMediaMetadataTaskParams) {
  return apiV2Request<BackgroundTask>('/tasks/media-metadata', {
    method: 'POST',
    body: {
      fileId: params.fileId,
      path: params.path,
      correlationId: params.correlationId,
    },
  });
}

export function parseBackgroundTaskState(publicStateJson?: string | null): BackgroundTaskState {
  if (!publicStateJson) {
    return {};
  }

  try {
    const parsed = JSON.parse(publicStateJson);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }

    return parsed as BackgroundTaskState;
  } catch {
    return {};
  }
}
