import type { AuthSession } from './types';

const SESSION_STORAGE_KEY = 'portal-session';
export const SESSION_EVENT_NAME = 'portal-session-change';

function notifySessionChanged() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(SESSION_EVENT_NAME));
  }
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
    return JSON.parse(rawValue) as AuthSession;
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
