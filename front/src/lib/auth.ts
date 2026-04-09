import { fetchApi } from './api';
import { clearSession, getSession, setSession, type PortalSession, type PortalUser } from './session';

type LoginPayload = {
  username: string;
  password: string;
};

type RegisterPayload = {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
  inviteCode: string;
};

export async function login(payload: LoginPayload) {
  const session = await fetchApi<PortalSession>('/auth/login', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  });
  setSession(session);
  return session;
}

export async function register(payload: RegisterPayload) {
  const session = await fetchApi<PortalSession>('/auth/register', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  });
  setSession(session);
  return session;
}

export async function devLogin(username = 'demo') {
  const session = await fetchApi<PortalSession>(`/auth/dev-login?username=${encodeURIComponent(username)}`, {
    method: 'POST',
    auth: false,
  });
  setSession(session);
  return session;
}

export async function getProfile() {
  const profile = await fetchApi<PortalUser>('/user/profile');
  const session = getSession();
  if (session) {
    setSession({ ...session, user: profile });
  }
  return profile;
}

export function logout() {
  clearSession();
}
