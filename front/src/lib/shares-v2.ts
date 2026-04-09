import { fetchApi } from './api';
import type { FileItem, PageResponse } from './files';

export type ShareItem = {
  id: number;
  token: string;
  shareName: string | null;
  ownerUsername: string;
  passwordRequired: boolean;
  passwordVerified: boolean;
  allowImport: boolean;
  allowDownload: boolean;
  maxDownloads: number | null;
  downloadCount: number;
  viewCount: number;
  expiresAt: string | null;
  createdAt: string;
  file: FileItem;
};

export type CreateSharePayload = {
  fileId: number;
  password?: string;
  expiresAt?: string | null;
  maxDownloads?: number | null;
  allowImport?: boolean;
  allowDownload?: boolean;
  shareName?: string;
};

export async function getMyShares(page = 0, size = 50) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return fetchApi<PageResponse<ShareItem>>(`/v2/shares/mine?${params.toString()}`);
}

export async function createShare(payload: CreateSharePayload) {
  return fetchApi<ShareItem>('/v2/shares', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function deleteShare(shareId: number) {
  return fetchApi<void>(`/v2/shares/${shareId}`, {
    method: 'DELETE',
  });
}

export async function getShareDetails(token: string) {
  return fetchApi<ShareItem>(`/v2/shares/${token}`, {
    auth: false,
  });
}

export async function verifySharePassword(token: string, password: string) {
  return fetchApi<ShareItem>(`/v2/shares/${token}/verify-password`, {
    method: 'POST',
    auth: false,
    body: JSON.stringify({ password }),
  });
}

export async function importShare(token: string, path: string, password?: string) {
  return fetchApi<FileItem>(`/v2/shares/${token}/import`, {
    method: 'POST',
    body: JSON.stringify({ path, password }),
  });
}

export function buildSharePublicUrl(token: string) {
  return `${window.location.origin}/share/${token}`;
}

export function buildShareDownloadUrl(token: string, password?: string) {
  const url = new URL(`/api/v2/shares/${token}`, window.location.origin);
  url.searchParams.set('download', '1');
  if (password) {
    url.searchParams.set('password', password);
  }
  return url.toString();
}
