import { ApiError, apiRequest } from '@/src/lib/api';
import type { AdminSummary } from '@/src/lib/types';

type AdminSummaryRequest = () => Promise<AdminSummary>;

export async function fetchAdminAccessStatus(
  request: AdminSummaryRequest = () => apiRequest<AdminSummary>('/admin/summary'),
) {
  try {
    await request();
    return true;
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return false;
    }

    throw error;
  }
}
