import { fetchApi, getApiBaseUrl } from './api';

export type TransferMode = 'ONLINE' | 'OFFLINE';

export type TransferFilePayload = {
  name: string;
  relativePath: string;
  size: number;
  contentType: string;
};

export type TransferFileItem = TransferFilePayload & {
  id?: string | null;
  uploaded?: boolean | null;
};

export type TransferSessionResponse = {
  sessionId: string;
  pickupCode: string;
  mode: TransferMode;
  expiresAt: string;
  files: TransferFileItem[];
};

export type LookupTransferSessionResponse = {
  sessionId: string;
  pickupCode: string;
  mode: TransferMode;
  expiresAt: string;
};

export function sanitizePickupCode(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
}

export function getTransferFileRelativePath(file: File) {
  const rawRelativePath =
    'webkitRelativePath' in file && typeof file.webkitRelativePath === 'string' && file.webkitRelativePath
      ? file.webkitRelativePath
      : file.name;

  const normalizedPath = rawRelativePath
    .replaceAll('\\', '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .join('/');

  return normalizedPath || file.name;
}

export function toTransferFilePayload(files: File[]) {
  return files.map<TransferFilePayload>((file) => ({
    name: file.name,
    relativePath: getTransferFileRelativePath(file),
    size: file.size,
    contentType: file.type || 'application/octet-stream',
  }));
}

export function createTransferSession(files: File[], mode: TransferMode) {
  return fetchApi<TransferSessionResponse>('/transfer/sessions', {
    method: 'POST',
    body: JSON.stringify({
      mode,
      files: toTransferFilePayload(files),
    }),
  });
}

export function lookupTransferSession(pickupCode: string) {
  return fetchApi<LookupTransferSessionResponse>(
    `/transfer/sessions/lookup?pickupCode=${encodeURIComponent(sanitizePickupCode(pickupCode))}`,
    {
      auth: false,
    },
  );
}

export function joinTransferSession(sessionId: string) {
  return fetchApi<TransferSessionResponse>(`/transfer/sessions/${encodeURIComponent(sessionId)}/join`, {
    method: 'POST',
    auth: false,
  });
}

export function listMyOfflineTransferSessions() {
  return fetchApi<TransferSessionResponse[]>('/transfer/sessions/offline/mine');
}

export function uploadOfflineTransferFile(sessionId: string, fileId: string, file: File) {
  const body = new FormData();
  body.append('file', file);

  return fetchApi<void>(`/transfer/sessions/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/content`, {
    method: 'POST',
    body,
  });
}

export function buildOfflineTransferDownloadUrl(sessionId: string, fileId: string) {
  return `${getApiBaseUrl()}/transfer/sessions/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/download`;
}

export function importOfflineTransferFile(sessionId: string, fileId: string, path: string) {
  return fetchApi<{ id: number; filename: string; path: string }>(
    `/transfer/sessions/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/import`,
    {
      method: 'POST',
      body: JSON.stringify({ path }),
    },
  );
}
