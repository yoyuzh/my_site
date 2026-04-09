import { fetchApi } from './api';

export type UploadSessionStrategy = {
  prepareUrl: string | null;
  proxyUploadUrl: string | null;
  preparePartUrlTemplate: string | null;
  recordPartUrlTemplate: string | null;
  completeUrl: string;
  proxyFieldName: string | null;
};

export type UploadSession = {
  sessionId: string;
  objectKey: string;
  directUpload: boolean;
  multipartUpload: boolean;
  uploadMode: 'PROXY' | 'DIRECT_SINGLE' | 'DIRECT_MULTIPART';
  path: string;
  filename: string;
  contentType: string;
  size: number;
  storagePolicyId: number | null;
  status: string;
  chunkSize: number;
  chunkCount: number;
  expiresAt: string;
  createdAt: string;
  updatedAt: string;
  strategy: UploadSessionStrategy;
};

export type PreparedUpload = {
  direct: boolean;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  storageName: string;
};

function buildPartUrl(template: string, partIndex: number) {
  return template.replace('{partIndex}', String(partIndex));
}

export async function createUploadSession(fileInfo: {
  path: string;
  filename: string;
  contentType: string;
  size: number;
}) {
  return fetchApi<UploadSession>('/v2/files/upload-sessions', {
    method: 'POST',
    body: JSON.stringify(fileInfo),
  });
}

export async function getUploadSession(sessionId: string) {
  return fetchApi<UploadSession>(`/v2/files/upload-sessions/${sessionId}`);
}

export async function cancelUploadSession(sessionId: string) {
  return fetchApi<UploadSession>(`/v2/files/upload-sessions/${sessionId}`, {
    method: 'DELETE',
  });
}

export async function prepareUpload(sessionId: string) {
  return fetchApi<PreparedUpload>(`/v2/files/upload-sessions/${sessionId}/prepare`);
}

export async function prepareUploadPart(sessionId: string, partIndex: number) {
  return fetchApi<PreparedUpload>(`/v2/files/upload-sessions/${sessionId}/parts/${partIndex}/prepare`);
}

export async function recordUploadedPart(sessionId: string, partIndex: number, etag: string, size: number) {
  return fetchApi<UploadSession>(`/v2/files/upload-sessions/${sessionId}/parts/${partIndex}`, {
    method: 'PUT',
    body: JSON.stringify({ etag, size }),
  });
}

export async function completeUploadSession(sessionId: string) {
  return fetchApi<UploadSession>(`/v2/files/upload-sessions/${sessionId}/complete`, {
    method: 'POST',
  });
}

export async function uploadFileWithSession(file: File, path = '/') {
  const session = await createUploadSession({
    path,
    filename: file.name,
    contentType: file.type || 'application/octet-stream',
    size: file.size,
  });

  if (session.uploadMode === 'PROXY') {
    const formData = new FormData();
    formData.append(session.strategy.proxyFieldName || 'file', file);
    await fetchApi(session.strategy.proxyUploadUrl || `/v2/files/upload-sessions/${session.sessionId}/content`, {
      method: 'POST',
      body: formData,
    });
    return getUploadSession(session.sessionId);
  }

  if (session.uploadMode === 'DIRECT_SINGLE') {
    const prepared = await prepareUpload(session.sessionId);
    await fetch(prepared.uploadUrl, {
      method: prepared.method || 'PUT',
      headers: prepared.headers,
      body: file,
    });
    return completeUploadSession(session.sessionId);
  }

  const chunkSize = Math.max(session.chunkSize || 5 * 1024 * 1024, 5 * 1024 * 1024);
  for (let partIndex = 0; partIndex < session.chunkCount; partIndex += 1) {
    const start = partIndex * chunkSize;
    const end = Math.min(file.size, start + chunkSize);
    const chunk = file.slice(start, end);
    const prepared = await prepareUploadPart(session.sessionId, partIndex);
    const uploadResponse = await fetch(prepared.uploadUrl, {
      method: prepared.method || 'PUT',
      headers: prepared.headers,
      body: chunk,
    });
    const etag = uploadResponse.headers.get('etag') ?? '';
    await recordUploadedPart(session.sessionId, partIndex, etag.replaceAll('"', ''), chunk.size);
  }

  return completeUploadSession(session.sessionId);
}
