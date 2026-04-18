import { fetchApi } from '@/src/lib/api';

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

export async function getAdminSummary() {
  return fetchApi<AdminSummary>('/admin/summary');
}
