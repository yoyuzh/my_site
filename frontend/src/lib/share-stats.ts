import { apiRequest } from '../api/client';
import type { ShareItem, ShareStats } from '../api/types';

export async function getShareStats(token: string) {
  return apiRequest<ShareStats>({
    url: `/v2/shares/${encodeURIComponent(token)}/stats`,
    method: 'GET',
  });
}

export async function updateSharePolicy(id: number, maxDownloads: number | null) {
  return apiRequest<ShareItem>({
    url: `/v2/shares/${id}/policy`,
    method: 'PATCH',
    data: {
      maxDownloads,
    },
  });
}
