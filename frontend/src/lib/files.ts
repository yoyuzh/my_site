import type { AxiosResponse } from 'axios';
import { apiRequest } from '../api/client';
import type {
  ArchiveListing,
  DownloadUrlResponse,
  FavoriteFileResponse,
  FileDeleteMode,
  FileDetail,
  FileItem,
  FileTag,
  FileViewerConfig,
  MediaCategory,
  MoveConflictStrategy,
  MoveResponse,
  PreparedUploadResponse,
  QueryPage,
  RecycleBinItem,
  ThumbnailResponse,
  UploadSessionResponse,
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

export async function searchFiles({
  name,
  page = 0,
  size = 100,
  type,
  category,
}: {
  name?: string;
  page?: number;
  size?: number;
  type?: 'file' | 'directory';
  category?: MediaCategory;
}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (name?.trim()) {
    params.set('name', name.trim());
  }

  if (type) {
    params.set('type', type);
  }

  if (category) {
    params.set('category', category);
  }

  return apiRequest<QueryPage<FileItem>>({
    url: `/v2/files/search?${params.toString()}`,
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

export async function batchDeleteFiles(fileIds: number[], mode: FileDeleteMode = 'RECYCLE') {
  return apiRequest<void>({
    url: '/files/batch/delete',
    method: 'POST',
    data: {
      fileIds,
      mode,
    },
  });
}

export async function deleteRecycleBinItem(fileId: number) {
  return apiRequest<void>({
    url: `/files/recycle-bin/${fileId}`,
    method: 'DELETE',
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

export async function moveFile(
  fileId: number,
  targetPath: string,
  conflictStrategy?: MoveConflictStrategy
) {
  return apiRequest<MoveResponse>({
    url: `/files/${fileId}/move`,
    method: 'PATCH',
    data: { targetPath, conflictStrategy },
  });
}

export async function batchMoveFiles(
  fileIds: number[],
  targetPath: string,
  conflictStrategy?: MoveConflictStrategy
) {
  return apiRequest<MoveResponse>({
    url: '/files/batch/move',
    method: 'POST',
    data: { fileIds, targetPath, conflictStrategy },
  });
}

export async function updateAppearance(
  fileId: number,
  payload: { customEmoji: string | null; folderColor: string | null }
) {
  return apiRequest<FileItem>({
    url: `/files/${fileId}/appearance`,
    method: 'PATCH',
    data: payload,
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

export async function uploadFile(
  path: string,
  file: File,
  signal?: AbortSignal,
  onProgress?: (progress: { loaded: number; total: number }) => void,
) {
  const params = new URLSearchParams({ path });
  const formData = new FormData();
  formData.append('file', file);
  return apiRequest<FileItem>({
    url: `/files/upload?${params.toString()}`,
    method: 'POST',
    data: formData,
    signal,
    timeout: 0,
    onUploadProgress: (event) => {
      onProgress?.({
        loaded: event.loaded,
        total: event.total ?? file.size,
      });
    },
  });
}

export async function createUploadSession(path: string, file: File) {
  return apiRequest<UploadSessionResponse>({
    url: '/v2/files/upload-sessions',
    method: 'POST',
    data: {
      path,
      filename: file.name,
      contentType: file.type || 'application/octet-stream',
      size: file.size,
    },
  });
}

export async function getUploadSession(sessionId: string) {
  return apiRequest<UploadSessionResponse>({
    url: `/v2/files/upload-sessions/${sessionId}`,
    method: 'GET',
  });
}

export async function cancelUploadSession(sessionId: string) {
  return apiRequest<UploadSessionResponse>({
    url: `/v2/files/upload-sessions/${sessionId}`,
    method: 'DELETE',
  });
}

export async function completeUploadSession(sessionId: string) {
  return apiRequest<UploadSessionResponse>({
    url: `/v2/files/upload-sessions/${sessionId}/complete`,
    method: 'POST',
  });
}

export async function uploadUploadSessionContent(
  contentUrl: string,
  formField: string,
  file: File,
  signal?: AbortSignal,
  onProgress?: (progress: { loaded: number; total: number }) => void,
) {
  const formData = new FormData();
  formData.append(formField, file);
  return apiRequest<UploadSessionResponse>({
    url: contentUrl,
    method: 'POST',
    data: formData,
    signal,
    timeout: 0,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress: (event) => {
      onProgress?.({
        loaded: event.loaded,
        total: event.total ?? file.size,
      });
    },
  });
}

export async function prepareUploadSession(prepareUrl: string) {
  return apiRequest<PreparedUploadResponse>({
    url: prepareUrl,
    method: 'GET',
  });
}

export async function prepareMultipartPartUpload(sessionId: string, partIndex: number) {
  return apiRequest<PreparedUploadResponse>({
    url: `/v2/files/upload-sessions/${sessionId}/parts/${partIndex}/prepare`,
    method: 'GET',
  });
}

export async function recordMultipartPart(sessionId: string, partIndex: number, etag: string, size: number) {
  return apiRequest<UploadSessionResponse>({
    url: `/v2/files/upload-sessions/${sessionId}/parts/${partIndex}`,
    method: 'PUT',
    data: { etag, size },
  });
}

export async function updateFileContent(fileId: number, file: File, signal?: AbortSignal) {
  const formData = new FormData();
  formData.append('file', file);
  return apiRequest<FileItem>({
    url: `/files/${fileId}/content`,
    method: 'PATCH',
    data: formData,
    signal,
  });
}

export async function getFileDownloadUrl(fileId: number, options?: { viewer?: boolean }) {
  const params = new URLSearchParams();
  if (options?.viewer) {
    params.set('viewer', 'true');
  }
  return apiRequest<DownloadUrlResponse>({
    url: `/files/download/${fileId}/url${params.size ? `?${params.toString()}` : ''}`,
    method: 'GET',
  });
}

export async function getFileViewerConfig() {
  return apiRequest<FileViewerConfig>({
    url: '/files/viewers/config',
    method: 'GET',
  });
}

export async function getArchiveListing(fileId: number) {
  return apiRequest<ArchiveListing>({
    url: `/files/${fileId}/archive`,
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

export async function downloadArchiveEntryBlob(fileId: number, path: string) {
  const params = new URLSearchParams({ path });
  const response = await apiRequest<AxiosResponse<Blob>>({
    url: `/files/${fileId}/archive/content?${params.toString()}`,
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
