import type { AuthResponse, AuthSession } from './types';

const SESSION_STORAGE_KEY = 'portal-session';
const POST_LOGIN_PENDING_KEY = 'portal-post-login-pending';
export const SESSION_EVENT_NAME = 'portal-session-change';

function notifySessionChanged() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(SESSION_EVENT_NAME));
  }
}

function normalizeSession(value: unknown): AuthSession | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const candidate = value as Partial<AuthSession> & {accessToken?: string};
  const token = typeof candidate.token === 'string' && candidate.token.trim()
    ? candidate.token
    : typeof candidate.accessToken === 'string' && candidate.accessToken.trim()
      ? candidate.accessToken
      : null;

  if (!token || !candidate.user) {
    return null;
  }

  return {
    token,
    refreshToken:
      typeof candidate.refreshToken === 'string' && candidate.refreshToken.trim()
        ? candidate.refreshToken
        : null,
    user: candidate.user,
  };
}

export function createSession(auth: AuthResponse): AuthSession {
  return {
    token: auth.accessToken || auth.token,
    refreshToken: auth.refreshToken ?? null,
    user: auth.user,
  };
}

export function readStoredSession(): AuthSession | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }

  const rawValue = localStorage.getItem(SESSION_STORAGE_KEY);
  if (!rawValue) {
    return null;
  }

  try {
    const session = normalizeSession(JSON.parse(rawValue));
    if (!session) {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    }
    return session;
  } catch {
    localStorage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
}

export function saveStoredSession(session: AuthSession) {
  if (typeof localStorage === 'undefined') {
    return;
  }

  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  notifySessionChanged();
}

export function clearStoredSession() {
  if (typeof localStorage === 'undefined') {
    return;
  }

  localStorage.removeItem(SESSION_STORAGE_KEY);
  notifySessionChanged();
}

export function markPostLoginPending() {
  if (typeof sessionStorage === 'undefined') {
    return;
  }

  sessionStorage.setItem(POST_LOGIN_PENDING_KEY, String(Date.now()));
}

export function hasPostLoginPending() {
  if (typeof sessionStorage === 'undefined') {
    return false;
  }

  return sessionStorage.getItem(POST_LOGIN_PENDING_KEY) != null;
}

export function clearPostLoginPending() {
  if (typeof sessionStorage === 'undefined') {
    return;
  }

  sessionStorage.removeItem(POST_LOGIN_PENDING_KEY);
}
