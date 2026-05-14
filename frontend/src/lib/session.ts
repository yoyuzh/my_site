import { getLocalStorageItem, removeLocalStorageItem, setLocalStorageItem } from './browser-storage';

export type PortalUserRole = 'USER' | 'MODERATOR' | 'ADMIN';

export type PortalUser = {
  id: number;
  username: string;
  displayName?: string | null;
  email: string;
  phoneNumber?: string | null;
  bio?: string | null;
  preferredLanguage?: string | null;
  avatarUrl?: string | null;
  role: PortalUserRole;
  createdAt: string;
  storageQuotaBytes: number;
  maxUploadSizeBytes: number;
};

export type PortalSession = {
  token?: string;
  accessToken: string;
  refreshToken: string;
  user: PortalUser;
};

const SESSION_KEY = 'portal-session';

export function canAccessAdmin(role?: PortalUserRole | null) {
  return role === 'MODERATOR' || role === 'ADMIN';
}

export function getDefaultSignedInRoute(role?: PortalUserRole | null) {
  return '/dashboard/files';
}

export function getSession() {
  const raw = getLocalStorageItem(SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as PortalSession;
  } catch {
    removeLocalStorageItem(SESSION_KEY);
    return null;
  }
}

export function setSession(session: PortalSession) {
  setLocalStorageItem(SESSION_KEY, JSON.stringify(session));
  window.dispatchEvent(new CustomEvent('portal-session-changed', { detail: session }));
}

export function clearSession() {
  removeLocalStorageItem(SESSION_KEY);
  window.dispatchEvent(new CustomEvent('portal-session-changed', { detail: null }));
}
