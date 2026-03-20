import { apiRequest } from './api';
import { normalizeNetdiskTargetPath } from './netdisk-upload';
import type { FileMetadata } from './types';

export function copyFileToNetdiskPath(fileId: number, path: string) {
  return apiRequest<FileMetadata>(`/files/${fileId}/copy`, {
    method: 'POST',
    body: {
      path: normalizeNetdiskTargetPath(path, '/'),
    },
  });
}
