import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { clearSession, getSession, setSession, type PortalSession } from '../lib/session';
import { getLocalStorageItem, setLocalStorageItem } from '../lib/browser-storage';

const CLIENT_HEADER = 'X-Yoyuzh-Client';
const CLIENT_ID_HEADER = 'X-Yoyuzh-Client-Id';
const CLIENT_TYPE = 'desktop';

function getDefaultApiBaseUrl() {
  if (typeof window === 'undefined') {
    return '/api';
  }

  const hostname = window.location.hostname.toLowerCase();
  if (hostname === 'yoyuzh.xyz' || hostname === 'www.yoyuzh.xyz') {
    return 'https://api.yoyuzh.xyz/api';
  }

  return '/api';
}

function shouldPreferLocalProxyInDev() {
  if (typeof window === 'undefined') {
    return false;
  }

  const isDev = (import.meta as ImportMeta & { env?: Record<string, string | boolean | undefined> }).env?.DEV === true;
  if (!isDev) {
    return false;
  }

  const hostname = window.location.hostname.toLowerCase();
  return hostname === 'localhost' || hostname === '127.0.0.1';
}

function normalizeApiBaseUrl(value: string) {
  try {
    const url = new URL(value);
    url.pathname = url.pathname.replace(/\/+$/, '');

    const isLocalHost = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
    const hasExplicitPort = url.port.length > 0;
    if (hasExplicitPort && !isLocalHost) {
      // Production traffic should go through the public origin without an explicit port.
      url.port = '';
    }

    return url.toString().replace(/\/+$/, '');
  } catch {
    return value.replace(/\/+$/, '');
  }
}

