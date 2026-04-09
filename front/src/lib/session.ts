export type PortalUser = {
  id: number;
  username: string;
  displayName?: string | null;
  email: string;
  phoneNumber?: string | null;
  bio?: string | null;
  preferredLanguage?: string | null;
  avatarUrl?: string | null;
  role: 'USER' | 'ADMIN';
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

export function getSession() {
  const raw = window.localStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as PortalSession;
  } catch {
    window.localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function setSession(session: PortalSession) {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  window.dispatchEvent(new CustomEvent('portal-session-changed', { detail: session }));
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new CustomEvent('portal-session-changed', { detail: null }));
}
