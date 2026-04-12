import { fetchApi } from '@/src/lib/api';
import type { PageResponse } from '@/src/lib/files';

export type AdminShare = {
  id: number;
  token: string;
  shareName: string | null;
  passwordProtected: boolean;
  expired: boolean;
  createdAt: string;
  expiresAt: string | null;
  maxDownloads: number | null;
  downloadCount: number;
  viewCount: number;
  allowImport: boolean;
  allowDownload: boolean;
  ownerId: number;
  ownerUsername: string;
  ownerEmail: string;
  fileId: number;
  fileName: string;
  filePath: string;
  fileContentType: string;
  fileSize: number;
  directory: boolean;
};

export type AdminShareQuery = {
  userQuery?: string;
  fileName?: string;
  token?: string;
  passwordProtected?: 'true' | 'false' | '';
  expired?: 'true' | 'false' | '';
};

export async function getAdminShares(page = 0, size = 100, query: AdminShareQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (query.userQuery?.trim()) {
    params.set('userQuery', query.userQuery.trim());
  }

  if (query.fileName?.trim()) {
    params.set('fileName', query.fileName.trim());
  }

  if (query.token?.trim()) {
    params.set('token', query.token.trim());
  }

  if (query.passwordProtected) {
    params.set('passwordProtected', query.passwordProtected);
  }

  if (query.expired) {
    params.set('expired', query.expired);
  }

  return fetchApi<PageResponse<AdminShare>>(`/admin/shares?${params.toString()}`);
}

export async function deleteAdminShare(shareId: number) {
  return fetchApi<void>(`/admin/shares/${shareId}`, {
    method: 'DELETE',
  });
}

