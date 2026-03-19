import { clearStoredSession, readStoredSession } from './session';

interface ApiEnvelope<T> {
  code: number;
  msg: string;
  data: T;
}

interface ApiRequestInit extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

interface ApiUploadRequestInit {
  body: FormData;
  headers?: HeadersInit;
  method?: 'POST' | 'PUT' | 'PATCH';
  onProgress?: (progress: {loaded: number; total: number}) => void;
}

interface ApiBinaryUploadRequestInit {
  body: Blob;
  headers?: HeadersInit;
  method?: 'PUT' | 'POST';
  onProgress?: (progress: {loaded: number; total: number}) => void;
}

const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

export class ApiError extends Error {
  code?: number;
  status: number;
  isNetworkError: boolean;

  constructor(message: string, status = 500, code?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.isNetworkError = status === 0;
  }
}

function isNetworkFailure(error: unknown) {
  return error instanceof TypeError || error instanceof DOMException;
}

function sleep(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function getRetryDelayMs(attempt: number) {
  const schedule = [500, 1200, 2200];
  return schedule[Math.min(attempt, schedule.length - 1)];
}

function getMaxRetryAttempts(path: string, init: ApiRequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase();

  if (method === 'POST' && path === '/auth/login') {
    return 1;
  }

  if (method === 'PATCH' && /^\/files\/\d+\/rename$/.test(path)) {
    return 0;
  }

  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    return 2;
  }

  return -1;
}

function getRetryDelayForRequest(path: string, init: ApiRequestInit = {}, attempt: number) {
  const method = (init.method || 'GET').toUpperCase();

  if (method === 'POST' && path === '/auth/login') {
    const loginSchedule = [350, 800];
    return loginSchedule[Math.min(attempt, loginSchedule.length - 1)];
  }

  return getRetryDelayMs(attempt);
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

export function toNetworkApiError(error: unknown) {
  const fallbackMessage = '网络连接异常，请稍后重试';
  const message = error instanceof Error && error.message ? error.message : fallbackMessage;
  return new ApiError(message === 'Failed to fetch' ? fallbackMessage : message, 0);
}

export function shouldRetryRequest(
  path: string,
  init: ApiRequestInit = {},
  error: unknown,
  attempt: number,
) {
  if (!isNetworkFailure(error)) {
    return false;
  }

  return attempt <= getMaxRetryAttempts(path, init);
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

  let response: Response;
  let lastError: unknown;

  for (let attempt = 0; attempt <= 3; attempt += 1) {
    try {
      response = await fetch(resolveUrl(path), {
        ...init,
        headers,
        body: requestBody,
      });
      break;
    } catch (error) {
      lastError = error;
      if (!shouldRetryRequest(path, init, error, attempt)) {
        throw toNetworkApiError(error);
      }

      await sleep(getRetryDelayForRequest(path, init, attempt));
    }
  }

  if (!response!) {
    throw toNetworkApiError(lastError);
  }

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

export function apiUploadRequest<T>(path: string, init: ApiUploadRequestInit) {
  const session = readStoredSession();
  const headers = new Headers(init.headers);

  if (session?.token) {
    headers.set('Authorization', `Bearer ${session.token}`);
  }
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(init.method || 'POST', resolveUrl(path));

    headers.forEach((value, key) => {
      xhr.setRequestHeader(key, value);
    });

    if (init.onProgress) {
      xhr.upload.addEventListener('progress', (event) => {
        if (!event.lengthComputable) {
          return;
        }

        init.onProgress?.({
          loaded: event.loaded,
          total: event.total,
        });
      });
    }

    xhr.onerror = () => {
      reject(toNetworkApiError(new TypeError('Failed to fetch')));
    };

    xhr.onload = () => {
      const contentType = xhr.getResponseHeader('content-type') || '';

      if (xhr.status === 401 || xhr.status === 403) {
        clearStoredSession();
      }

      if (!contentType.includes('application/json')) {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(undefined as T);
          return;
        }

        reject(new ApiError(`请求失败 (${xhr.status})`, xhr.status));
        return;
      }

      const payload = JSON.parse(xhr.responseText) as ApiEnvelope<T>;
      if (xhr.status < 200 || xhr.status >= 300 || payload.code !== 0) {
        if (xhr.status === 401 || payload.code === 401) {
          clearStoredSession();
        }
        reject(new ApiError(payload.msg || `请求失败 (${xhr.status})`, xhr.status, payload.code));
        return;
      }

      resolve(payload.data);
    };

    xhr.send(init.body);
  });
}

export function apiBinaryUploadRequest(path: string, init: ApiBinaryUploadRequestInit) {
  const headers = new Headers(init.headers);

  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(init.method || 'PUT', resolveUrl(path));

    headers.forEach((value, key) => {
      xhr.setRequestHeader(key, value);
    });

    if (init.onProgress) {
      xhr.upload.addEventListener('progress', (event) => {
        if (!event.lengthComputable) {
          return;
        }

        init.onProgress?.({
          loaded: event.loaded,
          total: event.total,
        });
      });
    }

    xhr.onerror = () => {
      reject(toNetworkApiError(new TypeError('Failed to fetch')));
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
        return;
      }

      reject(new ApiError(`请求失败 (${xhr.status})`, xhr.status));
    };

    xhr.send(init.body);
  });
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
