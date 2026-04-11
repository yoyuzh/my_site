import { getSession, setSession, clearSession, type PortalSession } from './session';
import { sessionRuntime } from './session-runtime';


const CLIENT_HEADER = 'X-Yoyuzh-Client';
const CLIENT_ID_HEADER = 'X-Yoyuzh-Client-Id';
const CLIENT_TYPE = 'desktop';

type ApiEnvelope<T> = {
  code: number;
  msg: string;
  data: T;
};

export class ApiError extends Error {
  status: number;
  code: number;

  constructor(message: string, status: number, code = -1) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export type FetchApiOptions = RequestInit & {
  auth?: boolean;
  rawResponse?: boolean;
  retryOnAuthFailure?: boolean;
};

export function getApiBaseUrl() {
  return '/api';
}

export function getClientId() {
  const storageKey = 'portal-client-id';
  const existing = window.localStorage.getItem(storageKey);
  if (existing) {
    return existing;
  }

  const generated = `web-${crypto.randomUUID()}`;
  window.localStorage.setItem(storageKey, generated);
  return generated;
}

function buildUrl(endpoint: string) {
  if (/^https?:\/\//.test(endpoint)) {
    return endpoint;
  }
  if (endpoint.startsWith('/api/')) {
    return endpoint;
  }
  if (endpoint.startsWith('/')) {
    return `${getApiBaseUrl()}${endpoint}`;
  }
  return `${getApiBaseUrl()}/${endpoint}`;
}

function looksLikeQuestionMarks(message: string | null | undefined) {
  if (!message) {
    return true;
  }

  const trimmed = message.trim();
  return trimmed.length === 0 || /^[?锛焆]+$/.test(trimmed);
}

function resolveFriendlyMessage(code: number, status: number, message: string) {
  if ((code === 1001 || status === 401) && looksLikeQuestionMarks(message)) {
    return '未登录或登录已过期，请先登录。';
  }
  if ((code === 1002 || status === 403) && looksLikeQuestionMarks(message)) {
    return '没有权限访问该页面。';
  }
  if (looksLikeQuestionMarks(message)) {
    return `请求失败（HTTP ${status}）`;
  }
  return message;
}

async function parseResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    if (!response.ok) {
      throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status);
    }
    return undefined as T;
  }

  const payload = (await response.json()) as ApiEnvelope<T> | T;

  if (
    typeof payload === 'object' &&
    payload !== null &&
    'code' in payload &&
    'msg' in payload &&
    'data' in payload
  ) {
    const envelope = payload as ApiEnvelope<T>;
    if (envelope.code !== 0) {
      throw new ApiError(
        resolveFriendlyMessage(envelope.code, response.status, envelope.msg),
        response.status,
        envelope.code,
      );
    }
    return envelope.data;
  }

  if (!response.ok) {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status);
  }

  return payload as T;
}

async function refreshAccessToken(session: PortalSession) {
  const response = await fetch(buildUrl('/auth/refresh'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [CLIENT_HEADER]: CLIENT_TYPE,
      [CLIENT_ID_HEADER]: getClientId(),
    },
    body: JSON.stringify({ refreshToken: session.refreshToken }),
  });

  const refreshed = await parseResponse<PortalSession>(response);
  const nextSession: PortalSession = {
    ...session,
    ...refreshed,
  };
  setSession(nextSession);
  return nextSession;
}

export async function fetchApi<T = unknown>(endpoint: string, options: FetchApiOptions = {}) {
  const {
    auth = true,
    rawResponse = false,
    retryOnAuthFailure = true,
    headers,
    body,
    ...rest
  } = options;

  const session = getSession();
  const resolvedHeaders = new Headers(headers ?? {});
  resolvedHeaders.set(CLIENT_HEADER, CLIENT_TYPE);
  resolvedHeaders.set(CLIENT_ID_HEADER, getClientId());

  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
  if (!isFormData && body != null && !resolvedHeaders.has('Content-Type')) {
    resolvedHeaders.set('Content-Type', 'application/json');
  }

  if (auth && session?.accessToken) {
    resolvedHeaders.set('Authorization', `Bearer ${session.accessToken}`);
  }

  const response = await fetch(buildUrl(endpoint), {
    ...rest,
    headers: resolvedHeaders,
    body,
  });

  if ((response.status === 401 || response.status === 403) && auth && session?.refreshToken && retryOnAuthFailure) {
    try {
      const refreshed = await refreshAccessToken(session);
      sessionRuntime.updateSession(refreshed);
      return fetchApi<T>(endpoint, {
        ...options,
        retryOnAuthFailure: false,
        headers: {
          ...(headers ?? {}),
          Authorization: `Bearer ${refreshed.accessToken}`,
        },
      });
    } catch {
      sessionRuntime.handleAuthFailure('EXPIRED');
      throw new ApiError('登录已过期，请重新登录', 401);
    }
  }

  if (response.status === 401 && !retryOnAuthFailure) {
    sessionRuntime.handleAuthFailure('EXPIRED');
  }

  if (rawResponse) {
    if (!response.ok) {
      throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status);
    }
    return response as T;
  }

  return parseResponse<T>(response);
}
