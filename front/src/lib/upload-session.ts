import { apiBinaryUploadRequest, apiUploadRequest, apiV2Request, ApiError } from './api';
import type {
  PreparedUploadResponse,
  UploadSessionResponse,
  UploadSessionStrategy,
} from './types';

export interface UploadFileToNetdiskOptions {
  onProgress?: (progress: {loaded: number; total: number}) => void;
  signal?: AbortSignal;
}

export interface UploadedNetdiskFileRef {
  sessionId: string;
  filename: string;
  path: string;
}

interface CreateUploadSessionRequest {
  path: string;
  filename: string;
  contentType: string | null;
  size: number;
}

interface UploadSessionPartRecordRequest {
  etag: string;
  size: number;
}

function replacePartIndex(template: string, partIndex: number) {
  return template.replace('{partIndex}', String(partIndex));
}

function toInternalApiPath(path: string) {
  return path.startsWith('/api/') ? path.slice('/api'.length) : path;
}

function getRequiredStrategyValue(value: string | null | undefined, key: keyof UploadSessionStrategy) {
  if (!value) {
    throw new Error(`上传会话缺少 ${key}，无法继续上传`);
  }
  return value;
}

export function createUploadSession(request: CreateUploadSessionRequest) {
  return apiV2Request<UploadSessionResponse>('/files/upload-sessions', {
    method: 'POST',
    body: request,
  });
}

export function getUploadSession(sessionId: string) {
  return apiV2Request<UploadSessionResponse>(`/files/upload-sessions/${sessionId}`);
}

export function cancelUploadSession(sessionId: string) {
  return apiV2Request<UploadSessionResponse>(`/files/upload-sessions/${sessionId}`, {
    method: 'DELETE',
  });
}

export function prepareSingleUploadSession(sessionId: string) {
  return apiV2Request<PreparedUploadResponse>(`/files/upload-sessions/${sessionId}/prepare`);
}

export function uploadUploadSessionContent(
  sessionId: string,
  file: File,
  options: UploadFileToNetdiskOptions = {},
) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUploadRequest<UploadSessionResponse>(`/v2/files/upload-sessions/${sessionId}/content`, {
    body: formData,
    onProgress: options.onProgress,
    signal: options.signal,
  });
}

export function prepareUploadSessionPart(sessionId: string, partIndex: number) {
  return apiV2Request<PreparedUploadResponse>(`/files/upload-sessions/${sessionId}/parts/${partIndex}/prepare`);
}

export function recordUploadSessionPart(
  sessionId: string,
  partIndex: number,
  request: UploadSessionPartRecordRequest,
) {
  return apiV2Request<UploadSessionResponse>(`/files/upload-sessions/${sessionId}/parts/${partIndex}`, {
    method: 'PUT',
    body: request,
  });
}

export function completeUploadSession(sessionId: string) {
  return apiV2Request<UploadSessionResponse>(`/files/upload-sessions/${sessionId}/complete`, {
    method: 'POST',
  });
}

async function runProxyUpload(session: UploadSessionResponse, file: File, options: UploadFileToNetdiskOptions) {
  const proxyFormField = getRequiredStrategyValue(session.strategy.proxyFormField, 'proxyFormField');
  const formData = new FormData();
  formData.append(proxyFormField, file);
  await apiUploadRequest<UploadSessionResponse>(
    toInternalApiPath(getRequiredStrategyValue(session.strategy.proxyContentUrl, 'proxyContentUrl')),
    {
      body: formData,
      onProgress: options.onProgress,
      signal: options.signal,
    },
  );
}

async function runDirectSingleUpload(session: UploadSessionResponse, file: File, options: UploadFileToNetdiskOptions) {
  const prepared = await prepareSingleUploadSession(session.sessionId);
  await apiBinaryUploadRequest(prepared.uploadUrl, {
    method: prepared.method,
    headers: prepared.headers,
    body: file,
    onProgress: options.onProgress,
    signal: options.signal,
  });
}

async function runMultipartUpload(session: UploadSessionResponse, file: File, options: UploadFileToNetdiskOptions) {
  const partPrepareUrlTemplate = getRequiredStrategyValue(session.strategy.partPrepareUrlTemplate, 'partPrepareUrlTemplate');
  const partRecordUrlTemplate = getRequiredStrategyValue(session.strategy.partRecordUrlTemplate, 'partRecordUrlTemplate');

  let uploadedBytes = 0;
  for (let partIndex = 0; partIndex < session.chunkCount; partIndex += 1) {
    const partStart = partIndex * session.chunkSize;
    const partEnd = Math.min(file.size, partStart + session.chunkSize);
    const partBlob = file.slice(partStart, partEnd);
    const prepared = await apiV2Request<PreparedUploadResponse>(
      toInternalApiPath(replacePartIndex(partPrepareUrlTemplate, partIndex)).replace('/v2', ''),
    );
    const uploadResult = await apiBinaryUploadRequest(prepared.uploadUrl, {
      method: prepared.method,
      headers: prepared.headers,
      body: partBlob,
      responseHeaders: ['etag'],
      signal: options.signal,
      onProgress: options.onProgress
        ? ({loaded, total}) => {
            options.onProgress?.({
              loaded: uploadedBytes + loaded,
              total: Math.max(file.size, uploadedBytes + total),
            });
          }
        : undefined,
    });
    const etag = uploadResult.headers.etag;
    if (!etag) {
      throw new Error('分片上传成功但未返回 etag，无法完成上传');
    }
    await apiV2Request<UploadSessionResponse>(
      toInternalApiPath(replacePartIndex(partRecordUrlTemplate, partIndex)).replace('/v2', ''),
      {
        method: 'PUT',
        body: {
          etag,
          size: partBlob.size,
        },
      },
    );
    uploadedBytes += partBlob.size;
    options.onProgress?.({
      loaded: uploadedBytes,
      total: file.size,
    });
  }
}

export async function uploadFileToNetdiskViaSession(
  file: File,
  path: string,
  options: UploadFileToNetdiskOptions = {},
): Promise<UploadedNetdiskFileRef> {
  const session = await createUploadSession({
    path,
    filename: file.name,
    contentType: file.type || null,
    size: file.size,
  });
  let shouldCancelSession = true;

  try {
    switch (session.uploadMode) {
      case 'PROXY':
        await runProxyUpload(session, file, options);
        break;
      case 'DIRECT_SINGLE':
        await runDirectSingleUpload(session, file, options);
        break;
      case 'DIRECT_MULTIPART':
        await runMultipartUpload(session, file, options);
        break;
      default:
        throw new Error(`不支持的上传模式：${String(session.uploadMode)}`);
    }

    await completeUploadSession(session.sessionId);
    shouldCancelSession = false;
    return {
      sessionId: session.sessionId,
      filename: file.name,
      path,
    };
  } catch (error) {
    if (shouldCancelSession && !(error instanceof ApiError && error.message === '上传已取消')) {
      await cancelUploadSession(session.sessionId).catch(() => undefined);
    }
    if (shouldCancelSession && error instanceof ApiError && error.message === '上传已取消') {
      await cancelUploadSession(session.sessionId).catch(() => undefined);
    }
    throw error;
  }
}
