import { fetchApi } from './api';

export type PageResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type FileItem = {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string;
  directory: boolean;
  createdAt: string;
};

export type RecycleBinItem = {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string;
  directory: boolean;
  createdAt: string;
  deletedAt: string;
  expiresAt: string;
};

export type DownloadUrlResponse = {
  url: string;
};

export type LegacyShareResponse = {
  token: string;
  url: string;
};

export async function listFiles(path = '/', page = 0, size = 100) {
  const params = new URLSearchParams({
    path,
    page: String(page),
    size: String(size),
  });
  return fetchApi<PageResponse<FileItem>>(`/files/list?${params.toString()}`);
}

export async function listRecentFiles() {
  return fetchApi<FileItem[]>('/files/recent');
}

export async function listRecycleBin(page = 0, size = 100) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return fetchApi<PageResponse<RecycleBinItem>>(`/files/recycle-bin?${params.toString()}`);
}

export async function restoreRecycleBinItem(fileId: number) {
  return fetchApi<FileItem>(`/files/recycle-bin/${fileId}/restore`, {
    method: 'POST',
  });
}

export async function deleteFile(fileId: number) {
  return fetchApi<void>(`/files/${fileId}`, {
    method: 'DELETE',
  });
}

export async function createDirectory(path: string) {
  const params = new URLSearchParams({ path });
  return fetchApi<FileItem>(`/files/mkdir?${params.toString()}`, {
    method: 'POST',
  });
}

export async function getDownloadUrl(fileId: number) {
  return fetchApi<DownloadUrlResponse>(`/files/download/${fileId}/url`);
}

export async function createLegacyShareLink(fileId: number) {
  return fetchApi<LegacyShareResponse>(`/files/${fileId}/share-links`, {
    method: 'POST',
  });
}

export async function renameFile(fileId: number, filename: string) {
  return fetchApi<FileItem>(`/files/${fileId}/rename`, {
    method: 'PATCH',
    body: JSON.stringify({ filename }),
  });
}

export async function moveFile(fileId: number, path: string) {
  return fetchApi<FileItem>(`/files/${fileId}/move`, {
    method: 'PATCH',
    body: JSON.stringify({ path }),
  });
}

export async function copyFile(fileId: number, path: string) {
  return fetchApi<FileItem>(`/files/${fileId}/copy`, {
    method: 'POST',
    body: JSON.stringify({ path }),
  });
}

export async function searchFiles(name: string, page = 0, size = 50) {
  const params = new URLSearchParams({
    name,
    page: String(page),
    size: String(size),
  });
  return fetchApi<PageResponse<FileItem>>(`/v2/files/search?${params.toString()}`);
}
