import { apiRequest } from '../api/client';
import type {
  LookupTransferSessionResponse,
  PollTransferSignalsResponse,
  TransferMode,
  TransferSessionResponse,
} from '../api/types';

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

export function uploadOfflineTransferFile(sessionId: string, fileId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return apiRequest<void>({
    url: `/transfer/sessions/${sessionId}/files/${fileId}/content`,
    method: 'POST',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}

export function listMyOfflineTransferSessions() {
  return apiRequest<TransferSessionResponse[]>({
    url: '/transfer/sessions/offline/mine',
    method: 'GET',
  });
}

export function lookupTransferSession(pickupCode: string) {
  return apiRequest<LookupTransferSessionResponse | null>({
    url: '/transfer/sessions/lookup',
    method: 'GET',
    params: { pickupCode },
    authRequired: false,
  });
}

export function joinTransferSession(sessionId: string) {
  return apiRequest<TransferSessionResponse>({
    url: `/transfer/sessions/${sessionId}/join`,
    method: 'POST',
    authRequired: false,
  });
}

export function buildOfflineTransferDownloadUrl(sessionId: string, fileId: string) {
  return `/api/transfer/sessions/${sessionId}/files/${fileId}/download`;
}

export function postTransferSignal(sessionId: string, role: 'sender' | 'receiver', type: string, payload: string) {
  return apiRequest<void>({
    url: `/transfer/sessions/${sessionId}/signals`,
    method: 'POST',
    params: { role },
    data: { type, payload },
    authRequired: false,
  });
}

export function pollTransferSignals(sessionId: string, role: 'sender' | 'receiver', after: number) {
  return apiRequest<PollTransferSignalsResponse>({
    url: `/transfer/sessions/${sessionId}/signals`,
    method: 'GET',
    params: { role, after },
    authRequired: false,
  });
}
