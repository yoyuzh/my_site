import { clearStoredSession, readStoredSession } from './session';

interface ApiEnvelope<T> {
  code: number;
  msg: string;
  data: T;
}

interface ApiRequestInit extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

export class ApiError extends Error {
  code?: number;
  status: number;

  constructor(message: string, status = 500, code?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

function resolveUrl(path: string) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }

  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE_URL}${normalizedPath}`;
}

function buildRequestBody(body: ApiRequestInit['body']) {
  if (body == null) {
    return undefined;
  }

  if (
    body instanceof FormData ||
    body instanceof Blob ||
    body instanceof URLSearchParams ||
    typeof body === 'string' ||
    body instanceof ArrayBuffer
  ) {
    return body;
  }

  return JSON.stringify(body);
}

async function parseApiError(response: Response) {
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    return new ApiError(`请求失败 (${response.status})`, response.status);
  }

  const payload = (await response.json()) as ApiEnvelope<null>;
  return new ApiError(payload.msg || `请求失败 (${response.status})`, response.status, payload.code);
}

async function performRequest(path: string, init: ApiRequestInit = {}) {
  const session = readStoredSession();
  const headers = new Headers(init.headers);
  const requestBody = buildRequestBody(init.body);

  if (session?.token) {
    headers.set('Authorization', `Bearer ${session.token}`);
  }
  if (requestBody && !(requestBody instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  const response = await fetch(resolveUrl(path), {
    ...init,
    headers,
    body: requestBody,
  });

  if (response.status === 401 || response.status === 403) {
    clearStoredSession();
  }

  return response;
}

export async function apiRequest<T>(path: string, init?: ApiRequestInit) {
  const response = await performRequest(path, init);
  const contentType = response.headers.get('content-type') || '';

  if (!contentType.includes('application/json')) {
    if (!response.ok) {
      throw new ApiError(`请求失败 (${response.status})`, response.status);
    }
    return undefined as T;
  }

  const payload = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || payload.code !== 0) {
    if (response.status === 401 || payload.code === 401) {
      clearStoredSession();
    }
    throw new ApiError(payload.msg || `请求失败 (${response.status})`, response.status, payload.code);
  }

  return payload.data;
}

export async function apiDownload(path: string) {
  const response = await performRequest(path, {
    headers: {
      Accept: '*/*',
    },
  });

  if (!response.ok) {
    throw await parseApiError(response);
  }

  return response;
}
