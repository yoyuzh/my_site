import { apiRequest } from '../api/client';
import { getSession, setSession, clearSession, type PortalSession, type PortalUser } from './session';

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
  const session = await apiRequest<PortalSession>({
    url: '/auth/login',
    method: 'POST',
    authRequired: false,
    data: payload,
  });
  setSession(session);
  return session;
}

export async function register(payload: RegisterPayload) {
  const session = await apiRequest<PortalSession>({
    url: '/auth/register',
    method: 'POST',
    authRequired: false,
    data: payload,
  });
  setSession(session);
  return session;
}

export async function getProfile() {
  const profile = await apiRequest<PortalUser>({
    url: '/user/profile',
    method: 'GET',
  });
  const session = getSession();
  if (session) {
    setSession({ ...session, user: profile });
  }
  return profile;
}

export function logout() {
  clearSession();
}
