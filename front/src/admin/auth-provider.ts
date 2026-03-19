import type { AuthProvider, UserIdentity } from 'react-admin';

import { clearStoredSession, readStoredSession } from '@/src/lib/session';
import type { AuthSession } from '@/src/lib/types';

export function hasAdminSession(session: AuthSession | null | undefined) {
  return Boolean(session?.token?.trim());
}

export function buildAdminIdentity(session: AuthSession): UserIdentity {
  return {
    id: String(session.user.id),
    fullName: session.user.username,
  };
}

export const portalAdminAuthProvider: AuthProvider = {
  login: async () => {
    throw new Error('请先使用门户登录页完成登录');
  },
  logout: async () => {
    clearStoredSession();
    return '/login';
  },
  checkAuth: async () => {
    if (!hasAdminSession(readStoredSession())) {
      throw new Error('当前没有可用登录状态');
    }
  },
  checkError: async (error) => {
    const status = error?.status;
    if (status === 401) {
      clearStoredSession();
      throw new Error('登录状态已失效');
    }

    if (status === 403) {
      return;
    }
  },
  getIdentity: async () => {
    const session = readStoredSession();
    if (!session) {
      throw new Error('当前没有可用登录状态');
    }

    return buildAdminIdentity(session);
  },
  getPermissions: async () => [],
};
