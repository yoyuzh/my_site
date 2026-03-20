import { apiRequest } from './api';
import { normalizeNetdiskTargetPath } from './netdisk-upload';
import type { FileMetadata } from './types';

export function moveFileToNetdiskPath(fileId: number, path: string) {
  return apiRequest<FileMetadata>(`/files/${fileId}/move`, {
    method: 'PATCH',
    body: {
      path: normalizeNetdiskTargetPath(path, '/'),
    },
  });
}