const configuredApiBaseUrl =
  (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env?.VITE_API_BASE_URL?.trim();

const rawApiBaseUrl =
  shouldPreferLocalProxyInDev()
    ? '/api'
    : configuredApiBaseUrl || getDefaultApiBaseUrl();

const API_BASE_URL = normalizeApiBaseUrl(rawApiBaseUrl);

type ApiEnvelope<T> = {
  code: number;
  msg: string;
  data: T;
};

type RetryableApiRequestConfig = ApiRequestConfig & {
  _retryOnAuthFailure?: boolean;
};

export type ApiRequestConfig = AxiosRequestConfig & {
  authRequired?: boolean;
  includeClientHeaders?: boolean;
  rawResponse?: boolean;
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

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

let refreshPromise: Promise<PortalSession> | null = null;

export function resolveApiUrl(url: string | null | undefined) {
  if (typeof url !== 'string' || url.length === 0) {
    return '';
  }

  if (typeof window === 'undefined') {
    return url;
  }

  try {
    return new URL(url).toString();
  } catch {
    const apiBase = new URL(API_BASE_URL, window.location.origin);
    const apiBasePath = apiBase.pathname.replace(/\/+$/, '');

    if (url.startsWith('/')) {
      if (apiBasePath && apiBasePath !== '/' && !url.startsWith(`${apiBasePath}/`) && url !== apiBasePath) {
        return new URL(`${apiBasePath}${url}`, apiBase.origin).toString();
      }
      return new URL(url, apiBase.origin).toString();
    }

    const baseHref = apiBase.href.endsWith('/') ? apiBase.href : `${apiBase.href}/`;
    return new URL(url, baseHref).toString();
  }
}

export function getClientId() {
  const storageKey = 'portal-client-id';
  const existing = getLocalStorageItem(storageKey);
  if (existing) {
    return existing;
  }

  const generated =
    typeof crypto.randomUUID === 'function'
      ? `web-${crypto.randomUUID()}`
      : `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  setLocalStorageItem(storageKey, generated);
  return generated;
}

function toApiError(error: unknown) {
  if (error instanceof ApiError) {
    return error;
  }

  if (error instanceof AxiosError) {
    const payload = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined;
    return new ApiError(
      typeof payload?.msg === 'string' && payload.msg.length > 0 ? payload.msg : error.message,
      error.response?.status ?? 0,
      typeof payload?.code === 'number' ? payload.code : -1,
    );
  }

  return new ApiError(error instanceof Error ? error.message : '请求失败', 0);
}

function buildHeaders(config: ApiRequestConfig) {
  const headers = new Headers(config.headers as HeadersInit | undefined);

  if (config.includeClientHeaders === true) {
    headers.set(CLIENT_HEADER, CLIENT_TYPE);
    headers.set(CLIENT_ID_HEADER, getClientId());
  }

  const session = getSession();
  if (config.authRequired !== false && session?.accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }

  return Object.fromEntries(headers.entries());
}

export function buildApiHeaders(config: ApiRequestConfig = {}) {
  return buildHeaders(config);
}

function unwrapEnvelope<T>(response: AxiosResponse<ApiEnvelope<T> | T>) {
  const payload = response.data;

  if (
    payload &&
    typeof payload === 'object' &&
    'code' in payload &&
    'msg' in payload &&
    'data' in payload
  ) {
    const envelope = payload as ApiEnvelope<T>;
    if (envelope.code !== 0) {
      throw new ApiError(envelope.msg || '请求失败', response.status, envelope.code);
    }
    return envelope.data;
  }

  return payload as T;
}

function redirectToLoginAfterAuthFailure(config: ApiRequestConfig) {
  if (config.authRequired === false || window.location.pathname === '/login') {
    return;
  }
  const from = `${window.location.pathname}${window.location.search}`;
  const loginUrl = new URL('/login', window.location.origin);
  loginUrl.searchParams.set('from', from);
  window.location.assign(`${loginUrl.pathname}${loginUrl.search}`);
}

async function refreshAccessToken(session: PortalSession) {
  const response = await apiClient.post<ApiEnvelope<PortalSession>>(
    '/auth/refresh',
    { refreshToken: session.refreshToken },
  );

  const refreshed = unwrapEnvelope(response);
  const nextSession: PortalSession = {
    ...session,
    ...refreshed,
  };
  setSession(nextSession);
  return nextSession;
}

function shouldTreatAsAuthenticationFailure(error: ApiError) {
  return error.status === 401;
}

function shouldClearSessionForRefreshFailure(session: PortalSession) {
  const currentSession = getSession();
  if (!currentSession?.refreshToken) {
    return true;
  }
  return currentSession.refreshToken === session.refreshToken;
}

async function refreshAccessTokenSingleFlight(session: PortalSession) {
  const currentSession = getSession();
  if (currentSession?.refreshToken && currentSession.refreshToken !== session.refreshToken) {
    return currentSession;
  }

  if (refreshPromise == null) {
    refreshPromise = refreshAccessToken(session).finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

export async function apiRequest<T>(config: RetryableApiRequestConfig): Promise<T> {
  const requestConfig: RetryableApiRequestConfig = {
    _retryOnAuthFailure: true,
    ...config,
    headers: buildHeaders(config),
  };

  try {
    const response = await apiClient.request<ApiEnvelope<T> | T>(requestConfig);
    if (requestConfig.rawResponse) {
      return response as T;
    }
    return unwrapEnvelope(response);
  } catch (error) {
    const apiError = toApiError(error);
    const session = getSession();

    const shouldRefresh =
      requestConfig.authRequired !== false &&
      requestConfig._retryOnAuthFailure !== false &&
      session?.refreshToken &&
      shouldTreatAsAuthenticationFailure(apiError);

    if (shouldRefresh) {
      try {
        const refreshed = await refreshAccessTokenSingleFlight(session);
        return apiRequest<T>({
          ...config,
          _retryOnAuthFailure: false,
          headers: {
            ...(config.headers ?? {}),
            Authorization: `Bearer ${refreshed.accessToken}`,
          },
        });
      } catch {
        if (shouldClearSessionForRefreshFailure(session)) {
          clearSession();
        }
      }
    }

    if (shouldTreatAsAuthenticationFailure(apiError)) {
      clearSession();
      redirectToLoginAfterAuthFailure(requestConfig);
    }

    throw apiError;
  }
}

export default apiClient;
