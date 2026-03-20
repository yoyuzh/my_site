import { apiBinaryUploadRequest, apiRequest, apiUploadRequest, ApiError } from './api';
import { joinNetdiskPath, resolveTransferSaveDirectory, splitNetdiskPath } from './netdisk-paths';
import type { FileMetadata, InitiateUploadResponse } from './types';

export function normalizeNetdiskTargetPath(path: string | null | undefined, fallback = '/下载') {
  const rawPath = path?.trim();
  if (!rawPath) {
    return fallback;
  }

  return joinNetdiskPath(splitNetdiskPath(rawPath === '/' ? '/' : rawPath)) || fallback;
}

export function resolveNetdiskSaveDirectory(relativePath: string | null | undefined, rootPath = '/下载') {
  return normalizeNetdiskTargetPath(resolveTransferSaveDirectory(relativePath, rootPath));
}

export async function saveFileToNetdisk(file: File, path: string) {
  const normalizedPath = normalizeNetdiskTargetPath(path);
  const initiated = await apiRequest<InitiateUploadResponse>('/files/upload/initiate', {
    method: 'POST',
    body: {
      path: normalizedPath,
      filename: file.name,
      contentType: file.type || null,
      size: file.size,
    },
  });

  if (initiated.direct) {
    try {
      await apiBinaryUploadRequest(initiated.uploadUrl, {
        method: initiated.method,
        headers: initiated.headers,
        body: file,
      });

      return await apiRequest<FileMetadata>('/files/upload/complete', {
        method: 'POST',
        body: {
          path: normalizedPath,
          filename: file.name,
          storageName: initiated.storageName,
          contentType: file.type || null,
          size: file.size,
        },
      });
    } catch (error) {
      if (!(error instanceof ApiError && error.isNetworkError)) {
        throw error;
      }
    }
  }

  const formData = new FormData();
  formData.append('file', file);
  return apiUploadRequest<FileMetadata>(`/files/upload?path=${encodeURIComponent(normalizedPath)}`, {
    body: formData,
  });
}
