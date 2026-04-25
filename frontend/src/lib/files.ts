import type { AxiosResponse } from 'axios';
import { apiRequest } from '../api/client';
import type {
  DownloadUrlResponse,
  FavoriteFileResponse,
  FileDetail,
  FileItem,
  FileTag,
  QueryPage,
  RecycleBinItem,
  ThumbnailResponse,
} from '../api/types';

export async function listFiles(path = '/', page = 0, size = 100) {
  const params = new URLSearchParams({
    path,
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<FileItem>>({
    url: `/files/list?${params.toString()}`,
    method: 'GET',
  });
}

export async function listRecentFiles() {
  return apiRequest<FileItem[]>({
    url: '/files/recent',
    method: 'GET',
  });
}

export async function listRecycleBin(page = 0, size = 100) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<RecycleBinItem>>({
    url: `/files/recycle-bin?${params.toString()}`,
    method: 'GET',
  });
}

export async function restoreRecycleBinItem(fileId: number) {
  return apiRequest<FileItem>({
    url: `/files/recycle-bin/${fileId}/restore`,
    method: 'POST',
  });
}

export async function listFavoriteFiles() {
  return apiRequest<FavoriteFileResponse[]>({
    url: '/files/favorites',
    method: 'GET',
  });
}

export async function getFileDetail(fileId: number) {
  return apiRequest<FileDetail>({
    url: `/files/${fileId}/detail`,
    method: 'GET',
  });
}

export async function batchDeleteFiles(fileIds: number[]) {
  return apiRequest<void>({
    url: '/files/batch/delete',
    method: 'POST',
    data: {
      fileIds,
    },
  });
}

export async function setFileFavorite(fileId: number, favorite: boolean) {
  return apiRequest<FavoriteFileResponse>({
    url: `/files/${fileId}/favorite`,
    method: favorite ? 'PUT' : 'DELETE',
  });
}

export async function getThumbnail(fileId: number) {
  return apiRequest<ThumbnailResponse>({
    url: `/v2/files/${fileId}/thumbnail`,
    method: 'GET',
  });
}

export async function renameFile(fileId: number, filename: string) {
  return apiRequest<FileItem>({
    url: `/files/${fileId}/rename`,
    method: 'PATCH',
    data: { filename },
  });
}

export async function moveFile(fileId: number, path: string) {
  return apiRequest<FileItem>({
    url: `/files/${fileId}/move`,
    method: 'PATCH',
    data: { path },
  });
}

export async function copyFile(fileId: number, path: string) {
  return apiRequest<FileItem>({
    url: `/files/${fileId}/copy`,
    method: 'POST',
    data: { path },
  });
}

export async function createDirectory(path: string) {
  const params = new URLSearchParams({ path });
  return apiRequest<FileItem>({
    url: `/files/mkdir?${params.toString()}`,
    method: 'POST',
  });
}

export async function uploadFile(path: string, file: File) {
  const params = new URLSearchParams({ path });
  const formData = new FormData();
  formData.append('file', file);
  return apiRequest<FileItem>({
    url: `/files/upload?${params.toString()}`,
    method: 'POST',
    data: formData,
  });
}

export async function createLegacyShareLink(fileId: number) {
  return apiRequest<{ token: string }>({
    url: `/files/${fileId}/share-links`,
    method: 'POST',
  });
}

export async function getFileDownloadUrl(fileId: number) {
  return apiRequest<DownloadUrlResponse>({
    url: `/files/download/${fileId}/url`,
    method: 'GET',
  });
}

export async function downloadFileBlob(fileId: number) {
  const response = await apiRequest<AxiosResponse<Blob>>({
    url: `/files/download/${fileId}`,
    method: 'GET',
    responseType: 'blob',
    rawResponse: true,
  });
  return response.data;
}

export async function listTags() {
  return apiRequest<FileTag[]>({
    url: '/files/tags',
    method: 'GET',
  });
}

export async function createTag(name: string, color: string) {
  return apiRequest<FileTag>({
    url: '/files/tags',
    method: 'POST',
    data: { name, color },
  });
}

export async function updateTag(tagId: number, name: string, color: string) {
  return apiRequest<FileTag>({
    url: `/files/tags/${tagId}`,
    method: 'PATCH',
    data: { name, color },
  });
}

export async function deleteTag(tagId: number) {
  return apiRequest<void>({
    url: `/files/tags/${tagId}`,
    method: 'DELETE',
  });
}

export async function listFileTags(fileId: number) {
  return apiRequest<FileTag[]>({
    url: `/files/${fileId}/tags`,
    method: 'GET',
  });
}

export async function addFileTag(fileId: number, tagId: number) {
  return apiRequest<void>({
    url: `/files/${fileId}/tags/${tagId}`,
    method: 'PUT',
  });
}

export async function removeFileTag(fileId: number, tagId: number) {
  return apiRequest<void>({
    url: `/files/${fileId}/tags/${tagId}`,
    method: 'DELETE',
  });
}
