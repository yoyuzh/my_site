import { apiRequest } from '../api/client';
import type { TransferMode, TransferSessionResponse } from '../api/types';

export function createTransferSession(files: File[], mode: TransferMode) {
  return apiRequest<TransferSessionResponse>({
    url: '/transfer/sessions',
    method: 'POST',
    data: {
      mode,
      files: files.map((file) => ({
        name: file.name,
        relativePath:
          'webkitRelativePath' in file &&
          typeof file.webkitRelativePath === 'string' &&
          file.webkitRelativePath.length > 0
            ? file.webkitRelativePath
            : file.name,
        size: file.size,
        contentType: file.type || 'application/octet-stream',
      })),
    },
  });
}

export function listMyOfflineTransferSessions() {
  return apiRequest<TransferSessionResponse[]>({
    url: '/transfer/sessions/offline/mine',
    method: 'GET',
  });
}
