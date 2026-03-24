import type { FileMetadata, TransferMode } from './types';
import { apiRequest } from './api';
import { apiUploadRequest } from './api';
import { getTransferFileRelativePath } from './transfer-protocol';
import type {
  LookupTransferSessionResponse,
  PollTransferSignalsResponse,
  TransferSessionResponse,
} from './types';

export const DEFAULT_TRANSFER_ICE_SERVERS: RTCIceServer[] = [
  {urls: 'stun:stun.cloudflare.com:3478'},
  {urls: 'stun:stun.l.google.com:19302'},
];

export function toTransferFilePayload(files: File[]) {
  return files.map((file) => ({
    name: file.name,
    relativePath: getTransferFileRelativePath(file),
    size: file.size,
    contentType: file.type || 'application/octet-stream',
  }));
}

export function createTransferSession(files: File[], mode: TransferMode) {
  return apiRequest<TransferSessionResponse>('/transfer/sessions', {
    method: 'POST',
    body: {
      mode,
      files: toTransferFilePayload(files),
    },
  });
}

export function lookupTransferSession(pickupCode: string) {
  return apiRequest<LookupTransferSessionResponse>(
    `/transfer/sessions/lookup?pickupCode=${encodeURIComponent(pickupCode)}`,
  );
}

export function joinTransferSession(sessionId: string) {
  return apiRequest<TransferSessionResponse>(`/transfer/sessions/${encodeURIComponent(sessionId)}/join`, {
    method: 'POST',
  });
}

export function uploadOfflineTransferFile(
  sessionId: string,
  fileId: string,
  file: File,
  onProgress?: (progress: {loaded: number; total: number}) => void,
) {
  const body = new FormData();
  body.append('file', file);

  return apiUploadRequest<void>(`/transfer/sessions/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/content`, {
    body,
    onProgress,
  });
}

export function buildOfflineTransferDownloadUrl(sessionId: string, fileId: string) {
  const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  return `${apiBaseUrl}/transfer/sessions/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/download`;
}

export function importOfflineTransferFile(sessionId: string, fileId: string, path: string) {
  return apiRequest<FileMetadata>(
    `/transfer/sessions/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/import`,
    {
      method: 'POST',
      body: {
        path,
      },
    },
  );
}

export function postTransferSignal(sessionId: string, role: 'sender' | 'receiver', type: string, payload: string) {
  return apiRequest<void>(`/transfer/sessions/${encodeURIComponent(sessionId)}/signals?role=${role}`, {
    method: 'POST',
    body: {
      type,
      payload,
    },
  });
}

export function pollTransferSignals(sessionId: string, role: 'sender' | 'receiver', after: number) {
  return apiRequest<PollTransferSignalsResponse>(
    `/transfer/sessions/${encodeURIComponent(sessionId)}/signals?role=${role}&after=${after}`,
  );
}
