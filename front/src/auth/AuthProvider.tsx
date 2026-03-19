import React, { createContext, useContext, useEffect, useState } from 'react';

import { apiRequest } from '@/src/lib/api';
import {
  clearStoredSession,
  createSession,
  readStoredSession,
  saveStoredSession,
  SESSION_EVENT_NAME,
} from '@/src/lib/session';
import type { AuthResponse, AuthSession, UserProfile } from '@/src/lib/types';

interface LoginPayload {
  username: string;
  password: string;
}

interface AuthContextValue {
  ready: boolean;
  session: AuthSession | null;
  user: UserProfile | null;
  login: (payload: LoginPayload) => Promise<void>;
  devLogin: (username?: string) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function buildSession(auth: AuthResponse): AuthSession {
  return createSession(auth);
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readStoredSession());
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const syncSession = () => {
      setSession(readStoredSession());
    };

    window.addEventListener('storage', syncSession);
    window.addEventListener(SESSION_EVENT_NAME, syncSession);

    return () => {
      window.removeEventListener('storage', syncSession);
      window.removeEventListener(SESSION_EVENT_NAME, syncSession);
    };
  }, []);

  useEffect(() => {
    let active = true;

    async function hydrate() {
      const storedSession = readStoredSession();
      if (!storedSession) {
        if (active) {
          setSession(null);
          setReady(true);
        }
        return;
      }

      try {
        const user = await apiRequest<UserProfile>('/user/profile');
        if (!active) {
          return;
        }

        const nextSession = {
          ...storedSession,
          user,
        };
        saveStoredSession(nextSession);
        setSession(nextSession);
      } catch {
        clearStoredSession();
        if (active) {
          setSession(null);
        }
      } finally {
        if (active) {
          setReady(true);
        }
      }
    }

    hydrate();

    return () => {
      active = false;
    };
  }, []);

  async function refreshProfile() {
    const currentSession = readStoredSession();
    if (!currentSession) {
      return;
    }

    const user = await apiRequest<UserProfile>('/user/profile');
    const nextSession = {
      ...currentSession,
      user,
    };
    saveStoredSession(nextSession);
    setSession(nextSession);
  }

  async function login(payload: LoginPayload) {
    const auth = await apiRequest<AuthResponse>('/auth/login', {
      method: 'POST',
      body: payload,
    });
    const nextSession = buildSession(auth);
    saveStoredSession(nextSession);
    setSession(nextSession);
  }

  async function devLogin(username?: string) {
    const params = new URLSearchParams();
    if (username?.trim()) {
      params.set('username', username.trim());
    }

    const auth = await apiRequest<AuthResponse>(
      `/auth/dev-login${params.size ? `?${params.toString()}` : ''}`,
      {
        method: 'POST',
      },
    );
    const nextSession = buildSession(auth);
    saveStoredSession(nextSession);
    setSession(nextSession);
  }

  function logout() {
    clearStoredSession();
    setSession(null);
  }

  return (
    <AuthContext.Provider
      value={{
        ready,
        session,
        user: session?.user || null,
        login,
        devLogin,
        logout,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
