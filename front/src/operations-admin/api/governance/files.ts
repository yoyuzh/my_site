import { fetchApi } from '@/src/lib/api';
import type { PageResponse } from '@/src/lib/files';

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
