import { fetchApi } from '@/src/lib/api';
import type { PageResponse } from '@/src/lib/files';

export type AdminSummary = {
  totalUsers: number;
  totalFiles: number;
  totalStorageBytes: number;
  downloadTrafficBytes: number;
  requestCount: number;
  transferUsageBytes: number;
  offlineTransferStorageBytes: number;
  offlineTransferStorageLimitBytes: number;
  dailyActiveUsers: Array<{
    date: string;
    count: number;
    usernames: string[];
  }>;
  requestTimeline: Array<{
    hour: number;
    requestCount: number;
  }>;
  inviteCode: string;
};

export type AdminFile = {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string;
  directory: boolean;
  createdAt: string;
  ownerId: number;
  ownerUsername: string;
  ownerEmail: string;
};

export async function getAdminSummary() {
  return fetchApi<AdminSummary>('/admin/summary');
}

export async function listAdminFiles(page = 0, size = 50, query = '', ownerQuery = '') {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    query,
    ownerQuery,
  });
  return fetchApi<PageResponse<AdminFile>>(`/admin/files?${params.toString()}`);
}

export async function deleteAdminFile(fileId: number) {
  return fetchApi<void>(`/admin/files/${fileId}`, {
    method: 'DELETE',
  });
}

