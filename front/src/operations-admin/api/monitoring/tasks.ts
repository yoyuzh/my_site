import { fetchApi } from '@/src/lib/api';
import type { PageResponse } from '@/src/lib/files';

export type AdminTask = {
  id: number;
  type: string;
  status: string;
  userId: number;
  ownerUsername: string;
  ownerEmail: string;
  publicStateJson: string | null;
  correlationId: string | null;
  errorMessage: string | null;
  attemptCount: number;
  maxAttempts: number;
  nextRunAt: string | null;
  leaseOwner: string | null;
  leaseExpiresAt: string | null;
  heartbeatAt: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  failureCategory: string | null;
  retryScheduled: boolean;
  workerOwner: string | null;
  leaseState: string;
};

export type AdminTaskQuery = {
  userQuery?: string;
  type?: string;
  status?: string;
  failureCategory?: string;
  leaseState?: string;
};

export async function getAdminTasks(page = 0, size = 20, query: AdminTaskQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (query.userQuery?.trim()) {
    params.set('userQuery', query.userQuery.trim());
  }

  if (query.type?.trim()) {
    params.set('type', query.type.trim());
  }

  if (query.status?.trim()) {
    params.set('status', query.status.trim());
  }

  if (query.failureCategory?.trim()) {
    params.set('failureCategory', query.failureCategory.trim());
  }

  if (query.leaseState?.trim()) {
    params.set('leaseState', query.leaseState.trim());
  }

  return fetchApi<PageResponse<AdminTask>>(`/admin/tasks?${params.toString()}`);
}

export async function getAdminTask(taskId: number) {
  return fetchApi<AdminTask>(`/admin/tasks/${taskId}`);
}

