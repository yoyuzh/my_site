import { apiRequest } from '../api/client';
import type { FileItem, QueryPage, ShareItem, SharePasswordPayload } from '../api/types';

export async function getMyShares(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<ShareItem>>({
    url: `/v2/shares/mine?${params.toString()}`,
    method: 'GET',
  });
}

export async function getShareDetails(token: string) {
  return apiRequest<ShareItem>({
    url: `/v2/shares/${token}`,
    method: 'GET',
    authRequired: false,
  });
}

export async function verifySharePassword(token: string, payload: SharePasswordPayload) {
  return apiRequest<ShareItem>({
    url: `/v2/shares/${token}/verify-password`,
    method: 'POST',
    authRequired: false,
    data: payload,
  });
}

export async function importShare(token: string, path: string, password?: string) {
  return apiRequest<FileItem>({
    url: `/v2/shares/${token}/import`,
    method: 'POST',
    data: { path, password },
  });
}

export function buildShareDownloadUrl(token: string, password?: string) {
  const url = new URL(`/api/v2/shares/${token}`, window.location.origin);
  url.searchParams.set('download', '1');
  if (password) {
    url.searchParams.set('password', password);
  }
  return url.toString();
}
