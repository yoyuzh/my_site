import { apiRequest } from './api';
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
    size: file.size,
    contentType: file.type || 'application/octet-stream',
  }));
}

export function createTransferSession(files: File[]) {
  return apiRequest<TransferSessionResponse>('/transfer/sessions', {
    method: 'POST',
    body: {
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
